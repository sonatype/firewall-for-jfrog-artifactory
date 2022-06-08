/*
 * Copyright 2019, Sonatype, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
