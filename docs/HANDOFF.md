# Session Handoff

**Last updated:** 2026-09-02
**Purpose:** Everything a fresh session needs to continue without re-deriving context.

---

## 1. What this project is

Rewriting **Pride Universal**, a housing society management app (Wings A/B —
flats, maintenance bills, complaints, notices, meetings, visitors, vendors,
marketplace, accounts), off the base44 platform.

**Two-phase plan, in this order:**

1. **Backend first** (in progress) — Java/Spring Boot replacing base44's data
   layer. Deliberately sequenced first: it fixes a critical security hole on its
   own schedule, and leaves the existing web UI working as a known-good client
   to validate against.
2. **React Native rewrite** (not started) — Expo, iOS/Android primary, RN Web
   enabled but not gating.

### Why the rewrite (the original finding)

The base44 app's authentication **was not authentication**. Verified in the
original source:

- `MOCK_OTP = '000000'` hardcoded in the OTP function, and printed on the login
  screen.
- `verifyOtp` returned the user's role as plain JSON. The client stored it in
  `sessionStorage`; `useRole()` read it back. So
  `sessionStorage.setItem('otp_user_role','admin')` **made you an admin.**
- Admin's mobile number hardcoded in committed source.
- `requiresAuth: false` on the API client.

Net effect: resident names, phone numbers, vehicle registrations and payment
history were readable/writable by anyone who could reach the app URL.

**There is no live production data** — confirmed by the user. This is a
clean-slate rebuild, no migration or dual-run cutover needed.

---

## 2. Repository layout

```
C:\RN\pride-universal\                     <- parent (plain folder, NOT a repo)
├── pride-universal-frontend\              <- base44 app, being retired
│   └── origin: github.com/codeimplants/pride-universal.git
└── pride-universal-backend\               <- active work
    └── origin: github.com/codeimplants/pride-universal-backend.git
```

Two **separate** git repos, deliberately — independent CI and deploy cadence
(backend → VPS, app → app stores).

Both clean and pushed as of this handoff:
- frontend `38e087c`, backend `f3f0858`, both `main` tracking origin.

> Leftover: `C:\RN\restructure-pride-universal.ps1` — the restructure was done
> manually instead. Safe to delete.
>
> Optional: no `.code-workspace` exists. A multi-root one would give both repos
> their own Source Control entry in one window.

---

## 3. ⚠️ Verification status — read this first

**No Java code in this repo has ever been compiled or run.** It was written
blind because the environment was blocked. Treat it as reviewed-but-unverified.

| Item | Status |
|---|---|
| Schema (`V1__initial_schema.sql`) | ❌ Never executed against Postgres |
| Java sources (38 files) | ❌ Never compiled |
| Auth flow | ❌ Never exercised end-to-end |
| Tests | ❌ None written yet |
| Git/GitHub | ✅ Verified — both repos pushed |
| Folder restructure | ✅ Verified on disk |

**Expect 2–5 compile errors on first build.** Normal for a scaffold this size.

---

## 4. Blockers

### Blocker 1 — `JAVA_HOME` points at Java 8

```
JAVA_HOME = C:\Program Files\Java\jdk1.8.0_202
```

Spring Boot 3 requires JDK 17+. **No install strictly needed** — these are
already on disk:

```
jdk-17   jdk-20   jdk-23   jdk-25.0.2   jdk1.8.0_202   Microsoft jdk-11
```

Note **21 is absent**, and `pom.xml` targets 21. Options:

- `winget install EclipseAdoptium.Temurin.21.JDK` (recommended — LTS, the
  reference config for Spring Boot 3.5, zero toolchain risk)
- Use existing `jdk-25.0.2` (current LTS, but Lombok is the usual thing that
  breaks on very new JDKs)
- Use existing `jdk-23` (works, but non-LTS and past EOL)

Then:
```powershell
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
# new terminal required; verify with: java -version
```

If using an existing JDK instead, `pom.xml`'s `<java.version>21</java.version>`
may need adjusting to match.

### Blocker 2 — Rancher Desktop not started

Docker CLI present (`29.6.2-rd` — the `-rd` suffix is Rancher Desktop) but no
daemon. Only used for **PostgreSQL** and, later, Testcontainers. The app itself
runs on the JVM, not in a container.

Start Rancher Desktop, then set **Preferences → Container Engine → `dockerd (moby)`**.
That mode makes `docker compose` and Testcontainers work unchanged; `containerd`
mode would need `nerdctl` and complicates Testcontainers.

Verify: `docker ps` returns an empty table rather than a pipe error.

> Native Postgres is an acceptable alternative — the only real casualty is
> Testcontainers. **H2 will not work**: the schema uses `gen_random_uuid()`,
> the `~` regex operator in CHECK constraints, and `TIMESTAMPTZ`.

---

## 5. First actions for the next session

```bash
# 1. Database
docker compose up -d

# 2. Compile — expect errors, fix them
./mvnw clean compile

# 3. Run with seeded dev data
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Exercise the auth flow
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H 'Content-Type: application/json' -d '{"mobile":"9876543210"}'
# OTP is printed to the console and returned in dev_otp (dev only)

curl -X POST http://localhost:8080/api/auth/otp/verify \
  -H 'Content-Type: application/json' -d '{"mobile":"9876543210","otp":"<code>"}'
```

Swagger UI: http://localhost:8080/swagger-ui.html

Seeded accounts (dev profile only, `DevDataSeeder`):

| Mobile | Role |
|---|---|
| `9850929698` | admin |
| `9876543210` | resident (Ramesh Kulkarni, A-101, owner) |
| `9876543212` | resident (Amit Patil, A-102, tenant) |

Residents are **auto-provisioned on first OTP login** if their mobile matches a
flat's owner/tenant/alternate contact. Committee roles are never self-assigned.

---

## 6. Decisions already made — do not re-litigate

| Decision | Choice | Rationale |
|---|---|---|
| Backend platform | Java 21 + Spring Boot 3.5 | User's choice over Supabase. Accepts ~1–2 weeks extra building auth/storage/access-control in exchange for no vendor lock-in — the thing that motivated leaving base44. |
| Build tool | **Maven** | User's explicit choice. `./mvnw` wrapper pins 3.9.9; the system Maven 3.3.9 (2015) must not be used. |
| Database | PostgreSQL 16 | Version parity with the target VPS. |
| Migrations | Flyway, `ddl-auto: validate` | Hibernate never owns the schema. |
| Auth | SMS OTP via MSG91 | User's choice. **Blocked on TRAI DLT registration** (approved sender ID + templates against the society's registered entity; days-to-weeks). `SMS_PROVIDER=console` unblocks everything else meanwhile. |
| Repos | Two separate | Independent deploy cadence. |
| Hosting | VPS + Docker Compose + Caddy | ~2 vCPU / 4GB. Budget ~400MB for the JVM. **Host in India** (DO Bangalore / AWS Mumbai / E2E) for latency and data residency. |
| RN Web | Enabled, not gating | Ships nearly free with Expo Router; committee screens benefit from a big screen. Residents are on phones. |

---

## 7. Working agreement (important)

The user is learning backend development and explicitly asked not to be handed
a repo they can't defend in an interview.

**The model:**
- Claude builds the **reference slice** for each pattern, plus genuinely hard
  parts (security, transactions, concurrency).
- The user builds the **repetitions** — Claude provides scaffolds with
  specified TODOs and failing tests.
- Every slice gets an entry in [`LEARNING-LOG.md`](LEARNING-LOG.md): what was
  built, what problem it solves, key decisions, **interview Q&A**, and a
  hands-on exercise.

Keep writing those entries. They are a primary deliverable, not documentation
overhead.

---

## 8. Built so far

38 files. See [`LEARNING-LOG.md`](LEARNING-LOG.md) entries 001–004 for the
reasoning behind each.

- **Schema** — all 12 entities with FKs, CHECK constraints and indexes the JSON
  originals lacked. `UNIQUE (flat_id, year, month)` makes bill generation
  idempotent.
- **Common** — `BaseEntity` (with the proxy-safe `equals`/constant `hashCode`
  reasoning documented), `Role`, `ApiException`, `GlobalExceptionHandler`.
- **Config** — `AppProperties` (record-based binding), `SecurityConfig`,
  `CacheConfig` (Caffeine, `wingSettings` only), `OpenApiConfig`,
  `DevDataSeeder` (`@Profile("dev")`).
- **Auth** — `JwtService`, `JwtAuthFilter`, `OtpService`, `OtpAttemptRecorder`,
  `TokenService`, `AuthController`, DTOs.
- **SMS** — `SmsSender` interface, `ConsoleSmsSender`, `Msg91SmsSender`.
- **Domain** — `Flat` and `User` entities + repositories only.

### Three things worth understanding in the auth code

1. **`OtpAttemptRecorder`** uses `REQUIRES_NEW` because the failed-attempt
   increment would otherwise be rolled back by the exception thrown right after
   it — silently giving unlimited guesses at a 6-digit code. It is a *separate
   bean* because Spring's `@Transactional` proxy is bypassed by self-invocation.
2. **`OtpService.checkBurstLimit()`** uses `ConcurrentHashMap.compute()` for an
   atomic read-modify-write. Get-then-put would leak the limit under exactly the
   concurrency it exists to stop.
3. **BCrypt for OTPs, SHA-256 for refresh tokens** — different threat models.
   OTPs are ~20 bits and brute-forceable, so they need slow hashing. Refresh
   tokens are 384 bits (unbruteforceable) but must be *looked up by value*, and
   BCrypt's per-row salt would force a table scan.

### Bugs already found and fixed (don't reintroduce)

- `TokenService.refresh()` read the caller from `SecurityContextHolder`, but the
  refresh endpoint is unauthenticated — always null. Now returns the user
  alongside the tokens.
- `/api/auth/me` sat inside a `permitAll("/api/auth/**")` block *and* the JWT
  filter skipped the whole prefix, so it would have 401'd every caller. Public
  paths are now listed individually and the filter no longer skips `/api/auth`.

---

## 9. Roadmap

- [ ] **Entry 005** — Flat domain, the reference CRUD slice *(Claude builds)*
- [ ] **Entry 006** — Maintenance bills: optimistic locking on concurrent "mark
      paid", bulk generation, `@Scheduled` overdue job *(Claude builds)*
- [ ] **Entry 007** — Remaining 9 domains *(**user builds** — scaffolds +
      failing tests provided)*
- [ ] **Entry 008** — File uploads (`photo_url` / `attachment_url` /
      `receipt_url` — base44 never implemented these despite the schema fields)
- [ ] **Entry 009** — Docker + Caddy + VPS deployment
- [ ] **Phase 2** — React Native rewrite

### Deferred / open

- **TRAI DLT registration** for MSG91 — long lead time, start it early. Needs
  the society's registration documents.
- **Production checklist** in [`README.md`](../README.md): `JWT_SECRET` must be
  real random material, `OTP_DEV_MODE=false`, `SPRING_PROFILES_ACTIVE` not
  `dev`, `CORS_ORIGINS` restricted.

---

## 10. Environment reference

| Tool | Version | Notes |
|---|---|---|
| Java (active) | 1.8.0_202 | ⚠️ Blocker — `JAVA_HOME` points here |
| Java (available) | 17, 20, 23, 25.0.2, MS 11 | 21 absent |
| Maven (system) | 3.3.9 (2015) | ⚠️ Do not use — use `./mvnw` |
| Gradle | 9.3.1 | Current, but project uses Maven |
| Docker CLI | 29.6.2-rd | Rancher Desktop; daemon not running |
| Node | v24.18.0 | For the RN phase |
| Git | 2.55.0 | `gh` CLI **not** installed |
| OS | Windows 11 Pro 26200 | PowerShell + Git Bash both available |
