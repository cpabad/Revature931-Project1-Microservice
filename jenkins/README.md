# Jenkins CI — setup & implementation guide

Local CI for both ERS repos: this microservice and the monolith
([`cpabad/Christian-Project1`](https://github.com/cpabad/Christian-Project1)). One Jenkins
controller (the compose file here), two Pipeline jobs, each reading the `Jenkinsfile` at its
repo's root. **Skeleton state:** every target stage exists; the security-scan and notification
stages are inert `echo` stubs awaiting implementation (locked decisions + order live in the
monolith `ROADMAP.md`, Phase 7.5).

## Boundary rule (do not cross)

The GitHub → GitLab mirror (`.github/workflows/mirror.yml` + the GitLab mirror settings) is a
**separate, independently-owned system**: it runs on GitHub's runners with its own credential and
shares nothing with Jenkins. No Jenkins change may touch `.github/workflows/` — a Jenkins PR's
diff must contain only `Jenkinsfile`s, `jenkins/`, and (for the SAST stage) the SpotBugs plugin
block in a `pom.xml`.

## Why polling, not webhooks

A GitHub webhook is GitHub reaching **in** to your server — impossible against a non-public dev
box. Polling is Jenkins reaching **out** ("anything new?" every ~2 min), which always works.
The `pollSCM('H/2 * * * *')` trigger is already declared in each Jenkinsfile; Jenkins activates
it after the job's first manual run. If instant triggers ever matter, the upgrade is a smee.io
relay — not port-forwarding your box to the internet.

## First-time setup (~30 min, interactive)

1. `docker compose up -d` (this directory) → http://localhost:8090.
2. Unlock: `docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`.
3. Install **suggested plugins**, then additionally: **Docker Pipeline** (the Jenkinsfiles use
   `agent { docker { ... } }`).
4. Credentials (Manage Jenkins → Credentials — values are typed in, **never** land in a repo):
   - `github-pat` — fine-grained GitHub PAT, read-only **Contents** on the two repos (lets
     polling and checkout work against private repos; harmless if they're public).
   - `discord-webhook` (Secret text) — the Discord channel webhook URL, for the notify stages.
5. Two jobs, New Item → **Pipeline**:
   - `ers-microservice` → Pipeline from SCM → Git → `https://github.com/cpabad/Revature931-Project1-Microservice.git`, credential `github-pat`, branch `*/main`, script path `Jenkinsfile`.
   - `ers-monolith` → same, `https://github.com/cpabad/Christian-Project1.git`, script path `Jenkinsfile`.
6. Click **Build Now** once per job (registers the poll trigger; expect green — the stubs pass).

## Implementation checklist (Opus, one stage per PR)

In order, per the ROADMAP:

1. **Docker-agent plumbing** — custom controller image with a docker CLI
   (`FROM jenkins/jenkins:lts-jdk21` + docker cli, `group_add` the host docker GID in the
   compose file) so the `agent { docker }` blocks actually run. Until then the pipelines fail
   at agent-allocation, which is expected and harmless.
2. **Unit tests stage** — provision the Postgres databases in-pipeline (compose sidecars or
   Testcontainers); monolith needs its seeded DB + env vars (see monolith `STARTUP.md`).
3. **SCA** — OWASP Dependency-Check or Trivy; fail CVSS ≥ 7; cache the NVD data.
4. **SAST** — SpotBugs + FindSecBugs maven plugin (build-plugin scope only).
5. **Secrets** — gitleaks over the checkout; fail on any hit.
6. **Discord notify** — `post { failure / fixed }` webhook POSTs: status, branch, commit, author.
7. *(stage 2, later)* **DAST** — OWASP ZAP baseline against the compose-booted stack, main only.

Every stage must keep the invariant the skeleton established: **the application builds, tests,
and runs identically whether or not Jenkins exists.** CI observes the code; it never shapes it.
