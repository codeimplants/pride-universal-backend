# Pride Universal — Backend

Housing society management API. Spring Boot 3.5 · Java 21 · PostgreSQL 16.

Replaces a base44-hosted app whose data layer had no server-side
authorisation. See [`docs/LEARNING-LOG.md`](docs/LEARNING-LOG.md) for what each
piece does and why.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | **21+** | Required. Spring Boot 3 will not run on Java 8. [Temurin 21](https://adoptium.net/temurin/releases/?version=21) |
| Docker | any recent | For PostgreSQL. Docker Desktop must be *running*, not just installed. |
| Maven | — | Not needed — use the bundled `./mvnw` wrapper. |

Verify your JDK before anything else:

```bash
java -version    # must report 21 or higher
```

If it reports 1.8, install JDK 21 and point `JAVA_HOME` at it.

---

## Running locally

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Run the app with dev data seeded
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open:

- **API docs** — http://localhost:8080/swagger-ui.html
- **Health** — http://localhost:8080/actuator/health

### Logging in

The `dev` profile seeds 8 flats and an admin account. No SMS provider is
needed — OTPs are printed to the console.

```bash
# Request a code
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"9876543210"}'

# The OTP appears in the app console AND in the response's dev_otp field
# (the dev profile turns on app.otp.dev-mode; it is off by default)

curl -X POST http://localhost:8080/api/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"9876543210","otp":"123456"}'
```

Seeded accounts:

| Mobile | Role | Notes |
|---|---|---|
| `9850929698` | admin | Society admin |
| `9876543210` | resident | Ramesh Kulkarni, A-101 (owner) |
| `9876543212` | resident | Amit Patil, A-102 (tenant) |

---

## Configuration

All settings live in [`application.yml`](src/main/resources/application.yml) and
are overridable by environment variable.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/pride_universal` | Database |
| `DB_USER` / `DB_PASSWORD` | `pride` / `pride_dev_password` | Credentials |
| `JWT_SECRET` | dev placeholder | **Must be ≥32 bytes and changed in production** |
| `OTP_DEV_MODE` | `false` | Returns the OTP in the API response (the `dev` profile turns it on). Refused at startup with a live messaging key. |
| `SMS_PROVIDER` | `console` | `console` (logs the code) \| `messaging` (Code Implants Messaging) |
| `MESSAGING_BASE_URL` | `https://msg.codeimplants.com` | `https://dev.msg.codeimplants.com` on dev |
| `MESSAGING_API_KEY` | — | Required with `SMS_PROVIDER=messaging`. Written into the env file by messaging-backend's `connect-app.sh` |
| `MESSAGING_FIXED_CODE` | — | Dev only: `123456`. Refused with a live (`sk_live_`) key |
| `CORS_ORIGINS` | localhost dev ports | Comma-separated allowed origins |

### Before going to production

- [ ] `JWT_SECRET` set to real random material (`openssl rand -base64 48`)
- [ ] `OTP_DEV_MODE` unset or `false`
- [ ] `SMS_PROVIDER=messaging` with the prod `MESSAGING_API_KEY` and no `MESSAGING_FIXED_CODE`
- [ ] `SPRING_PROFILES_ACTIVE` **not** set to `dev`
- [ ] `CORS_ORIGINS` restricted to the real app origins

> **SMS:** OTPs go through Code Implants Messaging (`msg.codeimplants.com`), which
> holds the shared 2Factor account and its DLT-approved OTP template. Pride
> Universal needs no DLT registration of its own.

---

## Project layout

```
src/main/java/com/prideuniversal/
├── auth/          JWT, OTP, refresh tokens
├── common/        BaseEntity, Role, error handling
├── config/        Security, caching, properties, dev seeder
├── domain/        One package per entity
├── sms/           Pluggable SMS providers
└── storage/       File uploads
src/main/resources/db/migration/   Flyway migrations
```

---

## Commands

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # run
./mvnw test                                             # tests
./mvnw clean package                                    # build jar
docker compose up -d                                    # database
docker compose down -v                                  # database + wipe data
docker exec -it pride-db psql -U pride -d pride_universal   # psql shell
```
