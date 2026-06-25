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
