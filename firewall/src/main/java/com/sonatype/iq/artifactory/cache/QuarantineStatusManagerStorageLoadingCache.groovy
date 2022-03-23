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
package com.sonatype.iq.artifactory.cache

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

import com.sonatype.iq.artifactory.Commons.QuarantineStatus
import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.IqQuarantineStatusLoader
import com.sonatype.iq.artifactory.PathFactory
import com.sonatype.iq.artifactory.StorageManager

import com.google.common.cache.AbstractLoadingCache
import org.slf4j.Logger

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW

/**
 * This LoadingCache caches the quarantine status using the provided Artifactory StorageManager.
 * When no value can be retrieved using the storageManager then the valueLoader will be invoked.
 * If the valueLoader retrieves a quarantine status then the cache is automatically updated using the storageManager.
 */
class QuarantineStatusManagerStorageLoadingCache
    extends AbstractLoadingCache<FirewallArtifactoryAsset, QuarantineStatus>
{
  private final IqQuarantineStatusLoader valueLoader

  private final StorageManager storageManager

  private final PathFactory pathFactory

  private final Logger log

  QuarantineStatusManagerStorageLoadingCache(
      final StorageManager storageManager,
      final IqQuarantineStatusLoader valueLoader,
      final PathFactory pathFactory,
      final Logger log)
  {
    this.log = log
    this.pathFactory = pathFactory
    this.storageManager = storageManager
    this.valueLoader = valueLoader
  }

  @Override
  QuarantineStatus get(final FirewallArtifactoryAsset asset) throws ExecutionException {
    return Optional.ofNullable(getIfPresent(asset))
        .orElseGet({loadAndUpdateCache(asset)})
  }

  @Override
  QuarantineStatus get(final FirewallArtifactoryAsset asset, final Callable<? extends QuarantineStatus> valueLoader)
      throws ExecutionException
  {
    throw new UnsupportedOperationException()
  }

  @Override
  QuarantineStatus getIfPresent(final Object key) {
    FirewallArtifactoryAsset asset = key as FirewallArtifactoryAsset

    def quarantineStatus = storageManager.maybeGetQuarantineStatus(asset.repoPath)
    if (quarantineStatus == null) {
      log.trace("Firewall quarantine status not found for '${asset.id}'")
    }
    else {
      log.trace("Using quarantine status '{}' from storage attribute for asset '{}'", quarantineStatus, asset.id)
    }
    return quarantineStatus
  }

  @Override
  void put(final FirewallArtifactoryAsset asset, final QuarantineStatus value) {
    storeQuarantineStatus(asset, value)
  }

  private QuarantineStatus loadAndUpdateCache(final FirewallArtifactoryAsset asset) {
    def quarantineStatus = valueLoader.apply(asset)
    put(asset, quarantineStatus)
    return quarantineStatus
  }

  @Override
  void invalidate(final Object key) {
    FirewallArtifactoryAsset asset = key as FirewallArtifactoryAsset
    storageManager.deleteQuarantineStatus(asset.repoPath)
  }

  private storeQuarantineStatus(final FirewallArtifactoryAsset asset, final QuarantineStatus quarantineStatus) {
    Objects.requireNonNull(quarantineStatus)
    quarantineStatus == ALLOW ? storageManager.unQuarantine(asset.repoPath) : storageManager.quarantine(asset.repoPath)
  }
}
