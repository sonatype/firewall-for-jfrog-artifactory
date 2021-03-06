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
import spock.lang.Unroll

import static com.sonatype.iq.artifactory.FirewallProperties.MODE.audit
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.quarantine
import static com.sonatype.iq.artifactory.TestHelper.createFirewallRepository

class FirewallRepositoryTest
    extends Specification
{
  @Unroll
  def 'verify repo format translation #packageType = #expectedFormat'() {
    setup:
      FirewallRepository firewallRepository = createFirewallRepository('theRepoKey', quarantine, 'remote', packageType)

    expect:
      firewallRepository.format == expectedFormat

    where:
      packageType | expectedFormat
      'maven'     | 'maven2'
      'npm'       | 'npm'
  }

  def 'test equality'() {
    setup:
      FirewallRepository firewallRepository1 = createFirewallRepository('one')


    expect:
      !firewallRepository1.equals(createFirewallRepository('two'))
      firewallRepository1.equals(createFirewallRepository('one'))
      firewallRepository1.equals(createFirewallRepository('one', audit))
      firewallRepository1.equals(createFirewallRepository('one', quarantine, 'local'))
  }
}
