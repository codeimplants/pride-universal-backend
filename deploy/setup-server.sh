#!/usr/bin/env bash
# One-time (idempotent) server setup for Pride Universal: dev + prod on the shared app server, all in
# Docker, no sudo needed. Modelled on restobill-backend/deploy/setup-server.sh. Run as the deploy user
# (a member of the `docker` group):
#
#   bash ~/pride/setup-server.sh            # creates whatever is missing, never overwrites
#
# Creates, only if absent:
#   ~/pride/{dev,prod}.env           per-environment settings with generated secrets (mode 600)
#   ~/pride/admin.env                Postgres superuser + per-environment DB passwords (mode 600)
#   ~/pride/{dev,prod}/              where scripts/deploy.sh puts each environment's jar
#   ~/pride/{dev,prod}/uploads/      uploaded photos and attachments
#   docker network  pride
#   docker volume   pride-pg
#   container       pride-postgres (16): private to the network, no host ports
#   databases       pride_dev / pride_prod, each with its own login
#   ~/bin/pride-db-backup.sh + a 02:55 crontab line (14 daily dumps per database, plus the uploads)
#
# Nothing here touches another app's containers, files or ports.
set -euo pipefail
umask 077

HOME_DIR="${PRIDE_HOME:-$HOME/pride}"
PG_IMAGE="postgres:16-alpine"
JRE_IMAGE="eclipse-temurin:21-jre"

say() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
secret() { openssl rand -hex 24; }

mkdir -p "$HOME_DIR/dev/uploads" "$HOME_DIR/prod/uploads" "$HOME/bin" "$HOME/backups/pride"

# --- passwords ------------------------------------------------------------------------------------------
if [[ ! -f "$HOME_DIR/admin.env" ]]; then
  say "admin.env (Postgres superuser + per-environment passwords)"
  {
    echo "PG_ADMIN_PASSWORD=$(secret)"
    echo "PG_DEV_PASSWORD=$(secret)"
    echo "PG_PROD_PASSWORD=$(secret)"
  } > "$HOME_DIR/admin.env"
fi
# shellcheck disable=SC1091
source "$HOME_DIR/admin.env"

# --- network, volume, database container ------------------------------------------------------------------
docker network inspect pride >/dev/null 2>&1 || { say "network pride"; docker network create pride >/dev/null; }
docker volume inspect pride-pg >/dev/null 2>&1 || { say "volume pride-pg"; docker volume create pride-pg >/dev/null; }

if ! docker container inspect pride-postgres >/dev/null 2>&1; then
  say "container pride-postgres ($PG_IMAGE)"
  docker run -d --name pride-postgres --network pride --restart unless-stopped \
    --memory 512m -e POSTGRES_USER=pride_admin -e POSTGRES_PASSWORD="$PG_ADMIN_PASSWORD" -e POSTGRES_DB=postgres \
    -e TZ=Asia/Kolkata -v pride-pg:/var/lib/postgresql/data "$PG_IMAGE" >/dev/null
fi

say "waiting for Postgres"
for _ in $(seq 1 60); do
  docker exec pride-postgres pg_isready -U pride_admin -q && break
  sleep 1
done

# --- one database + login per environment -------------------------------------------------------------------
psql_admin() { docker exec -i pride-postgres psql -U pride_admin -d postgres -v ON_ERROR_STOP=1 -qAt "$@"; }
for env in dev prod; do
  pw_var="PG_${env^^}_PASSWORD"
  role="pride_$env"
  if [[ -z "$(psql_admin -c "select 1 from pg_roles where rolname = '$role'")" ]]; then
    say "role + database $role"
    psql_admin -c "create role $role login password '${!pw_var}'"
    psql_admin -c "create database $role owner $role"
    psql_admin -c "revoke all on database $role from public"
  fi
done

# --- per-environment settings -------------------------------------------------------------------------------
write_env() {
  local env="$1" sms="$2" cors="$3"
  local file="$HOME_DIR/$env.env" pw_var="PG_${env^^}_PASSWORD"
  [[ -f "$file" ]] && return 0
  say "$env.env"
  {
    echo "# Pride Universal $env: written by deploy/setup-server.sh. See README.md in pride-universal-backend."
    echo "PORT=8080"
    echo "TZ=Asia/Kolkata"
    echo "DB_URL=jdbc:postgresql://pride-postgres:5432/pride_$env"
    echo "DB_USER=pride_$env"
    echo "DB_PASSWORD=${!pw_var}"
    echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
    # MESSAGING_BASE_URL / MESSAGING_API_KEY are added by messaging-backend/scripts/connect-app.sh.
    echo "SMS_PROVIDER=messaging"
    [[ "$sms" == fixed ]] && echo "MESSAGING_FIXED_CODE=123456"
    echo "OTP_DEV_MODE=false"
    echo "CORS_ORIGINS=$cors"
    echo "STORAGE_PROVIDER=local"
    echo "STORAGE_PATH=/app/uploads"
  } > "$file"
}
# Web origins: the custom domain plus the Firebase Hosting URL, which keeps working before and after the
# custom domain is attached. Native apps send no Origin and need no entry.
write_env dev fixed "https://dev.pride.codeimplants.com,https://pride-dev.web.app,http://localhost:8081,http://localhost:19006"
write_env prod live "https://pride.codeimplants.com,https://pride-prod.web.app"

# --- nightly backups ------------------------------------------------------------------------------------------
if [[ ! -f "$HOME/bin/pride-db-backup.sh" ]]; then
  say "~/bin/pride-db-backup.sh + crontab"
  cat > "$HOME/bin/pride-db-backup.sh" <<'EOF'
#!/usr/bin/env bash
# Nightly dumps of the Pride Universal databases and an archive of prod uploads; keeps 14 days.
# Written by deploy/setup-server.sh.
set -euo pipefail
umask 077
dir="$HOME/backups/pride"
mkdir -p "$dir"
for db in pride_dev pride_prod; do
  docker exec pride-postgres pg_dump -U pride_admin -Fc "$db" > "$dir/$db-$(date +%F).dump"
done
tar -czf "$dir/uploads-prod-$(date +%F).tgz" -C "$HOME/pride/prod" uploads
find "$dir" \( -name '*.dump' -o -name '*.tgz' \) -mtime +14 -delete
echo "$(date -Is) ok"
EOF
  chmod 700 "$HOME/bin/pride-db-backup.sh"
  ( crontab -l 2>/dev/null; echo "55 2 * * * $HOME/bin/pride-db-backup.sh >> $HOME/backups/pride/backup.log 2>&1" ) | crontab -
fi

say "pulling $JRE_IMAGE"
docker pull -q "$JRE_IMAGE" >/dev/null

say "done. Containers:"
docker ps --filter name=pride --format '  {{.Names}}\t{{.Status}}'
echo
echo "Next: connect each environment to Code Implants Messaging (on this server):"
echo "  bash ~/messaging/dev/scripts/connect-app.sh pride-universal $HOME_DIR/dev.env --ips 172.16.0.0/12,31.97.61.191/32"
echo "  bash ~/messaging/prod/scripts/connect-app.sh pride-universal $HOME_DIR/prod.env --ips 172.16.0.0/12,31.97.61.191/32"
