# Learning Log

A running record of what got built, why, and what it teaches. One entry per
slice. Read the entry, then read the code it points at, then do the exercise.

**How we're working:** Claude builds the reference implementation and the hard
parts (security, transactions, concurrency). You build the repetitions. Each
entry ends with a "Your turn" section — that's where the learning is.

---

## Entry 001 — Database schema

**Files:** [`V1__initial_schema.sql`](../src/main/resources/db/migration/V1__initial_schema.sql)

### What it solves

The base44 version stored 12 entities as loose JSON schemas with no foreign
keys, no unique constraints, no check constraints and no indexes. Every rule
about the data lived in JavaScript, which meant any bug or any direct API call
could write nonsense — a bill with `status: 'paid'` and no payment date, two
bills for the same flat and month, a complaint pointing at a deleted flat.

The database now enforces those rules itself. It's the last line of defence
and the only one that can't be bypassed.

### Key decisions

| Decision | Why |
|---|---|
| `wing` as a reference table, not an enum | base44 hardcoded `A`/`B` in 12 places. Adding Wing C is now an `INSERT`, not a schema migration + redeploy. |
| `UNIQUE (flat_id, year, month)` on bills | Makes the "Generate Bills" button safe to press twice. Idempotency enforced by the DB, not by hoping. |
| `owner_name` denormalised onto bills | Deliberate snapshot. If a flat changes hands, last year's bills must still show who was billed at the time. |
| `financial_transaction`, not `transaction` | `TRANSACTION` is a reserved SQL word. |
| `app_user`, not `user` | Same reason — `USER` is reserved in Postgres. |
| `version BIGINT` on every table | Backs JPA optimistic locking — see Entry 004. |
| Check constraints on every enum column | Postgres native `ENUM` types are painful to alter. `VARCHAR + CHECK` gives the same safety and can be changed with one migration. |

### Interview questions this maps to

**Q: What's the difference between a clustered and non-clustered index?**
A clustered index *is* the table — it determines physical row order, so there's
one per table (in Postgres, the primary key is not automatically clustered; you
opt in with `CLUSTER`). A non-clustered index is a separate structure pointing
back at rows. Our `idx_bill_lookup (wing, year, month, status)` is
non-clustered.

**Q: Why is column order in a composite index significant?**
An index on `(wing, year, month, status)` can serve queries filtering on `wing`,
or `wing + year`, or `wing + year + month` — a left-most prefix. It cannot
efficiently serve a query filtering only on `status`. That's why the column
order mirrors how `Maintenance.jsx` actually filters.

**Q: When would you denormalise?**
When the duplicated value is a historical snapshot (our `owner_name` on bills),
or when a join is measurably hot. Not as a default.

### Your turn

1. Start Postgres: `docker compose up -d`
2. Connect: `docker exec -it pride-db psql -U pride -d pride_universal`
3. Run `\d maintenance_bill` and read every constraint.
4. Try to break it — each of these **should** fail:
   ```sql
   INSERT INTO maintenance_bill (flat_id, wing, flat_number, month, year, amount, status)
   VALUES (gen_random_uuid(), 'A', '101', 13, 2026, 1000, 'pending');   -- bad month

   INSERT INTO maintenance_bill (flat_id, wing, flat_number, month, year, amount, status)
   VALUES (gen_random_uuid(), 'A', '101', 1, 2026, 1000, 'paid');        -- paid, no date
   ```
5. **Exercise:** add a `V2__parking_slot.sql` migration for a `parking_slot`
   table — slot number, wing, assigned flat (nullable FK), monthly charge.
   Enforce that a slot number is unique within a wing, and that a slot can't be
   assigned to an inactive flat.

---

## Entry 002 — Common layer

**Files:** [`BaseEntity.java`](../src/main/java/com/prideuniversal/common/BaseEntity.java),
[`Role.java`](../src/main/java/com/prideuniversal/common/Role.java),
[`ApiException.java`](../src/main/java/com/prideuniversal/common/ApiException.java),
[`GlobalExceptionHandler.java`](../src/main/java/com/prideuniversal/common/GlobalExceptionHandler.java)

### What it solves

Shared identity, auditing and error handling, so no controller has to think
about them. Without `@RestControllerAdvice`, every controller ends up with its
own try/catch and the API returns a different error shape per endpoint.

### The `equals`/`hashCode` trap — read `BaseEntity` carefully

This is the most-asked JPA question and the code has the answer in it:

- **`hashCode()` returns a constant.** An entity's ID is `null` before persist
  and populated after. If the hash were ID-based and you put the object in a
  `HashSet` before saving, the hash would change after save and the object would
  be unfindable in the bucket it was filed under.
- **`equals()` unwraps Hibernate proxies.** A lazily-loaded association is a
  generated subclass, so a naive `getClass() != o.getClass()` returns false when
  comparing a proxy against its own entity type.
- **Transient instances are equal only to themselves** (`id != null && ...`).

### Interview questions

**Q: What's the contract between `equals` and `hashCode`?**
Equal objects must have equal hash codes. The reverse isn't required — unequal
objects may collide. Breaking the first rule breaks every hash-based collection.

**Q: Why `@Enumerated(EnumType.STRING)` and not `ORDINAL`?**
`ORDINAL` stores the enum's position as an integer. Reorder the enum or insert
a value in the middle and every existing row silently changes meaning. In our
case that would mean residents becoming admins. `STRING` stores `'admin'`.

**Q: Checked vs unchecked exceptions — which for business errors?**
Unchecked. `ApiException` extends `RuntimeException` so it propagates without
polluting every signature with `throws`, and — importantly for Entry 004 —
Spring rolls back on unchecked exceptions by default, but *not* on checked ones.

### Your turn

**Exercise:** `GlobalExceptionHandler.friendlyConstraintMessage()` translates
Postgres constraint names into readable English. Add cases for
`uq_resolution_number` and `ck_vendor_contact`. Then write a `@WebMvcTest` that
asserts a violation returns HTTP 409 with the right `code` field.

---

## Entry 003 — Configuration

**Files:** [`AppProperties.java`](../src/main/java/com/prideuniversal/config/AppProperties.java),
[`SecurityConfig.java`](../src/main/java/com/prideuniversal/config/SecurityConfig.java),
[`CacheConfig.java`](../src/main/java/com/prideuniversal/config/CacheConfig.java),
[`application.yml`](../src/main/resources/application.yml)

### What it solves

Typed, validated configuration bound once at startup, instead of `@Value`
strings scattered across the codebase. A typo in a property name now fails the
application context on boot rather than surfacing as a `null` in production.

### Key decisions

- **Records for `@ConfigurationProperties`** — constructor binding, immutable,
  no setters to accidentally mutate at runtime.
- **`spring.jpa.hibernate.ddl-auto: validate`** — Flyway owns the schema.
  Hibernate only checks that its mappings match. Never `update` in a real
  project: it makes unreviewable, unrepeatable schema changes.
- **`open-in-view: false`** — Spring Boot defaults this to `true`, which keeps
  the Hibernate session open through view rendering. That hides N+1 problems
  until production. Turning it off makes them fail loudly in development.
- **Caffeine, one cache** — only `wing_settings` is cached: read on nearly
  every bill operation, written twice a year. Everything else is either
  write-heavy or already cheap.

### Interview questions

**Q: Why is `open-in-view` controversial?**
It keeps a database connection held for the whole request including view
rendering, and lets lazy loads fire from the presentation layer — so N+1
queries appear where you can't see them. It exists to prevent
`LazyInitializationException`, but the right fix is fetching what you need in
the service layer.

**Q: `ddl-auto` options?**
`none`, `validate`, `update`, `create`, `create-drop`. Use `validate` with a
migration tool. `update` never drops or alters columns safely and can't be
reviewed or rolled back.

**Q: Why is CSRF disabled here?**
CSRF exploits *ambient* credentials — cookies the browser attaches
automatically. A bearer token in an `Authorization` header is not ambient; an
attacker's page can't make the browser add it. If we used cookie auth, CSRF
protection would be mandatory.

### Your turn

**Exercise:** add an `app.maintenance.grace-period-days` property (default 5),
bind it in `AppProperties`, and add `@Validated` with `@Min(0) @Max(30)`.
Confirm the app refuses to start when you set it to `-1`.

---

## Entry 004 — Authentication

**Files:** [`JwtService.java`](../src/main/java/com/prideuniversal/auth/JwtService.java),
[`JwtAuthFilter.java`](../src/main/java/com/prideuniversal/auth/JwtAuthFilter.java),
[`OtpService.java`](../src/main/java/com/prideuniversal/auth/OtpService.java),
[`OtpAttemptRecorder.java`](../src/main/java/com/prideuniversal/auth/OtpAttemptRecorder.java),
[`TokenService.java`](../src/main/java/com/prideuniversal/auth/TokenService.java)

### What it solves

The base44 auth was not authentication. Specifically:

- `MOCK_OTP = '000000'`, hardcoded, and printed on the login screen.
- `verifyOtp` returned a role in plain JSON; the browser stored it in
  `sessionStorage`; `useRole()` read it back. So
  `sessionStorage.setItem('otp_user_role','admin')` made you an admin.
- The admin's mobile number was hardcoded in committed source.
- `requiresAuth: false` on the API client.

Now: codes are `SecureRandom`, BCrypt-hashed at rest, single-use, 5-minute TTL,
attempt-limited and rate-limited. Role comes from the database into a signed
JWT. Every endpoint rule is enforced server-side before the controller runs.

### Three things worth studying closely

**1. `OtpAttemptRecorder` — transaction propagation**

When `verify()` finds a wrong code it increments the attempt counter, then
throws. If that increment ran in the caller's transaction, **the throw would
roll it back** — the counter would never rise and the lockout would silently not
exist, giving unlimited guesses at a 6-digit code.

`@Transactional(propagation = REQUIRES_NEW)` suspends the caller's transaction
and commits this one independently.

It's also a **separate bean**, and that's not stylistic. Spring's
`@Transactional` works through a proxy. Calling a method on `this` goes straight
to the underlying object and never touches the proxy, so the annotation is
silently ignored. **Self-invocation is the #1 reason `@Transactional` "doesn't
work"** and it's a very common interview question.

**2. `checkBurstLimit()` — concurrency**

```java
burstWindows.compute(mobile, (key, existing) -> { ... });
```

`compute` applies the whole read-modify-write atomically for that key. A `get`
followed by a `put` would let two concurrent requests both read `count=2` and
both store `3` — the limit leaks under exactly the load it exists to stop.

`ConcurrentHashMap` rather than `synchronized HashMap` because the whole map
shouldn't lock for an operation touching one key.

**3. BCrypt for OTPs, SHA-256 for refresh tokens**

Not inconsistency — different threat models:

| | OTP | Refresh token |
|---|---|---|
| Entropy | ~20 bits (6 digits) | 384 bits |
| Brute-forceable? | Yes — needs slow hashing | No |
| Looked up by value? | No (by mobile) | Yes, every refresh |
| Hash | BCrypt (slow, salted) | SHA-256 (fast, deterministic) |

BCrypt salts per row, so it can't be used for a lookup — you'd table-scan.

### Interview questions

**Q: Access token vs refresh token — why both?**
A signed JWT can't be un-signed, so it's valid until it expires. Short-lived
access tokens (12h) bound that exposure; the refresh token is the revocable
half because it lives in the database. Logout deletes the row.

**Q: What's refresh token rotation?**
Each refresh token is single-use — refreshing issues a new one and revokes the
old. A stolen token is only usable until the legitimate client next refreshes.

**Q: Where should a JWT be stored on the client?**
Not `localStorage` (any XSS reads it). An httpOnly cookie is safer against XSS
but then needs CSRF protection. For our React Native app: `expo-secure-store`,
backed by the iOS Keychain / Android Keystore.

**Q: Why `OncePerRequestFilter`?**
Without it the filter re-runs on internal forwards and error dispatches,
re-parsing the token for no reason.

**Q: Is a JWT encrypted?**
No — signed. The payload is base64, readable by anyone holding it. Never put
secrets in claims. The signature guarantees the claims weren't *altered*, which
is exactly what `sessionStorage` couldn't do.

### Your turn

1. Start the app, open http://localhost:8080/swagger-ui.html
2. `POST /api/auth/otp/send` with a seeded mobile. The OTP appears in the
   console log (and in `dev_otp` while `app.otp.dev-mode=true`).
3. `POST /api/auth/otp/verify` → you get a token pair.
4. **Break it deliberately:**
   - Submit a wrong OTP 6 times. Confirm you're locked out.
   - Request 4 OTPs quickly. Confirm the burst limit fires.
   - Paste the access token into [jwt.io](https://jwt.io) — read the claims,
     then change `role` to `admin` and try the modified token. It must be
     rejected. **This is the exact attack the old app allowed.**
5. **Exercise:** `OtpService` has no tests. Write `OtpServiceTest` with Mockito
   covering: unknown mobile → 404; correct code → returns user; wrong code →
   401 *and* `recordFailedAttempt` was called; expired OTP → 401. Verify the
   attempt recorder is invoked with `verify(recorder).recordFailedAttempt(id)`.

---

## Entry 005 — First run: three bugs the compiler could not catch

**Files:** [`RoleConverter.java`](../src/main/java/com/prideuniversal/common/RoleConverter.java),
[`User.java`](../src/main/java/com/prideuniversal/domain/user/User.java),
[`SecurityErrorResponder.java`](../src/main/java/com/prideuniversal/config/SecurityErrorResponder.java),
[`SecurityConfig.java`](../src/main/java/com/prideuniversal/config/SecurityConfig.java),
[`GlobalExceptionHandler.java`](../src/main/java/com/prideuniversal/common/GlobalExceptionHandler.java)

### What it solves

Entries 001–004 were written without ever being compiled or run. This entry is
the first execution: Postgres up, Flyway applied, app started, auth exercised
end to end with `curl`.

The headline result is the lesson. **All 30 files compiled on the first
attempt — zero syntax or type errors — and there were still three real bugs.**
Two of them would have reached production. Every one lived in the gap
*between* two components, which is precisely the region a compiler cannot see:
it type-checks each side of a boundary, never the agreement across it.

Worth internalising: "it compiles" is a statement about one file at a time.
"It runs" is a statement about the system.

### Bug 1 — Two mapping layers, one enum, one told

`User.role` was annotated `@Enumerated(EnumType.STRING)`, with a comment
stating it stored the lowercase code. It did not. `@Enumerated(STRING)`
persists `Enum.name()` — `"ADMIN"` — while the `ck_user_role` CHECK constraint
only accepts `'admin'`. Every insert failed:

```
ERROR: new row for relation "app_user" violates check constraint "ck_user_role"
```

The trap is that `Role` *already* carried `@JsonValue getCode()`, so the
codebase looked like it mapped to lowercase everywhere. But `@JsonValue` is a
**Jackson** annotation. It governs JSON serialisation. JPA has never heard of
it. Two independent mapping layers sharing one enum, and each has to be told
separately.

Fixed with a JPA `AttributeConverter` (`RoleConverter`) rather than by
loosening the constraint, because the lowercase code is the contract the JWT
claim and the JSON API already use — the database was the odd one out.
`autoApply = true` means the 10 domains still to be built cannot reintroduce
the bug by forgetting an annotation.

> Note what caught this: the CHECK constraint from Entry 001. Without it the
> row would have inserted `'ADMIN'` happily and the bug would have surfaced
> much later as a mysterious authorisation failure. This is the argument for
> constraints in the database rather than only in application code.

### Bug 2 — `@RestControllerAdvice` cannot see the filter chain

`GlobalExceptionHandler` had correct-looking handlers mapping
`AuthenticationException` → 401 and `AccessDeniedException` → 403.
**Neither ever ran.** An unauthenticated request got a bare `403` with an empty
body.

`@RestControllerAdvice` only handles exceptions that escape the
`DispatcherServlet`. Spring Security rejects requests in the servlet **filter
chain**, which sits *in front of* the DispatcherServlet — so the request is
refused before any controller advice is in scope. `ExceptionTranslationFilter`
handles it instead, and with no collaborators configured it defaults to an
empty 403.

The fix wires an `AuthenticationEntryPoint` and an `AccessDeniedHandler`
(`SecurityErrorResponder`) into `http.exceptionHandling(...)`.

Why the status code is worth caring about, rather than a pedantic detail:

| Code | Means | Correct client reaction |
|---|---|---|
| 401 | "I don't know who you are." | Refresh the token, retry silently |
| 403 | "I know who you are; no." | Show the user a message |

Collapsing both into 403 would make the React Native app tell a resident whose
token merely expired that they are *not allowed in* — instead of refreshing.
A phase-2 bug, fixed in phase 1.

### Bug 3 — A catch-all that relabelled client errors as server errors

`@ExceptionHandler(Exception.class)` matches *everything*, including the typed
exceptions Spring MVC raises for ordinary client mistakes. Observed:

| Request | Was | Should be |
|---|---|---|
| GET an unmapped path | 500 `internal_error` | 404 |
| POST malformed JSON | 500 `internal_error` | 400 |
| GET a POST-only endpoint | 500 `internal_error` | 405 |

Each was also logged at ERROR *with a stack trace*. A single typo'd URL from
the mobile app would have looked like a server fault — and in production, real
500s would be buried under that noise.

Rather than enumerate every Spring MVC exception, the catch-all now asks the
exception itself: Spring 6's `ErrorResponse` interface is implemented by those
exceptions and already carries the right status. Honouring it fixes 405 and 415
without a handler being written for either — verified: 405 came back correct
with no code naming it.

### Interview questions this maps to

**Q: What does `@Enumerated(EnumType.STRING)` store, and when is it wrong?**
It stores `Enum.name()`. It is wrong when the database vocabulary differs from
the Java constant names — a lowercase code, a legacy abbreviation, a value
shared with another system. Use an `AttributeConverter` then. And never
`ORDINAL`: reordering the enum silently reassigns every existing row.

**Q: Why did your `@RestControllerAdvice` not catch the security exception?**
Because Spring Security operates as a servlet filter, before the
DispatcherServlet. Controller advice only covers exceptions escaping the
dispatch. Security failures need an `AuthenticationEntryPoint` /
`AccessDeniedHandler`.

**Q: 401 vs 403?**
401 = unauthenticated (who are you?). 403 = authenticated but unauthorised
(I know, and no). The client contract depends on it: 401 triggers a token
refresh, 403 does not.

**Q: What is wrong with `@ExceptionHandler(Exception.class)`?**
Nothing, as a last resort — but it swallows framework exceptions that already
carry correct statuses, turning client errors into 500s and destroying the
signal-to-noise ratio of your error logs. Handle the typed cases first, or
inspect for `ErrorResponse`.

**Q: Your code compiled and the schema was valid. Why did it still fail?**
Because both are single-component checks. The failures were all agreements
*between* components: JPA vs a CHECK constraint, controller advice vs the
filter chain, a catch-all vs framework exception types. That is the case for
integration tests over unit tests at boundaries.

### Verified behaviour (first end-to-end run)

| Check | Result |
|---|---|
| Flyway `V1` applied to Postgres 16 | ✅ 16 tables, `success = t` |
| Hibernate `ddl-auto: validate` vs schema | ✅ passes |
| OTP send → verify → token pair | ✅ |
| Resident auto-provisioned from flat contact | ✅ role `resident`, flat linked |
| Seeder idempotency on restart | ✅ "already present, skipping" |
| OTP resend cooldown | ✅ 429 |
| OTP single-use (replay) | ✅ 401 |
| Anonymous → protected route | ✅ 401 |
| Resident → admin route | ✅ 403 |
| **Forged `role=admin` JWT** | ✅ **401 — signature check holds** |
| Admin → admin route | ✅ 200 |

The forged-token row is Entry 004's "Your turn" step 4, now executed: the exact
attack the base44 app permitted is rejected.

### Your turn

1. Reproduce Bug 1 from the database side. Open `psql` and run
   `INSERT INTO app_user (id, mobile, display_name, role, is_active, created_date, updated_date, version)
   VALUES (gen_random_uuid(), '9999999999', 'Test', 'ADMIN', true, now(), now(), 0);`
   Read the error. Then retry with `'admin'`. That two-line experiment is the
   whole bug.
2. Delete the `@Converter(autoApply = true)` annotation from `RoleConverter`
   (keep the class) and restart. Predict the failure *before* you run it.
3. **Exercise — the tests that would have caught these.** There are still no
   tests. Write `AuthFlowIntegrationTest` using `@SpringBootTest` +
   `@AutoConfigureMockMvc` + Testcontainers (`postgres:16-alpine`), asserting:
   anonymous `/api/auth/me` → 401; resident → `/actuator/metrics` → 403; a
   token with a tampered `role` claim → 401; an unmapped path → 404.
   Each one pins a bug fixed above so it cannot silently return.
4. Note *why* Testcontainers and not H2: the schema uses `gen_random_uuid()`,
   the `~` regex operator and `TIMESTAMPTZ`. H2 has none of them. The bug in
   this entry was caught by a Postgres CHECK constraint — a fake database would
   have hidden it.

---

## Entry 006 — The reference CRUD slice, and the first tests

**Files:** [`FlatDtos.java`](../src/main/java/com/prideuniversal/domain/flat/dto/FlatDtos.java),
[`FlatService.java`](../src/main/java/com/prideuniversal/domain/flat/FlatService.java),
[`FlatController.java`](../src/main/java/com/prideuniversal/domain/flat/FlatController.java),
[`AbstractIntegrationTest.java`](../src/test/java/com/prideuniversal/AbstractIntegrationTest.java),
[`AuthFlowIntegrationTest.java`](../src/test/java/com/prideuniversal/auth/AuthFlowIntegrationTest.java),
[`FlatApiIntegrationTest.java`](../src/test/java/com/prideuniversal/domain/flat/FlatApiIntegrationTest.java)

### What it solves

Two things at once: the CRUD pattern every remaining domain will copy, and the
repository's first tests. **28 tests, all passing, against a real Postgres 16
container.**

Doing them together was deliberate. The slice is the thing the next ten domains
imitate, so it is worth having proof that it behaves as described rather than
only a claim that it does — and, as it turned out, the tests found a bug in the
slice within minutes.

### Decision 1 — Two response records, not one filtered record

A flat row carries owner and tenant mobile numbers, vehicle registrations and
share certificate numbers. In base44 all of it was readable by anyone who could
reach the URL. That single fact is why this rewrite exists, so the flat API is
where it has to be answered properly.

| Type | Contains | Who gets it |
|---|---|---|
| `FlatSummary` | wing, number, floor, resident name | any authenticated user |
| `FlatDetail` | everything | committee, or a resident's own flat |

The alternative — one fat DTO with contact fields nulled out for residents —
works right up until someone adds a field and forgets to null it. **A type that
never held the data cannot be made to spill it.** The nulling approach fails
open; this one fails closed.

`GET /api/flats` therefore returns summaries *even to an admin*. Full records
are fetched one at a time, where the permission check is unambiguous.

### Decision 2 — Authorisation on the service, not the controller

`@PreAuthorize` sits on `FlatService`, not `FlatController`.

A controller is only one way in. A scheduled job, another service, or a future
GraphQL layer reaches the same method without passing the controller — and a
rule enforced at the edge is a rule the next entry point silently skips.

`SecurityConfig`'s URL rules still exist and still matter. They are a coarse
outer fence; the method annotations are the actual decision. Defence in depth
means one mistake is not a breach.

### Decision 3 — Optimistic locking, in two layers on purpose

`PUT /api/flats/{id}` requires the `version` the client last read.

1. **Explicit comparison** in the service: catches the ordinary case — two
   committee members with the same screen open — and returns a clean 409 before
   doing any work.
2. **Hibernate's `@Version` column**: catches what layer 1 cannot. Two requests
   can both read version 3, both pass the check, and only then race to write.
   The loser fails at flush with
   `ObjectOptimisticLockingFailureException` — a subclass of the exception
   thrown by layer 1, so both surface identically as 409.

Layer 1 is the friendly early exit. Layer 2 is the guarantee. A check-then-act
in application code is never atomic by itself; it needs the database to make it
so.

Sending no version at all is a **400**, never a silent overwrite — "I didn't
know about concurrency" must not be a way to win a race.

### Decision 4 — Delete is deactivation

Bills, complaints and payment history all reference `flat`. A hard delete would
either cascade financial records away or fail on a foreign key. `DELETE` sets
`is_active = false`, which removes the flat from the directory and leaves the
ledger intact. It is idempotent: deactivating twice is not an error.

Note the permission split — secretaries may *edit* the register, but only
admin/chairman may *deactivate*. Losing a flat from the directory is a bigger
event than fixing a typo in an owner's name.

### Bug 4 — The stale version echo (found by a test, not by hand)

`updateWithCurrentVersionSucceeds` failed on the first run:

```
JSON path "$.version" expected:<1> but was:<0>
```

The update had succeeded — owner name changed, 200 returned — but the response
carried version `0`, the value the client had sent.

**Why:** Hibernate increments `@Version` at *flush*, and with a plain
`save()` inside a transaction the flush happens at commit — after the service
has already built the response DTO. So the API returned a version that was
already wrong by the time the caller read it.

**Why it matters:** the client's *next* update sends the version the API just
gave it, gets a 409, and has no way out except re-fetching. Two consecutive
edits were impossible. Fixed with `saveAndFlush`, and the test now asserts the
property that actually matters — that a second update using the returned
version succeeds.

Worth noticing: this is the first bug in this project found by a test rather
than by a human poking at `curl`. It is also one that manual testing would
plausibly have missed, because a single update looks completely fine.

### The Testcontainers / Docker 29 incompatibility

The first test run could not start a container at all:

```
NpipeSocketClientProviderStrategy: failed with exception BadRequestException
(Status 400: client version 1.32 is too old.
             Minimum supported API version is 1.41)
```

Spring Boot 3.5.0 manages Testcontainers **1.21.0**, which ships docker-java
3.4.2 and negotiates Docker API 1.32. Docker Engine 29 refuses anything below
1.41. Fixed by overriding `<testcontainers.version>` to **2.0.5**
(docker-java 3.7.1). Two migration notes for 2.x:

- modules were renamed — `org.testcontainers:postgresql` is now
  `org.testcontainers:testcontainers-postgresql`;
- `PostgreSQLContainer` moved to `org.testcontainers.postgresql` and is **no
  longer generic** — the old self-referential `PostgreSQLContainer<SELF>` is
  gone, so `new PostgreSQLContainer<>(...)` no longer compiles.

The general lesson: a managed dependency version is a *default*, not a law.
When the environment moves faster than the framework's BOM, override it — and
write down why, or the next person will "tidy up" the override.

### Interview questions this maps to

**Q: Where do you put authorisation — controller or service?**
Service. The controller is one entry point of several; a rule on the edge is
skipped by every other caller. URL rules stay as a coarse outer layer.

**Q: How do you stop two users overwriting each other?**
Optimistic locking with a `@Version` column. The client sends the version it
read; a mismatch is a 409. An explicit comparison alone is not enough — it is a
check-then-act race — so the version column at flush time is the real guard.
Pessimistic locking is the alternative, but holding row locks across a user's
think-time does not scale and invites deadlocks.

**Q: Why not return the JPA entity from the controller?**
It couples the API to the schema, drags lazy associations into serialisation
(N+1 queries, or `LazyInitializationException` with open-in-view off), and lets
a client set fields it must never control. Here it would also have meant one
shape for every audience — no way to give residents a view without contact
numbers.

**Q: Why `readOnly = true` on reads?**
Hibernate skips dirty-checking the loaded entities, and the driver can flag the
transaction as read-only. Cheaper, and it documents intent.

**Q: Why Testcontainers rather than an in-memory database?**
Because the tests are only evidence if they run against what production runs.
This schema uses `gen_random_uuid()`, the `~` regex operator and `TIMESTAMPTZ`;
H2 has none of them. And the enum bug in entry 005 was caught by a Postgres
CHECK constraint that H2 would not have enforced.

### Test coverage now in place

| Suite | Tests | Pins |
|---|---|---|
| `AuthFlowIntegrationTest` | 13 | 401 vs 403, forged JWT, role encoding, 404/400/405, OTP single-use, cooldown, auto-provisioning |
| `FlatApiIntegrationTest` | 15 | PII boundary, per-role write permissions, validation, optimistic locking, soft delete |

All 28 run against one Postgres container in about 25 seconds.

### Your turn

1. Read `FlatService.update()` and answer: why is the explicit version check
   *not* sufficient on its own? Then delete it, run the tests, and see which
   ones still pass. (Some will — that is the point.)
2. Break the PII boundary deliberately: add `ownerContact` to `FlatSummary` and
   run the tests. `directoryHidesContactDetails` should fail. Confirm it does,
   then revert.
3. **Exercise — the next slice is yours.** Build the same shape for `Notice`
   (see `V1__initial_schema.sql`): `NoticeDtos`, `NoticeService`,
   `NoticeController`, `NoticeRepository`, plus a `NoticeApiIntegrationTest`.
   The rules differ in an instructive way — residents may *read* notices but
   only `Role.COMMS` may write them, and a notice has a publish date, so a
   draft must not appear in the resident list. Copy the structure, not the
   rules.
4. When you get stuck, re-read `FlatService` rather than asking for the answer.
   Every decision in it is commented with its reasoning; the Notice slice needs
   the same decisions made again, differently.

---

## Next up

- [ ] Entry 007 — Maintenance bills: optimistic locking on concurrent "mark
      paid", bulk generation, `@Scheduled` overdue job (Claude builds)
- [ ] Entry 008 — The remaining 9 domains (**you build** — start with the
      Notice slice in entry 006's "Your turn")
- [ ] Entry 009 — File uploads (the `photo_url` fields base44 never implemented)
- [ ] Entry 010 — Docker + Caddy + VPS deployment
