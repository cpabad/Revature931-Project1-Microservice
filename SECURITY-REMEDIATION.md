# SECURITY-REMEDIATION — the Opus implementation order

Written by Fable (planning/validation pass, 2026-07-14) for Opus to implement. Context: Jenkins
build 4 went RED and surfaced the security-scan backlog; the owner ruled that all CVEs are
addressed before any cosmetic work. The monolith received the same treatment first, as the
worked example — branch `christian/phase7-cve-observability` in
`~/Repo/Revature-Project1/Christian-Project1` (commits: SCA hard gate, CVE bumps + BCrypt
guards, /health, monitoring/). Copy its conventions, not just its diffs.

## What build 4 actually showed (triage — read before touching anything)

- **The RED was a pipeline bug, not a security finding.** The secrets stage died before
  scanning: `FTL unable to load gitleaks config ... invalid escaped character U+002E`.
  Root cause: the Jenkinsfile writes `.gitleaks-ci.toml` inside `sh '''...'''`, and Groovy
  consumes one backslash layer — the intended TOML regex `JwkConfigTest\\.java` reached disk
  as `JwkConfigTest\.java`, and `\.` is not a valid TOML string escape. The scan never ran.
- **SCA (Trivy) worked as designed**: warn-mode UNSTABLE with **218 HIGH/CRITICAL** across the
  poms — auth-service 30, gateway 23, root 109, reimbursement-service 30, soap-adapter 26.
  Almost all are BOM-managed transitives of Spring Boot 3.3.6 / Spring Cloud 2023.0.5.
- **SAST (SpotBugs + FindSecBugs) worked as designed**: warn-mode with **49 findings** —
  auth-service 8, reimbursement-service 36, soap-adapter 5. One security pattern
  (LDAP_INJECTION, triaged below); the rest are overwhelmingly EI_EXPOSE_REP/REP2 style noise
  on JPA entities and DTOs.

## Ground rules (owner-standing, apply to every step)

- **CVE comment convention:** every CVE-motivated edit gets an inline comment AT the edit site
  (beside/above the changed lines) naming the CVE and linking the advisory as shown by the scan
  (e.g. https://avd.aquasec.com/nvd/cve-2025-22228). NEVER prepend these above a file's or
  method's existing header comments.
- No edits to `.github/workflows/` (Fable-owned mirror system). No emojis. No AI-credit commit
  trailer. Secrets only ever in the Jenkins credentials store.
- Invariant: the app must build/test/run identically with or without Jenkins.
- One PR for this remediation, clean commits per concern (mirror the monolith's split).
  Delete the branch after merge.

## Step 1 — pipeline bugfix: gitleaks TOML escaping (do FIRST; unblocks the hard gate)

File: `Jenkinsfile`, secrets stage heredoc (`.gitleaks-ci.toml`), the allowlist line:

```
"auth-service/src/test/java/com/revature/ers/auth/config/JwkConfigTest\\.java",
```

Replace the escaped dot with a character class:

```
"auth-service/src/test/java/com/revature/ers/auth/config/JwkConfigTest[.]java",
```

`[.]` matches a literal dot in regex but contains no backslash, so it is immune to the
Groovy -> shell -> TOML escaping chain. Do not "fix" it by adding more backslashes — every
layer you satisfy breaks another. Self-test locally before pushing (see Verification).

## Step 2 — dependency CVEs: bump the BOMs, not 218 artifacts

File: root `pom.xml`.

- `spring-boot-starter-parent` **3.3.6 -> 3.5.3** (latest 3.5.x on Maven Central as of
  2026-07-14 — take a newer 3.5.x patch if one exists when you run; stay on the 3.5 line,
  do not jump to a new major without a separate ruling).
- `spring-cloud.version` **2023.0.5 -> 2025.0.0** (the release train matched to Boot 3.5.x —
  again, take the latest 2025.0.x patch available).

The 3.3 line is out of OSS support; the root pom's 109 findings are BOM-managed transitives,
so the parent bump IS the fix — do not pin individual transitive versions unless the rescan
proves one is still unfixed after the bump.

**CVE-2025-22228 note (also the monolith's headline CVE):** Boot 3.3.6 manages
spring-security-crypto 6.3.x, which is vulnerable (BCryptPasswordEncoder.matches() returns
true for any >72-byte password whose first 72 bytes match). The fix landed in Spring Security
6.4.4+, and Boot 3.5.x manages 6.5.x — so the parent bump clears it here. Lesson from the
monolith, for the record: on the 5.x line Trivy advertises 5.8.18 as the fix, but 5.8.18 is a
commercial-only (Tanzu) release — Maven Central OSS stops at 5.8.16, which is still flagged.
The monolith therefore pinned 5.8.16, made the in-code 72-byte guard the real mitigation, and
accepted the residual in `.trivyignore` with rationale. The micro does NOT need any of that —
its 6.x OSS line has the real fix. Do not hand-pin spring-security-crypto here; let the parent
BOM manage it.

Iterate: bump -> `mvn test` (all 59 must stay green; if the gateway module breaks, check the
`HttpHeaders.headerSet()` pin history first — that API has shifted under us before) -> local
offline Trivy rescan (command in Verification) -> repeat until **0 unignored HIGH/CRITICAL**.
Anything genuinely unfixable goes in a `.trivyignore` with a justification block, exactly like
the monolith's two entries (`ReimbursementManagement/.trivyignore` is the template: CVE id,
why no fix exists, why the code path is unreachable/mitigated, and the revisit condition).

## Step 3 — code guard: BCrypt 72-byte limit (CVE-2025-22228), defense in depth

Even with the fixed library, the owner wants the input limit explicit in code (the monolith's
`UserService.authenticate`/`updateProfile` + its four boundary tests are the worked example).
BCrypt reads only the first 72 BYTES (UTF-8 bytes, not chars — multi-byte characters count
per byte) of its input; longer input is silently truncated. Guard every site where a
request-supplied password reaches the encoder:

- `auth-service/.../controller/AuthController.java` `login(...)` (~line 44) — reject before
  `passwordEncoder.matches(...)`; keep returning the same 401 as any bad credential (do not
  create a distinguishable response — the endpoint deliberately never says why).
- `auth-service/.../service/UserService.java` change-password path (~lines 44 and 61) — guard
  BOTH the current password (matches) and the new password (encode) before either hits BCrypt.

Use the byte-length check `password.getBytes(StandardCharsets.UTF_8).length > 72` (a shared
private helper per class is fine — see the monolith's `exceedsBCrypt72ByteLimit`). Each guard
carries the convention comment naming CVE-2025-22228 + the advisory link, placed at the guard.
Add tests mirroring the monolith's: exactly-72-bytes still authenticates; two different
80-byte passwords sharing a 72-byte prefix must NOT verify; oversized old/new password rejects
the profile change.

## Step 4 — SAST: triage the security pattern, exclude the noise

### 4a. LDAP_INJECTION at `soap-adapter/.../security/PartnerResolver.java:46` — FALSE POSITIVE

Planning-pass correction (Fable, after reading the code): the original plan said "fix with
`Rdn.escapeValue`", but that is wrong for this site. `Rdn.escapeValue` is for BUILDING a DN
from untrusted components; line 46 is `new LdapName(certificate.getSubjectX500Principal()
.getName())`, which PARSES the X.500 subject of a certificate the container has already
cryptographically verified against our CA (`server.ssl.client-auth=need`). There is no LDAP
directory in this system, no query is constructed, and a malformed DN simply throws
`InvalidNameException` -> `Optional.empty()`. FindSecBugs flags any tainted-looking value
reaching an `LdapName` constructor; here the taint model, not the code, is wrong.

Remediation: **suppress, with the triage written down** — a targeted entry in
`spotbugs-exclude.xml` (class `com.revature.ers.soap.security.PartnerResolver` + bug pattern
`LDAP_INJECTION`, never a blanket pattern-wide exclusion) with an XML comment carrying the
rationale above. Do NOT add an escapeValue call that would corrupt valid DNs to silence a
scanner.

### 4b. EI_EXPOSE_REP / EI_EXPOSE_REP2 (~45 findings) — excluded by ruling

Owner-ruled: SAST gates on CVEs + security patterns (FindSecBugs) only; mutable-reference
style findings on JPA entities/DTOs are noise here (defensive copies of entity references
defeat how JPA works). Add to the same `spotbugs-exclude.xml`: both bug patterns excluded
repo-wide, one XML comment documenting the triage and the revisit condition (if a class ever
carries security-sensitive mutable state, reconsider).

### 4c. Wiring

New file `spotbugs-exclude.xml` at repo root, wired in the root pom's existing
`spotbugs-maven-plugin` block via `<excludeFilterFile>` — still inside the one allowed pom
touch, no `<executions>` added, so a plain `mvn package` remains unchanged. After 4a/4b, run
`mvn -DskipTests compile spotbugs:check --fail-at-end` and fix or triage whatever remains
(there may be a handful of non-EI findings in the 49); every remaining exclusion needs its
own comment.

## Step 5 — ratchet: SCA becomes a hard gate in THIS PR

Delete the SCA stage's `catchError` wrapper in the same PR that clears the findings (the
monolith did the same — see its Jenkinsfile header for the policy paragraph and the lesson:
warn-mode masked a scanner CRASH as a yellow "findings" build for an entire pipeline
generation; a hard gate makes both findings and scanner failures RED). Update this
Jenkinsfile's header scan-policy paragraph to match. SAST stays warn-mode for now: ratchet it
only if `spotbugs:check` is at zero after Step 4 — if it is, delete that catchError too and
say so in the commit message; if not, leave it warn and list the remainder in the PR
description.

## Step 6 — verification (all local, before pushing)

```bash
# 1. Full suite - 59/59, JDK 21 (~/jdks Temurin):
mvn test

# 2. Offline Trivy rescan - expect exit 0, 0 unignored HIGH/CRITICAL.
#    Cache must have been populated online once (see the monolith scratchpad harness);
#    --skip-db-update fails on a cold cache.
sg docker -c "docker run --rm \
  -v $HOME/Repo/Revature931-Project1-Microservice:/repo:ro \
  -v $HOME/.m2/repository:/root/.m2/repository:ro \
  -v <trivy-cache-dir>:/trivy-cache \
  -e TRIVY_CACHE_DIR=/trivy-cache -e HOME=/root \
  aquasec/trivy:latest fs --scanners vuln --severity HIGH,CRITICAL \
  --exit-code 1 --no-progress --skip-db-update \
  --skip-dirs .m2 --skip-dirs .trivy-cache --skip-dirs .git /repo"

# 3. SpotBugs - expect BUILD SUCCESS after step 4:
mvn -DskipTests compile spotbugs:check --fail-at-end

# 4. Gitleaks self-test, INCLUDING the config-on-disk case that killed build 4:
#    write the heredoc TOML exactly as the Jenkinsfile does (through sh, so the same
#    escaping layers apply), then:
sg docker -c "docker run --rm -v $HOME/Repo/Revature931-Project1-Microservice:/repo \
  zricethezav/gitleaks:latest detect --source /repo --no-git --redact \
  --config /repo/.gitleaks-ci.toml"
#    Expect: 'no leaks found' - and NOT 'FTL unable to load gitleaks config'.
```

End state: push, PR, merge (delete branch), Jenkins Build Now -> **GREEN** — all four stages,
SCA now hard-gated.

## Appendix — PR #10 review verdict (for the record)

Fable reviewed the merged PR #10 (`ci(jenkins): add SCA, SAST, secrets, and Discord-notify
stages`, 0f53442): structurally sound — the offline-resolution fix (`HOME=$WORKSPACE` +
workspace-local `.m2/repository`) is correct and proven in CI; stage layout, sidecar DB
seeding, and warn-then-ratchet policy all behaved as designed in build 4. One defect: the
gitleaks TOML escaping bug above (Groovy heredoc backslash consumption), which made the hard
gate fire on a config parse error rather than a leak. SCA and SAST warn-modes surfaced real
backlogs exactly as intended. Verdict: merge was correct; this document is the follow-through.
