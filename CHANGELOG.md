# Changelog — ERS Microservice

The history of the `ers-service` microservice (Phase 4 of the ERS project). For the **monolith**
this was refactored from — and the hardening/refresh that preceded it — see the
[`Christian-Project1`](https://github.com/cpabad/Christian-Project1) changelog.

---

## Phase 4 — microservice build

Decompose ERS into a Spring Boot 3 / Java 17 service by **business capability** (not technical
layer), reusing the same seeded PostgreSQL. Reference architecture: the past-cohort "Chronicle"
microservice (containerized Spring Boot + JWT).

### Foundation
- **Scaffold.** Spring Boot 3.3.5 module, builds on a portable Temurin JDK 21 (Boot 3 needs a
  Java 17+ *compiler*; the box's default Java 21 was a JRE with no `javac`).
- **Role tracer.** A first end-to-end slice (entity → Spring Data repo → service → controller →
  MockMvc) proving the whole stack against the seeded DB.
- **Request slice.** Entities `Request`/`User`/`Role`/`EventLocation`/`CityStatePostal`/
  `RequestStatus` + `RequestRepository` + service + controller + tests. Hand-rolled DAOs
  (~185 lines of `Session`/try/catch/commit per method) collapse to Spring Data interfaces.
  Entity mapping uses the as-is Hibernate naming strategy with explicit lowercase columns and the
  quoted mixed-case schema, so `ddl-auto=validate` matches the existing DB.

### Auth — self-issued JWT + Spring Security
Replaced the monolith's three hand-rolled `HttpSession` servlet filters
(`SessionFilter`/`EmployeeFilter`/`ManagerFilter`) with Spring Security. ERS owns its user table
and BCrypt hashes, so it **issues its own JWTs** — no external identity provider.

- **Validation side.** `SecurityConfig`: stateless, CSRF-disabled (moot for a Bearer-token API),
  deny-by-default. A Nimbus `JwtDecoder` verifies the HS256 signature + expiry; a
  `JwtAuthenticationConverter` maps the custom `role` claim to a `ROLE_*` authority.
- **Issuer side.** `JwtEncoder` + `TokenService.mint` (`sub`=userId, `role`, `iss`, `iat`/`exp`)
  + `POST /login`, which verifies `BCryptPasswordEncoder.matches` against `loginpassword` and
  returns a signed token. One `401` covers both unknown-user and bad-password (no username
  enumeration).
- **Role rules.** The filters' Supervisor/Employee split, two ways against the same claim: a path
  rule (`GET /requests` → `hasRole('Supervisor')`) and method security (`@EnableMethodSecurity` +
  `@PreAuthorize` on `GET /roles`).
- 17/17 tests green, asserting the **401 (no/invalid token) vs 403 (valid token, wrong role)**
  distinction.

### Repo
- Extracted into this standalone repository from the monolith repo, preserving the microservice
  commit history (`git subtree split`), with `ers-service/` promoted to the project root.

### Approval chain — the domain core, ported from the extracted monolith services
The 2026-07 monolith stabilization pass moved the two business cascades out of `RequestHelper`
into tested services; this slice ports them, which is why the port is a translation instead of an
untangling. Entities `Hierarchy` (the `employee_supervisor_jt` reporting graph) /
`SupervisorApproval` / `SupervisorApprovalStatus` / `Reimbursement` / `ReimbursementStatus`, five
reference-data repositories, `ApprovalService`, and three endpoints:

- **`POST /requests`** (any authenticated user) — `RequestService.submit`, the monolith's
  `submitRequest` fan-out: one pending vote per direct supervisor of the requester, plus the
  pending reimbursement hung off the top-of-chain supervisor's vote. The requester is the token's
  `sub`, never a body field. Two upgrades over the monolith: `save()` returns the generated id
  (the insert-then-re-find hack dies), and the whole fan-out is **one `@Transactional` unit**
  instead of one transaction per write.
- **`PUT /requests/{id}/approval`** (Supervisor) — `ApprovalService.resolveApproval`, the
  monolith's approval-resolution cascade: APPROVED / DENIED / ESCALATED / WAITING_ON_OTHERS.
  Counters are computed from database state *before* the decision is applied (exactly the
  monolith's fresh-read semantics); nothing is mutated on the waiting path, since JPA
  dirty-checking would otherwise flush it anyway. Unknown request/manager pair → 404 (the
  monolith NPE'd); waiting → 409 (the monolith said 400).
- **`GET /approvals/pending`** (Supervisor) — the manager's inbox as one derived query, replacing
  the monolith's `findAll()`-then-filter-in-Java.

Two findings the port surfaced:
- **`WAITING_ON_OTHERS` is unreachable on referentially consistent data.** A "subordinate with a
  pending vote" needs a hierarchy row proving they are *not* top-of-chain — which routes their
  vote into the other counter. Kept for parity, documented on the enum.
- **The seed carries a stray second reimbursement for request 2** (hung off a *non-top* approval),
  violating the fan-out invariant. Traced against that data, the monolith's approve-path on
  request 2 would crash: its `getSingleResult` reimbursement lookup throws an uncaught
  `NonUniqueResultException` → 500. Never seen because the monolith unit-tests mock that
  repository. The port resolves **all** reimbursement rows of the request — identical on
  consistent data, total instead of crashing on the legacy row (pinned by a test).

Tests 17 → 26, all green. The write tests are `@Transactional` so every insert/update rolls back
— the shared seed (which the monolith's integration tests treat as the spec) stays byte-identical,
verified by row counts and status spot-checks after the run.

### Profile self-service — the last capability, with deliberately cleaner semantics
`PUT /users/me` ports the monolith's `UserService.updateProfile`, redesigned rather than
translated (the one slice where the monolith's behavior was NOT worth keeping):

- **One identity gate.** `currentPassword` authorizes the whole form (the monolith demanded the
  old value of each field being changed, but only the password check actually proved identity).
- **Conflicts are explicit.** A taken username/email returns **409**; the monolith silently
  skipped the change and still reported success (a quirk its tests pin as documentation).
  The uniqueness check ignores the caller's own row — resubmitting your current username is not
  a conflict.
- **`/me` only.** The subject comes from the JWT; there is no user-id parameter to tamper with.
- Wrong password → 403; nothing to update → 400; rejected forms change nothing (checks precede
  all mutation, inside one transaction).

Implementation notes: `ProfileUpdateResult` is a record wrapping a status enum; the controller
maps it with a Java 17 exhaustive `switch` expression (add a status, the compile breaks until it
is handled). Tests stamp user 3 with a known bcrypt hash inside the rolled-back test transaction,
since the seed's plaintexts are unrecorded. Tests 26 → 34, all green.

### The service split — gateway + auth-service + reimbursement-service
The first genuine service boundary: the single Boot app becomes a Maven multi-module reactor with
three independently deployable applications, in the shape of the Chronicle reference architecture.

- **`ers-auth-service` (:8081) owns identity.** Login (the system's only `JwtEncoder`), profile
  self-service, roles reference data, and the only write access to `users`/`roles`. Package
  `com.revature.ers.auth`.
- **`ers-reimbursement-service` (:8082) owns the domain.** Requests, the approval chain,
  reimbursements — as a **pure resource server**: it validates tokens it cannot mint (HS256
  symmetry, shared secret) and maps `users` **read-only without the password column**, so the
  credential physically cannot leak from it. Duplicating a trimmed entity per service instead of
  sharing a domain library is deliberate microservice orthodoxy: duplication over coupling.
- **`ers-gateway` (:8080).** Spring Cloud Gateway routes by path (`/login`,`/users/**`,`/roles/**`
  → auth; `/requests/**`,`/approvals/**` → reimbursement) and forwards the Bearer token untouched
  — each service authorizes its own resources, so a compromised gateway cannot mint identities.
- **Shared DB with documented ownership** (the chosen training step before databases-per-service).

**Verified two ways.** Per-service test suites (33 tests: 16 auth, 16 reimbursement, 1 gateway
route config), plus a **live end-to-end smoke test**: all three JVMs running, login through the
gateway, then one token exercising routes proxied to BOTH services (200/200/200), no token → 401,
unrouted path → 404. The upgraded `AuthorizationRulesTest` doubles as a cross-service contract
test — it mints tokens with its own Nimbus encoder from the shared secret, proving an externally
minted token validates in a service that has no encoder.

**Two traps worth recording:**
- **Version skew, invisible until runtime.** Spring Cloud Gateway 4.1.6 (train 2023.0.5) calls
  `HttpHeaders.headerSet()`, which only exists from Spring Framework 6.1.15 — Boot 3.3.5 ships
  6.1.14. The gateway *starts cleanly* and throws `NoSuchMethodError` on the **first proxied
  request**. Fix: Boot parent 3.3.5 → 3.3.6. Lesson: BOM alignment bugs can hide behind a green
  startup; smoke-test the actual request path.
- **Port 8080 was already taken** on the dev box — by the monolith's own standalone Tomcat. The
  smoke test runs the gateway on 9080; the README notes the flag.

The smoke test needed a known password (the seed's bcrypt plaintexts are unrecorded): it stamps
user 1 with a known hash, runs, and restores the original **byte-identical** (verified) — the
shared seed stays the monolith tests' source of truth.

### Databases-per-service — the ownership boundary made physical
Each service now runs against its **own PostgreSQL database**; the shared-DB arrangement (and the
legacy `db/ers_script.sql` dump, kept for history/monolith parity) is retired for the services.

- **`ers_auth`** (`db/auth/init.sql`): users + roles, credentials included. auth-service is the
  only writer of identity anywhere in the system.
- **`ers_reimbursement`** (`db/reimbursement/init.sql`): the domain tables plus a **replicated
  reference copy of users/roles with no password column at all** — the previous slice merely
  didn't *map* the credential; now the column doesn't exist in this service's database. In
  production the copy would be synced by events from auth-service; with a static seed it is
  seeded identically (minus the password). The trade — profile renames in auth don't propagate
  here until synced — is the eventual-consistency lesson, documented in the README.
- No cross-database joins exist; the FKs inside each database still hold (requests reference the
  local users copy). Ids use `GENERATED BY DEFAULT AS IDENTITY` with the sequences restarted past
  the seed rows, replacing the dump's serial+setval arrangement.
- Config: `AUTH_DB_URL/USER/PASSWORD` and `REIMB_DB_URL/USER/PASSWORD` (localhost defaults);
  the monolith-era `dburl/dbuser/dbpassword` vars no longer drive the services.

**Verified live:** all 33 tests green against the split databases, plus the three-JVM gateway
smoke test — login answered solely from `ers_auth`, request JSON built from the password-less
copy (`"password" in requester -> False`), and a submitted request fanned out in
`ers_reimbursement` with the identity sequence continuing past the seed.

### Containerized — one Dockerfile, five containers, one command
The Chronicle-shaped deployment: `docker compose up --build` starts two seeded postgres
containers (each running its service's own `init.sql` on first start) plus the three services,
with **only the gateway published to the host** — the services and databases exist solely on the
compose network, which is the network-level spelling of "internal".

- **One parameterized Dockerfile** for all three images: compose passes `MODULE` as a build arg,
  so the recipe lives in one place. Multi-stage — Maven + JDK in the build stage, a bare JRE at
  runtime (no compiler, no source in the shipped image) — running as a non-root user, with a
  BuildKit cache mount so the Maven repo downloads once across all three builds.
- Inside the network, config swaps hostnames, not shapes: `AUTH_DB_URL=jdbc:postgresql://auth-db:...`,
  `AUTH_SERVICE_URL=http://auth-service:8081` — the same env vars local dev uses with localhost.
- `GATEWAY_PORT=9080 docker compose up` for hosts where 8080 is taken (the dev box's monolith Tomcat).

**Verification status, honestly:** Docker is not installed on the dev box, so the containers have
not been executed. What IS verified: the compose YAML parses; the init scripts it mounts are the
exact scripts the live three-JVM smoke test ran against (scratch cluster, port 5433); and the
env-var wiring it sets is the same wiring that smoke test exercised. First `docker compose up`
on a Docker-equipped machine is the remaining step, and would be the first true test of the
Dockerfile build stage.

### SOAP intake + Kafka — the asynchronous pipeline
Legacy partners speak SOAP; the domain shouldn't have to. A fourth module, **`ers-soap-adapter`**
(:8083, no database), is the anti-corruption layer: it accepts a contract-first SOAP submission,
translates it into a JSON event on `reimbursement.request.submitted`, and acknowledges with a
correlation id. reimbursement-service consumes the topic and runs the SAME `RequestService.submit`
fan-out the REST endpoint uses — one domain rule, two transports, decoupled by the broker.

- **Contract-first SOAP** (Spring-WS): `requests.xsd` is the single source of truth — xjc
  generates the payload classes from it at build time, and the WSDL served at
  `GET /ws/requests.wsdl` is generated from the same file at runtime. Dispatch is by payload
  root element, not URL. The fields mirror `POST /requests` (the `Request` entity's submission
  data) plus the requester id.
- **Event contract by duplication**: each side owns its own `RequestSubmissionEvent` record —
  the JSON field names are the contract, pinned textually by BOTH sides' tests (the producer
  test reads the raw string off the broker; the consumer test feeds a raw string in). Producer
  type headers are off; the consumer deserializes by its own class.
- **Semantics documented as they are**: at-least-once; "accepted" = queued, not persisted;
  poison events are logged and dropped (the training-wheels dead-letter topic); `submit` now
  fails soft on an unknown requester (unreachable via REST's JWT, very reachable via a
  partner-asserted id). **Trust boundary**: the partner asserts `requesterUserId`; production
  would authenticate the partner (mTLS/WS-Security) + allowlist — noted on the endpoint.
- Gateway routes `/ws/**` to the adapter; compose gains a single-node KRaft Kafka (internal,
  like the databases) and the adapter container — 7 containers total.

**Tests 33 → 36**, all green, Kafka exercised against a real in-JVM broker (`@EmbeddedKafka`).
Two bugs the new tests caught before any human did:
- **XPath boolean coercion**: `evaluatesTo(true)` on an element asserts "the node exists", so
  the `accepted=false` assertion could never pass — and the `true` one passed vacuously. Fixed
  to compare element TEXT (`evaluatesTo("false")`).
- **Wire-format drift**: the producer's default mapper serialized `LocalDate` as `[2026,7,1]`
  while the consumer contract pinned ISO-8601 `"2026-07-01"`. Caught by the producer-side raw-
  JSON assertion — exactly the drift the textual contract tests exist for. Fixed by making the
  event's `eventDate` a String, pinning ISO-8601 in the contract itself.

### Object-level authorization — closing the IDOR on the request reads
The two request-read routes were only `.anyRequest().authenticated()`, so any logged-in
employee could read ANY employee's reimbursement requests (amounts, event details) by walking
`GET /requests/{id}` ids or `GET /requests/requester/{userId}` userIds — broken object-level
authorization (OWASP API Top 10 #1), and a regression from the monolith, which scoped these
to the session user.

- **Where the check lives**: in the controller, against the injected `JwtAuthenticationToken` —
  NOT in `SecurityConfig`. The filter chain's path rules can express "who may hit this URL
  shape" but not "who owns request 7"; ownership needs the loaded entity. `/requests/{id}` now
  filters to owner-or-Supervisor; `/requests/requester/{userId}` rejects a mismatched caller
  unless Supervisor.
- **404 vs 403 is a security decision**: a foreign `/requests/{id}` returns **404**, because a
  403 would confirm the id exists — an enumeration oracle for an id-walker. The by-requester
  route returns an honest **403**: the caller supplied the userId themselves, so there is no
  existence to leak.
- **Tests as the contract** (`AuthorizationRulesTest`, 9): owner 200 / foreign employee 404 /
  Supervisor 200 by id; own 200 / foreign 403 / Supervisor 200 by requester. The data-shape
  tests now mint numeric subjects — the ownership check parses the JWT `sub` the same way
  `POST /requests` always has.

### Honest SOAP ack — the send result is now part of the answer
`RequestSubmissionEndpoint` called `kafkaTemplate.send(...)` fire-and-forget: the returned
future was ignored, so with the broker down the partner still received `accepted=true` plus a
correlation id for an event that was never published — silent data loss wearing a receipt.

- **Block on the future**: the endpoint now `get()`s the send result before answering; any
  failure returns `accepted=false`, an EMPTY correlation id (nothing was published, so there
  is nothing to correlate), and a retry-later message. The partner's call is synchronous
  anyway, so the happy path pays nothing extra.
- **Fail fast, not in two minutes**: the Kafka client's defaults would hang the synchronous
  caller ~60s in metadata fetch (`max.block.ms`) and retry ~120s (`delivery.timeout.ms`)
  before the future fails. Bounded to 5s/10s in application.properties; `acks=all` pinned so
  "accepted" means the event survives a broker failure (the Kafka 3.x default via
  idempotence, but the ack's honesty depends on it, so it is explicit).
- **Two test styles on purpose**: the embedded-broker integration test can only prove the
  happy path (its broker is up by definition); the broker-down nack is pinned by a plain
  Mockito unit test feeding the endpoint a failed future. Live-drilled too: adapter started
  with no broker answered `accepted=false` in 5.2s (`max.block.ms` firing).
