#!/usr/bin/env bash
#
# ci/analyze.sh
#
# Runs all static analysis tools and writes their XML reports into target/.
# Called from a single Jenkins "Execute shell" build step:
#
#     bash ci/analyze.sh
#
# Developers can run the exact same command locally to reproduce CI results.
#
# This script deliberately does NOT fail on violations. Pass/fail is decided
# by the Jenkins post-build recorders (Warnings NG / Coverage), which can
# compare against a reference build. If this script failed on violations,
# later analyzers would never run and Jenkins would get no reports at all.
#
set -euo pipefail

# ---------------------------------------------------------------------------
# Pinned analyzer versions. Bump these deliberately; never let them float.
# ---------------------------------------------------------------------------
CHECKSTYLE_GAV="org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0"
PMD_GAV="org.apache.maven.plugins:maven-pmd-plugin:3.26.0"
SPOTBUGS_GAV="com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0"
JACOCO_GAV="org.jacoco:jacoco-maven-plugin:0.8.15"

# The Checkstyle engine bundled inside maven-checkstyle-plugin lags the
# upstream releases. Pin it so the rules match what ci/checkstyle.xml expects.
CHECKSTYLE_ENGINE_VERSION="13.5.0"

MVN="mvn -B -ntp"

# Absolute path to this script's directory, so the -D paths below work no
# matter which directory Maven decides to run a module from.
CI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------------------------------------------------------------------------
# Fail fast with a clear message if a config file is missing.
# ---------------------------------------------------------------------------
for f in checkstyle.xml pmd-ruleset.xml spotbugs-exclude.xml; do
  if [[ ! -f "${CI_DIR}/${f}" ]]; then
    echo "ERROR: missing ${CI_DIR}/${f}" >&2
    echo "       All four ci/ files must be committed together." >&2
    exit 1
  fi
done

echo "=========================================================="
echo " 1/4  Compile + tests + JaCoCo coverage"
echo "=========================================================="
# One Maven invocation instead of the previous 'clean compile' followed by
# 'install': the second pass recompiled everything the first pass had just
# built. Merging saves a full JVM startup and a full compile.
#
# 'install' (not 'test') is required: the standalone analyzer goals below run
# outside the reactor and resolve sibling modules from ~/.m2, so the module
# jars have to be installed there first.
#
# prepare-agent sets the 'argLine' property, which Surefire picks up on its
# own. No pom change needed -- UNLESS the pom hard-codes argLine itself, in
# which case coverage silently comes out empty. Check that first if
# jacoco.xml shows zero coverage.
#
# -Xlint:all cannot be passed via -D (compilerArgs has no user property),
# but the two flags below do have user properties and give most of the value.
$MVN clean \
  "${JACOCO_GAV}:prepare-agent" \
  install \
  "${JACOCO_GAV}:report" \
  -Dmaven.compiler.showWarnings=true \
  -Dmaven.compiler.showDeprecation=true \
  -Dmaven.test.failure.ignore=true \
  -pl '!modules/holodeckb2b-distribution'

echo "=========================================================="
echo " 2/4  Checkstyle  -> target/checkstyle-result.xml"
echo "=========================================================="
$MVN "${CHECKSTYLE_GAV}:checkstyle" \
  -Dcheckstyle.config.location="${CI_DIR}/checkstyle.xml" \
  -Dcheckstyle.version="${CHECKSTYLE_ENGINE_VERSION}" \
  -Dcheckstyle.failOnViolation=false \
  -pl '!modules/holodeckb2b-distribution'

echo "=========================================================="
echo " 3/4  PMD + CPD  -> target/pmd.xml, target/cpd.xml"
echo "=========================================================="
$MVN "${PMD_GAV}:pmd" "${PMD_GAV}:cpd" \
  -Drulesets="${CI_DIR}/pmd-ruleset.xml" \
  -Dformat=xml \
  -Dpmd.failOnViolation=false \
  -Dcpd.minimumTokens=100 \
  -pl '!modules/holodeckb2b-distribution'

echo "=========================================================="
echo " 4/4  SpotBugs  -> target/spotbugsXml.xml"
echo "=========================================================="
$MVN "${SPOTBUGS_GAV}:spotbugs" \
  -Dspotbugs.effort=Max \
  -Dspotbugs.threshold=Medium \
  -Dspotbugs.excludeFilterFile="${CI_DIR}/spotbugs-exclude.xml" \
  -Dspotbugs.failOnError=false \
  -pl '!modules/holodeckb2b-distribution'

echo "=========================================================="
echo " Done. Reports written:"
echo "=========================================================="
# In a multi-module build there will be one set per module; Warnings NG
# picks them all up via its **/target/... patterns.
find . -name 'checkstyle-result.xml' \
    -o -name 'pmd.xml' \
    -o -name 'cpd.xml' \
    -o -name 'spotbugsXml.xml' \
    -o -name 'jacoco.xml' \
  | sort
