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

import java.util.concurrent.TimeUnit

import com.sonatype.iq.artifactory.Commons.QuarantineStatus
import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallProperties
import com.sonatype.iq.artifactory.IqQuarantineStatusLoader
import com.sonatype.iq.artifactory.PathFactory
import com.sonatype.iq.artifactory.QuarantineStatusManagerProperties
import com.sonatype.iq.artifactory.StorageManager

import com.google.common.cache.CacheBuilder
import com.google.common.cache.LoadingCache
import org.slf4j.Logger

import static com.google.common.cache.RemovalCause.EXPLICIT
import static com.sonatype.iq.artifactory.cache.CacheInvalidatingRemovalListener.conditionallyForwardCacheInvalidationsTo

enum CacheStrategy {
  STORAGE_ONLY,
  MEMORY_THEN_STORAGE

  /**
   * Builds a cache given the configuration in firewallProperties.
   *
   * @throws IllegalArgumentException if the cache configuration is invalid
   */
  LoadingCache<FirewallArtifactoryAsset, QuarantineStatus> buildCache(
      final StorageManager storageManager,
      final IqQuarantineStatusLoader iqQuarantineStatusLoader,
      final PathFactory pathFactory,
      final Logger log,
      final FirewallProperties firewallProperties)
  {
    def properties = new QuarantineStatusManagerProperties(firewallProperties, log)
    switch (properties.cacheStrategy) {
      case MEMORY_THEN_STORAGE:
        return createMemoryThenStorageCache(storageManager, iqQuarantineStatusLoader, pathFactory, log, properties)
      case STORAGE_ONLY:
        return createStorageCache(storageManager, iqQuarantineStatusLoader, pathFactory, log)
      default:
        throw new IllegalArgumentException("Unknown cache strategy '${properties.cacheStrategy}'. Allowed values" +
            " are: ${values()}")
    }
  }

  /**
   * Can use cache expiry options 'firewall.cache.expire.after.access.in.millis' and 'firewall.cache.max.size' and
   * 'firewall.cache.expire.after.write.in.millis' in properties.
   *
   * @return boolean indicating if this option is supported for this cache strategy
   */
  boolean supportsCacheConfiguration() {
    switch(this) {
      case MEMORY_THEN_STORAGE:
        return true
      case STORAGE_ONLY:
        return false
      default:
        throw new IllegalArgumentException("Unknown cache strategy '${properties.cacheStrategy}'. Allowed values" +
            " are: ${values()}")
    }
  }

  /**
   * creates a LoadingCache with two layers.
   * 1st: it will try to the value from a memory cache created using guava CacheBuilder.
   * 2nd: if the values is not present in the memory cache it will try to load the value from StorageCache.
   * if both cache layers have no value then iqQuarantineStatusLoader is invoked to retrieve the value from IQ.
   *
   * if an item is explicitly removed from the cache (invalidated) it will also be removed from the 2nd layer cache.
   * if an item expires in the 1st level it will remain in the 2nd level cache.
   * items stored in the memoryCache expire as configured and will not be replicated to other nodes in an HA
   * environment, but its backing storage cache is replicated to other nodes and persistent.
   */
  private LoadingCache<FirewallArtifactoryAsset, QuarantineStatus> createMemoryThenStorageCache(
      final StorageManager storageManager,
      final IqQuarantineStatusLoader iqQuarantineStatusLoader,
      final PathFactory pathFactory,
      final Logger log,
      final QuarantineStatusManagerProperties properties) {
    def storageLoadingCache = createStorageCache(storageManager, iqQuarantineStatusLoader, pathFactory, log)
    return CacheBuilder.newBuilder()
        .maximumSize(properties.maxSize)
        .removalListener(conditionallyForwardCacheInvalidationsTo(storageLoadingCache, [EXPLICIT] as Set))
        .expireAfterAccess(properties.expireAfterAccessInMillis, TimeUnit.MILLISECONDS)
        .expireAfterWrite(properties.expireAfterWriteInMillis, TimeUnit.MILLISECONDS)
        .build({ storageLoadingCache.get(it as FirewallArtifactoryAsset) })
  }

  /**
   * creates a LoadingCache backed by attributes stored as artifactory assets (components).
   * if no value can be found in the component attributes then iqQuarantineStatusLoader is invoked to retrieve the value
   * from IQ.
   * items stored in the storageCache do not expire and will remain in the cache even after artifactory restarts.
   * items stored in the storageCache are replicated to other nodes in an HA environment
   */
  private LoadingCache<FirewallArtifactoryAsset, QuarantineStatus> createStorageCache(
      final StorageManager storageManager,
      final IqQuarantineStatusLoader iqQuarantineStatusLoader,
      final PathFactory pathFactory,
      final Logger log)
  {
    return new QuarantineStatusManagerStorageLoadingCache(storageManager, iqQuarantineStatusLoader, pathFactory, log)
  }
}
