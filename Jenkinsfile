/*
 * ERS microservice CI pipeline — FULLY IMPLEMENTED.
 *
 * Runs on the controller (agent any) so stages can orchestrate sibling containers over the
 * docker socket, exactly like the monolith pipeline (the working reference). Heavy work still
 * happens INSIDE throwaway containers via the Docker Pipeline plugin's .inside()/.withRun() —
 * the controller only holds the docker CLI. Maven cache is workspace-local (-Dmaven.repo.local),
 * so it survives between builds without the root-owned named-volume permission headaches.
 *
 * Scan policy (owner-ruled, same as the monolith): WARN-THEN-RATCHET. SCA and SAST findings
 * mark the build UNSTABLE (yellow) — visible, never ignored, but not red — until the initial
 * backlog is triaged; then delete the catchError wrappers to make them hard gates. The secrets
 * scan is a hard RED from day one: a credential in the working tree is never a warning.
 *
 * Infra notes (mirrors the monolith header):
 *   - Controller runs in Docker with the host socket (docker-outside-of-docker). Workspace paths
 *     only reach sibling containers through .inside() (--volumes-from) — never `docker run -v`.
 *   - Scanner images ship ENTRYPOINTs; .inside() needs --entrypoint='' or the plugin's `cat`
 *     keep-alive becomes `trivy cat` and dies.
 *   - SpotBugs analyzes BYTECODE and its FindSecBugs detectors are wired in via the one pom
 *     touch (spotbugs-maven-plugin, no <executions>) — so a plain `mvn package` is unchanged and
 *     the SAST stage compiles first, then runs `mvn spotbugs:check`.
 *
 * Boundary: this file and jenkins/ are the whole CI footprint. The GitHub->GitLab mirror
 * (.github/workflows/mirror.yml) is a separate, Fable-owned system — never modify it here.
 */

def notifyDiscord(String message) {
  // Webhook URL lives ONLY in the Jenkins credentials store (Secret text, id: discord-webhook).
  // No credential yet -> log and move on; notification must never break the build.
  try {
    withCredentials([string(credentialsId: 'discord-webhook', variable: 'DISCORD_URL')]) {
      def safe = message.replace('\\', '\\\\').replace('"', '\\"')
      writeFile file: '.discord-payload.json', text: "{\"content\": \"${safe}\"}"
      sh 'curl -fsS -o /dev/null -H "Content-Type: application/json" -d @.discord-payload.json "$DISCORD_URL" || true'
    }
  } catch (ignored) {
    echo "Discord notify skipped - add a Secret-text credential with id 'discord-webhook' to enable pings."
  }
}

def blame() {
  // "Who to blame" — on a one-person team this is a mirror, but it's the habit that counts.
  try {
    return sh(script: "git log -1 --pretty='%an'", returnStdout: true).trim()
  } catch (ignored) {
    return 'unknown'
  }
}

pipeline {
  agent any   // the controller node — it owns the docker CLI; heavy work runs in containers

  triggers {
    // Poll GitHub every ~2 min ('H' spreads load so every job doesn't fire the same second).
    // Polling, not webhooks: github.com cannot reach a non-public dev box.
    pollSCM('H/2 * * * *')
  }

  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '25'))
  }

  environment {
    MAVEN_IMAGE = 'maven:3.9-eclipse-temurin-21'   // JDK 21 toolchain, matching the local build
    // Canonical local-repo path (~/.m2/repository under a workspace HOME): survives between builds
    // AND is exactly where Trivy looks to resolve BOM-managed versions offline (see the SCA stage).
    MVN = 'mvn -B -ntp -Dmaven.repo.local="$WORKSPACE/.m2/repository"'
  }

  stages {

    stage('Build') {
      steps {
        // Compile + package all four modules (skip tests — the next stage owns those). Runs in
        // a disposable Maven container so the controller stays clean.
        script {
          docker.image(env.MAVEN_IMAGE).inside {
            sh "$MVN -DskipTests package"
          }
        }
      }
    }

    stage('Unit tests — auth + reimbursement against fresh databases') {
      steps {
        script {
          // Database-per-service, mirrored in CI: auth-service owns ers_auth, reimbursement-
          // service owns ers_reimbursement, and no query crosses between them. Two throwaway
          // Postgres sidecars on a private bridge network reproduce that isolation — each
          // service reaches ONLY its own database, by docker-DNS name.
          //
          // Seeding note: the docker socket is docker-outside-of-docker, so a sidecar cannot
          // bind-mount the workspace init.sql (the host has no such path — see the monolith
          // header). We instead run a psql container ON THE SAME NETWORK and feed it the scripts
          // from the workspace (the Docker Pipeline plugin brings the workspace in via
          // --volumes-from). These scripts need no filtering, unlike the monolith's.
          //
          // gateway/soap-adapter need no database (no postgres dependency); the messaging tests
          // use an embedded Kafka broker and the mTLS test generates its own certs via keytool —
          // so Postgres is the only infrastructure this stage provisions.
          def net = "ers-ci-${env.BUILD_TAG}".replaceAll('[^A-Za-z0-9_.-]', '-')
          sh "docker network create ${net}"
          try {
            docker.image('postgres:16-alpine').withRun("--network ${net} --network-alias auth-db -e POSTGRES_USER=ers -e POSTGRES_PASSWORD=ers -e POSTGRES_DB=ers_auth") { authDb ->
              docker.image('postgres:16-alpine').withRun("--network ${net} --network-alias reimb-db -e POSTGRES_USER=ers -e POSTGRES_PASSWORD=ers -e POSTGRES_DB=ers_reimbursement") { reimbDb ->
                // Wait for both, then seed each from its own init.sql (ON_ERROR_STOP so a bad
                // line fails the build instead of leaving a half-seeded schema).
                docker.image('postgres:16-alpine').inside("--network ${net} -e PGPASSWORD=ers") {
                  sh '''
                    for i in $(seq 1 60); do pg_isready -h auth-db  -U ers -d ers_auth          -q && break; sleep 1; done
                    for i in $(seq 1 60); do pg_isready -h reimb-db -U ers -d ers_reimbursement -q && break; sleep 1; done
                    psql -h auth-db  -U ers -d ers_auth          -v ON_ERROR_STOP=1 -f db/auth/init.sql
                    psql -h reimb-db -U ers -d ers_reimbursement -v ON_ERROR_STOP=1 -f db/reimbursement/init.sql
                  '''
                }
                // Run the whole reactor's tests with each service pointed at its own database.
                // hbm2ddl=validate means a boot that reaches this far already proved the seeded
                // schema matches the entities.
                docker.image(env.MAVEN_IMAGE).inside("--network ${net} -e AUTH_DB_URL=jdbc:postgresql://auth-db:5432/ers_auth -e REIMB_DB_URL=jdbc:postgresql://reimb-db:5432/ers_reimbursement") {
                  sh "$MVN test"
                }
              }
            }
          } finally {
            // withRun already removed both DB containers; drop the now-empty network so builds
            // don't leak one per run.
            sh "docker network rm ${net} || true"
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
        }
      }
    }

    stage('SCA — dependency CVEs (Trivy)') {
      steps {
        script {
          // Trivy over Dependency-Check on purpose: no NVD API key to manage (credential friction
          // is the enemy in this shop). `trivy fs` finds Java dependencies through each module's
          // pom.xml, and to report a CVE it must first RESOLVE the versions the Spring Cloud BOM
          // manages — which means reading the local Maven repository. We point Trivy's HOME at the
          // workspace so ~/.m2/repository IS the cache the Build stage already populated: every
          // version resolves offline. Without it Trivy hits Maven Central and gets 429-throttled
          // into a FATAL. Skip the two workspace caches (.m2 = the Maven repo we resolve FROM, not
          // a scan target; .trivy-cache = Trivy's own vuln DB) and .git, so we scan OUR modules.
          docker.image('aquasec/trivy:latest').inside("--entrypoint='' -e HOME=$WORKSPACE -e TRIVY_CACHE_DIR=$WORKSPACE/.trivy-cache") {
            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE',
                       message: 'SCA findings (HIGH/CRITICAL) — warn-mode; see log. Ratchet: delete this catchError.') {
              sh 'trivy fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --no-progress --skip-dirs .m2 --skip-dirs .trivy-cache --skip-dirs .git .'
            }
          }
        }
      }
    }

    stage('SAST — static analysis (SpotBugs + FindSecBugs)') {
      steps {
        script {
          // SpotBugs analyzes BYTECODE, so compile the reactor first (skip tests — the Unit tests
          // stage owns those). The FindSecBugs detectors ride in via the pom's spotbugs-maven-
          // plugin block; because that block has no <executions>, `mvn spotbugs:check` is the only
          // thing that triggers analysis — a plain build stays identical.
          docker.image(env.MAVEN_IMAGE).inside {
            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE',
                       message: 'SAST findings — warn-mode; see log. Ratchet: delete this catchError.') {
              // --fail-at-end: analyze EVERY module before failing, so one module's findings don't
              // hide the rest (a plain reactor stops at the first failure).
              sh "$MVN -DskipTests compile spotbugs:check --fail-at-end"
            }
          }
        }
      }
    }

    stage('Secrets — gitleaks (hard gate)') {
      steps {
        script {
          // Working tree only (--no-git): history contains long-revoked keys from before the P3
          // git-filter-repo scrub; a baseline history scan is a later, separate step. --redact so
          // a caught secret is not immortalized in the CI log. NO catchError: a live secret in
          // current files is red, full stop.
          // The workspace persists between builds (that's how the .m2 Maven cache works), so the
          // allowlist keeps gitleaks off cached third-party jars and build output — we scan OUR
          // files, not the internet's.
          sh '''
            cat > .gitleaks-ci.toml <<'EOF'
[extend]
useDefault = true

[allowlist]
paths = [
  # Persistent-workspace caches and build output - other people's jars, not our secrets.
  ".m2/.*",
  ".trivy-cache/.*",
  ".*/target/.*",
  # Self-signed, script-generated (scripts/gen-partner-certs.sh) SOAP mTLS demo certs. They are
  # gitignored, so they never exist in a CI checkout; allowlisted so a LOCAL run does not false-red
  # on known throwaway dev keys. Never production material.
  "certs/.*",
  # False positive: line 31 is a "-----BEGIN PRIVATE KEY-----" PEM-armor STRING in a test that
  # GENERATES a fresh RSA keypair at runtime - no secret is committed. The ideal long-term fix is
  # an inline `gitleaks:allow` on that line, but that edits app code (out of this PR's scope).
  # [.] matches a literal dot without a backslash, so it survives the Groovy -> shell -> TOML
  # escaping chain that turned a backslash-dot into an invalid TOML escape and killed build 4
  # (FTL: invalid escaped character U+002E). Do NOT "fix" this by adding backslashes.
  "auth-service/src/test/java/com/revature/ers/auth/config/JwkConfigTest[.]java",
]
EOF
          '''
          docker.image('zricethezav/gitleaks:latest').inside("--entrypoint=''") {
            sh 'gitleaks detect --source . --no-git --redact --verbose --config .gitleaks-ci.toml'
          }
        }
      }
    }
  }

  post {
    failure {
      script { notifyDiscord("FAILURE: **${env.JOB_NAME} #${env.BUILD_NUMBER}** failed on `${env.GIT_BRANCH ?: 'main'}` — author: ${blame()} — ${env.BUILD_URL}") }
    }
    unstable {
      script { notifyDiscord("WARNING: **${env.JOB_NAME} #${env.BUILD_NUMBER}** is UNSTABLE — a security scan reported findings (warn-mode) — ${env.BUILD_URL}") }
    }
    fixed {
      script { notifyDiscord("RECOVERED: **${env.JOB_NAME} #${env.BUILD_NUMBER}** is passing again — ${env.BUILD_URL}") }
    }
  }
}
