#!/usr/bin/env bash
#
# Wires a Pride Universal environment into Nexus, the Code Implants admin console, the way Feenix and
# RestoBill are: an App row (appId com.prideuniversal.app) whose federation points at this server's
# /api/admin over loopback, so Nexus can put names to its engagement rows, purge accounts through Pride's
# own deletion, and drop telemetry for accounts deleted in the app. Run it ON THE SERVER:
#
#   bash ~/pride/connect-nexus.sh dev     # Nexus dev and preprod -> Pride dev
#   bash ~/pride/connect-nexus.sh prod    # Nexus prod            -> Pride prod
#
# The service token is Pride's ADMIN_SERVICE_TOKEN from ~/pride/<env>.env (written by setup-server.sh).
# It is read from the file and passed to Nexus's scripts/connect-app-backend.js through the environment,
# which stores it encrypted; it is never printed. Then the three federation paths are set on the row.
#
# Prints the App's SDK API key per Nexus environment: the app's EXPO_PUBLIC_VC_API_KEY for that
# environment (a client key; it ships in the app).
#
# Nexus checkouts are looked for in $NEXUS_HOME (default: $HOME) as nexus-backend-<env>, built (dist/).

set -euo pipefail
ENV_NAME="${1:-}"
NEXUS_HOME="${NEXUS_HOME:-$HOME}"
PRIDE_HOME="${PRIDE_HOME:-$HOME/pride}"
case "$ENV_NAME" in
  prod) PORT=8110; TARGETS=(prod) ;;
  dev)  PORT=8111; TARGETS=(dev preprod) ;;
  *) echo "Usage: $0 dev|prod" >&2; exit 1 ;;
esac

ENVF="$PRIDE_HOME/$ENV_NAME.env"
[[ -f "$ENVF" ]] || { echo "$ENVF not found; run setup-server.sh first" >&2; exit 1; }
TOKEN="$(grep -E '^ADMIN_SERVICE_TOKEN=' "$ENVF" | head -1 | cut -d= -f2-)"
[[ -n "$TOKEN" ]] || { echo "no ADMIN_SERVICE_TOKEN in $ENVF" >&2; exit 1; }

# The container must already answer with this token, or Nexus would be registered against a dead link.
code="$(curl -s -o /dev/null -w '%{http_code}' -m 10 -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:$PORT/api/admin/stats?days=1" || true)"
[[ "$code" == 200 ]] || { unset TOKEN; echo "Pride $ENV_NAME on 127.0.0.1:$PORT answered $code to its own token; deploy it first" >&2; exit 1; }

for target in "${TARGETS[@]}"; do
  dir="$NEXUS_HOME/nexus-backend-$target"
  if [[ ! -f "$dir/scripts/connect-app-backend.js" || ! -d "$dir/dist" ]]; then
    echo "nexus-backend-$target: skipped, no built checkout with scripts/connect-app-backend.js in $dir" >&2
    continue
  fi
  (cd "$dir" && APP_PACKAGE=com.prideuniversal.app APP_NAME="Pride Universal" APP_PLATFORMS=android,ios,web \
      BACKEND_URL="http://127.0.0.1:$PORT/api/admin" SERVICE_TOKEN="$TOKEN" node scripts/connect-app-backend.js \
      | sed "s/^/nexus-backend-$target: /")
  (cd "$dir" && APP_PACKAGE=com.prideuniversal.app node -e '
      require("dotenv").config();
      const { PrismaClient } = require("@prisma/client");
      const prisma = new PrismaClient();
      prisma.app.updateMany({
        where: { appId: process.env.APP_PACKAGE, isActive: true },
        data: { backendUsersPath: "/users", backendDeleteUserPath: "/users", backendDeletedUsersPath: "/users/deleted" },
      }).then((r) => console.log(`federation paths set on ${r.count} app row(s)`))
        .catch((e) => { console.error(String(e.message || e)); process.exitCode = 1; })
        .finally(() => prisma.$disconnect());
    ' | sed "s/^/nexus-backend-$target: /")
done
unset TOKEN
