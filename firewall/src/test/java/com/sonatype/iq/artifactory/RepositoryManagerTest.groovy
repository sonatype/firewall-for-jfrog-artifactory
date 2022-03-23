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

import org.artifactory.repo.Repositories
import org.artifactory.repo.RepositoryConfiguration
import org.slf4j.Logger
import spock.lang.Specification

import static com.sonatype.iq.artifactory.FirewallProperties.MODE.audit
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.disabled
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.quarantine

class RepositoryManagerTest
    extends Specification
{
  Logger logger = Mock()

  FirewallProperties firewallProperties

  FirewallRepositories firewallRepositories

  IqConnectionManager iqConnectionManager

  Repositories repositories = Mock(Repositories)

  StorageManager storageManager

  RepositoryManager repositoryManager

  Map defaultProperties = [
      'firewall.iq.url'              : 'http://localhost:8072',
      'firewall.iq.username'         : 'admin',
      'firewall.iq.password'         : 'admin123',
      'firewall.repo.main-java-libs' : 'quarantine',
      'firewall.repo.other-java-libs': 'audit',
      'firewall.repo.foo-cache'      : 'audit'
  ]

  def 'test init - repo not found'() {
    given:
      setupMocks(['firewall.repo.foo': 'quarantine'])

    when:
      repositoryManager.loadRepositoriesFromProperties()

    then:
      1 * repositories.getRepositoryConfiguration(_) >> null
      1 * logger.warn('Repository \'foo\' was not found in Artifactory. Skipping...')
      0 * iqConnectionManager._
  }

  def 'test init - repos loaded from storage'() {
    setup:
      setupMocks([:])
      repositories.remoteRepositories >> ['foo', 'bar', 'baz']

    when:
      repositoryManager.loadRepositoriesFromStorage()
      def firewallRepository = firewallRepositories.getEnabledOrDisabledFirewallRepoByKey(repoKey)

    then:
      firewallRepository != null
      firewallRepository.repoKey == repoKey
      firewallRepository.mode == mode
      1 * storageManager.getFirewallMode(repoKey) >> mode
      1 * repositories.getRepositoryConfiguration(repoKey) >> getMockRepositoryConfiguration(repoKey, 'remote')
      1 * logger.warn("Repository configuration for '${repoKey}' in mode '${mode}' missing from firewall.properties")

    where:
      repoKey | mode
      'foo'   | quarantine
      'bar'   | audit
      'baz'   | disabled
  }

  def 'test init - repos loaded from storage do not override firewall.properties'() {
    setup:
      setupMocks([
          'firewall.repo.foo': 'quarantine',
          'firewall.repo.bar': 'audit',
          'firewall.repo.baz': 'disabled',
      ])
      repositories.remoteRepositories >> ['foo', 'bar', 'baz']

    when: 'repositories are loaded from properties'
      repositoryManager.loadRepositoriesFromProperties()
      def firewallRepositoryAfterLoadFromProperties = firewallRepositories.getEnabledOrDisabledFirewallRepoByKey(repoKey)

    then: 'the repo repository is in audit mode'
      firewallRepositoryAfterLoadFromProperties.repoKey == repoKey
      firewallRepositoryAfterLoadFromProperties.mode == mode
      repositories.getRepositoryConfiguration(repoKey) >> getMockRepositoryConfiguration(repoKey, 'remote')

    when: 'repositories are loaded from storage afterwards'
      repositoryManager.loadRepositoriesFromStorage()
      def firewallRepositoryAfterLoadFromStorage = firewallRepositories.getEnabledOrDisabledFirewallRepoByKey(repoKey)

    then: 'the repo repository is still in audit mode even though in storage it is marked as something else'
      firewallRepositoryAfterLoadFromProperties.is(firewallRepositoryAfterLoadFromStorage)
      firewallRepositoryAfterLoadFromStorage.repoKey == repoKey
      firewallRepositoryAfterLoadFromStorage.mode == mode
      1 * storageManager.getFirewallMode(repoKey) >> modeInStorage

    where:
      repoKey | mode       | modeInStorage
      'foo'   | quarantine | audit
      'bar'   | audit      | quarantine
      'baz'   | disabled   | quarantine
  }

  def 'test init - only remote repos supported'() {
    given:
      setupMocks(['firewall.repo.foo': 'quarantine'])
      RepositoryConfiguration repositoryConfiguration = getMockRepositoryConfiguration('foo', 'local')

    when:
      repositoryManager.loadRepositoriesFromProperties()

    then:
      1 * repositories.getRepositoryConfiguration(_) >> repositoryConfiguration
      1 * logger.warn(
          "Repository 'foo' is not a supported type. Found type 'local', but Firewall can only be enabled " +
              "on type 'remote'. Skipping...")
      0 * iqConnectionManager._
  }

  def 'test init - verify enabling on cache repo happens against the real'() {
    given:
      setupMocks(['firewall.repo.foo-cache': 'quarantine'])
      RepositoryConfiguration repositoryConfiguration = getMockRepositoryConfiguration('foo')

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()

    then:
      // note - first an attempt on 'foo-cache', then 'foo'. Only 'foo' call to IQ.
      1 * repositories.getRepositoryConfiguration('foo-cache') >> repositoryConfiguration
      1 * repositories.getRepositoryConfiguration('foo') >> repositoryConfiguration
      1 * iqConnectionManager.enableQuarantine('foo')
      0 * iqConnectionManager.enableQuarantine(_)
      firewallRepositories.count() == 2 // note two items in map as it contains a key for both cache and normal names
  }

  def 'test init - repositories enabled in IQ'() {
    given:
      setupMocks(['firewall.repo.foo': 'quarantine', 'firewall.repo.bar': 'audit'])
      RepositoryConfiguration fooRepositoryConfiguration = getMockRepositoryConfiguration('foo')
      RepositoryConfiguration barRepositoryConfiguration = getMockRepositoryConfiguration('bar')

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()

    then:
      1 * repositories.getRepositoryConfiguration('foo') >> fooRepositoryConfiguration
      1 * repositories.getRepositoryConfiguration('bar') >> barRepositoryConfiguration
      1 * iqConnectionManager.enableQuarantine('foo')
      1 * iqConnectionManager.enableAudit('bar')
      firewallRepositories.count() == 4 // 2 for each = 4. One for real name, the other for cache name.
  }

  def 'test init - IQ enable failure still has repos'() {
    given:
      setupMocks(['firewall.repo.foo': 'quarantine'])
      RepositoryConfiguration fooRepositoryConfiguration = getMockRepositoryConfiguration('foo')

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()

    then:
      1 * repositories.getRepositoryConfiguration('foo') >> fooRepositoryConfiguration
      1 * iqConnectionManager.enableQuarantine(_) >> { throw new RuntimeException('cannot connect to IQ!') }
      firewallRepositories.count() == 2
  }

  def 'test firewall enabled modes'() {
    given:
      setupMocks(['firewall.repo.foo': 'quarantine', 'firewall.repo.bar': 'audit'])
      RepositoryConfiguration fooRepositoryConfiguration = getMockRepositoryConfiguration('foo')
      RepositoryConfiguration barRepositoryConfiguration = getMockRepositoryConfiguration('bar')

    when:
      repositoryManager.loadRepositoriesFromProperties()

    then:
      1 * repositories.getRepositoryConfiguration('foo') >> fooRepositoryConfiguration
      1 * repositories.getRepositoryConfiguration('bar') >> barRepositoryConfiguration

      repositoryManager.isFirewallEnabledForRepo('foo')
      repositoryManager.isQuarantineEnabledForRepo('foo')

      repositoryManager.isFirewallEnabledForRepo('bar')
      !repositoryManager.isQuarantineEnabledForRepo('bar')
  }

  def 'test enabled timestamp is start time'() {
    def startTime = Date.parse(StorageManager.TIMESTAMP_FORMAT, '2019-02-20 20:20:20.000 +0000')

    given:
      setupMocks(['firewall.repo.foo': 'quarantine'])
      RepositoryConfiguration fooRepositoryConfiguration = getMockRepositoryConfiguration('foo')
      repositoryManager.startTime = startTime

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()
      repositoryManager.verifyFirewallEnabled()

    then:
      1 * repositories.getRepositoryConfiguration('foo') >> fooRepositoryConfiguration
      1 * storageManager.markFirewallEnabledIfNecessary('foo', startTime)
  }

  def 'test iq repository url set on verify for quarantine enabled'() {
    given:
      setupMocks(['firewall.repo.foo': 'quarantine'])
      RepositoryConfiguration fooRepositoryConfiguration = getMockRepositoryConfiguration('foo')

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()
      repositoryManager.verifyRepositoryInitProperties()

    then:
      1 * repositories.getRepositoryConfiguration('foo') >> fooRepositoryConfiguration
      1 * storageManager.setIqRepositoryUrl('foo', '/test/summary')
  }

  def 'test iq repository url set on verify for audit enabled'() {
    given:
      setupMocks(['firewall.repo.foo': 'audit'])
      RepositoryConfiguration fooRepositoryConfiguration = getMockRepositoryConfiguration('foo')

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()
      repositoryManager.verifyRepositoryInitProperties()

    then:
      1 * repositories.getRepositoryConfiguration('foo') >> fooRepositoryConfiguration
      1 * storageManager.setIqRepositoryUrl('foo', '/test/summary')
  }

  def 'test iq repository mode set'() {
    given:
      setupMocks([
          'firewall.repo.foo': 'audit',
          'firewall.repo.bar': 'quarantine',
          'firewall.repo.baz': 'disabled'
      ])

    when:
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()
      repositoryManager.verifyRepositoryInitProperties()

    then:
      1 * repositories.getRepositoryConfiguration(repoKey) >> getMockRepositoryConfiguration(repoKey)
      1 * storageManager.setFirewallMode(repoKey, mode)

    where:
      repoKey | mode
      'foo'   | 'audit'
      'bar'   | 'quarantine'
  }

  def 'test firewall properties removed'() {
    given:
      def repoKey  =  'baz'

      setupMocks([
          'firewall.repo.foo': 'audit',
          'firewall.repo.bar': 'quarantine',
          'firewall.repo.baz': 'disabled'
      ])
      repositories.getRepositoryConfiguration(repoKey) >> getMockRepositoryConfiguration(repoKey)
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()

    when:
      repositoryManager.disableRepositories()

    then: 'remove properties is called for the disabled repo'
      1 * storageManager.removeFirewallPropertiesFromRepository(repoKey)

    and: 'remove properties is not called for any other repo'
      0 * storageManager.removeFirewallPropertiesFromRepository(_)
  }

  def 'test repository disabled in IQ on disable'() {
    given:
      setupMocks([
          'firewall.repo.foo'           : 'audit',
          'firewall.repo.bar-audit'     : 'disabled',
          'firewall.repo.bar-quarantine': 'disabled',
      ])
      repositories.getRepositoryConfiguration(repoKey) >> getMockRepositoryConfiguration(repoKey)
      repositoryManager.loadRepositoriesFromProperties()
      repositoryManager.enableRepositoriesInIq()

    when:
      repositoryManager.disableRepositories()

    then: 'we look at the previous quarantine mode'
      1 * storageManager.getFirewallMode(repoKey) >> mode

    and: 'if audit, disable audit is called in IQ'
      (mode == audit? 1 : 0) * iqConnectionManager.disableAudit(repoKey)

    and: 'if quarantine, disable quarantine is called in IQ'
      (mode == quarantine? 1 : 0) * iqConnectionManager.disableQuarantine(repoKey)

    where:
      repoKey          | mode
      'bar-audit'      | audit
      'bar-quarantine' | quarantine
  }

  void setupMocks(final Map<String, String> propertiesMap = defaultProperties) {
    firewallRepositories = new FirewallRepositories()

    Properties properties = new Properties()
    properties.putAll(propertiesMap)
    firewallProperties = FirewallProperties.load(properties, logger)

    iqConnectionManager = Mock(IqConnectionManager, constructorArgs: [Mock(RestClientFactory), Mock(
        RestClientConfiguration), firewallRepositories, logger])

    iqConnectionManager.getPolicyEvaluationSummary(_) >> TestHelper.createRepositoryPolicyEvaluationSummary()

    storageManager = Mock(StorageManager, constructorArgs: [repositories, Mock(PathFactory), firewallRepositories, logger])

    repositoryManager = new RepositoryManager(logger, repositories, firewallProperties, iqConnectionManager,
        storageManager, firewallRepositories)
  }

  RepositoryConfiguration getMockRepositoryConfiguration(final String repoKey, final String type = 'remote') {
    RepositoryConfiguration repositoryConfiguration = Mock(RepositoryConfiguration)
    repositoryConfiguration.getKey() >> repoKey
    repositoryConfiguration.getType() >> type
    return repositoryConfiguration
  }
}
