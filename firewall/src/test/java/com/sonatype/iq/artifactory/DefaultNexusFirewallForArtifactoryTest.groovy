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
import com.sonatype.iq.artifactory.Commons.QuarantineStatus

import groovy.util.logging.Slf4j
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.search.Searches
import spock.lang.Shared
import spock.lang.Unroll

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.DENY
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.audit
import static com.sonatype.iq.artifactory.TestHelper.createFirewallRepository

@Slf4j(value = 'logger')
class DefaultNexusFirewallForArtifactoryTest
    extends NexusFirewallForArtifactoryTestContainer
{

  @Shared
  FirewallProperties firewallProperties

  @Shared
  QuarantineStatusManagerProperties quarantineStatusManagerProperties

  PathFactory pathFactory = Mock(PathFactory)

  FirewallRepositories firewallRepositories = Mock(FirewallRepositories)

  FirewallRepository firewallRepository = Mock()

  IqConnectionManager iqConnectionManager =
      Mock(constructorArgs: [firewallProperties, firewallRepositories, logger, 'it-test', '6.6.5', 'Pro'])

  StorageManager storageManager = Mock(StorageManager, constructorArgs: [Mock(Repositories), Mock(PathFactory),
                                                                         firewallRepositories, logger])

  def loadingCache = quarantineStatusManagerProperties.cacheStrategy.buildCache(storageManager, iqQuarantineStatusLoader,
      pathFactory, logger, firewallProperties)

  QuarantineStatusManager quarantineStatusManager =
      Mock(QuarantineStatusManager, constructorArgs: [loadingCache, iqConnectionManager, firewallRepositories, pathFactory, logger])

  UnquarantinedComponentsUpdater unquarantinedComponentsUpdater = new UnquarantinedComponentsUpdater(
      iqConnectionManager, storageManager, quarantineStatusManager, pathFactory, firewallProperties, logger)

  RepositoryManager repositoryManager = Mock(
      constructorArgs: [logger, Mock(Repositories), firewallProperties, iqConnectionManager, storageManager,
                        firewallRepositories])

  IgnorePatternMatcher ignorePatternMatcher = IgnorePatternMatcher.instance

  RepositoryConfiguration repositoryConfiguration = Mock(RepositoryConfiguration)

  Searches searches = Mock()

  NexusFirewallForArtifactory nexusFirewallForArtifactory

  @Shared
  TestRepoPath testRepoPath = new TestRepoPath(repoKey: 'main-java-libs', folder: true, id: 'main-java-libs')

  private IqQuarantineStatusLoader iqQuarantineStatusLoader = new IqQuarantineStatusLoader(iqConnectionManager,
      storageManager, logger)

  def 'setupSpec'() {
    Properties properties = new Properties()
    properties.putAll([
        'firewall.iq.url'              : 'http://localhost:8072',
        'firewall.iq.username'         : 'admin',
        'firewall.iq.password'         : 'admin123',
        'firewall.repo.main-java-libs' : 'quarantine',
        'firewall.repo.other-java-libs': 'audit',
        'firewall.repo.disabled-repo'  : 'disabled',
        'firewall.verify.wait.in.millis' : 1
    ])
    firewallProperties = FirewallProperties.load(properties, logger)
    quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, logger)
  }

  def setup() {
    def firewallIgnorePatterns = new FirewallIgnorePatterns()
    firewallIgnorePatterns.regexpsByRepositoryFormat = ['maven2': ['.*xml']]
    ignorePatternMatcher.ignorePatterns = firewallIgnorePatterns
    repositoryConfiguration.packageType >> 'maven2'

    nexusFirewallForArtifactory = new DefaultNexusFirewallForArtifactory(logger, firewallRepositories,
        iqConnectionManager,
        firewallProperties,
        quarantineStatusManagerProperties,
        ignorePatternMatcher, storageManager, repositoryManager,
        unquarantinedComponentsUpdater,
        pathFactory, searches,
        iqQuarantineStatusLoader)
    nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.invalidateAll()

    FirewallRepository mainJavaLibs = createFirewallRepository('main-java-libs')
    FirewallRepository otherJavaLibs = createFirewallRepository('other-java-libs', audit)
    firewallRepositories.getEnabledFirewallRepoByKey('main-java-libs') >> mainJavaLibs
    firewallRepositories.getEnabledFirewallRepoByKey('other-java-libs') >> otherJavaLibs
    firewallRepositories.getEnabledFirewallRepoByKey('disabled-repo') >> null
    firewallRepositories.getEnabledFirewallRepoByKey(_) >> firewallRepository
    repositoryManager.isFirewallEnabledForRepo('main-java-libs') >> true
    repositoryManager.isQuarantineEnabledForRepo('main-java-libs') >> true
    repositoryManager.isFirewallEnabledForRepo('other-java-libs') >> true

    iqConnectionManager.tryInitializeConnection() >> true
  }

  def 'test plugin initialization'() {
    when: 'the plugin is initialized in the startup script'
      nexusFirewallForArtifactory.init()

    then: 'we load the properties configuring the plugin'
      1 * repositoryManager.loadRepositoriesFromProperties()

    and: 'we attempt to connect to IQ server and update firewall state appropriately'
      1 * iqConnectionManager.tryInitializeConnection() >> true
      1 * iqConnectionManager.getFirewallIgnorePatterns()
      1 * repositoryManager.enableRepositoriesInIq()

    and: 'the plugin is not ready yet to evaluate component requests'
      nexusFirewallForArtifactory.initializationVerified.count == 2
  }

  def 'test repository initialization runs once and only once'() {
    when: 'we verify the first time'
      nexusFirewallForArtifactory.isReady()

    then: 'we trigger an initialization of repository data'
      1 * repositoryManager.initialize()

    when: 'we try to verify again'
      nexusFirewallForArtifactory.isReady()

    then: 'we do no work'
      0 * _
  }

  def 'test download uses quarantine cache'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath('main-java-libs', repoPath.path) >> repoPath

    when: 'on initial download, a call is made to IQ to get the quarantine status'
      QuarantineStatus quarantineStatusFromIQ = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'the asset along with its quarantine status from IQ is loaded into the cache'
      quarantineStatusFromIQ == DENY
      1 * iqConnectionManager.evaluateWithQuarantine(_) >>
          TestHelper.createRepositoryComponentEvaluationDataList(true)

    when: 'when downloaded again'
      QuarantineStatus quarantineStatusFromCache = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'the quarantine status is returned from the cache and the getQuarantineStatusFromIQ function is not called'
      quarantineStatusFromCache == DENY
      0 * storageManager.getHash(_)
      0 * iqConnectionManager.evaluateWithQuarantine(_)
  }

  def 'denied status is not cached when caused by temporary IQ connection errors'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath

    when: 'on initial download, a call is made to IQ to get the quarantine status'
      QuarantineStatus quarantineStatusFromIQ = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'the asset along with its quarantine status from IQ is not loaded into the cache'
      quarantineStatusFromIQ == DENY
      !nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.asMap().get(repoPath.id)
      1 * iqConnectionManager.evaluateWithQuarantine(_) >> { throw new IqConnectionException('BANG') }
  }

  def 'access is denied for repository in quarantine mode when there are IQ connection errors'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath

    when: 'on initial download, a call is made to IQ to get the quarantine status'
      QuarantineStatus quarantineStatusFromIQ = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'the asset is denied'
      1 * iqConnectionManager.evaluateWithQuarantine(_) >> { throw new IqConnectionException('Cannot connect to IQ') }
      quarantineStatusFromIQ == DENY
  }

  def 'access is allowed for repository in quarantine mode when there are IQ connection errors'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('other-java-libs', 'jar')
      def hash = 'abcdefg123'
      ItemInfo itemInfo = TestHelper.getTestItemInfo(repoPath, hash, (new Date() - 7).time)
      storageManager.getItemInfo(repoPath) >> itemInfo
      storageManager.getHash(repoPath) >> hash

    when: 'on initial download, a call is made to IQ to get the quarantine status'
      QuarantineStatus quarantineStatusFromIQ = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'the asset is allowed'
      1 * iqConnectionManager.evaluateWithAudit(_) >> { throw new IqConnectionException('Cannot connect to IQ') }
      quarantineStatusFromIQ == ALLOW
  }

  def 'test download updates quarantine status from metadata when cache item expired'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath
      nexusFirewallForArtifactory = getNexusFirewallForArtifactory(testRepoPath, repoPath)

    when: 'a download is processed (cache miss)'
      def quarantineStatus = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'storage manager is queried and result is cached in quarantineStatusManager'
      quarantineStatus == DENY
      1 * storageManager.maybeGetQuarantineStatus(repoPath) >> DENY
      nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.size() == 1

    when: 'a download is processed (cache hit)'
      def quarantineStatusCacheHit = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'storageManager is not queried'
      0 * storageManager.maybeGetQuarantineStatus(_)
      quarantineStatusCacheHit == DENY

    when: 'cache is invalidated and another download is processed'
      nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.invalidateAll()

    then: 'cache is empty'
      nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.size() == 0

    when: 'another download is processed (cache miss)'
      def quarantineStatusEmptyCaches = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'storage manager is queried again and result is cached in quarantineStatusManager'
      quarantineStatusEmptyCaches == DENY
      1 * storageManager.maybeGetQuarantineStatus(repoPath) >> DENY
      nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.size() == 1
  }

  def 'test storageEvent for IQ exception'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath
      iqConnectionManager.evaluateWithQuarantine(_) >> { throw new Exception("Cannot connect") }
      nexusFirewallForArtifactory.initializationVerified.countDown() //assume init thread started
      nexusFirewallForArtifactory.initializationVerified.countDown() //assume init thread completed

    when:
      nexusFirewallForArtifactory.beforeDownloadHandler(repoPath)

    then:
      CancelException cancelException = thrown()
      cancelException.message.contains('cancelled due to quarantine')
  }

  def 'test checkQuarantineStatus returns DENY when asset is quarantined'() {
    given:
      RepoPath root = TestHelper.createRootRepo()
      RepoPath repoPath = TestHelper.createAsset(root)
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath
      nexusFirewallForArtifactory = getNexusFirewallForArtifactory(testRepoPath, repoPath)

    when:
      QuarantineStatus status = nexusFirewallForArtifactory.checkQuarantineStatus(repoPath)

    then:
      status == DENY
      storageManager.maybeGetQuarantineStatus(repoPath) >> DENY
  }

  def 'test repo marked as mode disabled allows components to be downloaded'() {
    given:
      RepoPath repoPath = TestHelper.createStruts2RepoPath('disabled-repo', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath
      nexusFirewallForArtifactory = getNexusFirewallForArtifactory(testRepoPath, repoPath)

    when:
      def quarantineStatus = nexusFirewallForArtifactory.handleDownload(repoPath)

    then: 'artifacts in the repository are allowed'
      quarantineStatus == ALLOW

    and: 'previous firewall quarantine state is not considered'
      0 * quarantineStatusManager.getQuarantineStatus(repoPath)

    and: 'evaluate with audit is not called'
      0 * iqConnectionManager.evaluateWithAudit(*_)
  }

  def 'test checkQuarantineStatus does not cancel when quarantine status is allow'() {
    given:
      RepoPath root = TestHelper.createRootRepo()
      RepoPath repoPath = TestHelper.createAsset(root)
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath

      FirewallArtifactoryAsset asset = FirewallArtifactoryAsset.of(repoPath)
      nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.asMap().put(asset, ALLOW)

    when:
      nexusFirewallForArtifactory.checkQuarantineStatus(repoPath)

    then:
      notThrown CancelException
  }

  def 'test firewall is not enabled for repository'() {
    given:
      NexusFirewallForArtifactory nexusFirewallForArtifactorySpy =
          Spy(DefaultNexusFirewallForArtifactory,
              constructorArgs: [logger, firewallRepositories, iqConnectionManager, firewallProperties,
                                quarantineStatusManagerProperties, ignorePatternMatcher, storageManager,
                                repositoryManager, unquarantinedComponentsUpdater, pathFactory, searches,
                                iqQuarantineStatusLoader])

      RepoPath struts2 = TestHelper.createStruts2RepoPath('main-java-libs')

    when:
      QuarantineStatus result = nexusFirewallForArtifactorySpy.handleDownload(struts2)

    then:
      result == ALLOW
      // verify no interactions with IQ
      0 * iqConnectionManager._
      1 * firewallRepositories.getEnabledFirewallRepoByKey('main-java-libs') >> null
      // TODO should we verify internals here? A bit smelly. Would like to refactor into predicates and get rid of Spy.
      0 * nexusFirewallForArtifactorySpy.isAssetAuditable(_, struts2)
      0 * nexusFirewallForArtifactorySpy.isAssetQuarantinable(_, _)
  }

  def 'test firewall is enabled for repo but asset is not auditable'() {
    given:
      NexusFirewallForArtifactory nexusFirewallForArtifactorySpy =
          Spy(DefaultNexusFirewallForArtifactory,
              constructorArgs: [logger, firewallRepositories, iqConnectionManager, firewallProperties,
                                quarantineStatusManagerProperties, ignorePatternMatcher, storageManager,
                                repositoryManager, unquarantinedComponentsUpdater, pathFactory, searches,
                                iqQuarantineStatusLoader])

      RepoPath struts2 = TestHelper.createStruts2RepoPath('main-java-libs', 'xml')

    when:
      QuarantineStatus status = nexusFirewallForArtifactorySpy.handleDownload(struts2)

    then:
      status == ALLOW
      // verify no interactions with IQ
      0 * iqConnectionManager._
      // TODO should we verify internals here? A bit smelly. Would like to refactor into predicates and get rid of Spy.
      1 * nexusFirewallForArtifactorySpy.isAssetAuditable(*_)
      0 * nexusFirewallForArtifactorySpy.isAssetQuarantinable(_, _)
  }

  def 'test asset is quarantinable'() {
    given:
      NexusFirewallForArtifactory nexusFirewallForArtifactorySpy =
          Spy(DefaultNexusFirewallForArtifactory,
              constructorArgs: [logger, firewallRepositories, iqConnectionManager, firewallProperties,
                                quarantineStatusManagerProperties, ignorePatternMatcher, storageManager,
                                repositoryManager, unquarantinedComponentsUpdater, pathFactory, searches,
                                iqQuarantineStatusLoader])

      RepoPath struts2 = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> struts2.repoKey
      pathFactory.createRepoPath(struts2.repoKey, struts2.path) >> struts2

      FirewallArtifactoryAsset asset = FirewallArtifactoryAsset.of(struts2, 'abcdefg')
      nexusFirewallForArtifactorySpy.quarantineStatusManager.quarantineStatusCache.asMap().put(asset, asset)

    when:
      QuarantineStatus status = nexusFirewallForArtifactorySpy.handleDownload(struts2)

    then:
      status == DENY
      // verify no interactions with IQ
      0 * iqConnectionManager._
      // TODO should we verify internals here? A bit smelly. Would like to refactor into predicates and get rid of Spy.
      1 * nexusFirewallForArtifactorySpy.isAssetAuditable(_, _)
      1 * nexusFirewallForArtifactorySpy.isAssetQuarantinable(_, _)

      1 * nexusFirewallForArtifactorySpy.checkQuarantineStatus(_)
      0 * nexusFirewallForArtifactorySpy.updateAuditStatusIfNeeded(_)
  }

  def 'test asset is auditable'() {
    given:
      def hash = 'abcdefg123'

      RepoPath struts2 = TestHelper.createStruts2RepoPath('other-java-libs', 'jar')
      ItemInfo itemInfo = TestHelper.getTestItemInfo(struts2, hash, (new Date() - 7).time)
      storageManager.getItemInfo(struts2) >> itemInfo
      storageManager.getHash(struts2) >> hash

      NexusFirewallForArtifactory nexusFirewallForArtifactorySpy =
          Spy(DefaultNexusFirewallForArtifactory,
              constructorArgs: [logger, firewallRepositories, iqConnectionManager, firewallProperties,
                                quarantineStatusManagerProperties, ignorePatternMatcher, storageManager,
                                repositoryManager, unquarantinedComponentsUpdater, pathFactory, searches,
                                iqQuarantineStatusLoader])

    when:
      QuarantineStatus status = nexusFirewallForArtifactorySpy.handleDownload(struts2)

    then:
      status == ALLOW
      1 * iqConnectionManager.evaluateWithAudit(_) >> { params ->
        FirewallArtifactoryAsset asset = params[0]
        assert asset.repoKey == 'other-java-libs'
        //assert asset., 'maven2',
        assert asset.path == 'org/apache/struts/struts2-core/2.5.17/struts2-core-2.5.17.jar'
        assert asset.hash == hash
      }
  }

  def 'when loading ignorePatterns while IQ is unreachable then the old patterns remain active'() {
    given:
      def oldPatterns = nexusFirewallForArtifactory.ignorePatternMatcher.ignorePatterns
      iqConnectionManager.firewallIgnorePatterns >> { throw new IqConnectionException('booom', new IOException()) }

    when:
      nexusFirewallForArtifactory.loadIgnorePatterns()

    then:
      oldPatterns.is(nexusFirewallForArtifactory.ignorePatternMatcher.ignorePatterns)
  }

  NexusFirewallForArtifactory getNexusFirewallForArtifactory(final RepoPath repo, final RepoPath repoPath) {
    nexusFirewallForArtifactory = new DefaultNexusFirewallForArtifactory(logger, firewallRepositories,
        iqConnectionManager, firewallProperties, quarantineStatusManagerProperties, ignorePatternMatcher,
        storageManager, repositoryManager,
        unquarantinedComponentsUpdater, pathFactory, searches, iqQuarantineStatusLoader)
    nexusFirewallForArtifactory.quarantineStatusManager.quarantineStatusCache.invalidateAll()
    return nexusFirewallForArtifactory
  }

  PathFactory getMockPathFactory(final RepoPath repo, final RepoPath repoPath) {
    PathFactory pathFactory = Mock(PathFactory)
    pathFactory.createRepoPath(repo.repoKey) >> repo
    pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath
    return pathFactory
  }

  @Unroll
  def 'test quarantine manager receives delete notifications for repo #repoKey'() {
    given: 'enabled repo'
      RepoPath repoPath = TestHelper.createStruts2RepoPath(repoKey, 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath(repoKey, repoPath.path) >> repoPath
      nexusFirewallForArtifactory = getNexusFirewallForArtifactory(testRepoPath, repoPath)

    when: 'delete handler is called'
      nexusFirewallForArtifactory.handleDelete(repoPath)

    then: 'IQ remove notification is created as expected'
      expectedInvocations * iqConnectionManager.removeComponent( { it.repoPath == repoPath } )

    where:
      repoKey          | expectedInvocations
      'main-java-libs' | 1
      'disabled-repo'  | 0
  }

  def 'test quarantine manager does not notify IQ for delete events of folders'() {
    given: 'enabled repo'
      RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath('main-java-libs', repoPath.path) >> repoPath
      nexusFirewallForArtifactory = getNexusFirewallForArtifactory(testRepoPath, repoPath)

    when: 'delete handler is called on folder'
      nexusFirewallForArtifactory.handleDelete(repoPath.parent)

    then: 'IQ remove notification is created as expected'
      0 * iqConnectionManager.removeComponent(_)
  }

  @Unroll
  def 'contract #id : [mode = #firewallMode, ext = #ignoreExtension, iq = #quarantinedByIq, after = #afterEnabled, status = #beginningStatus]'() {
    setup:
      def contract = [
          'repository': [
              'repoKey'     : 'test-central',
              'type'        : 'remote',
              'packageType' : 'maven2',
              'firewallMode': firewallMode
          ],
          'artifact'  : [
              'path'                       : 'org.apache.tomcat:tomcat-embed-core:8.5.6',
              'extension'                  : 'jar',
              'sha1'                       : 'sha1c0c0babe',
              'ignoreExtension'            : ignoreExtension,
              'beginningQuarantineStatus'  : beginningStatus,
              'createdAfterFirewallEnabled': afterEnabled,
              'quarantinedByIq'            : quarantinedByIq
          ]
      ]

    when: 'creating plugin manager'
      def observablePluginManager = getObservablePluginManager(logger, contract)

    then: 'before firewall init'
      observablePluginManager.isFirewallEnabled() == firewallEnabled
      observablePluginManager.isMarkedQuarantined() == (DENY == beginningStatus)
      observablePluginManager.isMarkedAllowed() == (ALLOW == beginningStatus)
      !observablePluginManager.hasFirewallEnabledTimestamp()
      !observablePluginManager.hasVerifyInitRun()

    when: 'initializing plugin manager'
      observablePluginManager.init()

    then: 'after firewall init'
      !observablePluginManager.hasException()
      observablePluginManager.isFirewallEnabled() == firewallEnabled
      observablePluginManager.isMarkedQuarantined() == (DENY == beginningStatus)
      observablePluginManager.isMarkedAllowed() == (ALLOW == beginningStatus)
      !observablePluginManager.hasFirewallEnabledTimestamp()

    when: 'verifying the initialization of the plugin'
      observablePluginManager.initializationVerified.countDown() //assume init thread started
      observablePluginManager.verifyInit()

    then:
      observablePluginManager.hasVerifyInitRun()

    when:
      observablePluginManager.onBeforeDownload()

    then:
      !observablePluginManager.hasException()
      observablePluginManager.wasCancelled() == expectQuarantine
      observablePluginManager.isMarkedQuarantined() == markedDeny
      observablePluginManager.isMarkedAllowed() == markedAllow

    where:
      /*
        Test scenario notes:
          A - artifact marked DENY before we've processed it is still marked DENY afterwards when repo is not in quarantine mode
          B - artifact previously marked DENY but artifact created date is before quarantine enabled date
          C - artifact previously not marked ALLOW but didn't evaluate to quarantine either (if we don't IQ eval we don't update status)
       */
      id | firewallMode | ignoreExtension | quarantinedByIq | afterEnabled | beginningStatus || firewallEnabled | expectQuarantine | markedDeny | markedAllow | notes
      // id |  mode      | ext   |  iq   | after | STATUS || fwEn  | expQ  | deny  | allow | notes
      101 | 'quarantine' | false | true  | true  | DENY   || true  | true  | true  | false | ''
      102 | 'quarantine' | false | true  | true  | ALLOW  || true  | false | false | true  | ''
      103 | 'quarantine' | false | true  | true  | ''     || true  | true  | true  | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      104 | 'quarantine' | false | true  | false | DENY   || true  | false | true  | false | 'B,C'
      105 | 'quarantine' | false | true  | false | ALLOW  || true  | false | false | true  | ''
      106 | 'quarantine' | false | true  | false | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  IQ   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      107 | 'quarantine' | false | false | true  | DENY   || true  | true  | true  | false | ''
      108 | 'quarantine' | false | false | true  | ALLOW  || true  | false | false | true  | ''
      109 | 'quarantine' | false | false | true  | ''     || true  | false | false | true  | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      110 | 'quarantine' | false | false | false | DENY   || true  | false | true  | false | 'B,C'
      111 | 'quarantine' | false | false | false | ALLOW  || true  | false | false | true  | ''
      112 | 'quarantine' | false | false | false | ''     || true  | false | false | false | 'C'

      // QUARANTINE + EXTENSION IGNORED
      // id |  mode      | EXT   |  iq   | after | status || fwEn  | expQ  | deny  | allow | notes
      151 | 'quarantine' | true  | true  | true  | DENY   || true  | false | true  | false | ''
      152 | 'quarantine' | true  | true  | true  | ALLOW  || true  | false | false | true  | ''
      153 | 'quarantine' | true  | true  | true  | ''     || true  | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      154 | 'quarantine' | true  | true  | false | DENY   || true  | false | true  | false | 'B,C'
      155 | 'quarantine' | true  | true  | false | ALLOW  || true  | false | false | true  | ''
      156 | 'quarantine' | true  | true  | false | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  IQ   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      157 | 'quarantine' | true  | false | true  | DENY   || true  | false | true  | false | ''
      158 | 'quarantine' | true  | false | true  | ALLOW  || true  | false | false | true  | ''
      159 | 'quarantine' | true  | false | true  | ''     || true  | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      160 | 'quarantine' | true  | false | false | DENY   || true  | false | true  | false | 'B,C'
      161 | 'quarantine' | true  | false | false | ALLOW  || true  | false | false | true  | ''
      162 | 'quarantine' | true  | false | false | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  iq   | after | status || fwEn  | expQ  | deny  | allow | notes
      201 | 'audit'      | false | true  | true  | DENY   || true  | false | true  | false | 'A,C'
      202 | 'audit'      | false | true  | true  | ALLOW  || true  | false | false | true  | ''
      203 | 'audit'      | false | true  | true  | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      204 | 'audit'      | false | true  | false | DENY   || true  | false | true  | false | 'B,C'
      205 | 'audit'      | false | true  | false | ALLOW  || true  | false | false | true  | ''
      206 | 'audit'      | false | true  | false | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  IQ   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      207 | 'audit'      | false | false | true  | DENY   || true  | false | true  | false | ''
      208 | 'audit'      | false | false | true  | ALLOW  || true  | false | false | true  | ''
      209 | 'audit'      | false | false | true  | ''     || true  | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      210 | 'audit'      | false | false | false | DENY   || true  | false | true  | false | 'B,C'
      211 | 'audit'      | false | false | false | ALLOW  || true  | false | false | true  | ''
      212 | 'audit'      | false | false | false | ''     || true  | false | false | false | 'C'

      // AUDIT + EXTENSION IGNORED
      // id |  mode      | EXT   |  iq   | after | status || fwEn  | expQ  | deny  | allow | notes
      251 | 'audit'      | true  | true  | true  | DENY   || true  | false | true  | false | ''
      252 | 'audit'      | true  | true  | true  | ALLOW  || true  | false | false | true  | ''
      253 | 'audit'      | true  | true  | true  | ''     || true  | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      254 | 'audit'      | true  | true  | false | DENY   || true  | false | true  | false | 'B,C'
      255 | 'audit'      | true  | true  | false | ALLOW  || true  | false | false | true  | ''
      256 | 'audit'      | true  | true  | false | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  IQ   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      257 | 'audit'      | true  | false | true  | DENY   || true  | false | true  | false | ''
      258 | 'audit'      | true  | false | true  | ALLOW  || true  | false | false | true  | ''
      259 | 'audit'      | true  | false | true  | ''     || true  | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      260 | 'audit'      | true  | false | false | DENY   || true  | false | true  | false | 'B,C'
      261 | 'audit'      | true  | false | false | ALLOW  || true  | false | false | true  | ''
      262 | 'audit'      | true  | false | false | ''     || true  | false | false | false | 'C'

      // id |  mode      | ext   |  iq   | after | status || fwEn  | expQ  | deny  | allow | notes
      301 | ''           | false | true  | true  | DENY   || false | false | true  | false | 'A,C'
      302 | ''           | false | true  | true  | ALLOW  || false | false | false | true  | ''
      303 | ''           | false | true  | true  | ''     || false | false | false | false | 'C'

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      304 | ''           | false | true  | false | DENY   || false | false | true  | false | 'B,C'
      305 | ''           | false | true  | false | ALLOW  || false | false | false | true  | ''
      306 | ''           | false | true  | false | ''     || false | false | false | false | 'C'

      // id |  mode      | ext   |  IQ   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      307 | ''           | false | false | true  | DENY   || false | false | true  | false | ''
      308 | ''           | false | false | true  | ALLOW  || false | false | false | true  | ''
      309 | ''           | false | false | true  | ''     || false | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      310 | ''           | false | false | false | DENY   || false | false | true  | false | 'B,C'
      311 | ''           | false | false | false | ALLOW  || false | false | false | true  | ''
      312 | ''           | false | false | false | ''     || false | false | false | false | 'C'

      // AUDIT + EXTENSION IGNORED
      // id |  mode      | EXT   |  iq   | after | status || fwEn  | expQ  | deny  | allow | notes
      351 | ''           | true  | true  | true  | DENY   || false | false | true  | false | ''
      352 | ''           | true  | true  | true  | ALLOW  || false | false | false | true  | ''
      353 | ''           | true  | true  | true  | ''     || false | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      354 | ''           | true  | true  | false | DENY   || false | false | true  | false | 'B,C'
      355 | ''           | true  | true  | false | ALLOW  || false | false | false | true  | ''
      356 | ''           | true  | true  | false | ''     || false | false | false | false | 'C'

      // id |  mode      | ext   |  IQ   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      357 | ''           | true  | false | true  | DENY   || false | false | true  | false | ''
      358 | ''           | true  | false | true  | ALLOW  || false | false | false | true  | ''
      359 | ''           | true  | false | true  | ''     || false | false | false | false | ''

      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      360 | ''           | true  | false | false | DENY   || false | false | true  | false | 'B,C'
      361 | ''           | true  | false | false | ALLOW  || false | false | false | true  | ''
      362 | ''           | true  | false | false | ''     || false | false | false | false | 'C'

      // DISABLED
      // id |  mode      | ext   |  iq   | AFTER | status || fwEn  | expQ  | deny  | allow | notes
      401 | 'disabled'   | false | true  | true  | ALLOW  || false | false | false | true  | ''
  }

  def "perform a full audit of repositories"() {

    given: 'A state where an audit pass has not been accomplished'
      assert nexusFirewallForArtifactory.auditPassAccomplished == false
      nexusFirewallForArtifactory.initializationVerified.countDown()
      nexusFirewallForArtifactory.initializationVerified.countDown() //assume verifyInit already called

    when: 'initially calling into auditRepositories'
      nexusFirewallForArtifactory.auditRepositories()

    then: 'we request an audit'
      1 * firewallRepositories.reposToAudit() >> []

    when: 'a full audit has happened and we try to audit repos again'
      nexusFirewallForArtifactory.auditRepositories()

    then: 'no interactions happen with collaborators'
      0 * _

    and: 'the marker variable is set'
      nexusFirewallForArtifactory.auditPassAccomplished == true
  }

  @Unroll
  def "confirm that downloads will be blocked until initialization is verified"() {
    when: 'a download is attempted before verifying initialization'
      TestRepoPath repoPath = TestRepoPath.createFileInstance('foo', 'bar')
      nexusFirewallForArtifactory.initializationVerified.countDown() // assume init job not completed
      nexusFirewallForArtifactory."${handlerName}"(repoPath)

    then: 'CancelException is thrown'
      CancelException thrown = thrown()
      thrown.errorCode == 500
      thrown.message == "Invoked ${handlerName} before initialisation."

    when:
      nexusFirewallForArtifactory.verifyInit()
      nexusFirewallForArtifactory."${handlerName}"(repoPath)

    then:
      1 * repositoryManager.initialize()
      firewallRepository.getRepoKey() >> repoPath.repoKey
      pathFactory.createRepoPath('foo', repoPath.path) >> repoPath
      1 * iqConnectionManager."${connectionManagerMethod}"(_) >> connectionManagerReturns

    where:
      handlerName             | connectionManagerMethod  | connectionManagerReturns
      'beforeDownloadHandler' | 'evaluateWithQuarantine' | TestHelper.createRepositoryComponentEvaluationDataList(false)
      'afterDeleteHandler'    | 'removeComponent'        | _
  }

  def "audit pass will be started after initialization"() {
    given: 'an audit pass is attempted before verifying initialization'
      nexusFirewallForArtifactory.initializationVerified.countDown() // assume init job not completed

    when: 'an audit pass is attempted after verifhing initialization'
      nexusFirewallForArtifactory.verifyInit()

    then: 'the audit pass is triggered'
      1 * repositoryManager.initialize()
      1 * firewallRepositories.reposToAudit() >> []

    when: 'verify init is called again'
      nexusFirewallForArtifactory.verifyInit()

    then: 'CancelException is thrown'
      CancelException thrown = thrown()
      thrown.message == 'Initialisation already ran since startup. initializationVerified has invalid state 0'
      thrown.errorCode == 500

    and: "no interaction with Mocks"
      0 * _
  }
}
