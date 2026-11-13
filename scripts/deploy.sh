#!/usr/bin/env bash
#
# Deploys pride-universal-backend to the shared VPS: builds the jar HERE, copies it to the server, and
# (re)starts that environment's container. Two environments: dev and prod. Modelled on restobill-backend.
#
# Usage:
#   ./scripts/deploy.sh --env dev                  # build, copy, restart dev
#   ./scripts/deploy.sh --env prod --yes           # prod without the confirm prompt
#   ./scripts/deploy.sh --env prod --dry-run       # print what would happen, change nothing
#   ./scripts/deploy.sh --env prod --restart-only  # restart the container on the jar already there
#   ./scripts/deploy.sh --env prod --rollback      # swap back to the jar the last deploy replaced
#   ./scripts/deploy.sh --inspect                  # read-only: what each environment runs
#
# Flags:
#   --env <dev|prod>  Target environment. ALWAYS pass it from automation/AI runs (otherwise it asks on stdin).
#   --yes             Skip the prod confirmation.
#   --dry-run         Show the plan; change nothing.
#   --restart-only    No build or copy: recreate the container on the current jar.
#   --rollback        Swap <env>/app.jar with <env>/app.prev.jar and restart. Twice swaps back.
#   --inspect         Read-only survey of the server.
#   --skip-tests      Build without running the test suite (dev only; prod always runs it).
#   --remote-tests    Run the test suite ON THE SERVER (in a throwaway Maven container that uses the
#                     server's Docker for Testcontainers) against this exact commit, then build here
#                     without tests. For a machine with no Docker. Needs a clean tree.
#
# On the server (deploy/setup-server.sh creates all of it once, no sudo):
#   ~/pride/<env>.env          settings + secrets, never read or transported by this script
#   ~/pride/<env>/app.jar      the jar that runs; app.prev.jar is the one it replaced
#   ~/pride/<env>/uploads/     uploaded files, mounted read-write
#   container pride-api-<env>  eclipse-temurin:21-jre, network pride, 127.0.0.1:<port> → 8080
#
# Prod deploys must come from a clean tree whose HEAD is on origin/main, with the tests passing.
set -euo pipefail

RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YEL=$'\033[1;33m'; CYA=$'\033[0;36m'; NC=$'\033[0m'
info() { printf '%s%s%s\n' "$CYA" "$*" "$NC"; }
ok()   { printf '%s%s%s\n' "$GRN" "$*" "$NC"; }
warn() { printf '%s%s%s\n' "$YEL" "$*" "$NC"; }
die()  { printf '%s%s%s\n' "$RED" "ERROR: $*" "$NC" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/scripts/deploy.config.sh"
[[ -f "$CONFIG" ]] || die "missing scripts/deploy.config.sh — copy scripts/deploy.config.example.sh."
# shellcheck disable=SC1090
source "$CONFIG"
: "${DEPLOY_SSH_HOST:?set in deploy.config.sh}" "${REMOTE_HOME:?set in deploy.config.sh}" "${DEV_PORT:?}" "${PROD_PORT:?}"
JRE_IMAGE="${JRE_IMAGE:-eclipse-temurin:21-jre}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"
CONTAINER_MEMORY="${CONTAINER_MEMORY:-768m}"

ENV_NAME=""; ASSUME_YES=0; DRY_RUN=0; RESTART_ONLY=0; ROLLBACK=0; INSPECT=0; SKIP_TESTS=0; REMOTE_TESTS=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENV_NAME="${2:-}"; shift 2 ;;
    --yes) ASSUME_YES=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --restart-only) RESTART_ONLY=1; shift ;;
    --rollback) ROLLBACK=1; shift ;;
    --inspect) INSPECT=1; shift ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --remote-tests) REMOTE_TESTS=1; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) die "unknown flag $1 (see --help)" ;;
  esac
done

ssh_run() { ssh -o BatchMode=yes -o ConnectTimeout=15 "$DEPLOY_SSH_HOST" "$@"; }

if [[ $INSPECT -eq 1 ]]; then
  info "Server $DEPLOY_SSH_HOST (read-only)"
  ssh_run "docker ps -a --filter name=pride --format '  {{.Names}}\t{{.Status}}\t{{.Ports}}'; for e in dev prod; do printf '  %s: ' \$e; cat $REMOTE_HOME/\$e/VERSION 2>/dev/null || echo 'no jar'; done; curl -s -m 5 127.0.0.1:$DEV_PORT$HEALTH_PATH | head -c 80; echo ' (dev)'; curl -s -m 5 127.0.0.1:$PROD_PORT$HEALTH_PATH | head -c 80; echo ' (prod)'"
  exit 0
fi

if [[ -z "$ENV_NAME" ]]; then
  [[ -t 0 ]] || die "--env is required (dev|prod) when not running interactively."
  echo "Deploy which environment?"; echo "  1) dev   (port $DEV_PORT)"; echo "  2) prod  (port $PROD_PORT)"
  read -r -p "> " choice
  case "$choice" in 1|dev) ENV_NAME=dev ;; 2|prod) ENV_NAME=prod ;; *) die "no environment chosen" ;; esac
fi
case "$ENV_NAME" in
  dev) PORT="$DEV_PORT" ;;
  prod) PORT="$PROD_PORT" ;;
  *) die "--env must be dev or prod" ;;
esac
NAME="pride-api-$ENV_NAME"
SHA="current jar"
DIR="$REMOTE_HOME/$ENV_NAME"

if [[ "$ENV_NAME" == prod && $ROLLBACK -eq 0 && $RESTART_ONLY -eq 0 ]]; then
  [[ $SKIP_TESTS -eq 0 ]] || die "prod is never deployed with --skip-tests (use --remote-tests without Docker)."
  [[ -z "$(git -C "$ROOT" status --porcelain)" ]] || die "prod deploys need a clean working tree."
  git -C "$ROOT" fetch -q origin main
  git -C "$ROOT" merge-base --is-ancestor HEAD origin/main || die "HEAD is not on origin/main: push it first."
fi
if [[ "$ENV_NAME" == prod && $ASSUME_YES -eq 0 && $DRY_RUN -eq 0 ]]; then
  [[ -t 0 ]] || die "prod needs --yes when not running interactively."
  read -r -p "Deploy $(git -C "$ROOT" rev-parse --short HEAD) to PROD? Type 'deploy': " answer
  [[ "$answer" == deploy ]] || die "cancelled"
fi

# Recreated on every deploy so a change here takes effect. Runs as the deploy user so the uploads it
# writes belong to that user (and to the nightly backup), not to root.
RUN_CMD="mkdir -p $DIR/uploads; docker rm -f $NAME >/dev/null 2>&1 || true; docker run -d --name $NAME --network pride --restart unless-stopped \
--memory $CONTAINER_MEMORY --user \$(id -u):\$(id -g) --env-file $REMOTE_HOME/$ENV_NAME.env -p 127.0.0.1:$PORT:8080 \
-v $DIR/uploads:/app/uploads -v $DIR/app.jar:/app/app.jar:ro $JRE_IMAGE \
java -XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Kolkata -jar /app/app.jar >/dev/null"

HEALTH_CMD="for i in \$(seq 1 $HEALTH_TIMEOUT); do curl -sf -m 3 127.0.0.1:$PORT$HEALTH_PATH >/dev/null && exit 0; sleep 1; done; echo 'health check failed; last log lines:'; docker logs --tail 60 $NAME 2>&1; exit 1"

if [[ $ROLLBACK -eq 1 ]]; then
  info "Rolling $ENV_NAME back to the previous jar"
  REMOTE="set -e; cd $DIR; [ -f app.prev.jar ] || { echo 'no app.prev.jar to roll back to'; exit 1; }; mv app.jar app.swap.jar; mv app.prev.jar app.jar; mv app.swap.jar app.prev.jar; $RUN_CMD; $HEALTH_CMD"
  [[ $DRY_RUN -eq 1 ]] && { echo "$REMOTE"; exit 0; }
  ssh_run "$REMOTE" && ok "$ENV_NAME rolled back and healthy."; exit $?
fi

if [[ $REMOTE_TESTS -eq 1 && $RESTART_ONLY -eq 0 && $ROLLBACK -eq 0 ]]; then
  [[ -z "$(git -C "$ROOT" status --porcelain)" ]] || die "--remote-tests needs a clean tree: it tests the commit, not your edits."
  info "Running the test suite on $DEPLOY_SSH_HOST against $(git -C "$ROOT" rev-parse --short HEAD)"
  if [[ $DRY_RUN -eq 0 ]]; then
    git -C "$ROOT" archive --format=tar HEAD | ssh_run "rm -rf $REMOTE_HOME/ci && mkdir -p $REMOTE_HOME/ci && tar -x -C $REMOTE_HOME/ci"
    ssh_run "docker run --rm --name pride-ci-tests --network host --memory 2g -v /var/run/docker.sock:/var/run/docker.sock       -v $REMOTE_HOME/ci:/src -v pride-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -q test"       || die "tests failed on the server; nothing deployed"
    ok "All tests passed on the server."
  fi
  SKIP_TESTS=1
fi

if [[ $RESTART_ONLY -eq 0 ]]; then
  JAR="$ROOT/target/pride-universal-backend.jar"
  SHA="$(git -C "$ROOT" rev-parse --short HEAD)$([[ -n "$(git -C "$ROOT" status --porcelain)" ]] && echo -dirty || true)"
  MVN="./mvnw"; [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]] && MVN="./mvnw.cmd"
  MVN_ARGS=(-B -q clean package); [[ $SKIP_TESTS -eq 1 ]] && MVN_ARGS+=(-DskipTests)
  info "Building $SHA$([[ $SKIP_TESTS -eq 1 ]] && echo ' (tests skipped)')"
  if [[ $DRY_RUN -eq 0 ]]; then
    (cd "$ROOT" && "$MVN" "${MVN_ARGS[@]}") || die "build failed"
    [[ -f "$JAR" ]] || die "no jar at $JAR"
  fi
  info "Copying the jar to $DEPLOY_SSH_HOST:$DIR ($(du -h "$JAR" 2>/dev/null | cut -f1))"
  if [[ $DRY_RUN -eq 0 ]]; then
    ssh_run "mkdir -p $DIR"
    scp -q -o BatchMode=yes "$JAR" "$DEPLOY_SSH_HOST:$DIR/app.new.jar"
  fi
  REMOTE="set -e; cd $DIR; [ -f app.jar ] && mv app.jar app.prev.jar; mv app.new.jar app.jar; echo '$SHA' > VERSION; $RUN_CMD; $HEALTH_CMD"
else
  REMOTE="set -e; $RUN_CMD; $HEALTH_CMD"
fi

if [[ $DRY_RUN -eq 1 ]]; then info "Would run on the server:"; echo "$REMOTE"; exit 0; fi
info "Starting $NAME"
if ssh_run "$REMOTE"; then
  ok "$ENV_NAME is up: 127.0.0.1:$PORT on $DEPLOY_SSH_HOST ($SHA)"
else
  warn "Deploy of $ENV_NAME failed its health check. Roll back with: ./scripts/deploy.sh --env $ENV_NAME --rollback"
  exit 1
fi
