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
package com.sonatype.iq.artifactory.audit

import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallProperties.MODE
import com.sonatype.iq.artifactory.FirewallRepositories
import com.sonatype.iq.artifactory.FirewallRepository
import com.sonatype.iq.artifactory.IgnorePatternMatcher
import com.sonatype.iq.artifactory.IqConnectionManager
import com.sonatype.iq.artifactory.PathFactory
import com.sonatype.iq.artifactory.StorageManager

import com.google.common.collect.Iterators
import org.artifactory.repo.RepoPath
import org.artifactory.search.Searches
import org.artifactory.search.aql.AqlResult
import org.artifactory.search.aql.AqlResultHandler
import spock.lang.Specification
import spock.lang.Subject

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static org.awaitility.Awaitility.await


class AuditManagerTest
    extends Specification
{
  IqConnectionManager iqConnectionManager = Mock()

  StorageManager storageManager = Mock()

  IgnorePatternMatcher ignorePatternMatcher = Mock()

  Searches searches = Mock()

  PathFactory pathFactory = Mock()

  RepoPath repoPath = Mock()

  @Subject
  AuditManager auditManager = new AuditManager(iqConnectionManager, storageManager, ignorePatternMatcher, searches,
      pathFactory)

  def 'audit with no configured firewall repositories'() {
    given: 'no configured firewall repositories'
      FirewallRepositories firewallRepositories = new FirewallRepositories()

    when: 'we trigger an audit'
      auditManager.audit(firewallRepositories)

    then: 'we do not interact with any collaborators'
      0 * _
  }

  def 'audit with no auditable assets in repo'() {
    given: 'a single repository with no auditable assets'
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      FirewallRepository firewallRepository = new FirewallRepository(repoKey: 'foo', mode: MODE.audit, type: 'remote', format: 'bar')
      firewallRepositories.add('foo', firewallRepository)
      AqlResult aqlResult = Mock()
      AqlResultHandler handler

    when: 'we trigger an audit'
      auditManager.audit(firewallRepositories)

    then: 
      1 * searches.aql(_, _) >> { arguments -> handler = arguments[1] }
      1 * storageManager.setRepositoryAudited(firewallRepository, _)

    when: 'the search is executed'
      handler.handle(aqlResult)
    
    then: 'we do not interact with any other collaborators'
      aqlResult.iterator() >> Iterators.emptyIterator()
      0 * _
  }

  def 'audit with auditable assets in repo'() {
    given: 'a single repository with no auditable assets'
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      FirewallRepository firewallRepository = new FirewallRepository(repoKey: 'foo', mode: MODE.audit, type: 'remote', format: 'bar')
      firewallRepositories.add(firewallRepository.repoKey, firewallRepository)
      List<LinkedHashMap<String, String>> data = createDataset()
      AqlResult aqlResult = Mock()
      AqlResultHandler handler
      Collection<FirewallArtifactoryAsset> assets

    when: 'we trigger an audit'
      auditManager.audit(firewallRepositories)

    then:
      1 * searches.aql(_, _) >> { arguments -> handler = arguments[1] }

    when: 'the search is executed'
      handler.handle(aqlResult)
      await().atMost(500, MILLISECONDS).until({ !auditManager.isAuditInProgress() })

    then: 'we send a single request to IQ until we have more than 100 assets'
      aqlResult.iterator() >> data.iterator()
      1 * pathFactory.createRepoPath('foo', 'bar1/baz1') >> repoPath
      1 * ignorePatternMatcher.isIgnored('bar', repoPath) >> false
      1 * iqConnectionManager.evaluateWithAudit(firewallRepository, _) >> { arguments -> assets = arguments[1] }
      1 * storageManager.assignAuditTimestamp(repoPath)
      1 * storageManager.setRepositoryAudited(firewallRepository, _)
      0 * _
      assets.size() == 1
      assets[0].repoPath == repoPath
      assets[0].hash == 'hash1'
  }

  def 'audit with more than the set limit of 100 auditable assets per request'() {
    given: 'a single repository with no auditable assets'
      FirewallRepositories firewallRepositories = new FirewallRepositories()
      FirewallRepository firewallRepository = new FirewallRepository(repoKey: 'foo', mode: MODE.audit, type: 'remote',
          format: 'bar')
      firewallRepositories.add(firewallRepository.repoKey, firewallRepository)
      int searchResultSize = AuditManager.BATCH_LIMIT + 1
      List<LinkedHashMap<String, String>> data = createDataset(searchResultSize)
      AqlResult aqlResult = Mock()
      AqlResultHandler handler
      def assets = []

    when: 'we trigger an audit'
      auditManager.audit(firewallRepositories)

    then:
      1 * searches.aql(_, _) >> { arguments -> handler = arguments[1] }

    when: 'the search is executed'
      handler.handle(aqlResult)
      await().atMost(500, MILLISECONDS).until({ !auditManager.isAuditInProgress() })

    then: 'we batched requests to IQ'
      aqlResult.iterator() >> data.iterator()
      searchResultSize * pathFactory.createRepoPath('foo', _) >> repoPath
      searchResultSize * ignorePatternMatcher.isIgnored('bar', repoPath) >> false
      2 * iqConnectionManager.evaluateWithAudit(firewallRepository, _) >> { arguments -> assets << arguments[1] }
      searchResultSize * storageManager.assignAuditTimestamp(repoPath)
      1 * storageManager.setRepositoryAudited(firewallRepository, _)
      0 * _
      assets[0].size() == AuditManager.BATCH_LIMIT
      assets[1].size() == 1
  }

  private List<LinkedHashMap<String, String>> createDataset(int size = 1) {
    List<LinkedHashMap<String, String>> maps = []
    (1..size).each { int i ->
      maps << [repo: 'foo', name: "baz$i", path: "bar$i", actual_sha1: "hash$i"]
    }
    return maps
  }
}
