# ERS Microservices

The **Employee Reimbursement System (ERS)**, decomposed into real services — the endpoint of the
Phase 4 refactor that began as a legacy Java 8 / JDBC / Hibernate / standalone-Tomcat monolith,
became a single Spring Boot service, and now runs as **three independently deployable apps behind
a gateway**:

```
              REST ───── :8080 ─▶┌──────────────────────┐            SOAP over mTLS ─── :8443 ─┐
                                 │      ers-gateway      │  Spring Cloud Gateway (reactive)     │
                                 └──────────┬───────────┘  routes by path, forwards headers     │
             /login /users/** /roles/**     │    /requests/** /approvals/**                     │
                     ▼                      ▼                                                    ▼
        ┌────────────────────┐   ┌──────────────────────────┐          ┌──────────────────────┐
        │  ers-auth-service   │   │ ers-reimbursement-service │          │   ers-soap-adapter    │
        │  :8081  (issuer)    │   │  :8082 (resource server)  │          │  :8083 http / :8443   │
        │  login → mints JWT  │   │  requests / approvals /   │          │  mTLS (no database)   │
        │  profile, roles     │   │  reimbursements + Kafka   │          │  legacy SOAP → event  │
        │  OWNS users, roles  │   │  consumer                 │          └──────────┬───────────┘
        └─────────┬──────────┘   └─────┬──────────▲─────────┘                     │
                  ▼                    ▼          │     reimbursement.request.submitted
            ers_auth DB     ers_reimbursement DB  └────────────── Kafka ◀──────────┘
        (db/auth/init.sql)  (db/reimbursement/init.sql)   (partners bypass the gateway - mTLS direct)
```

**Databases-per-service:** no cross-database joins exist. `ers_reimbursement` carries a
replicated reference copy of users/roles **without the password column** — the credential is
structurally absent from that service's world. The copy is **kept in sync by events**: every
committed profile update in auth-service is published on `auth.user.updated` as a full state
snapshot (event-carried state transfer), and reimbursement-service overwrites its replica row
from the event alone — a rename propagates in milliseconds locally, *eventually* by contract.
That eventual consistency is the price this layout pays for its autonomy — and the delivery is
guaranteed by a **transactional outbox**: the event is written to `outbox_event` in the same
database transaction as the profile change, then a relay drains it to Kafka (1s poll + an
after-commit nudge for ~ms latency) and deletes the row on the broker's ack. An update
committed while the broker is down parks in the outbox and delivers when it returns — the
caller's 200 never depends on Kafka being alive. Replay after a crash-between-ack-and-delete
is harmless: overwriting with a snapshot is idempotent by shape.

> **Full history & the monolith it came from:** [`cpabad/Christian-Project1`](https://github.com/cpabad/Christian-Project1)
> — the original monolith, its hardening/refresh changelog, and the monolith→microservice journey.

(Not drawn above: auth-service also *produces* to Kafka — `auth.user.updated`, the replica-sync
topic reimbursement-service consumes alongside the request intake.)

## Stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3.3.6 (3.3.6 minimum: Gateway 4.1.6 needs Framework 6.1.15) |
| Gateway | Spring Cloud Gateway (release train 2023.0.5) |
| Language / build | Java 17, compiled on JDK 21; Maven multi-module |
| Persistence | Spring Data JPA + Hibernate; **one PostgreSQL database per service**; associations LAZY with per-query `@EntityGraph` fetch plans; OSIV off |
| API responses | **DTO records, never entities**: the wire shape is declared per endpoint, so schema changes cannot leak into JSON by default |
| Messaging | Apache Kafka (spring-kafka); JSON events, each side owns its event record; idempotent consumer (correlation-id ledger) + retries + dead-letter topic; users/roles replica synced via `auth.user.updated` snapshots (transactional outbox) |
| Legacy intake | Spring-WS contract-first SOAP (XSD → xjc classes; WSDL served at runtime) |
| Containers | Dockerfile (multi-stage, one recipe for all services) + docker-compose (7 containers) |
| Security | Self-issued **RS256 JWT** for users (auth signs + serves JWKS; others verify, cannot mint); **mutual TLS** for SOAP partners (client-cert handshake + per-partner allowlist) |
| Passwords | BCrypt (hashes carried over from the monolith seed) |
| Tests | JUnit 5 + MockMvc per service; write tests are `@Transactional` (roll back, seed stays pristine) |

## The service boundary, in one paragraph

`ers-auth-service` **owns identity**: it holds the only `JwtEncoder`, the only BCrypt verification,
and the only write access to `users`/`roles`. `ers-reimbursement-service` is a **pure resource
server**: it validates tokens it genuinely cannot mint (it holds only auth-service's PUBLIC key,
fetched from the JWKS endpoint — RS256's asymmetry, the fix for the old shared-secret HS256) and maps
`users` **read-only, without the password column** — the credential physically cannot leak from a
service that never loads it. The gateway routes by path and passes the Bearer token through
untouched: authorization stays with the service that owns the resource, so a compromised gateway
cannot mint identities. Each service owns its own database outright — the reference-data copy in
`ers_reimbursement` is the price of that autonomy, paid in eventual consistency.

## Prerequisites

- **A JDK 17+ with a compiler** (built on Temurin JDK 21 — a JRE has no `javac`).
  > **On this dev box:** the default `java` on `PATH` is a JRE-only 21 (no `javac`), so the build
  > silently falls back to JDK 8 and fails with `release version 17 not supported`. A full Temurin
  > JDK 21 is installed at `~/jdks/jdk-21.0.11+10` — point every `mvn`/`java` command at it with
  > `JAVA_HOME=~/jdks/jdk-21.0.11+10`. To install one on a fresh machine, see the monolith
  > `STARTUP.md` install appendix (same JDK, different version line — grab 21 instead of 8).
- **PostgreSQL** with the ERS schema seeded (below).

## Run with Docker (the whole system, one command)

```bash
docker compose up --build
# host port 8080 taken (e.g. the monolith's Tomcat)? ->  GATEWAY_PORT=9080 docker compose up --build
```

Five containers: two seeded postgres databases + the three services; only the gateway is
published to the host. The database containers run each service's init script on first start.

## Database setup (running the services without Docker)

Both services run `ddl-auto=validate` — they never create or mutate schemas. Each service seeds
its **own** database:

```bash
psql -d postgres -c "CREATE ROLE ers LOGIN PASSWORD 'ers';"   # once, if not present
createdb -O ers ers_auth
createdb -O ers ers_reimbursement
psql -d ers_auth          -f db/auth/init.sql
psql -d ers_reimbursement -f db/reimbursement/init.sql
```

(`db/ers_script.sql` is the legacy shared dump, kept for history and monolith parity — the
services no longer use it.)

## Build, test, run

```bash
export JAVA_HOME=~/jdks/jdk-21.0.11+10        # on this box; any full JDK 17+ works elsewhere
# Datasources default to localhost/ers_auth and localhost/ers_reimbursement (user/pass ers);
# override with AUTH_DB_URL/USER/PASSWORD and REIMB_DB_URL/USER/PASSWORD if yours differ.
# JWT is RS256: auth-service GENERATES its signing keypair at startup (no secret to set) and
# serves the public half at /.well-known/jwks.json. reimbursement-service fetches that to verify;
# it defaults to http://localhost:8081/.well-known/jwks.json, override with ERS_JWKS_URI if needed.

mvn test          # all modules, 54 tests green (Kafka tests run against an embedded in-JVM broker;
                  # a real broker is only needed at runtime - localhost:9092 or KAFKA_BOOTSTRAP)
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

## The asynchronous intake (SOAP → Kafka → domain)

Legacy partners submit reimbursement requests as SOAP — the protocol B2B integrations actually
still speak. The adapter is an **anti-corruption layer**: it keeps XML at the edge, translates a
valid submission into a JSON event on `reimbursement.request.submitted`, and acknowledges with a
correlation id. reimbursement-service consumes the topic and runs the **same `RequestService.submit`
fan-out the REST endpoint uses** — one domain rule, two transports. "Accepted" therefore means
*queued*, not persisted: the pipeline is at-least-once and eventually consistent, and the consumer
handles both consequences of that promise:

- **Duplicates (idempotent consumer):** a crash between the database commit and the offset commit
  replays the event, so the listener records every correlation id in a `processed_event` table
  **in the same transaction as the domain write** and skips ids it has already seen — a replay
  cannot create a second request. A deterministic business rejection (unknown location/requester)
  is also marked processed: replaying it can never succeed, so its outcome is final (the async
  twin of REST answering 4xx).
- **Failures (dead-letter topic):** processing exceptions are retried (3 attempts, 1s backoff —
  a database blip heals itself), then the record is published unchanged to
  `reimbursement.request.submitted.DLT` and consumption moves on, so one poison event cannot
  block the partition. Malformed payloads that never deserialize are classified as fatal and
  dead-letter on the first attempt (`ErrorHandlingDeserializer`). The DLT is the operator's
  inbox: everything there needs a human, nothing was silently lost — and records keep the wire
  contract's field formats, so a repaired event can be replayed onto the main topic as-is.

**Partner trust, closed in two layers.** A REST caller proves identity with a JWT; a SOAP partner
*asserts* `requesterUserId`, so the partner itself must be authenticated. On the adapter's `mtls`
profile that happens at the transport: **mutual TLS** with `client-auth=need` — Tomcat refuses any
connection whose client certificate does not chain to our CA, so authentication finishes *before a
byte of SOAP is read*. The partner's name is the certificate's CN; a per-partner **allowlist**
(`ers.partners.allowed-user-ids.<cn>`) then decides which `requesterUserId`s that partner may submit
for (a valid certificate proves *who you are*, never *whom you may act for*). Partners connect to the
adapter's TLS port **directly** — there is deliberately no gateway `/ws` route, since routing partner
traffic through the employee edge would either bypass the handshake or make the gateway the partner.
The default (dev) profile keeps plain HTTP with the intake unauthenticated and logs that posture at
startup — convenient for local runs, never a mode for real partner traffic. Generate local
certificates with `scripts/gen-partner-certs.sh` (keys are gitignored, never committed).

```bash
# mtls profile: present a CA-signed partner certificate (acme, allowlisted for users 3 & 5).
# No --cert, or a cert from another CA, is refused at the TLS handshake - it never reaches here.
curl -s --cacert certs/ca.crt --cert certs/acme.crt --key certs/acme.key \
  -X POST https://localhost:8443/ws -H 'Content-Type: text/xml' -d '<soapenv:Envelope
  xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
  xmlns:req="http://revature.com/ers/soap/requests">
  <soapenv:Body>
    <req:submitReimbursementRequest>
      <req:requesterUserId>3</req:requesterUserId>
      <req:amount>88.25</req:amount>
      <req:eventDate>2026-07-01</req:eventDate>
      <req:eventLocationId>1</req:eventLocationId>
      <req:requestedEvent>Partner Conference</req:requestedEvent>
    </req:submitReimbursementRequest>
  </soapenv:Body>
</soapenv:Envelope>'
# dev profile (default, plain HTTP): same body to  http://localhost:8083/ws  with no certificate.
```

## Auth model

1. `POST /login` (auth-service, via the gateway) verifies the BCrypt hash and returns a signed JWT
   (`sub`=userId, `role` claim, `iss`/`iat`/`exp`).
2. Every other request carries `Authorization: Bearer <token>`. **Each service validates the
   signature itself** — the gateway does not authenticate; it forwards.
3. Roles enforced per service: path rules in each service's `SecurityConfig`, plus `@PreAuthorize`
   on `GET /roles`. Unauthenticated → **401**; authenticated but wrong role → **403**.

### Endpoints (all through the gateway on :8080 — except the SOAP adapter, see the partner-trust note)

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
| `POST /ws` (SOAP) | soap-adapter | **mTLS partner** (`mtls` profile) + per-partner allowlist | direct to the adapter's TLS `:8443`, NOT via the gateway; `submitReimbursementRequest` → acknowledged with a correlation id, processed asynchronously via Kafka |
| `GET /ws/requests.wsdl` | soap-adapter | mTLS partner | the SOAP contract, behind the same handshake |

## Configuration

| Property | Env var | Default | Used by |
|---|---|---|---|
| auth datasource | `AUTH_DB_URL` / `AUTH_DB_USER` / `AUTH_DB_PASSWORD` | `localhost:5432/ers_auth`, `ers`/`ers` | auth only |
| reimbursement datasource | `REIMB_DB_URL` / `REIMB_DB_USER` / `REIMB_DB_PASSWORD` | `localhost:5432/ers_reimbursement`, `ers`/`ers` | reimbursement only |
| `ers.jwt.jwks-uri` | `ERS_JWKS_URI` | `localhost:8081/.well-known/jwks.json` | reimbursement (verifies via auth's public key) |
| `ers.jwt.ttl-seconds` | `ERS_JWT_TTL_SECONDS` | `3600` | auth only (issuer concern; RS256 keypair is generated at startup, no secret) |
| Kafka broker | `KAFKA_BOOTSTRAP` | `localhost:9092` | soap-adapter + auth (produce), reimbursement (consume) |
| route targets | `AUTH_SERVICE_URL`, `REIMB_SERVICE_URL`, `SOAP_SERVICE_URL` | `localhost:8081/8082/8083` | gateway |
| gateway host port | `GATEWAY_PORT` (compose only) | `8080` | docker-compose |

## Repo mirroring (GitHub ⇄ GitLab)

This repo lives on two hosts so it stays host-agnostic: **GitHub is the primary** (PRs and merges
happen there) and the GitLab twin (`ca132731/Revature931-Project1-Microservice`, the same project
that hosts the container registry) is a **receive-only mirror**. Never push feature work to the
mirror — the sync prunes anything that exists only there.

- **GitHub → GitLab (active):** `.github/workflows/mirror.yml` pushes every branch and tag on
  every push/delete. Credential (preferred): the `GITLAB_MIRROR_SSH_KEY` Actions secret — the
  private half of an ed25519 pair whose public half is a **write-enabled Deploy Key** on the
  GitLab project. Deploy keys never expire, so the mirror needs zero credential maintenance.
  Fallback during transition: the `GITLAB_MIRROR_TOKEN` Actions secret (GitLab project access
  token, Maintainer / `write_repository`, expires 2027-07) — delete it once the key is live.
  Neither value is ever committed.
- **GitLab → GitHub (dormant):** GitLab's built-in push mirroring (Settings → Repository →
  Mirroring repositories) with a GitHub fine-grained PAT (`contents: write`). Configured but
  idle while GitHub is primary.

**Primary-flip runbook** (if the project ever moves its front door to GitLab): 1) merge/close all
open GitHub PRs; 2) disable the GitHub Action (delete `mirror.yml` or disable the workflow in the
Actions tab); 3) enable/verify GitLab push mirroring toward GitHub; 4) update this section — the
"primary" is wherever merges happen, and there is exactly one at a time.

The mirror is deliberately independent of the local Jenkins CI (`jenkins/`): it runs on GitHub's
runners with its own credential. Jenkins work must never modify `.github/workflows/`.
