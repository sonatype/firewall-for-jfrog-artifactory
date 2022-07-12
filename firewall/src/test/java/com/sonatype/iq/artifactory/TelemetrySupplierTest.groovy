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

import com.sonatype.insight.brain.client.UnsupportedServerVersionException
import com.sonatype.iq.artifactory.restclient.RestClient.Base
import com.sonatype.iq.artifactory.restclient.RestClientConfiguration
import com.sonatype.iq.artifactory.restclient.RestClientFactory

import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Subject

class TelemetrySupplierTest
    extends Specification
{
  RestClientFactory restClientFactory = Mock()

  RestClientConfiguration restClientConfiguration = Mock()

  Logger log = Mock()

  @Subject
  TelemetrySupplier telemetrySupplier = new TelemetrySupplier(restClientFactory, restClientConfiguration, 'arti-1.0',
      'fwfa-1.1', log)

  def "telemetry disabled when not initialized"() {
    expect:
      telemetrySupplier.getUserAgent() == 'Firewall_For_Jfrog_Artifactory/fwfa-1.1'
  }

  def "telemetry disabled when version does not match"() {
    given: 'version check results in exception being thrown'
      restClientFactory.forConfiguration(restClientConfiguration) >>
          Mock(Base) { validateServerVersion(_) >>
              { throw new UnsupportedServerVersionException('ignored', 'ignored') } }

    and: 'telemetry enabled'
      telemetrySupplier.enableIfSupported()

    when: 'telemetry is generated'
      def actual = telemetrySupplier.getUserAgent()

    then: 'basic telemetry is generated'
      actual == 'Firewall_For_Jfrog_Artifactory/fwfa-1.1'
  }

  def "telemetry is generated"() {
    given: 'version check does not fail'
      restClientFactory.forConfiguration(restClientConfiguration) >> Mock(Base)
      telemetrySupplier.enableIfSupported()

    when: 'telemetry is generated'
      def actual = telemetrySupplier.getUserAgent()

    then: 'full telemetry is generated'
      actual == "Firewall_For_Jfrog_Artifactory/fwfa-1.1 (; " +
          "${System.getProperty('os.name')}; " +
          "${System.getProperty('os.version')}; " +
          "${System.getProperty('os.arch')}; " +
          "${System.getProperty("java.version")}; Jfrog Artifactory arti-1.0)"
  }
}
