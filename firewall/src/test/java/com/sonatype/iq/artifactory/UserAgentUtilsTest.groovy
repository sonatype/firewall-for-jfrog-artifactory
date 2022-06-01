package com.sonatype.iq.artifactory

import spock.lang.Specification

class UserAgentUtilsTest extends Specification {
  def 'test get default user agent with server edition'() {
    setup:
    def expectedClientVersion = "2.3-SNAPSHOT"
    def expectedClientEdition = "PRO"
    def expectedRepository = "Jfrog Artifactory 7.37.15"
    def expectedUserAgent = String.format("Firewall_For_Jfrog_Artifactory/%s (%s; %s; %s; %s; %s; %s)",
        expectedClientVersion, expectedClientEdition, System.getProperty("os.name"),
        System.getProperty("os.version"), System.getProperty("os.arch"), System.getProperty("java.version"),
        expectedRepository)

    when:
    def userAgent = UserAgentUtils.getDefaultUserAgent("2.3-SNAPSHOT", "PRO", "Jfrog Artifactory 7.37.15")

    then:
    userAgent == expectedUserAgent
  }

  def 'test get default user agent without server edition '() {
    setup:
    def expectedClientVersion = "2.3-SNAPSHOT"
    def expectedClientEdition = ""
    def expectedRepository = "Jfrog Artifactory 7.37.15"
    def expectedUserAgent = String.format("Firewall_For_Jfrog_Artifactory/%s (%s; %s; %s; %s; %s; %s)",
        expectedClientVersion, expectedClientEdition, System.getProperty("os.name"),
        System.getProperty("os.version"), System.getProperty("os.arch"), System.getProperty("java.version"),
        expectedRepository)

    when:
    def userAgent = UserAgentUtils.getDefaultUserAgent("2.3-SNAPSHOT", "", "Jfrog Artifactory 7.37.15")

    then:
    userAgent == expectedUserAgent
  }
}
