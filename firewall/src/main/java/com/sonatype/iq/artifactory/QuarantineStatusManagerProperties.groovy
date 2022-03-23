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

import com.sonatype.iq.artifactory.cache.CacheStrategy

import org.slf4j.Logger

import static com.sonatype.iq.artifactory.cache.CacheStrategy.MEMORY_THEN_STORAGE

class QuarantineStatusManagerProperties
{
  long maxSize = 1000

  long expireAfterAccessInMillis = 3600000

  long expireAfterWriteInMillis = 300000

  CacheStrategy cacheStrategy = MEMORY_THEN_STORAGE

  QuarantineStatusManagerProperties(final FirewallProperties firewallProperties, final Logger log) {

    CacheStrategy quarantineCacheStrategy = firewallProperties.quarantineCacheStrategy
    if (quarantineCacheStrategy) {
      this.cacheStrategy = quarantineCacheStrategy
    }
    else {
      log.info("Using default cache strategy of: $cacheStrategy")
    }

    if (cacheStrategy.supportsCacheConfiguration()) {
      Long maxSize = firewallProperties.cacheMaxSize
      if (maxSize) {
        this.maxSize = maxSize
      }
      else {
        log.info("Using default cache size of: $maxSize")
      }

      Long expireAfterAccessInMillis = firewallProperties.cacheExpireAfterAccessInMillis
      if (expireAfterAccessInMillis) {
        this.expireAfterAccessInMillis = expireAfterAccessInMillis
      }
      else {
        log.info("Using default cache expiry after access of: $expireAfterAccessInMillis ms")
      }

      Long expireAfterWriteInMillis = firewallProperties.cacheExpireAfterWriteInMillis
      if (expireAfterWriteInMillis) {
        this.expireAfterWriteInMillis = expireAfterWriteInMillis
      }
      else {
        log.info("Using default cache expiry after write of: $expireAfterWriteInMillis ms")
      }
    }
  }
}
