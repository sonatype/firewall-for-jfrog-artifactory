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

import com.google.common.annotations.VisibleForTesting
import org.slf4j.Logger

class FirewallProperties
{
  public static final String REPO_KEY_PREFIX = "firewall.repo."

  public static final String IQ_SERVER_PUBLIC_URL = '<IQ_SERVER_PUBLIC_URL>'

  public static final String FIREWALL_IQ_URL = 'firewall.iq.url'

  public static final String FIREWALL_IQ_PUBLIC_URL = 'firewall.iq.public.url'

  public static final String FIREWALL_IQ_USERNAME = 'firewall.iq.username'

  public static final String FIREWALL_IQ_PASSWORD = 'firewall.iq.password'

  public static final String FIREWALL_IQ_CONNECT_TIMEOUT_IN_MILLIS = 'firewall.iq.connect.timeout.in.millis'

  public static final String FIREWALL_IQ_SOCKET_TIMEOUT_IN_MILLIS = 'firewall.iq.socket.timeout.in.millis'

  public static final String FIREWALL_IQ_CONNECTION_RETRY_INTERVAL_IN_MILLIS = 'firewall.iq.connection.retry' +
      '.interval.in.millis'

  public static final String FIREWALL_IQ_PROXY_HOSTNAME = 'firewall.iq.proxy.hostname'

  public static final String FIREWALL_IQ_PROXY_PORT = 'firewall.iq.proxy.port'

  public static final String FIREWALL_IQ_PROXY_USERNAME = 'firewall.iq.proxy.username'

  public static final String FIREWALL_IQ_PROXY_PASSWORD = 'firewall.iq.proxy.password'

  public static final String FIREWALL_IQ_PROXY_NTLM_DOMAIN = 'firewall.iq.proxy.ntlm.domain'

  public static final String FIREWALL_IQ_PROXY_NTLM_WORKSTATION = 'firewall.iq.proxy.ntlm.workstation'

  public static final String FIREWALL_CACHE_MAX_SIZE = 'firewall.cache.max.size'

  public static final String FIREWALL_CACHE_EXPIRE_AFTER_ACCESS_IN_MILLIS = 'firewall.cache.expire.after.access.in.millis'



  public static final String FIREWALL_CACHE_EXPIRE_AFTER_WRITE_IN_MILLIS = 'firewall.cache.expire.after.write.in.millis'

  public static final String FIREWALL_CACHE_QUARANTINE_STRATEGY = 'firewall.cache.quarantine.strategy'

  public static final String FIREWALL_UNQUARANTINE_UPDATE_IN_MILLIS = 'firewall.unquarantine.update.in.millis'

  public static final String FIREWALL_IGNOREPATTERN_RELOAD_CRON = 'firewall.ignorepattern.reload.cron'

  public static final String FIREWALL_VERIFY_WAIT_MILLIS = 'firewall.verify.wait.in.millis'

  enum MODE {
    quarantine, audit, disabled
  }

  private static File propertiesFile

  private final Properties properties

  private final Logger logger

  private Map<String, MODE> repositories

  private FirewallProperties(final Properties properties, final Logger logger) {
    this.properties = properties
    this.logger = logger
  }

  String getIqUrl() {
    return properties[FIREWALL_IQ_URL]
  }

  String getIqPublicUrl() {
    return properties[FIREWALL_IQ_PUBLIC_URL] ?: IQ_SERVER_PUBLIC_URL
  }

  String getIqUsername() {
    return properties[FIREWALL_IQ_USERNAME]
  }

  String getIqPassword() {
    return properties[FIREWALL_IQ_PASSWORD] as String
  }

  Integer getIqConnectTimeoutInMillis() {
    return properties[FIREWALL_IQ_CONNECT_TIMEOUT_IN_MILLIS] as Integer
  }

  Integer getIqSocketTimeoutInMillis() {
    return properties[FIREWALL_IQ_SOCKET_TIMEOUT_IN_MILLIS] as Integer
  }

  Integer getIqConnectionRetryIntervalInMillis() {
    return properties[FIREWALL_IQ_CONNECTION_RETRY_INTERVAL_IN_MILLIS] as Integer
  }

  String getProxyHostname() {
    return properties[FIREWALL_IQ_PROXY_HOSTNAME] as String
  }

  Integer getProxyPort() {
    return properties[FIREWALL_IQ_PROXY_PORT] as Integer
  }

  String getProxyUsername() {
    return properties[FIREWALL_IQ_PROXY_USERNAME] as String
  }

  String getProxyPassword() {
    return properties[FIREWALL_IQ_PROXY_PASSWORD] as String
  }

  String getProxyNtlmDomain() {
    return properties[FIREWALL_IQ_PROXY_NTLM_DOMAIN] as String
  }

  String getProxyNtlmWorkstation() {
    return properties[FIREWALL_IQ_PROXY_NTLM_WORKSTATION] as String
  }

  Long getCacheMaxSize() {
    return properties[FIREWALL_CACHE_MAX_SIZE] as Long
  }

  Long getCacheExpireAfterAccessInMillis() {
    return properties[FIREWALL_CACHE_EXPIRE_AFTER_ACCESS_IN_MILLIS] as Long
  }

  Long getCacheExpireAfterWriteInMillis() {
    return properties[FIREWALL_CACHE_EXPIRE_AFTER_WRITE_IN_MILLIS] as Long
  }

  CacheStrategy getQuarantineCacheStrategy() {
    def value = properties[FIREWALL_CACHE_QUARANTINE_STRATEGY] as String
    try {
      return value?.toUpperCase() as CacheStrategy
    }
    catch (IllegalArgumentException ignored) {
      logger.error("Unknown option '${value}' for property '$FIREWALL_CACHE_QUARANTINE_STRATEGY'. Allowed values are: " +
          "${CacheStrategy.values()}")
      return null
    }
  }

  Long getUnquarantineUpdateInMillis() {
    return properties[FIREWALL_UNQUARANTINE_UPDATE_IN_MILLIS] as Long
  }

  String getIgnorePatternReloadCronExpression() {
    return properties[FIREWALL_IGNOREPATTERN_RELOAD_CRON] as String
  }
  
  Long getVerifyInitWaitInMillis() {
    return properties[FIREWALL_VERIFY_WAIT_MILLIS] as Long
  }

  String getRepositoryManagerId() {
    return properties['firewall.repository.manager.id']
  }

  Map<String, MODE> getRepositories() {
    if (!repositories) {
      repositories = loadRepositories()
    }
    return repositories
  }

  MODE getRepositoryMode(final String repositoryName) {
    return getRepositories()[repositoryName]
  }

  static FirewallProperties load(final File pluginDirectory, final Logger logger) {
    propertiesFile = getPropertiesFile(pluginDirectory)
    Properties props = new Properties()
    if (propertiesFile.exists()) {
      propertiesFile.withInputStream {
        props.load(it)
      }
    }
    else {
      throw new MissingPropertiesException()
    }

    FirewallProperties firewallProperties = new FirewallProperties(props, logger)

    validateProperties(firewallProperties, logger)

    return firewallProperties
  }

  @VisibleForTesting
  static FirewallProperties load(final Properties properties, final Logger logger) {
    FirewallProperties firewallProperties = new FirewallProperties(properties, logger)

    validateProperties(firewallProperties, logger)

    return firewallProperties
  }

  static void validateProperties(final FirewallProperties firewallProperties, final Logger logger) {
    // repository manager id can only be alphanumeric, dashes, and underscores (simplified path segment)
    def id = firewallProperties.getRepositoryManagerId()
    if (id && !(id ==~ /^([\w-]*)+$/)) {
      throw new InvalidPropertiesException("Invalid value '$id' for 'firewall.repository.manager.id'. Valid characters are: A-Z, a-z, 0-9, -, _")
    }
  }

  private Map<String, MODE> loadRepositories() {
    def repos = [:]
    properties.each {
      def key = it.key as String
      def value = it.value as String
      if (key.startsWith(REPO_KEY_PREFIX)) {
        def repo = key.substring(REPO_KEY_PREFIX.length())
        try {
          def mode = MODE.valueOf(value)
          repos.put(repo, mode)
        }
        catch (IllegalArgumentException e) {
          this.@logger.error("Unknown option '${it.value}' for repository '${it.key}'. Allowed values are: quarantine, audit")
        }
      }
    }
    return repos
  }

  static File getPropertiesFile() {
    return propertiesFile
  }

  static File getPropertiesFile(File pluginDirectory) {
    return new File(pluginDirectory, 'firewall.properties')
  }
}
