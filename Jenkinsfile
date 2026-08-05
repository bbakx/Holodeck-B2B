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
          spotBugs(pattern: '**/target/spotbugsXml.xml')
        ],
        qualityGates: [
          [threshold: 298, type: 'TOTAL',      criticality: 'UNSTABLE'],
          [threshold: 1,   type: 'NEW_HIGH',   criticality: 'UNSTABLE'],
          [threshold: 3,   type: 'NEW_NORMAL', criticality: 'UNSTABLE']
        ]
      )

      recordCoverage(
        tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
        sourceCodeEncoding: 'UTF-8',
        sourceDirectories: sourceDirs
      )
    }
  }
}
