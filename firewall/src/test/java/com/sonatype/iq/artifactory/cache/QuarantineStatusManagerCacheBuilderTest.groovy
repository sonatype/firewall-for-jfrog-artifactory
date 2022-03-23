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

import com.sonatype.iq.artifactory.Commons.QuarantineStatus
import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallProperties
import com.sonatype.iq.artifactory.IqQuarantineStatusLoader
import com.sonatype.iq.artifactory.PathFactory
import com.sonatype.iq.artifactory.QuarantineStatusManagerProperties
import com.sonatype.iq.artifactory.StorageManager
import com.sonatype.iq.artifactory.TestHelper

import org.artifactory.repo.RepoPath
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll

import static com.sonatype.iq.artifactory.FirewallProperties.FIREWALL_CACHE_QUARANTINE_STRATEGY
import static com.sonatype.iq.artifactory.cache.CacheStrategy.MEMORY_THEN_STORAGE
import static com.sonatype.iq.artifactory.cache.CacheStrategy.STORAGE_ONLY

class QuarantineStatusManagerCacheBuilderTest
    extends Specification
{
  Logger logger = Mock()

  RepoPath repoPath = TestHelper.createStruts2RepoPath('main-java-libs', 'jar')

  PathFactory pathFactory = Mock()

  StorageManager storageManager = Mock()

  IqQuarantineStatusLoader iqQuarantineStatusLoader = Mock()

  def setup() {
    pathFactory.createRepoPath('main-java-libs', repoPath.path) >> repoPath
  }

  @Unroll
  def 'builds right type of cache with strategy #strategy'() {
    given: 'properties configured with a cache strategy'
      Properties properties = new Properties()
      properties[FIREWALL_CACHE_QUARANTINE_STRATEGY] = strategy
      FirewallProperties firewallProperties = FirewallProperties.load(properties, logger)

    when: 'builder is invoked'
      def quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, logger)
      def cache = quarantineStatusManagerProperties.cacheStrategy
          .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, logger, firewallProperties)

    then: 'the expected implementation type was returned by the builder'
      cache.class.canonicalName == expectedImplementation

    where:
      strategy                   || expectedImplementation
      STORAGE_ONLY.name()        || 'com.sonatype.iq.artifactory.cache.QuarantineStatusManagerStorageLoadingCache'
      MEMORY_THEN_STORAGE.name() || 'com.google.common.cache.LocalCache.LocalLoadingCache'
  }

  def 'no strategy configured uses default strategy'() {
    given: 'properties configured without a cache strategy'
      FirewallProperties firewallProperties = FirewallProperties.load(new Properties(), logger)

    when: 'builder is invoked'
      def quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, logger)
      def cache = quarantineStatusManagerProperties.cacheStrategy
          .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, logger, firewallProperties)

    then: 'the expected implementation type was returned by the builder'
      cache.class.canonicalName == 'com.google.common.cache.LocalCache.LocalLoadingCache'
  }

  def 'memory_then_storage cache invalidations are forwarded to storage cache'() {
    given: 'cache configured with a MEMORY_THEN_STORAGE cache strategy'
      Properties properties = new Properties()
      properties[FIREWALL_CACHE_QUARANTINE_STRATEGY] = MEMORY_THEN_STORAGE.name()
      FirewallProperties firewallProperties = FirewallProperties.load(properties, logger)
      def quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, logger)
      def cache = quarantineStatusManagerProperties.cacheStrategy
          .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, logger, firewallProperties)

    and: 'a cached asset'
      def asset = FirewallArtifactoryAsset.of(repoPath)
      cache.put(asset, QuarantineStatus.ALLOW)

    when: 'the memory cache is invalidated'
      cache.invalidate(asset)

    then: 'the storage cache is also invalidated'
      1 * storageManager.deleteQuarantineStatus(repoPath)
  }
}
