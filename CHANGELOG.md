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
