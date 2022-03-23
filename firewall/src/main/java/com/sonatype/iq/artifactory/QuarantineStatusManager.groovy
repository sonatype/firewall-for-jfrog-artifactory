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

import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.UncheckedExecutionException
import org.artifactory.repo.RepoPath
import org.slf4j.Logger

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW

class QuarantineStatusManager
{
  final IqConnectionManager iqConnectionManager

  final FirewallRepositories firewallRepositories

  final PathFactory pathFactory

  final Logger log

  final LoadingCache<FirewallArtifactoryAsset, QuarantineStatus> quarantineStatusCache

  QuarantineStatusManager(
      final LoadingCache<FirewallArtifactoryAsset, QuarantineStatus> quarantineStatusCache,
      final IqConnectionManager iqConnectionManager,
      final FirewallRepositories firewallRepositories,
      final PathFactory pathFactory,
      final Logger log)
  {
    this.quarantineStatusCache = quarantineStatusCache
    this.iqConnectionManager = iqConnectionManager
    this.firewallRepositories = firewallRepositories
    this.pathFactory = pathFactory
    this.log = log
  }

  /**
   * Called by the beforeDownload handler. This will get the asset from the cache or load it into the cache
   * from the JFrog Artifactory artifact metadata.
   */
  QuarantineStatus getQuarantineStatus(final RepoPath repoPath) {
    try {
      def asset = FirewallArtifactoryAsset.of(getNormalizedRepoPath(repoPath))
      return quarantineStatusCache.get(asset)
    }
    catch (UncheckedExecutionException e) {
      throw e.cause
    }
  }

  void unQuarantine(final RepoPath repoPath) {
    def asset = FirewallArtifactoryAsset.of(getNormalizedRepoPath(repoPath))
    quarantineStatusCache.invalidate(asset)
    quarantineStatusCache.put(asset, ALLOW)
  }

  /**
   * Notification to keep audit and quarantine status in sync with IQ.
   */
  void removeIqComponent(final RepoPath repoPath) {
    def asset = FirewallArtifactoryAsset.of(getNormalizedRepoPath(repoPath))
    iqConnectionManager.removeComponent(asset)
    quarantineStatusCache.invalidate(asset)
  }

  private RepoPath getNormalizedRepoPath(RepoPath repoPath) {
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(repoPath.repoKey)
    return pathFactory.createRepoPath(firewallRepository.repoKey, repoPath.path)
  }
}
