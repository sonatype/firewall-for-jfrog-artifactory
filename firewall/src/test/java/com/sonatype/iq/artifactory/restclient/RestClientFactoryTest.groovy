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
package com.sonatype.iq.artifactory.restclient

import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList
import com.sonatype.insight.brain.client.ConfigurationClient
import com.sonatype.iq.artifactory.restclient.RestClient.Repository
import com.sonatype.iq.artifactory.restclient.RestClientFactory.BaseClient

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.sonatype.iq.artifactory.restclient.RepositoryManagerType.ARTIFACTORY

class RestClientFactoryTest
    extends Specification
{
  @Subject
  RestClientFactory restClientFactory = new RestClientFactory()

  RestClientConfiguration restClientConfiguration = new RestClientConfiguration()
      .setServerUrl('http://example.com/')

  @Unroll
  def 'calls #wrappedMethod on configurationClient'() {
    given: 'real restClient with mode configuration client'
      def client = restClientFactory.forConfiguration(restClientConfiguration) as BaseClient
      client.configurationClientFactory = Mock(Closure)
      def mockConfigurationClient = Mock(ConfigurationClient)
      client.configurationClientFactory.call() >> mockConfigurationClient

    when: 'rest client method is invoked'
      client."${wrappedMethod}"(*args)

    then: 'configuration client is invoked'
      if (args) {
        1 * mockConfigurationClient."${wrappedMethod}"({ [it] == args })
      }
      else {
        1 * mockConfigurationClient."${wrappedMethod}"()
      }

    where:
      wrappedMethod               | args
      'validateConfiguration'     | []
      'validateServerVersion'     | ['123']
      'getFirewallIgnorePatterns' | []
  }

  @Unroll
  def 'calls #wrappedMethod on firewallClient'() {
    given: 'real restClient with mocked firewall client'
      def client = restClientFactory.forConfiguration(restClientConfiguration).forRepository('instanceId', 'repoId',
          ARTIFACTORY) as Repository
      client.firewallClientFactory = Mock(Closure)
      def mockFirewallClient = Mock(Repository)
      client.firewallClientFactory.call() >> mockFirewallClient

    when: 'rest client method is invoked'
      client."${wrappedMethod}"(*args)

    then: 'firewall client is invoked'
      if (args) {
        1 * mockFirewallClient."${wrappedMethod}"({ [it] == args })
      }
      else {
        1 * mockFirewallClient."${wrappedMethod}"()
      }

    where:
      wrappedMethod                     | args
      'setEnabled'                      | [true]
      'setQuarantine'                   | [true]
      'evaluateComponents'              | [Mock(RepositoryComponentEvaluationDataRequestList)]
      'evaluateComponentWithQuarantine' | [Mock(RepositoryComponentEvaluationDataRequestList)]
      'getPolicyEvaluationSummary'      | []
      'removeComponent'                 | ['pathname']
      'getUnquarantinedComponents'      | [1616161616]
  }
}
