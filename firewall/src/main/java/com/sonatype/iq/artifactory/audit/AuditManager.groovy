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

import java.time.Duration
import java.time.LocalDateTime

import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallRepositories
import com.sonatype.iq.artifactory.FirewallRepository
import com.sonatype.iq.artifactory.IgnorePatternMatcher
import com.sonatype.iq.artifactory.IqConnectionManager
import com.sonatype.iq.artifactory.PathFactory
import com.sonatype.iq.artifactory.StorageManager

import groovy.util.logging.Slf4j
import org.artifactory.repo.RepoPath
import org.artifactory.search.Searches
import org.artifactory.search.aql.AqlResultHandler

import static com.google.common.base.Preconditions.checkNotNull

@Slf4j
class AuditManager
{
  static final int BATCH_LIMIT = 100

  final IqConnectionManager iqConnectionManager

  final StorageManager storageManager

  final IgnorePatternMatcher ignorePatternMatcher
  
  final AuditExecutor auditExecutor

  final Searches searches
  
  final PathFactory pathFactory

  AuditManager(final IqConnectionManager iqConnectionManager, final StorageManager storageManager,
               final IgnorePatternMatcher ignorePatternMatcher, final Searches searches, final PathFactory pathFactory)
  {
    this.iqConnectionManager = checkNotNull(iqConnectionManager)
    this.storageManager = checkNotNull(storageManager)
    this.ignorePatternMatcher = checkNotNull(ignorePatternMatcher)
    this.auditExecutor = new AuditExecutor(iqConnectionManager, storageManager)
    this.searches = checkNotNull(searches)
    this.pathFactory = checkNotNull(pathFactory)
  }

  boolean isAssetAuditable(final FirewallRepository firewallRepository, final RepoPath repoPath) {
    return !ignorePatternMatcher.isIgnored(firewallRepository.format, repoPath)
  }

  /**
   * determines if the given repo artifact has already been marked for audit and, if not, will mark it so in IQ
   */
  def updateAuditStatusIfNeeded(final RepoPath repoPath) {
    if (!storageManager.hasAuditTimestamp(repoPath)) {
      iqConnectionManager.evaluateWithAudit(FirewallArtifactoryAsset.of(repoPath, storageManager.getHash(repoPath)))
      storageManager.assignAuditTimestamp(repoPath)
    }
  }

  void audit(FirewallRepositories firewallRepositories) {
    log.info("Beginning audit collection pass")
    
    firewallRepositories.reposToAudit().each { FirewallRepository repo ->
      final String repoKey = repo.repoKey
      LocalDateTime start = LocalDateTime.now()
      long count = 0
      def aql = """items.find(
{
  "\$and": [
    {
      "\$or": [
        {
          "repo": "${repoKey}"
        },
        {
          "repo": "${repoKey}-cache"
        }
      ]
    },
    {
      "\$and": [
        {
          "@firewall.quarantine": {"\$nmatch": "*"}
          
        },
        {
          "@firewall.auditTimestamp": {"\$nmatch": "*"}
        }
      ]
    }
  ]
}
).include("repo", "name", "path", "actual_sha1")"""
      log.trace('aql= {}', aql)
      List<FirewallArtifactoryAsset> assets = []

      AqlResultHandler handler = { result ->
        result.each {
          RepoPath repoPath = pathFactory.createRepoPath(repoKey, "${it.path}/${it.name}")
          if (isAssetAuditable(repo, repoPath)) {
            assets << FirewallArtifactoryAsset.of(repoPath, it.actual_sha1)
          }
          if(assets && assets.size() % BATCH_LIMIT == 0) {
            log.debug("sending batch of ${assets.size()} assets for audit on repo: ${repoKey}")
            count += assets.size()
            auditExecutor.auditRepository(repo, assets.collect().asImmutable())
            assets.clear()
          }
        }
        if (assets) {
          log.debug("sending final batch of ${assets.size()} for audit on repo: ${repoKey}")
          count += assets.size()
          auditExecutor.auditRepository(repo, assets.collect().asImmutable())
        }
      } as AqlResultHandler

      searches.aql(aql, handler)

      log.info("Audit processing of {} completed in {} and processed {} components. Evaluation process has been " +
          "started in the background", repoKey, Duration.between(start, LocalDateTime.now()), count)
      
      if(count == 0) {
        log.info("Marking repository '$repoKey' as audited since all components have already been audited")
        storageManager.setRepositoryAudited(repo, System.currentTimeMillis())
      }
    }
    log.info("Ending audit collection pass")
  }
  
  boolean isAuditInProgress() {
    return auditExecutor.isProcessing()
  }
}
