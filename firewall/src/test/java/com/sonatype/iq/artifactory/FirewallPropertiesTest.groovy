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

import com.sonatype.iq.artifactory.FirewallProperties.MODE
import com.sonatype.iq.artifactory.cache.CacheStrategy

import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll

import static com.sonatype.iq.artifactory.FirewallProperties.FIREWALL_CACHE_QUARANTINE_STRATEGY

class FirewallPropertiesTest
    extends Specification
{
  Logger logger

  def 'setup'() {
    logger = Mock()
  }

  def 'test golden config'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'foo'                          : 'bar',
          'firewall.repo.main-java-libs' : 'quarantine',
          'firewall.repo.other-java-libs': 'audit'
      ])

    when:
      def result = FirewallProperties.load(properties, logger).getRepositories()

    then:
      result == ['main-java-libs': MODE.quarantine, 'other-java-libs': MODE.audit]
  }

  def "test non-repo config is ignored"() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'foo'                          : 'bar',
          'firewall.repo.main-java-libs' : 'quarantine',
          'firewall.repo.other-java-libs': 'audit'
      ])

    when:
      def result = FirewallProperties.load(properties, logger).getRepositories()

    then:
      result == ['main-java-libs': MODE.quarantine, 'other-java-libs': MODE.audit]
  }

  def "ensure bad mode throws error"() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.repo.foo': 'quarantine',
          'firewall.repo.bar': 'bad'
      ])

    when:
      FirewallProperties firewallProperties = FirewallProperties.load(properties, logger)
      def result = firewallProperties.getRepositories()

    then:
      1 * logger.error("Unknown option 'bad' for repository 'firewall.repo.bar'. Allowed values are: quarantine, audit")
  }

  def 'get mode of repository'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.repo.main-java-libs' : 'quarantine',
          'firewall.repo.other-java-libs': 'audit'
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.getRepositoryMode('main-java-libs') == MODE.quarantine
      firewallProperties.getRepositoryMode('other-java-libs') == MODE.audit
  }

  def 'get iq config'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.iq.url'                      : 'http://localhost:8080',
          'firewall.iq.username'                 : 'admin',
          'firewall.iq.password'                 : 'admin123'
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.iqUrl == 'http://localhost:8080'
      firewallProperties.iqUsername == 'admin'
      firewallProperties.iqPassword == 'admin123'
  }

  def 'get connection timeouts'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.iq.connect.timeout.in.millis' : '20000',
          'firewall.iq.socket.timeout.in.millis': '30000'
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.iqConnectTimeoutInMillis == 20000
      firewallProperties.iqSocketTimeoutInMillis == 30000
  }

  def 'get quarantine update millis configured'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.unquarantine.update.in.millis' : '100000'
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.unquarantineUpdateInMillis == 100000
  }

  def 'get verify wait millis configured'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.verify.wait.in.millis' : '12000'
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.verifyInitWaitInMillis == 12000
  }

  def 'get iq public url'() {
    given:
      def testUrl = '/test/url'
      Properties properties = new Properties()
      properties.putAll([
          'firewall.iq.public.url' : testUrl
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.iqPublicUrl == testUrl
  }

  def 'get proxy config'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.iq.proxy.hostname'        : 'localhost',
          'firewall.iq.proxy.port'            : '1234',
          'firewall.iq.proxy.username'        : 'foo',
          'firewall.iq.proxy.password'        : 'bar',
          'firewall.iq.proxy.ntlm.domain'     : 'dom',
          'firewall.iq.proxy.ntlm.workstation': 'wk'
      ])

    when:
      def firewallProperties = FirewallProperties.load(properties, logger)

    then:
      firewallProperties.proxyHostname == 'localhost'
      firewallProperties.proxyPort == 1234
      firewallProperties.proxyUsername == 'foo'
      firewallProperties.proxyPassword == 'bar'
      firewallProperties.proxyNtlmDomain == 'dom'
      firewallProperties.proxyNtlmWorkstation == 'wk'
  }

  @Unroll
  def 'test valid repository manager id - #value'() {
    given:
      Properties properties = new Properties()
      properties.putAll(['firewall.repository.manager.id': value])

    when:
      FirewallProperties.load(properties, logger)

    then:
      noExceptionThrown()

    where:
      value << ['foo', 'foo-bar', 'foo_bar', '_foo', 'foo_', '-foo', 'foo-', 'Foo', 'FooBar']
  }

  @Unroll
  def 'test invalid repository manager id - #value'() {
    given:
      Properties properties = new Properties()
      properties.putAll(['firewall.repository.manager.id': value])

    when:
      FirewallProperties.load(properties, logger)

    then:
      thrown(InvalidPropertiesException)

    where:
      value << ['baz!', 'baz~', 'foo bar', 'f@o', 'foo^bar']
  }

  @Unroll
  def 'cache strategy config of #configuredStrategy'() {
    given: 'properties with a configured cache strategy'
      Properties properties = new Properties()
      properties[FIREWALL_CACHE_QUARANTINE_STRATEGY] = configuredStrategy

    when: 'properties are loaded'
      def firewallProperties = FirewallProperties.load(properties, logger)

    then: 'the expected cache strategy is returned'
      firewallProperties.quarantineCacheStrategy == expectedStrategy

    where:
      configuredStrategy    || expectedStrategy
      'memory_then_storage' || CacheStrategy.MEMORY_THEN_STORAGE
      'storage_only'        || CacheStrategy.STORAGE_ONLY
      'MEMORY_THEN_STORAGE' || CacheStrategy.MEMORY_THEN_STORAGE
      'invalid'             || null
  }

  @Unroll
  def  'cache strategy config validation'() {
    given: 'an invalid cache strategy'
      Properties properties = new Properties()
      properties[FIREWALL_CACHE_QUARANTINE_STRATEGY] = 'invalid'

    when: 'cache property is accessed'
      def firewallProperties = FirewallProperties.load(properties, logger)
      def actual = firewallProperties.quarantineCacheStrategy

    then: 'no value is returned'
      actual == null

    and: 'error is logged'
      1 * logger.error('Unknown option \'invalid\' for property \'firewall.cache.quarantine.strategy\'. Allowed ' +
          'values are: [STORAGE_ONLY, MEMORY_THEN_STORAGE]')
  }

  def 'quarantine cache strategy not configured'() {
    expect: 'when no cache strategy is configured then no cache strategy is returned'
      FirewallProperties.load(new Properties(), logger).quarantineCacheStrategy == null
  }
}
