# Deploying Pride Universal

Two environments, **dev** and **prod**, both on the shared app server (31.97.61.191; SSH alias
`hostinger-vps`, user `sanskarpandit`), in Docker beside RestoBill. No sudo is needed except once, for
nginx + TLS. The layout copies `restobill-backend/deploy`.

| | dev | prod |
|---|---|---|
| Public URL | `https://dev.api.pride.codeimplants.com` | `https://api.pride.codeimplants.com` |
| Container | `pride-api-dev` → `127.0.0.1:8111` | `pride-api-prod` → `127.0.0.1:8110` |
| Database | `pride_dev` | `pride_prod` |
| OTP | Code Implants Messaging dev: every code is **123456**, nothing sent | `msg.codeimplants.com`, real SMS |
| Settings | `~/pride/dev.env` | `~/pride/prod.env` |
| Uploads | `~/pride/dev/uploads` | `~/pride/prod/uploads` |

Shared by both: the `pride-postgres` container (Postgres 16) on the private `pride` Docker network, with
no host port. Nightly at 02:55 server time, `~/bin/pride-db-backup.sh` dumps both databases and archives
prod's uploads into `~/backups/pride`, keeping 14 days.

## One-time setup

1. **Server** (no sudo), from this repo:
   ```bash
   ssh hostinger-vps 'mkdir -p ~/pride'
   scp deploy/setup-server.sh deploy/install-nginx.sh hostinger-vps:pride/
   scp -r deploy/nginx hostinger-vps:pride/
   ssh hostinger-vps 'bash ~/pride/setup-server.sh'
   ```
   Idempotent: creates the network, volume, database container, both databases with their own logins,
   `dev.env` / `prod.env` with generated secrets, and the backup job. Nothing that exists is overwritten.
2. **Messaging** — create the app in each messaging environment once, then give each Pride environment its
   key (run on the server; writes `MESSAGING_*` into the env file without showing it):
   ```bash
   bash ~/messaging/dev/scripts/cli.sh  app:create --slug pride-universal --name "Pride Universal"
   bash ~/messaging/dev/scripts/connect-app.sh  pride-universal ~/pride/dev.env  --ips 172.16.0.0/12,31.97.61.191/32
   bash ~/messaging/prod/scripts/cli.sh app:create --slug pride-universal --name "Pride Universal"
   bash ~/messaging/prod/scripts/connect-app.sh pride-universal ~/pride/prod.env --ips 172.16.0.0/12,31.97.61.191/32
   ```
3. **DNS** (Hostinger): A records `dev.api.pride.codeimplants.com` and `api.pride.codeimplants.com` → 31.97.61.191.
4. **nginx + TLS** — the only sudo step, once DNS resolves: `sudo bash ~/pride/install-nginx.sh`.
5. **Nexus** — after the environment's first deploy, register it with the Code Implants admin console:
   ```bash
   scp deploy/connect-nexus.sh hostinger-vps:pride/
   ssh hostinger-vps 'bash ~/pride/connect-nexus.sh dev'    # Nexus dev + preprod -> Pride dev
   ssh hostinger-vps 'bash ~/pride/connect-nexus.sh prod'   # Nexus prod -> Pride prod
   ```
   It hands `ADMIN_SERVICE_TOKEN` to Nexus's `connect-app-backend.js` (stored encrypted, never printed), points
   the App row (`com.prideuniversal.app`) at `http://127.0.0.1:<port>/api/admin` with the users, delete and
   deleted-users paths, and prints the App's **SDK API key** — the app's `EXPO_PUBLIC_VC_API_KEY` for that
   environment (version control and analytics). Nexus's Server tab needs Pride in its `server-inventory.ts`.
6. **First admin** — nobody can sign in until an admin exists. After the first prod deploy:
   ```bash
   ssh hostinger-vps "docker exec -i pride-postgres psql -U pride_admin -d pride_prod -c \
     \"insert into app_user (mobile, display_name, role) values ('<10-digit mobile>', '<Name>', 'admin')\""
   ```
   That admin then adds flats, committee and security accounts from the app.

## Deploying

```bash
./scripts/deploy.sh --env dev                # build (with tests), copy, restart, wait for health
./scripts/deploy.sh --env dev --skip-tests   # dev only, e.g. without Docker locally
./scripts/deploy.sh --env prod --yes         # clean tree, HEAD on origin/main, tests must pass
./scripts/deploy.sh --inspect                # what each environment runs
./scripts/deploy.sh --env prod --rollback    # back to the jar the last deploy replaced
```

The script builds the jar locally, copies it to `~/pride/<env>/app.jar` (keeping the previous as
`app.prev.jar`), recreates the container on `eclipse-temurin:21-jre` with the jar mounted read-only, and
waits for `/actuator/health`. Flyway migrates the database on start. Settings: `scripts/deploy.config.sh`
(gitignored; copy `scripts/deploy.config.example.sh`).

Logs: `ssh hostinger-vps docker logs -f pride-api-prod`.

## Notes

- nginx **overwrites** `X-Forwarded-For` with the client address. The OTP limiter trusts that header's first
  entry, so appending (`$proxy_add_x_forwarded_for`) would let a client forge it.
- Both nginx sites return 404 for `/actuator/` and `/api/admin/` (Nexus uses loopback); prod also hides Swagger.
- `/api/admin/**` accepts only `ADMIN_SERVICE_TOKEN`, never a user's JWT, and answers in Nexus's `{"data": …}`
  envelope with camelCase keys.
- The web app (Firebase Hosting) origins are in `CORS_ORIGINS` in each env file.
