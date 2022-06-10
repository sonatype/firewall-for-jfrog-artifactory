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

import com.sonatype.iq.artifactory.restclient.RestClientConfiguration
import com.sonatype.iq.artifactory.restclient.RestClientFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j(value = 'logger')
class IqConnectionManagerTest extends Specification
{
    RestClientFactory restClientFactory = Mock()

    RestClientConfiguration restClientConfiguration = Mock()

    FirewallRepositories firewallRepositories = Mock(FirewallRepositories)

    @Unroll
    def 'test get user agent'() {
      given:
        def iqConnectionManager = new IqConnectionManager(restClientFactory, restClientConfiguration, firewallRepositories, logger)
        def expectedUserAgent = String.format("Firewall_For_Jfrog_Artifactory/%s (%s; %s; %s; %s; %s; %s)",
          pluginVersion, repositoryManagerEdition, System.getProperty("os.name"),
          System.getProperty("os.version"), System.getProperty("os.arch"), System.getProperty("java.version"),
          repositoryManagerNameAndVersion)

        when:
          def userAgent = iqConnectionManager.getUserAgent(pluginVersion, repositoryManagerEdition, repositoryManagerNameAndVersion)

        then:
          userAgent == expectedUserAgent

        where:
          pluginVersion  | repositoryManagerEdition | repositoryManagerNameAndVersion
          "2.3-SNAPSHOT" | ""                       | "Jfrog Artifactory 7.37.15"
    }
}
