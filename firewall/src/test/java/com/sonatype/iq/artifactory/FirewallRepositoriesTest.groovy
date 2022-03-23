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

import static org.hamcrest.Matchers.containsInAnyOrder
import static spock.util.matcher.HamcrestSupport.that
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.disabled

class FirewallRepositoriesTest
    extends Specification
{
  def 'test repositories correctly populated'() {
    setup:
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      firewallRepositories.add('key', TestHelper.createFirewallRepository('key'))
      firewallRepositories.add('disabled-key', TestHelper.createFirewallRepository('disabled-key', disabled))

    expect:
      firewallRepositories.count() == 4 // 4 to account for cache entry
      firewallRepositories.uniqueRepos.size() == 2 // uniquely there is still 1
      firewallRepositories.getEnabledFirewallRepoByKey('key').repoKey == 'key' // the inner key matches
      firewallRepositories.getEnabledFirewallRepoByKey('disabled-key') == null
      firewallRepositories.getEnabledOrDisabledFirewallRepoByKey('disabled-key').repoKey == 'disabled-key'
  }

  def 'test get normalized repo key'() {
    given:
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      firewallRepositories.add('key', TestHelper.createFirewallRepository('key'))

    when:
      def key = firewallRepositories.getNormalizedRepoKey(repoKey)

    then:
      key == expectedKey

    where:
      repoKey     | expectedKey
      'key'       | 'key'
      'key-cache' | 'key'
      'not-found' | 'not-found'
  }

  def 'test find'(){
    given:
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      firewallRepositories.add('key', TestHelper.createFirewallRepository('key'))

    when:
      def found = firewallRepositories.find { it.repoKey == 'key' }

    then:
      found.repoKey == 'key'
  }

  def 'test findAll'() {
    given:
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      firewallRepositories.add('key1', TestHelper.createFirewallRepository('key1'))
      firewallRepositories.add('key2', TestHelper.createFirewallRepository('key2'))
      firewallRepositories.add('key3', TestHelper.createFirewallRepository('key3'))


    when:
      def found = firewallRepositories.findAll { it.repoKey.startsWith('key') && !it.repoKey.contains('3') }

    then:
      found.size() ==2
      that found*.repoKey, containsInAnyOrder('key2', 'key1')

  }
}
