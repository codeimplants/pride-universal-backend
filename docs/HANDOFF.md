# Session Handoff

**Last updated:** 2026-09-02 (session 2 — first run, first tests, Flat slice)
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

## 3. Verification status

**The backend now compiles, boots, migrates and authenticates.** Everything
below was executed, not reasoned about.

| Item | Status |
|---|---|
| Schema (`V1__initial_schema.sql`) | ✅ Applied to Postgres 16 — 16 tables, `success = t` |
| Java sources (32 files) | ✅ Compile clean (`release 21` on JDK 23) |
| Hibernate `ddl-auto: validate` | ✅ Passes against the Flyway schema |
| App startup (`dev` profile) | ✅ Starts, seeds 8 flats + admin |
| Auth flow | ✅ Exercised end-to-end — see the table in LEARNING-LOG entry 005 |
| Privilege escalation (forged JWT) | ✅ Rejected with 401 |
| Tests | ✅ **28 integration tests, all passing** (Testcontainers + Postgres 16) |
| Git/GitHub | ✅ Both repos pushed |

**Zero compile errors on the first build** (the previous handoff predicted
2–5). All three bugs found were runtime/integration ones — see
[`LEARNING-LOG.md`](LEARNING-LOG.md) entry 005 for why that is the interesting
part.

Test suites: `AuthFlowIntegrationTest` (13) and `FlatApiIntegrationTest` (15).
Run them with `.\mvnw.cmd -B test` — they need the Docker daemon, not
`docker compose up`, since Testcontainers starts its own Postgres.

> ⚠️ Nothing from this session is **committed** yet. See §8 for the file list.

---

## 4. Environment setup — both original blockers cleared

Nothing needed installing. Resolutions, in the order they mattered:

### Java — use JDK 23, no install required

`JAVA_HOME` still points at **Java 8** system-wide, and **JDK 21 is still
absent**. Neither matters: `pom.xml` targets `release 21`, and the already
installed **`jdk-23`** compiles to that target fine. Set it per-shell rather
than changing the machine (Java 8 may be there for other work):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
```

Installing Temurin 21 remains optional, not required. Lombok 1.18.38 (managed
by Boot 3.5.0) is happy on 23; JDK 25 is the one to avoid.

### Docker — Rancher Desktop, already on `moby`

Its settings were already `containerEngine.name = "moby"` with Kubernetes
disabled, so only launching it was needed. `docker compose up -d` then brings
up `pride-db` (healthy in ~10s).

### ⚠️ Maven — the corporate `settings.xml` breaks this project

**This was the real blocker, and it was not in the previous handoff.**
`~/.m2/settings.xml` is a Netcracker corporate config with:

```xml
<mirror><id>project-thirdparty-group-mirror</id>
        <mirrorOf>central,...</mirrorOf>
        <url>https://nexussrv.netcracker.com/...</url></mirror>
```

It mirrors **all of `central`** to an internal Nexus that is unreachable from
this machine (connection refused — VPN-only). With it active *every* dependency
fails to resolve.

Fixed without touching the corporate file: [`.mvn/settings.xml`](../.mvn/settings.xml)
pins resolution to Maven Central and uses its own local repository
(`~/.m2/repository-oss`), so the corporate cache is neither read nor written.
[`.mvn/maven.config`](../.mvn/maven.config) applies it automatically — plain
`mvnw` picks it up with no extra flags.

### ⚠️ Use `mvnw.cmd`, not `./mvnw`, on this machine

The Bash wrapper script fails under Git Bash while installing Maven itself:

```
mv: cannot move '/tmp/.../apache-maven-3.9.9' to '.../dists/...': Permission denied
fail to move MAVEN_HOME
```

`mvnw.cmd` from PowerShell works. Note the shell trap that hid this: piping to
`tail` makes `$?` the exit status of `tail`, so the failed build reported
**exit 0**. Check `$LASTEXITCODE` / `PIPESTATUS`, not the pipeline's status.

### ⚠️ Port 8080 is taken — run on 8090

A `node`/pnpm process from another project listens on 8080. `server.port` is
`${PORT:8080}`, so override rather than kill it:

```powershell
$env:PORT = '8090'
```

Swagger UI is therefore at **http://localhost:8090/swagger-ui.html**.
(The other project's Postgres is on 5433; 5432 is free for ours.)

### ⚠️ Testcontainers is pinned to 2.0.5, above Spring Boot's managed version

Boot 3.5.0 manages Testcontainers 1.21.0, whose docker-java 3.4.2 negotiates
Docker API 1.32. Docker Engine 29 rejects anything below 1.41, so every
container start failed with *"client version 1.32 is too old"*. `pom.xml`
overrides `<testcontainers.version>` to 2.0.5. **Do not remove that override**
while the daemon is Docker 29+. Details in LEARNING-LOG entry 006.

---

## 5. How to run it

```powershell
# 1. Start Rancher Desktop (GUI), then:
docker compose up -d              # pride-db, healthy in ~10s

# 2. Build / run  (JDK 23 + port 8090; see §4 for why)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
$env:PORT = '8090'
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Startup takes ~15s once dependencies are cached. Swagger UI:
http://localhost:8090/swagger-ui.html

Exercising auth (the OTP is returned in `dev_otp` in the dev profile):

```bash
curl -s -X POST http://localhost:8090/api/auth/otp/send \
  -H 'Content-Type: application/json' -d '{"mobile":"9876543210"}'

curl -s -X POST http://localhost:8090/api/auth/otp/verify \
  -H 'Content-Type: application/json' -d '{"mobile":"9876543210","otp":"<code>"}'
```

Two behaviours that look like faults but are the controls working:
**429** on a second OTP within 60s (resend cooldown), and **401** on re-using
an OTP (single-use).

Seeded accounts (dev profile only, `DevDataSeeder`, idempotent on restart):

| Mobile | Role |
|---|---|
| `9850929698` | admin |
| `9876543210` | resident (Ramesh Kulkarni, A-101, owner) |
| `9876543212` | resident (Amit Patil, A-102, tenant) |

Residents are auto-provisioned on first OTP login if their mobile matches a
flat's owner/tenant/alternate contact — **verified working**. Committee roles
are never self-assigned.

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

See [`LEARNING-LOG.md`](LEARNING-LOG.md) entries 001–006 for the reasoning
behind each.

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
- **Domain** — `User` entity + repository; **`Flat` is now a complete CRUD
  slice** (`FlatDtos`, `FlatService`, `FlatController`) and is the pattern the
  remaining 10 domains should copy — see entry 006.
- **Tests** — `AbstractIntegrationTest` (singleton Testcontainers Postgres),
  `AuthFlowIntegrationTest`, `FlatApiIntegrationTest`. 28 tests.

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

Found by review, before the first run:

- `TokenService.refresh()` read the caller from `SecurityContextHolder`, but the
  refresh endpoint is unauthenticated — always null. Now returns the user
  alongside the tokens.
- `/api/auth/me` sat inside a `permitAll("/api/auth/**")` block *and* the JWT
  filter skipped the whole prefix, so it would have 401'd every caller. Public
  paths are now listed individually and the filter no longer skips `/api/auth`.

Found by **running it** (session 2 — full write-up in LEARNING-LOG entry 005):

- **`User.role` never persisted.** `@Enumerated(EnumType.STRING)` writes
  `"ADMIN"`; the `ck_user_role` CHECK accepts only `'admin'`. The `@JsonValue`
  on `Role` is Jackson-only and does not affect JPA. Fixed with
  `RoleConverter` (`@Converter(autoApply = true)`) — **new file**; the
  `@Enumerated` annotation was removed from `User`.
- **Anonymous requests got 403, not 401**, with an empty body.
  `@RestControllerAdvice` cannot see exceptions thrown in the Spring Security
  *filter chain*, so its `AuthenticationException` handler was dead code. Fixed
  with `SecurityErrorResponder` — **new file** — wired into
  `http.exceptionHandling(...)`.
- **404s and malformed JSON were reported as 500s** and logged at ERROR with
  stack traces, because `@ExceptionHandler(Exception.class)` swallowed Spring
  MVC's typed exceptions. `GlobalExceptionHandler` now handles
  `NoResourceFoundException` (404) and `HttpMessageNotReadableException` (400)
  explicitly, and the catch-all honours `ErrorResponse` statuses (so 405/415
  are correct without named handlers).

Found while building the Flat slice (entry 006):

- **`update()` returned a stale `version`.** Hibernate increments `@Version` at
  flush, which with a plain `save()` happens at commit — after the response DTO
  was built. The client's next update then sent a version the database had
  moved past and got an inescapable 409. Fixed with `saveAndFlush`. Caught by a
  test, not by hand; a single update looks fine.

Uncommitted work (nothing from session 2 is committed):

```
new:      src/main/java/com/prideuniversal/common/RoleConverter.java
          src/main/java/com/prideuniversal/config/SecurityErrorResponder.java
          src/main/java/com/prideuniversal/domain/flat/FlatService.java
          src/main/java/com/prideuniversal/domain/flat/FlatController.java
          src/main/java/com/prideuniversal/domain/flat/dto/FlatDtos.java
          src/test/java/com/prideuniversal/AbstractIntegrationTest.java
          src/test/java/com/prideuniversal/auth/AuthFlowIntegrationTest.java
          src/test/java/com/prideuniversal/domain/flat/FlatApiIntegrationTest.java
          .mvn/settings.xml, .mvn/maven.config
modified: src/main/java/com/prideuniversal/domain/user/User.java
          src/main/java/com/prideuniversal/config/SecurityConfig.java
          src/main/java/com/prideuniversal/common/GlobalExceptionHandler.java
          pom.xml, docs/HANDOFF.md, docs/LEARNING-LOG.md
```

---

## 9. Roadmap

- [x] **Entry 005** — First run: three bugs the compiler could not catch *(done)*
- [x] **Entry 006** — Flat CRUD reference slice + first 28 tests *(done)*
- [ ] **Entry 007** — Maintenance bills: optimistic locking on concurrent "mark
      paid", bulk generation, `@Scheduled` overdue job *(Claude builds)*
      — **next**
- [ ] **Entry 008** — Remaining 9 domains *(**user builds** — start with the
      Notice slice set as the exercise at the end of entry 006)*
- [ ] **Entry 009** — File uploads (`photo_url` / `attachment_url` /
      `receipt_url` — base44 never implemented these despite the schema fields)
- [ ] **Entry 010** — Docker + Caddy + VPS deployment
- [ ] **Phase 2** — React Native rewrite

**The user's next hands-on task** is the Notice slice (entry 006, "Your turn"
step 3) — same shape as Flat, deliberately different rules: residents read,
only `Role.COMMS` writes, and unpublished drafts must not appear in the
resident list.

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
| Java (active) | 1.8.0_202 | `JAVA_HOME` still points here — override per-shell |
| Java (used to build) | **jdk-23** | Compiles `release 21`. 21 still absent, not needed |
| Maven (system) | 3.3.9 (2015) | ⚠️ Do not use — use `mvnw.cmd` (pins 3.9.9) |
| Maven settings | `.mvn/settings.xml` | ⚠️ Required — bypasses the corporate Nexus mirror |
| Gradle | 9.3.1 | Current, but project uses Maven |
| Docker | 29.6.2-rd (moby) | Rancher Desktop; `pride-db` runs on 5432 |
| Port 8080 | taken by node/pnpm | Run the app with `PORT=8090` |
| Node | v24.18.0 | For the RN phase |
| Git | 2.55.0 | `gh` CLI **not** installed |
| OS | Windows 11 Pro 26200 | PowerShell + Git Bash both available |
