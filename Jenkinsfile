/*
 * ERS microservice CI pipeline.
 *
 * Runs on the controller (agent any) so stages can orchestrate sibling containers over the
 * docker socket, exactly like the monolith pipeline (the working reference). Heavy work still
 * happens INSIDE throwaway containers via the Docker Pipeline plugin's .inside()/.withRun() —
 * the controller only holds the docker CLI. Maven cache is workspace-local (-Dmaven.repo.local),
 * so it survives between builds without the root-owned named-volume permission headaches.
 *
 * State: Build + Unit tests are implemented; SCA / SAST / Secrets / Discord are inert echo
 * stubs, implemented one PR at a time (order + locked decisions: monolith ROADMAP.md Phase 7.5).
 *
 * Boundary: this file and jenkins/ are the whole CI footprint. The GitHub->GitLab mirror
 * (.github/workflows/mirror.yml) is a separate, Fable-owned system — never modify it here.
 */
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
    MVN = 'mvn -B -ntp -Dmaven.repo.local="$WORKSPACE/.m2repo"'
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

    stage('SCA — dependency CVEs') {
      steps {
        // TODO(Opus): OWASP Dependency-Check (dependency-check-maven) or Trivy fs over the
        // dependency tree; fail on CVSS >= 7. Cache the NVD database in a named volume or
        // every build re-downloads ~20 min of CVE data.
        echo 'STUB — SCA scan not wired yet'
      }
    }

    stage('SAST — static analysis') {
      steps {
        // TODO(Opus): SpotBugs + FindSecBugs via spotbugs-maven-plugin. This is the ONE
        // allowed pom.xml touch (build-plugin scope only — the shipped artifact is unchanged).
        echo 'STUB — SAST scan not wired yet'
      }
    }

    stage('Secrets scan') {
      steps {
        // TODO(Opus): gitleaks (official docker image) over the checkout; fail on any hit.
        // The P3 git-filter-repo history scrub is the war story this stage prevents.
        echo 'STUB — secrets scan not wired yet'
      }
    }
  }

  post {
    failure {
      // TODO(Opus): Discord webhook — status, branch, commit, author (the "who to blame" ping).
      // Webhook URL from the Jenkins credentials store (id: discord-webhook), NEVER in this file.
      echo 'STUB — Discord failure notification not wired yet'
    }
    fixed {
      // TODO(Opus): the recovery ping — back to green.
      echo 'STUB — Discord recovery notification not wired yet'
    }
  }
}
