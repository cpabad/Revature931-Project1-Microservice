/*
 * ERS microservice CI pipeline — SKELETON (Fable, 2026-07-13).
 *
 * Contract: every stage of the target pipeline exists and the pipeline runs GREEN end-to-end
 * with zero effect on the application — no Java, no runtime dependency, no behavior change.
 * Stages marked TODO(Opus) are inert `echo` stubs to be implemented one at a time (see the
 * monolith ROADMAP.md, Phase 7.5, for the locked decisions and order).
 *
 * Boundary: this file and jenkins/ are the whole CI footprint. The GitHub->GitLab mirror
 * (.github/workflows/mirror.yml) is a separate, Fable-owned system — never modify it from
 * Jenkins work.
 */
pipeline {
  // Build inside a disposable Maven container so the Jenkins controller stays clean.
  // Requires the Docker Pipeline plugin + a docker CLI in the controller image — jenkins/README.md.
  agent {
    docker {
      image 'maven:3.9-eclipse-temurin-21'   // JDK 21 toolchain, matching the local build
      args '-v ers-jenkins-m2:/root/.m2'     // named volume: cache the Maven repo between builds
    }
  }

  triggers {
    // Poll GitHub every ~2 min ('H' spreads load so every job doesn't fire the same second).
    // Polling, not webhooks: github.com cannot reach a non-public dev box.
    pollSCM('H/2 * * * *')
  }

  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '25'))
  }

  stages {
    stage('Build') {
      steps {
        // Real, active today: compiles all four modules. -ntp silences transfer spam.
        sh 'mvn -B -ntp -DskipTests package'
      }
    }

    stage('Unit tests') {
      steps {
        // TODO(Opus): the suites hit real Postgres databases (db/auth/init.sql,
        // db/reimbursement/init.sql). Provision them in-pipeline (compose sidecars or
        // Testcontainers), then replace this stub with:
        //   sh 'mvn -B -ntp test'
        // and publish:  junit '**/target/surefire-reports/*.xml'
        echo 'STUB — unit tests not wired yet (needs in-pipeline DB provisioning)'
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
