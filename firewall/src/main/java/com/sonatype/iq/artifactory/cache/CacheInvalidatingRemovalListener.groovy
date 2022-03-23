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

import com.google.common.cache.Cache
import com.google.common.cache.RemovalCause
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification

/**
 * This implements a cache RemovalListener which will invalidate cache entries in the provided targetCache when it
 * receives an given cache removal notification.
 * Use case: When a cache entry is explicitly removed from one cache then invalidate it in another cache.
 */
class CacheInvalidatingRemovalListener<K, V> implements RemovalListener<K, V> {

  private final Cache targetCache

  private final Set<RemovalCause> causeFilter

  private CacheInvalidatingRemovalListener(final Cache targetCache, final Set<RemovalCause> causeFilter) {
    this.causeFilter = causeFilter
    this.targetCache = targetCache
  }

  /**
   * @param targetCache The target cache
   * @param causeFilter Set of RemovalCauses which should trigger invalidation of the target cache
   */
  static <K, V> CacheInvalidatingRemovalListener conditionallyForwardCacheInvalidationsTo(
      final Cache targetCache,
      final Set<RemovalCause> causeFilter) {
    return new CacheInvalidatingRemovalListener<K, V>(targetCache, causeFilter)
  }

  /**
   * Invalidates target cache when the RemovalListener received a notification of an explicitly removed cache key
   */
  @Override
  void onRemoval(final RemovalNotification<K, V> removalNotification) {
    if (this.causeFilter.contains(removalNotification.cause)) {
      targetCache.invalidate(removalNotification.key)
    }
  }
}
