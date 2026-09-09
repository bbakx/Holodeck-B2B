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
    stage('SBOM') {
      steps {
        sh '''
          mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
          -pl '!:holodeckb2b-distribution' \
          -DoutputFormat=json \
          -DoutputName=bom
        '''
      }
      post {
        always {
          archiveArtifacts artifacts: 'target/bom.json', allowEmptyArchive: true
        }
      }
    }
    stage('Dependency-Check') {
      steps {
        // 'aggregate' rather than 'check' since this is a multi-module reactor -
        // produces one merged report at target/dependency-check-report.xml
        // instead of one per module.
        //
        // catchError lets a CVSS-7+ finding mark this stage FAILED and the
        // build UNSTABLE without aborting the pipeline, so the Trivy Scan
        // stage below still runs instead of being skipped.
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            mvn -B org.owasp:dependency-check-maven:aggregate \
            -pl '!:holodeckb2b-distribution' \
            -DnvdApiKey=$NVD_API_KEY \
            -DfailBuildOnCVSS=7 \
            -Dformats=JSON,HTML
          '''
        }
      }
    }
    stage('Trivy Scan') {
      steps {
        // fs mode reads the reactor's resolved deps directly, no container needed.
        // Matches the CVSS-7 threshold used above so the two gates agree.
        //
        // catchError here too, for the same reason: a HIGH/CRITICAL finding
        // marks this stage FAILED and the build UNSTABLE, but still lets the
        // post block (junit, recordIssues, recordCoverage) run to completion.
        //
        // Scans once to JSON (the authoritative, machine-readable report),
        // then converts that same result to a plain-text table - no second
        // scan, so it doesn't cost extra time. set +e/-e around the scan is
        // needed because a plain multi-line sh script only reports the exit
        // code of its LAST command; without capturing scan_exit explicitly,
        // a nonzero exit from trivy fs would get silently overwritten by
        // trivy convert's exit 0, and catchError would never see the failure.
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            set +e
            trivy fs \
                --severity HIGH,CRITICAL \
                --exit-code 1 \
                --format json \
                --output trivy-report.json \
                --scanners vuln \
                .
            scan_exit=$?
            set -e

            trivy convert \
                --format table \
                --output trivy-report.txt \
                trivy-report.json

            exit $scan_exit
          '''
        }
        archiveArtifacts artifacts: 'trivy-report.json,trivy-report.txt', allowEmptyArchive: true
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
          owaspDependencyCheck(pattern: '**/target/dependency-check-report.json')
        ],
        qualityGates: [
            // Ratchet ceiling on the existing backlog (applied per tool).
            // Set to the current worst-tool total + ~10% headroom; lower it over time.
            [threshold: 7000, type: 'TOTAL', criticality: 'UNSTABLE'],
          
            // Regression gates: nothing NEW may be introduced.
            [threshold: 1, type: 'NEW_HIGH',   criticality: 'FAILURE'],
            [threshold: 1, type: 'NEW_NORMAL', criticality: 'UNSTABLE']
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
