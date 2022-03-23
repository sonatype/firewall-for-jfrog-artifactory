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
import spock.lang.Specification

import static com.sonatype.iq.artifactory.FirewallProperties.FIREWALL_CACHE_QUARANTINE_STRATEGY

class QuarantineStatusManagerPropertiesTest
    extends Specification
{
  Logger logger

  def 'setup'() {
    logger = Mock()
  }

  def 'test default values'() {
    given:
      Properties properties = new Properties()
      FirewallProperties firewallProperties = FirewallProperties.load(properties, logger)

    when:
      QuarantineStatusManagerProperties result = new QuarantineStatusManagerProperties(firewallProperties, logger)

    then:
      result.maxSize == 1000
      result.expireAfterAccessInMillis == 3600000
      result.expireAfterWriteInMillis == 300000
      result.cacheStrategy == CacheStrategy.MEMORY_THEN_STORAGE
  }

  def 'test override values'() {
    given:
      Properties properties = new Properties()
      properties.putAll([
          'firewall.cache.max.size'                            : '999',
          'firewall.cache.expire.after.access.in.millis'       : '7',
          'firewall.cache.expire.after.write.in.millis'        : '2'
      ])
      properties[FIREWALL_CACHE_QUARANTINE_STRATEGY] = 'memory_then_storage'
      FirewallProperties firewallProperties = FirewallProperties.load(properties, logger)

    when:
      QuarantineStatusManagerProperties result = new QuarantineStatusManagerProperties(firewallProperties, logger)

    then:
      result.maxSize == 999
      result.expireAfterAccessInMillis == 7
      result.expireAfterWriteInMillis == 2
      result.cacheStrategy == CacheStrategy.MEMORY_THEN_STORAGE
  }
}
