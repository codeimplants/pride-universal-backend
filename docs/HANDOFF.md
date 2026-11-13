# Session Handoff

**Last updated:** 2026-11-14 (session 3 — delivery mode: full API, messaging, dev on the VPS, Nexus)
**Purpose:** Everything a fresh session needs to continue without re-deriving context.

---

## 1. What this project is

Rewriting **Pride Universal**, a housing society management app (Wings A/B — flats, maintenance bills,
complaints, notices, meetings, visitors, vendors, marketplace, accounts), off the Base44 platform.

- **Backend** (this repo) — Java 21 / Spring Boot 3.5 / Postgres 16. **Feature-complete for Base44 parity,
  deployed to dev.**
- **App** — Expo / React Native for Android + iOS, RN Web for web, in `codeimplants/pride-universal`
  (replacing the Base44 code there; the old app goes to an archive branch, as RestoBill did). **Next.**

### Working mode (changed 2026-11-14 — this replaces the old "working agreement")

The user said: **"No learning, just delivery on priority to prod."** Claude builds everything. No more
exercises, "Your turn" sections or interview Q&A; `LEARNING-LOG.md` is historical. Keep tests and
operations docs. Launch decisions: **everything at once** (full Base44 parity), **Android + iOS + web**,
codeimplants.com domains.

### Why the rewrite

The Base44 app's authentication was not authentication: `MOCK_OTP = '000000'` printed on the login screen,
the role stored in `sessionStorage` (so `sessionStorage.setItem('otp_user_role','admin')` made you admin),
`requiresAuth: false`. Every resident's phone number was readable by anyone. **No production data exists**;
clean-slate rebuild.

---

## 2. Follow the sibling projects

| Concern | Copied from |
|---|---|
| Deploy (Docker on the shared VPS, nginx + certbot, dev/prod) | `restobill-backend/deploy`, `scripts/deploy.sh` |
| OTP delivery | RestoBill's `OtpSenderConfig` → our `MessagingSmsSender` |
| Nexus federation | RestoBill `/v1/admin/**` + Feenix `scripts/connect-nexus.sh` |
| App stack (next) | `restobill-frontend/CLAUDE.md`: Expo 57, expo-router, NativeWind 4, react-query, RHF + zod, `@codeimplants/*` |

---

## 3. State

| Item | Status |
|---|---|
| API | ✅ All Base44 features: auth, flats, bills (+ waive), notices, complaints, meetings + resolutions, visitors, vendors, marketplace, ledger, settings, users/roles, profile + account deletion, dashboard, Nexus console |
| Migrations | V1 schema, V2 complaint number sequence, V3 deleted-account log |
| Unit tests | ✅ `MessagingSmsSenderTest` (7) runs locally |
| Integration tests | ⚠️ ~95 written (45 old + new suites), **not run since session 2**: no Docker on this machine. The user is installing Docker Desktop. Run `.\mvnw.cmd -B test` before any prod deploy (deploy.sh enforces it) |
| Dev environment | ✅ **Live on the VPS** (`pride-api-dev`, 127.0.0.1:8111). Migrations applied; a 27-step end-to-end smoke test passed (OTP via messaging dev, billing, helpdesk, notices, meetings, gate, ledger, vendors, marketplace, console) |
| Messaging | ✅ dev: app `pride-universal` in messaging dev, key in `~/pride/dev.env`. prod: not yet |
| Nexus | ✅ Pride dev registered in Nexus dev + preprod (`com.prideuniversal.app`, federation at `http://127.0.0.1:8111/api/admin`). Server-tab inventory entry committed in nexus-backend **locally, not pushed** (`8f67fae`). Prod: not yet |
| DNS / nginx | ❌ Waiting on the user: A records for `api.pride` / `dev.api.pride.codeimplants.com`, then `sudo bash ~/pride/install-nginx.sh` |
| Prod | ❌ Not deployed. Needs: passing tests, messaging prod connect, DNS + nginx, first admin, Nexus prod registration |
| App | ❌ Not started |

Smoke test: `~/pride/smoke-dev.sh` on the server (test numbers `99999000xx`, code 123456).

---

## 4. Environment (this machine: `D:\CI Projects`, Windows 11 Home)

- `JAVA_HOME` = Temurin 21. Use `.\mvnw.cmd` (PowerShell) or `./mvnw.cmd` from Git Bash.
- **No Docker** yet → integration tests can't run; `deploy.sh --env dev --skip-tests` is allowed for dev only.
- `.mvn/settings.xml` pins Maven Central (needed on the old corporate machine; harmless here).
- Testcontainers is pinned to 2.0.5 (Docker 29 needs API ≥ 1.41) — don't remove the override.
- PowerShell 5.1 `Set-Content -Encoding utf8` writes a BOM that breaks javac. Edit files with Python or the editor.
- SSH alias `hostinger-vps` (31.97.61.191, user sanskarpandit) works from here.
- Git: commit as **Mahesh Garade <maheshgarade@gmail.com>** (repo-local config; the global identity is someone else's).

---

## 5. Server layout (see `deploy/README.md`)

`~/pride/{dev,prod}.env` (secrets, mode 600), `~/pride/{dev,prod}/app.jar` (+ `app.prev.jar`, `VERSION`,
`uploads/`), container `pride-postgres` on network `pride`, databases `pride_dev` / `pride_prod`, nightly
backup 02:55 to `~/backups/pride`. Ports 8110 prod / 8111 dev. nginx closes `/actuator/` and `/api/admin/`.

---

## 6. Decisions — don't re-litigate

| Decision | Choice |
|---|---|
| Backend | Java 21 + Spring Boot 3.5, Maven, Postgres 16, Flyway (`ddl-auto: validate`) |
| OTP | Pride generates/verifies its own codes; Code Implants Messaging delivers them (no DLT registration needed). Non-prod code is always 123456 |
| `OTP_DEV_MODE` | Defaults to **false**; refused at startup with a live messaging key |
| Access token TTL | 1 hour (role changes and deletions take effect quickly); refresh 30 days |
| Hosting | Shared VPS, Docker, beside RestoBill — not a separate VPS/Caddy as first planned |
| Uploads | Base44 never implemented them; not needed for launch parity. Folder and mount are ready |
| Play Store | Same listing `com.prideuniversal.app`, Play App Signing on; RN app ships as the update (see §8) |

### Rules worth knowing in the code

- Authorisation lives on services (`@PreAuthorize`); URL rules are the outer fence.
- Residents see only their own flat's complaints and visitors (Base44 showed everyone everything).
- Hidden notices/meetings are **404**, not 403 (existence of a draft is the secret).
- Nobody grants a role above their own; the last active admin can't be removed or deleted.
- `/api/admin/**` is Nexus-only (service token), camelCase keys — the rest of the API is snake_case.
- `?status=open` binds through `CodedEnum` + `WebConfig` (Spring's default would 400 on lowercase codes).

---

## 7. Next steps

1. **User:** DNS A records → `sudo bash ~/pride/install-nginx.sh`. Install Docker Desktop.
2. **Ask the user:** push + deploy the nexus-backend inventory commit.
3. Run the full test suite once Docker exists; fix whatever it finds.
4. **Build the app** (Expo, `codeimplants/pride-universal`): port every Base44 screen; version control +
   analytics through Nexus (the dev SDK key is on the App row in Nexus dev; identify users by their Pride user id).
5. Prod go-live (ask first): messaging prod connect, `deploy.sh --env prod`, first admin row, Nexus prod
   registration, Firebase Hosting for web, Play upload key → AAB to the existing listing, iOS (needs an Apple
   Developer account).

---

## 8. Google Play listing

Play Console shows the Base44 build blocked from updates until it targets API 36 (enforced 31 Aug 2026) and
hidden from new users on Android versions above its target. It is **not** removed. The rewrite ships as the
update: package **`com.prideuniversal.app`**, **Play App Signing ON** (lost upload key → App integrity →
Request reset, takes days), target API 36 (Expo SDK 54+), `versionCode` above the old build's. How the old
build was made is unknown and irrelevant.

---

## 9. History

Sessions 1–2 (old machine): schema, auth, flats, billing, 45 integration tests; bugs found by running
(role persistence, 401 vs 403, error mapping, stale version echo, ₹0 bills) are written up in
`LEARNING-LOG.md` entries 005–007.
