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

import com.sonatype.clm.dto.model.component.FirewallIgnorePatterns
import com.sonatype.iq.artifactory.FirewallProperties.MODE

import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.search.Searches
import org.slf4j.Logger
import spock.lang.Specification

import static com.sonatype.iq.artifactory.StorageManager.getHASH
import static com.sonatype.iq.artifactory.StorageManager.getQUARANTINE
import static org.apache.commons.lang3.StringUtils.isNotBlank

class NexusFirewallForArtifactoryTestContainer
    extends Specification
{
  enum TestFirewallMode {
    disabled, quarantine, audit
  }

  enum TestQuarantineStatus {
    none, ALLOW, DENY
  }

  /*
    This is the expected format of a container definition:

      def contract = [
          'repository' : [
              'repoKey' : 'test-central',
              'type' : 'remote',
              'packageType' : 'maven2',
              'firewallMode' : TestFirewallMode.quarantine
          ],
          'artifact' : [
              'path' : 'org.apache.tomcat:tomcat-embed-core:8.5.6',
              'extension' : 'jar',
              'sha1' : 'sha1c0c0babe',
              'ignoreExtension' : ignoreExtension,
              'beginningQuarantineStatus' : TestQuarantineStatus.DENY,
              'createdAfterFirewallEnabled' : true,
              'quarantinedByIq' : quarantinedByIq
          ]
      ]

   */

  def getObservablePluginManager(Logger logger, Map contract) {
    String repoKey = contract['repository']['repoKey']
    String format = contract['repository']['packageType']
    def firewallMode = contract['repository']['firewallMode']

    def extension = contract['artifact']['extension']
    String path = contract['artifact']['path'] + '.' + extension
    TestRepoPath repoPath = TestRepoPath.createFileInstance(repoKey, path)

    def repoConfig = Mock(RepositoryConfiguration)
    repoConfig.packageType >> format
    repoConfig.key >> repoKey
    repoConfig.type >> contract['repository']['type']

    // setup the item information with a create time based on whether
    // it should be before or after quaratine enabled timestamp
    String sha1 = contract['artifact']['sha1']
    boolean createdAfterFirewallEnabled = contract['artifact']['createdAfterFirewallEnabled']
    def offset = createdAfterFirewallEnabled ? 1 : -7
    TestItemInfoImpl itemInfo = new TestItemInfoImpl(repoPath, sha1, (new Date() + offset).time)
    TestFileInfoImpl fileInfo = new TestFileInfoImpl(repoPath, sha1)

    // setup the 'repositories' with an implementation that records property changes so we can inspect later
    String beginningQuarantineStatus = contract['artifact']['beginningQuarantineStatus']
    TestRepositories repositories = new TestRepositories()
        .withConfiguration(repoKey, repoConfig)
        .withItemInfo(repoPath, itemInfo)
        .withFileInfo(repoPath, fileInfo)

    if (isNotBlank(beginningQuarantineStatus)) {
      repositories.withProperty(repoPath, QUARANTINE, beginningQuarantineStatus)
      repositories.withProperty(repoPath, HASH, sha1)
    }

    FirewallRepositories firewallRepositories = new FirewallRepositories()

    MODE mode = null
    switch (firewallMode) {
      case 'audit':
        mode = MODE.audit
        firewallRepositories.add(repoKey, FirewallRepository.of(repoConfig, mode))
        break

      case 'quarantine':
        mode = MODE.quarantine
        firewallRepositories.add(repoKey, FirewallRepository.of(repoConfig, mode))
        break
    }

    // setup the ignore patterns per the contract
    def firewallIgnorePatterns = new FirewallIgnorePatterns()
    firewallIgnorePatterns.regexpsByRepositoryFormat = [:]
    if (contract['artifact']['ignoreExtension']) {
      firewallIgnorePatterns.regexpsByRepositoryFormat[format] = ['.*\\.' + extension]
    }
    else {
      firewallIgnorePatterns.regexpsByRepositoryFormat[format] = []
    }
    IgnorePatternMatcher ignorePatternMatcher = IgnorePatternMatcher.instance
    ignorePatternMatcher.ignorePatterns = firewallIgnorePatterns

    // setup the firewall configuration
    Properties properties = new Properties()
    properties.putAll([
        'firewall.iq.url'     : 'http://localhost:8072',
        'firewall.iq.username': 'admin',
        'firewall.iq.password': 'admin123'
    ])

    if (TestFirewallMode.disabled != firewallMode) {
      def repoConfigKey = 'firewall.repo.' + contract['repository']['repoKey']
      properties.put(repoConfigKey, firewallMode.toString())
    }
    def firewallProperties = FirewallProperties.load(properties, logger)

    // setup the IQ connection manager to return responses based on the contract
    boolean quarantinedByIq = contract['artifact']['quarantinedByIq']
    IqConnectionManager iqConnectionManager =
        Mock(constructorArgs: [firewallProperties, firewallRepositories, logger, 'test', '6.6.5', 'Pro'])
    iqConnectionManager.firewallIgnorePatterns >> firewallIgnorePatterns
    iqConnectionManager.tryInitializeConnection() >> true

    FirewallArtifactoryAsset asset = FirewallArtifactoryAsset.of(repoPath, sha1)

    iqConnectionManager.evaluateWithQuarantine(asset) >>
        TestHelper.createRepositoryComponentEvaluationDataList(quarantinedByIq)

    iqConnectionManager.evaluateWithAudit(asset) >>
        TestHelper.createRepositoryComponentEvaluationDataList(quarantinedByIq)

    iqConnectionManager.getPolicyEvaluationSummary(_) >> TestHelper.createRepositoryPolicyEvaluationSummary()

    // this pathfactory is used inside the storage manager to access the repo level properties
    PathFactory pathFactory = new TestPathFactory()

    // wrap the repositories in a storage manager that will allow us to inspect the activity of the plugin manager
    def storageManager = new StorageManager(repositories, pathFactory, firewallRepositories, logger)

    def iqQuarantineStatusLoader = new IqQuarantineStatusLoader(iqConnectionManager, storageManager, logger)

    def quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, logger)

    def loadingCache = quarantineStatusManagerProperties.cacheStrategy
        .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, logger, firewallProperties)

    def quarantineStatusManager = new QuarantineStatusManager(loadingCache, iqConnectionManager, firewallRepositories,
        pathFactory, logger)

    def quarantineStatusLoader = new IqQuarantineStatusLoader(iqConnectionManager, storageManager, logger)

    UnquarantinedComponentsUpdater unquarantinedComponentsUpdater = new UnquarantinedComponentsUpdater(
        iqConnectionManager, storageManager, quarantineStatusManager, pathFactory, firewallProperties, logger)

    RepositoryManager repositoryManager = new RepositoryManager(
        logger,
        repositories,
        firewallProperties,
        iqConnectionManager,
        storageManager,
        firewallRepositories)

    def searches = Mock(Searches)
    def pluginManager = new DefaultNexusFirewallForArtifactory(
        logger,
        firewallRepositories,
        iqConnectionManager,
        firewallProperties,
        quarantineStatusManagerProperties,
        ignorePatternMatcher,
        storageManager,
        repositoryManager,
        unquarantinedComponentsUpdater,
        pathFactory,
        searches, quarantineStatusLoader
    )

    return new ObservablePluginManager(repoKey, repoPath, pluginManager, storageManager, sha1, itemInfo, fileInfo)
  }
}
