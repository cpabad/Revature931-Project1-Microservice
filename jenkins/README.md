# Jenkins CI — setup & implementation guide

Local CI for both ERS repos: this microservice and the monolith
([`cpabad/Christian-Project1`](https://github.com/cpabad/Christian-Project1)). One Jenkins
controller (the compose file here), two Pipeline jobs, each reading the `Jenkinsfile` at its
repo's root. **State:** controller plumbing (custom image + docker CLI, loopback bind, socket
group) and the **monolith pipeline are fully implemented** (Fable, 2026-07-13); the
**microservice Jenkinsfile is still the stubbed skeleton** awaiting Opus (locked decisions +
order live in the monolith `ROADMAP.md`, Phase 7.5 — the monolith Jenkinsfile is the working
reference to copy patterns from).

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

## Security posture (why this can't be your remote-access horror story)

- **Loopback-only UI**: the compose file binds `127.0.0.1:8090` — the port does not exist on
  the LAN or internet. Nothing can reach Jenkins that isn't already on this machine.
- **Pull, not push**: SCM polling means Jenkins only ever dials OUT (github.com, Discord).
  Zero inbound ports, zero port-forwarding, zero tunnel — there is no doorway to attack.
- **The honest caveat**: the mounted docker socket is root-equivalent *on this host*. That's
  the price of docker-agent builds, and it's exactly why the two lines above are non-negotiable
  and the admin password should be a real one even though it's "just local".

## First-time setup (~30 min, interactive)

1. `docker compose up -d --build` (this directory) → http://localhost:8090.
   (`--build` bakes the custom image: stock LTS + docker CLI, see `Dockerfile`. If your host's
   docker GID isn't 985 — `getent group docker` — fix `group_add` in the compose file first.)
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

## The Jenkins watchdog (who watches the watcher)

`watchdog/jenkins-watchdog.sh`, run by host cron every 5 minutes — deliberately OUTSIDE Docker
and K3s so it survives anything short of the box dying. Kuma-style semantics: one Discord ping
on down, silence while down, one ping on recovery (docker's `restart: unless-stopped` heals
crashes silently; the watchdog reports when healing didn't happen). **Arming:** put the Discord
webhook URL in `~/.config/ers/discord-webhook` (chmod 600, never tracked) — until that file
exists the script exits silently, so the cron entry is safe to install first:

```
*/5 * * * * /home/cpabad/Repo/Revature931-Project1-Microservice/jenkins/watchdog/jenkins-watchdog.sh
```

## Implementation checklist — microservice pipeline (Opus implements, Fable verifies)

Done already (Fable, 2026-07-13): docker-agent plumbing (custom image + `group_add`), the whole
monolith pipeline, the watchdog. **Process (owner-ruled 2026-07-14): items 2-5 below land
together as ONE PR; Fable verifies the complete pipeline once, after that PR** — the original
one-stage-per-PR cadence applied only to item 1. **The monolith `Jenkinsfile` is the working
reference for every pattern below** (sidecar DB via `withRun`, warn-then-ratchet via
`catchError(buildResult: 'UNSTABLE')`, Discord notify via `withCredentials` + curl):

1. **DONE (branch christian/jenkins-unit-tests, Fable-verified 2026-07-14)** — Unit tests
   stage: TWO Postgres sidecars on a private bridge network with DNS aliases (`auth-db`,
   `reimb-db`), seeded from `db/auth/init.sql` / `db/reimbursement/init.sql` by a psql
   container joined to that network. NOT via `/docker-entrypoint-initdb.d` as originally
   written here: the docker socket is docker-outside-of-docker, so a sidecar cannot bind-mount
   a workspace file — the psql-over-network pattern is the only one that works (see the
   monolith Jenkinsfile header).
2. **SCA** — Trivy fs over the module poms; HIGH/CRITICAL; `catchError`-UNSTABLE first
   (warn-then-ratchet, owner-ruled), flip to hard-fail after the first triage.
3. **SAST** — SpotBugs + FindSecBugs maven plugin (Java 17 here, no JDK-8 constraint —
   build-plugin scope only, the ONE allowed pom touch).
4. **Secrets** — gitleaks over the working tree (`--no-git --redact`), hard fail from day one,
   with a workspace-cache allowlist like the monolith's (`.m2repo/`, `target/` noise).
5. **Discord notify** — copy the monolith's `notifyDiscord` helper and post blocks verbatim
   (plain SUCCESS/FAILURE/WARNING/RECOVERED prefixes — no emojis).
6. *(stage 2, later — NOT part of the bundled PR)* **DAST** — OWASP ZAP baseline against the
   compose-booted stack, main only.

Every stage must keep the invariant the skeleton established: **the application builds, tests,
and runs identically whether or not Jenkins exists.** CI observes the code; it never shapes it.
