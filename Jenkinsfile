// Holodeck-B2B quality gates.
// Replaces the hand-clicked Freestyle configuration; lives on the 'ci' branch.

def sourceDirs = [
  [path: 'modules/holodeckb2b-as4secprovider/src/main/java'],
  [path: 'modules/holodeckb2b-certmanager/src/main/java'],
  [path: 'modules/holodeckb2b-core/src/main/java'],
  [path: 'modules/holodeckb2b-default-mds/src/main/java'],
  [path: 'modules/holodeckb2b-default-psp/src/main/java'],
  [path: 'modules/holodeckb2b-ebms3as4/src/main/java'],
  [path: 'modules/holodeckb2b-interfaces/src/main/java'],
  [path: 'modules/holodeckb2b-ui/src/main/java']
]

pipeline {
  agent any

  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '30'))
  }

  tools {
    // Must match the name under Manage Jenkins -> Tools -> Maven installations.
    // Jenkins puts this on the PATH for the whole pipeline, so ci/analyze.sh
    // can just call 'mvn' with no hardcoded path.
    maven 'Maven_3.9.14'
    jdk 'JAVA_21'
  }

  environment {
    // Free key from https://nvd.nist.gov/developers/request-an-api-key
    // Without it, dependency-check's NVD feed sync gets rate-limited hard
    // and the stage below can take 20-30+ min on a cold cache.
    NVD_API_KEY = credentials('nvd-api-key')
  }

  stages {

    stage('Checkout') {
      steps {
        checkout scm
        discoverGitReferenceBuild(referenceJob: env.JOB_NAME)
      }
    }

    stage('Analyze') {
      steps {
        sh 'bash ci/analyze.sh'
      }
    }

    stage('Dependency-Check') {
      steps {
        // 'aggregate' rather than 'check' since this is a multi-module reactor -
        // produces one merged report at target/dependency-check-report.xml
        // instead of one per module.
        sh '''
          mvn -B org.owasp:dependency-check-maven:aggregate \
            -pl '!holodeckb2b-distribution' \
            -DnvdApiKey=$NVD_API_KEY \
            -DfailBuildOnCVSS=7 \
            -Dformats=XML,HTML
        '''
      }
    }

    stage('Trivy Scan') {
      steps {
        // fs mode reads the reactor's resolved deps directly, no container needed.
        // Matches the CVSS-7 threshold used above so the two gates agree.
        sh '''
          trivy fs \
              --severity HIGH,CRITICAL \
              --exit-code 1 \
              --format table \
              --scanners vuln \
              .
        '''
      }
    }
  }

  post {
    always {
      junit testResults: '**/target/surefire-reports/*.xml',
            allowEmptyResults: true

      recordIssues(
        enabledForFailure: true,
        sourceCodeEncoding: 'UTF-8',
        sourceDirectories: sourceDirs,
        tools: [
          java(),
          checkStyle(pattern: '**/target/checkstyle-result.xml'),
          pmdParser(pattern: '**/target/pmd.xml'),
          spotBugs(pattern: '**/target/spotbugsXml.xml'),
          dependencyCheckParser(pattern: '**/target/dependency-check-report.xml')
        ],
        qualityGates: [
          [threshold: 298, type: 'TOTAL',      criticality: 'UNSTABLE'],
          [threshold: 1,   type: 'NEW_HIGH',   criticality: 'UNSTABLE'],
          [threshold: 3,   type: 'NEW_NORMAL', criticality: 'UNSTABLE']
        ]
      )

      archiveArtifacts artifacts: '**/target/dependency-check-report.html', allowEmptyArchive: true

      recordCoverage(
        tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
        sourceCodeEncoding: 'UTF-8',
        sourceDirectories: sourceDirs
      )
    }
  }
}
