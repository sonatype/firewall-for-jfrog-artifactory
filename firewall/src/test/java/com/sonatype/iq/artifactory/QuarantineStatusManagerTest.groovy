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

import com.sonatype.iq.artifactory.Commons.QuarantineStatus

import org.artifactory.repo.RepoPath
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.DENY

class QuarantineStatusManagerTest
    extends Specification
{

  final StorageManager storageManager = Mock()

  final IqConnectionManager iqConnectionManager = Mock()

  final FirewallProperties firewallProperties = Mock()

  final FirewallRepositories firewallRepositories = Mock()

  final FirewallRepository firewallRepository = Mock()

  final PathFactoryImpl pathFactory = Mock()

  final Logger log = Mock()

  final RepoPath repoPath = TestHelper.createStruts2RepoPath()

  QuarantineStatusManager quarantineStatusManager

  def setup() {
    firewallRepository.getRepoKey() >> repoPath.repoKey
    firewallRepositories.getEnabledFirewallRepoByKey(repoPath.repoKey) >> firewallRepository
    pathFactory.createRepoPath(repoPath.repoKey, repoPath.path) >> repoPath

    def iqQuarantineStatusLoader = new IqQuarantineStatusLoader(iqConnectionManager, storageManager, log)

    def quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, log)
    def loadingCache = quarantineStatusManagerProperties.cacheStrategy
        .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, log, firewallProperties)

    quarantineStatusManager = new QuarantineStatusManager(loadingCache, iqConnectionManager, firewallRepositories,
        pathFactory, log)

    iqConnectionManager.evaluateWithQuarantine(_) >> TestHelper.createRepositoryComponentEvaluationDataList(true)

  }

  def 'test cached assets'() {
    given:
      iqConnectionManager.evaluateWithQuarantine(_) >> TestHelper.createRepositoryComponentEvaluationDataList(true)

    when:
      QuarantineStatus asset = quarantineStatusManager.getQuarantineStatus(repoPath)

    then:
      1 * storageManager.getHash(repoPath) >> 'abcdefg'

    and:
      DENY == quarantineStatusManager.getQuarantineStatus(repoPath)

    when:
      QuarantineStatus cached = quarantineStatusManager.getQuarantineStatus(repoPath)

    then:
      cached == asset

    and:
      DENY == quarantineStatusManager.getQuarantineStatus(repoPath)
  }

  def 'test cached assets are only loaded once from iq server using memory cache'() {
    when: 'same repoPath is checked twice'
      QuarantineStatus initial = quarantineStatusManager.getQuarantineStatus(repoPath)
      QuarantineStatus cached = quarantineStatusManager.getQuarantineStatus(repoPath)

    then: 'exactly once call to IQ server is made'
      1 * storageManager.getHash(repoPath) >> 'abcdefg'
      1 * iqConnectionManager.evaluateWithQuarantine(_) >> TestHelper.createRepositoryComponentEvaluationDataList(true)
      cached == initial
  }

  @Unroll
  def 'test cached assets are only loaded once from iq server using attribute cache'() {
    given: 'use case: typical cache miss then hit'
      2 * storageManager.maybeGetQuarantineStatus(repoPath) >>> [null, status]

    when: 'same repoPath is checked twice and memory cache is wiped between calls'
      QuarantineStatus initial = quarantineStatusManager.getQuarantineStatus(repoPath)
      quarantineStatusManager.quarantineStatusCache.invalidateAll()
      QuarantineStatus cached = quarantineStatusManager.getQuarantineStatus(repoPath)

    then: 'exactly once call to IQ server is made'
      1 * storageManager.getHash(repoPath) >> 'abcdefg'
      1 * iqConnectionManager.evaluateWithQuarantine(_) >> TestHelper.createRepositoryComponentEvaluationDataList(status == DENY)

    and: 'correct value is returned'
      cached == initial
      cached == status

    and: 'status is cached in attribute storage exactly once'
      1 * storageManager."$storageMethodName"(repoPath)

    where:
      status | storageMethodName
      DENY   | 'quarantine'
      ALLOW  | 'unQuarantine'
  }

  @Unroll
  def 'cached assets are loaded from cache if present in attribute cache'() {
    given: 'use case: typical cache hit'
      1 * storageManager.maybeGetQuarantineStatus(repoPath) >>> [status]

    when: 'quarantine status is checked'
      QuarantineStatus actual = quarantineStatusManager.getQuarantineStatus(repoPath)

    then: 'no call to IQ server is made'
      0 * storageManager.getHash(_)
      0 * iqConnectionManager.evaluateWithQuarantine(_)

    and: 'correct value is returned'
      actual == expectedResponse

    and: 'attributes are not modified'
      0 * storageManager.quarantine(_)
      0 * storageManager.unQuarantine(_)

    where:
      status | expectedResponse
      DENY   | DENY
      ALLOW  | ALLOW
  }

  def 'unquarantined asset is updated in the asset cache'() {
    given: 'an asset'
      def asset = FirewallArtifactoryAsset.of(repoPath)

    when: 'A (DENY) asset exists in the cache'
      quarantineStatusManager.getQuarantineStatus(repoPath)

    then: 'the asset is present in the cache'
      quarantineStatusManager.quarantineStatusCache.size() == 1
      quarantineStatusManager.quarantineStatusCache.getIfPresent(asset) == DENY

    when: 'when the asset in unquarantined'
      quarantineStatusManager.unQuarantine(repoPath)

    then: 'the asset cache contains the now unquarantined asset'
      quarantineStatusManager.quarantineStatusCache.size() == 1
      quarantineStatusManager.quarantineStatusCache.getIfPresent(asset) == ALLOW

    and: 'the quarantine status has been deleted'
      1 * storageManager.deleteQuarantineStatus(repoPath)
  }

  def 'removes component from IQ and cache'() {
    given:
      def asset = FirewallArtifactoryAsset.of(repoPath)

    when: 'load asset into cache'
      quarantineStatusManager.getQuarantineStatus(repoPath)

    then: 'asset exists in cache'
      quarantineStatusManager.quarantineStatusCache.size() == 1
      quarantineStatusManager.quarantineStatusCache.getIfPresent(asset) == DENY

    when: 'Asset is removed'
      quarantineStatusManager.removeIqComponent(repoPath)

    then: 'IQ is notified of removal'
      1 * iqConnectionManager.removeComponent({ it.repoPath == repoPath })

    and: 'attribute in storage is reset'
      1 * storageManager.deleteQuarantineStatus(repoPath)

    and: 'asset is removed from cache'
      quarantineStatusManager.quarantineStatusCache.size() == 0
      quarantineStatusManager.quarantineStatusCache.getIfPresent(asset) == null
  }
}
