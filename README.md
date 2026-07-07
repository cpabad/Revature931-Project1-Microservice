# ERS Microservice (`ers-service`)

A Spring Boot 3 / Java 17 microservice for the **Employee Reimbursement System (ERS)** — the
Phase 4 refactor of a legacy Java 8 / JDBC / Hibernate / standalone-Tomcat monolith into a
single, independently deployable service. Decomposed by **business capability**, with hand-rolled
DAOs replaced by Spring Data JPA and hand-rolled `HttpSession` servlet filters replaced by
Spring Security + a **self-issued JWT**.

> **Full history & the monolith it came from:** [`cpabad/Christian-Project1`](https://github.com/cpabad/Christian-Project1)
> — that repo holds the original monolith, the hardening/refresh changelog, and the
> monolith→microservice journey. This repo is the go-forward home of the service itself.

## Stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3.3.5 (Jakarta EE namespace) |
| Language / build | Java 17, compiled on JDK 21 |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL |
| Security | Spring Security OAuth2 **resource server**, self-issued **HS256** JWT |
| Passwords | BCrypt (carried over from the monolith's `loginpassword` hashes) |
| Tests | JUnit 5 + Spring Test (MockMvc), against the seeded DB |

## Prerequisites

- **A JDK 17+ _with a compiler_.** Boot 3 needs a Java 17+ `javac`. A JRE won't do (no `javac`);
  this project was built on a portable Temurin **JDK 21**. Point `JAVA_HOME` at a real JDK.
- **PostgreSQL** with the ERS schema seeded (below).

## Database setup

The service runs `ddl-auto=validate` — it never creates or mutates the schema, only checks its
entities against an existing one. Stand the schema up from the bundled dump:

```bash
# 1. a database + login role the service connects as
createdb ers
psql -d postgres -c "CREATE ROLE ers LOGIN PASSWORD 'ers';"

# 2. the ERS schema "ExpenseReimbursementManagementSystem" + seed data
psql -d ers -f db/ers_script.sql
```

The schema name is mixed-case (`"ExpenseReimbursementManagementSystem"`) and is mapped explicitly
in the entities, so the as-is (non-snake_case) Hibernate naming strategy is configured to match.

## Build & run

The datasource and JWT secret are read from environment variables:

```bash
export JAVA_HOME="/path/to/jdk-21"
export dburl="jdbc:postgresql://localhost:5432/ers"
export dbuser=ers
export dbpassword=ers
# Optional: HS256 needs a >=32-char secret. Defaults to a dev placeholder if unset.
export ERS_JWT_SECRET="change-me-to-a-32+-char-random-secret"

mvn test            # 26 tests, all green (write tests roll back; the seed stays pristine)
mvn spring-boot:run # serves on http://localhost:8080
```

## Auth model

Login mints a token; every other request must carry it.

1. `POST /login` with `{"username","password"}` → verifies the BCrypt hash, returns a signed JWT
   (`sub`=userId, `role` claim = `"Supervisor"`/`"Employee"`, plus `iss`/`iat`/`exp`).
2. Send it on subsequent calls as `Authorization: Bearer <token>`. Spring's resource-server filter
   validates the signature + expiry on every request — the service holds **no session** (stateless).
3. Roles are enforced two ways: URL rules (`authorizeHttpRequests`) and method security
   (`@PreAuthorize`). `hasRole('Supervisor')` checks the `ROLE_Supervisor` authority the JWT
   converter derives from the `role` claim.

### Endpoints

| Method & path | Access | Notes |
|---|---|---|
| `POST /login` | public | mint a JWT |
| `GET /requests` | **Supervisor** | all employees' requests |
| `GET /requests/{id}` | authenticated | one request |
| `GET /requests/requester/{userId}` | authenticated | a user's requests |
| `POST /requests` | authenticated | submit for yourself (requester = token subject); fans out the approval chain; 201 |
| `PUT /requests/{id}/approval` | **Supervisor** | apply your approve/deny; returns `{"outcome": APPROVED\|DENIED\|ESCALATED\|WAITING_ON_OTHERS}`; 404 no vote, 409 waiting |
| `GET /approvals/pending` | **Supervisor** | your still-pending votes (the approval inbox) |
| `PUT /users/me` | authenticated | update own username/email/password; requires `currentPassword`; 403 wrong password, 409 taken value |
| `GET /roles` | **Supervisor** | reference data |

Unauthenticated → **401** ("who are you?"); authenticated but wrong role → **403** ("not allowed").

## Configuration

| Property | Env var | Default |
|---|---|---|
| `spring.datasource.url` | `dburl` | — |
| `spring.datasource.username` | `dbuser` | — |
| `spring.datasource.password` | `dbpassword` | — |
| `ers.jwt.secret` | `ERS_JWT_SECRET` | dev-only placeholder (override in prod) |
| `ers.jwt.ttl-seconds` | `ERS_JWT_TTL_SECONDS` | `3600` |
