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

## Next up

- [ ] Entry 005 — Flat domain, the reference CRUD slice (Claude builds)
- [ ] Entry 006 — Maintenance bills: optimistic locking, bulk generation,
      `@Scheduled` overdue job (Claude builds)
- [ ] Entry 007 — The remaining 9 domains (**you build**, scaffolds + failing
      tests provided)
- [ ] Entry 008 — File uploads (the `photo_url` fields base44 never implemented)
- [ ] Entry 009 — Docker + Caddy + VPS deployment
