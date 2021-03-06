package no.skatteetaten.aurora.version.suggest

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Testing the combined outcome of ReleaseVersionEvaluator and ReleaseVersionIncrementer
 * Documenting the combined behaviour
 */
class ReleaseVersionTest extends Specification {

  @Unroll
  def "shall suggest version #expectedVersion for version #versionHint"() {
    given:
      def existingVersions = ["2.2.2", "3.0.0", "1.1.0", "1.1.1", "1.2.2", "1.2.1", "1.2.0", "1.3.0"]

    when:
      def versionSegmentToIncrement = ReleaseVersionEvaluator.findVersionSegmentToIncrement(
          versionHint,
          Optional.empty(),
          [],
          [])

      def inferredVersion = ReleaseVersionIncrementer.suggestNextReleaseVersion(
          versionSegmentToIncrement,
          versionHint,
          existingVersions)

    then:
      inferredVersion.toString() == expectedVersion

    where:
      expectedVersion | versionHint
      "1.1.2"         | "1.1.0"
      "1.1.2"         | "1.1.1"
      "1.1.2"         | "1.1"
      "1.0.0"         | "1.0"
      "1.1.2"         | "1.1"
      "1.4.0"         | "1.4"
      "1.4.0"         | "1"
      "2.3.0"         | "2"
      "3.1.0"         | "3"
      "4.0.0"         | "4"
  }

  @Unroll
  def "shall suggest version #expectedVersion for version #versionHint for #originatingBranchName, #forcePatchIncrementFor and #forceMinorIncrementFor"() {
    given:
      def existingVersions = ["2.2.2", "3.0.0", "1.1.0", "1.1.1", "1.2.2", "1.2.1", "1.2.0", "1.3.0"]

    when:
      def versionSegmentToIncrement = ReleaseVersionEvaluator.findVersionSegmentToIncrement(
          versionHint,
          Optional.of(originatingBranchName),
          forcePatchIncrementFor,
          forceMinorIncrementFor)

      def inferredVersion = ReleaseVersionIncrementer.suggestNextReleaseVersion(
          versionSegmentToIncrement,
          versionHint,
          existingVersions)

    then:
      inferredVersion.toString() == expectedVersion

    where:
      expectedVersion | versionHint | originatingBranchName | forcePatchIncrementFor | forceMinorIncrementFor
      "1.1.2"         | "1.1"       | "branch/some"         | ["branch"]             | []
      "1.4.0"         | "1.1"       | "branch/some"         | []                     | ["branch"]
      "1.5.0"         | "1.5"       | "branch/some"         | ["branch"]             | []
      "1.5.0"         | "1.5"       | "branch/some"         | []                     | ["branch"]
      "1.3.1"         | "1"         | "branch/some"         | ["branch"]             | []
      "1.4.0"         | "1"         | "branch/some"         | []                     | ["branch"]
      "2.2.3"         | "2"         | "branch/some"         | ["branch"]             | []
      "2.3.0"         | "2"         | "branch/some"         | []                     | ["branch"]
      "3.0.1"         | "3"         | "branch/some"         | ["branch"]             | []
      "3.1.0"         | "3"         | "branch/some"         | []                     | ["branch"]
      "4.0.0"         | "4"         | "branch/some"         | ["branch"]             | []
      "4.0.0"         | "4"         | "branch/some"         | []                     | ["branch"]

  }

}
