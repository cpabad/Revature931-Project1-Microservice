# ERS Microservices

The **Employee Reimbursement System (ERS)**, decomposed into real services — the endpoint of the
Phase 4 refactor that began as a legacy Java 8 / JDBC / Hibernate / standalone-Tomcat monolith,
became a single Spring Boot service, and now runs as **three independently deployable apps behind
a gateway**:

```
                        ┌──────────────────────┐
   client ──── :8080 ──▶│      ers-gateway      │   Spring Cloud Gateway (reactive)
                        └──────────┬───────────┘   routes by path, forwards the Bearer token
                     /login /users/** /roles/**  /requests/** /approvals/**
                          ▼                            ▼
              ┌────────────────────┐        ┌──────────────────────────┐
              │  ers-auth-service   │        │ ers-reimbursement-service │
              │  :8081  (issuer)    │        │  :8082  (resource server) │
              │  login → mints JWT  │        │  requests / approvals /   │
              │  profile, roles     │        │  reimbursements           │
              │  OWNS users, roles  │        │  reads users read-only    │
              └─────────┬──────────┘        └────────────┬─────────────┘
                        └───────────── PostgreSQL ───────┘
                              (shared DB, ownership per service)
```

> **Full history & the monolith it came from:** [`cpabad/Christian-Project1`](https://github.com/cpabad/Christian-Project1)
> — the original monolith, its hardening/refresh changelog, and the monolith→microservice journey.

## Stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3.3.6 (3.3.6 minimum: Gateway 4.1.6 needs Framework 6.1.15) |
| Gateway | Spring Cloud Gateway (release train 2023.0.5) |
| Language / build | Java 17, compiled on JDK 21; Maven multi-module |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL |
| Security | Self-issued **HS256 JWT**: auth-service mints, every service validates (shared secret) |
| Passwords | BCrypt (hashes carried over from the monolith seed) |
| Tests | JUnit 5 + MockMvc per service; write tests are `@Transactional` (roll back, seed stays pristine) |

## The service boundary, in one paragraph

`ers-auth-service` **owns identity**: it holds the only `JwtEncoder`, the only BCrypt verification,
and the only write access to `users`/`roles`. `ers-reimbursement-service` is a **pure resource
server**: it validates tokens it could never mint (HS256 symmetry via the shared secret) and maps
`users` **read-only, without the password column** — the credential physically cannot leak from a
service that never loads it. The gateway routes by path and passes the Bearer token through
untouched: authorization stays with the service that owns the resource, so a compromised gateway
cannot mint identities. Sharing one database with documented per-service ownership is the training
step before the full lesson (separate databases per service).

## Prerequisites

- **A JDK 17+ with a compiler** (built on a portable Temurin JDK 21 — a JRE has no `javac`).
- **PostgreSQL** with the ERS schema seeded (below).

## Database setup

Both services run `ddl-auto=validate` — they never create or mutate the schema. Stand it up from
the bundled dump:

```bash
createdb ers
psql -d postgres -c "CREATE ROLE ers LOGIN PASSWORD 'ers';"
psql -d ers -f db/ers_script.sql
```

## Build, test, run

```bash
export JAVA_HOME="/path/to/jdk-21"
export dburl="jdbc:postgresql://localhost:5432/ers"
export dbuser=ers
export dbpassword=ers
# HS256 needs a >=32-char secret; BOTH services must get the SAME value (auth signs, reimb verifies).
export ERS_JWT_SECRET="change-me-to-a-32+-char-random-secret"

mvn test          # all three modules: gateway route config + 32 service tests, green
mvn package -DskipTests

# three terminals (or background each):
java -jar auth-service/target/ers-auth-service-0.0.1-SNAPSHOT.jar                    # :8081
java -jar reimbursement-service/target/ers-reimbursement-service-0.0.1-SNAPSHOT.jar  # :8082
java -jar gateway/target/ers-gateway-0.0.1-SNAPSHOT.jar                              # :8080
```

> If the monolith's standalone Tomcat is running locally it already owns 8080 — start the gateway
> with `--server.port=9080` (routes are unaffected; they target 8081/8082 directly).

Smoke test through the front door:

```bash
TOKEN=$(curl -s -X POST localhost:8080/login -H 'Content-Type: application/json' \
        -d '{"username":"<user>","password":"<pass>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s localhost:8080/requests -H "Authorization: Bearer $TOKEN"   # gateway -> reimbursement-service
curl -s localhost:8080/roles    -H "Authorization: Bearer $TOKEN"   # gateway -> auth-service
```

## Auth model

1. `POST /login` (auth-service, via the gateway) verifies the BCrypt hash and returns a signed JWT
   (`sub`=userId, `role` claim, `iss`/`iat`/`exp`).
2. Every other request carries `Authorization: Bearer <token>`. **Each service validates the
   signature itself** — the gateway does not authenticate; it forwards.
3. Roles enforced per service: path rules in each service's `SecurityConfig`, plus `@PreAuthorize`
   on `GET /roles`. Unauthenticated → **401**; authenticated but wrong role → **403**.

### Endpoints (all through the gateway on :8080)

| Method & path | Service | Access | Notes |
|---|---|---|---|
| `POST /login` | auth | public | mint a JWT |
| `PUT /users/me` | auth | authenticated | update own username/email/password; requires `currentPassword`; 403 wrong password, 409 taken value |
| `GET /roles` | auth | **Supervisor** | reference data |
| `GET /requests` | reimbursement | **Supervisor** | all employees' requests |
| `GET /requests/{id}` | reimbursement | authenticated | one request |
| `GET /requests/requester/{userId}` | reimbursement | authenticated | a user's requests |
| `POST /requests` | reimbursement | authenticated | submit for yourself (requester = token subject); fans out the approval chain; 201 |
| `PUT /requests/{id}/approval` | reimbursement | **Supervisor** | apply your approve/deny; returns `{"outcome": ...}`; 404 no vote, 409 waiting |
| `GET /approvals/pending` | reimbursement | **Supervisor** | your still-pending votes (the approval inbox) |

## Configuration

| Property | Env var | Default | Used by |
|---|---|---|---|
| `spring.datasource.url` | `dburl` | — | auth, reimbursement |
| `spring.datasource.username` | `dbuser` | — | auth, reimbursement |
| `spring.datasource.password` | `dbpassword` | — | auth, reimbursement |
| `ers.jwt.secret` | `ERS_JWT_SECRET` | dev-only placeholder | auth (signs) + reimbursement (verifies) |
| `ers.jwt.ttl-seconds` | `ERS_JWT_TTL_SECONDS` | `3600` | auth only (issuer concern) |
| route targets | `AUTH_SERVICE_URL`, `REIMB_SERVICE_URL` | `localhost:8081/8082` | gateway |
