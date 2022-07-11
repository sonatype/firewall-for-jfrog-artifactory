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

import java.nio.charset.StandardCharsets
import java.time.Instant

import com.sonatype.clm.dto.model.component.FirewallIgnorePatterns
import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataList
import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList
import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList.RepositoryComponentEvaluationDataRequest
import com.sonatype.clm.dto.model.component.UnquarantinedComponentList
import com.sonatype.clm.dto.model.policy.RepositoryPolicyEvaluationSummary
import com.sonatype.iq.artifactory.httpclient.PreemptiveAuthHttpRequestInterceptor
import com.sonatype.iq.artifactory.restclient.HttpException
import com.sonatype.iq.artifactory.restclient.RestClient
import com.sonatype.iq.artifactory.restclient.RestClient.Repository
import com.sonatype.iq.artifactory.restclient.RestClientConfiguration
import com.sonatype.iq.artifactory.restclient.RestClientConfiguration.HttpClientProvider
import com.sonatype.iq.artifactory.restclient.RestClientFactory

import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.ProxyAuthenticationStrategy
import org.apache.http.message.BasicHeader
import org.slf4j.Logger

import static com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList.INITIAL_AUDIT
import static com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList.NEW_COMPONENT
import static com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList.REEVALUATION
import static com.sonatype.iq.artifactory.restclient.RepositoryManagerType.ARTIFACTORY
import static java.lang.System.currentTimeMillis
import static java.time.temporal.ChronoUnit.MILLIS
import static java.util.Objects.requireNonNull

class IqConnectionManager
{
  private static final Integer DEFAULT_TIMEOUT_IN_MILLISECONDS = 10_000

  private static final Integer DEFAULT_RETRY_INTERVAL_IN_MILLISECONDS = 2_000

  private static final String REQUIRED_IQ_VERSION = "1.104.0"

  private final Logger log

  final RestClientFactory restClientFactory

  final RestClientConfiguration restClientConfiguration

  private Integer connectionRetryIntervalInMillis

  private boolean connectionFailed = true

  private Instant lastFailedConnectionAttempt = Instant.MIN

  final FirewallRepositories firewallRepositories

  private String iqPublicUrl

  private String repositoryManagerId

  private final TelemetrySupplier telemetrySupplier

  @VisibleForTesting
  IqConnectionManager(final FirewallProperties firewallProperties, final FirewallRepositories firewallRepositories,
                      final Logger log, final String pluginVersion, final String artifactoryVersion)
  {
    def iqServerUrl = requireNonNull(firewallProperties.iqUrl,
        "firewall.iq.url is required in firewall.properties")
    def iqServerUsername = requireNonNull(firewallProperties.iqUsername,
        "firewall.iq.username is required in firewall.properties")
    def iqServerPassword = requireNonNull(firewallProperties.iqPassword,
        "firewall.iq.password is required in firewall.properties")
    def connectTimeout = Optional.ofNullable(firewallProperties.iqConnectTimeoutInMillis).
        orElse(DEFAULT_TIMEOUT_IN_MILLISECONDS)
    def socketTimeout = Optional.ofNullable(firewallProperties.iqSocketTimeoutInMillis).
        orElse(DEFAULT_TIMEOUT_IN_MILLISECONDS)
    this.connectionRetryIntervalInMillis = firewallProperties.iqConnectionRetryIntervalInMillis ?:
        DEFAULT_RETRY_INTERVAL_IN_MILLISECONDS
    this.iqPublicUrl = firewallProperties.iqPublicUrl
    this.repositoryManagerId = firewallProperties.repositoryManagerId
    this.log = log

    this.restClientFactory = new RestClientFactory()
    this.restClientConfiguration = new RestClientConfiguration()
    this.restClientConfiguration.setServerUrl(iqServerUrl)
    this.restClientConfiguration.setHttpClientProvider(getHttpClientProvider(iqServerUrl, iqServerUsername,
        iqServerPassword, connectTimeout, socketTimeout, firewallProperties))
    this.firewallRepositories = firewallRepositories
    this.telemetrySupplier = new TelemetrySupplier(restClientFactory, restClientConfiguration, artifactoryVersion,
        pluginVersion, log)
  }

  @VisibleForTesting
  IqConnectionManager(final RestClientFactory restClientFactory,
                      final RestClientConfiguration restClientConfiguration,
                      final FirewallRepositories firewallRepositories,
                      final TelemetrySupplier telemetrySupplier,
                      final Logger log)
  {
    this.telemetrySupplier = telemetrySupplier
    this.restClientFactory = restClientFactory
    this.restClientConfiguration = restClientConfiguration
    this.firewallRepositories = firewallRepositories
    this.log = log
    this.connectionRetryIntervalInMillis = DEFAULT_RETRY_INTERVAL_IN_MILLISECONDS
  }

  /**
   * Test the connection to the IQ server that was defined in the FirewallProperties
   */
  void validateConnection() {
    log.info("Firewall is validating the connection to Nexus IQ")
    // create a client which bypasses the error handling wrapper
    restClientFactory.forConfiguration(restClientConfiguration).validateConfiguration()
  }

  synchronized boolean tryInitializeConnection() {
    if (connectionFailed) {
      if (retryTimeoutHasElapsed) {
        try {
          restClientFactory.forConfiguration(restClientConfiguration).validateConfiguration()
          try {
            log.trace("Firewall is validating the Nexus IQ API version")
            // if requested URL endpoint does not exist then we assume an API version mismatch
            // because the firewallIgnorePatterns endpoint does not exist prior to 1.62
            restClientFactory.forConfiguration(restClientConfiguration).firewallIgnorePatterns
          }
          catch (HttpException e) {
            throw e.status == 404 ? new IqConnectionException("IQ server 1.62 or later is required.") : e
          }
          connectionFailed = false

          migrationServerVersionCheck()

          telemetrySupplier.enableIfSupported()
        }
        catch (IOException e) {
          lastFailedConnectionAttempt = Instant.now()
          log.error("Failed to initialize IQ connection. " +
              "No further connection attempts for ${connectionRetryIntervalInMillis} ms. " +
              "${e?.message ? " Reason: ${e.message}" : ''}")
          return false
        }
      }
      else {
        return false
      }
    }
    return true
  }

  /**
   * Check if IQ Server version is >= 104 which is need for the repositoryManagerId migration (see INT-2403)
   */
  def migrationServerVersionCheck() {
    try {
      // The server version endpoint is IQ 1.50, since we validated IQ is 1.62 or higher we can be confident this
      // endpoint should exist. We can also be confident that the IQ connection tests have passed at this point.
      restClientFactory.forConfiguration(restClientConfiguration).validateServerVersion(REQUIRED_IQ_VERSION)

      if(repositoryManagerId == null) {
        // We have a good version of IQ, but the repositoryManagerId is not set therefore no migration will occur
        log.warn("IQ is the required version for migration, but the 'firewall.repository.manager.id' property is not set in firewall.properties. To complete migration please set this value.")
      }
    }
    catch (Exception e) {
      // If we fail this, it is not blocking so just log at WARN.
      log.warn(
          "IQ Server is not the required version for the 2.x+ Firewall for Artifactory Plugin. Requires IQ version" +
              " {} or newer.", REQUIRED_IQ_VERSION)
      log.warn(
          "Please update your IQ Server to version 1.104 or newer. Falling back to using legacy repository manager ID.")

      // Log the stack trace at DEBUG for support
      log.debug("Warning while checking IQ Server version", e)

      // However it does mean we cannot do a migration. If they have a repositoryManagerId set in the
      // firewall.properties we need to clear it so it will continue to send the old legacy version.
      repositoryManagerId = null
    }
  }

  private <T> T withErrorHandlerWrapper(final T client) {
    client.metaClass.invokeMethod = { String method, Object args ->
      if (tryInitializeConnection()) {
        try {
          return delegate.metaClass.getMetaMethod(method, args).invoke(delegate, args)
        }
        catch (HttpException e) {
          throw new IqConnectionException("Server error when calling IQ: ${e.message}", e)  // TODO test
        }
        catch (IOException e) {
          connectionFailed = true
          lastFailedConnectionAttempt = Instant.now()
          def message = 'Call to IQ failed. No further connection attempts for ' +
              lastFailedConnectionAttempt.minusMillis(currentTimeMillis()).plusMillis(connectionRetryIntervalInMillis).toEpochMilli() +
              "ms. ${e?.message ? " Reason: ${e.message}" : ''}"
          throw new IqConnectionException(message, e)
        }
      } else {
        throw new IqConnectionException("Suppressed call to ${method} because of recent failures.")
      }
    }
    return client
  }

  private boolean getRetryTimeoutHasElapsed() {
    lastFailedConnectionAttempt.plus(connectionRetryIntervalInMillis, MILLIS) < Instant.now()
  }

  /**
   * Get regexs for files to ignore for firewall
   */
  FirewallIgnorePatterns getFirewallIgnorePatterns() {
    log.trace('Calling IQ to get ignore patterns.')
    return getRestClientBase().getFirewallIgnorePatterns()
  }

  void enableAudit(final String repositoryName) {
    getRepositoryClient(repositoryName).setEnabled(true)
  }

  void disableAudit(final String repositoryName) {
    getRepositoryClient(repositoryName).setEnabled(false)
  }

  void enableQuarantine(final String repositoryName) {
    enableAudit(repositoryName)
    getRepositoryClient(repositoryName).setQuarantine(true)
  }

  void disableQuarantine(final String repositoryName) {
    disableAudit(repositoryName)
    getRepositoryClient(repositoryName).setQuarantine(false)
  }

  /**
   * Audit the given component list for the given repository
   */
  RepositoryComponentEvaluationDataList evaluateWithAudit(final FirewallArtifactoryAsset asset) {
    log.trace("Calling IQ to evaluate with audit: ${asset.id}")
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(asset.repoKey)
    return getRepositoryClient(firewallRepository.repoKey).evaluateComponents(createEvalRequest(asset))
  }

  /**
   * @param firewallRepository
   * @param assets assumed to all be members of firewallRepository
   */
  void evaluateWithAudit(final FirewallRepository firewallRepository,
                         final Collection<FirewallArtifactoryAsset> assets) {
      log.trace("Calling IQ to evaluate with audit for ${assets.size()} assets in repo: ${firewallRepository.repoKey}")
      getRepositoryClient(firewallRepository.repoKey).
          evaluateComponents(createEvalRequest(INITIAL_AUDIT, firewallRepository.format, assets))
  }

  /**
   * Evaluate the given component list in quarantine mode for the given repository
   */
  RepositoryComponentEvaluationDataList evaluateWithQuarantine(final FirewallArtifactoryAsset asset) {
    log.trace("Calling IQ to evaluate with quarantine: ${asset.id}")
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(asset.repoKey)
    return getRepositoryClient(firewallRepository.repoKey).evaluateComponentWithQuarantine(createEvalRequest(asset))
  }

  /**
   * Removes the given component from IQ firewall.
   */
  void removeComponent(final FirewallArtifactoryAsset asset) {
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(asset.repoKey)
    log.trace("Calling IQ to remove asset: ${asset.id}")
    getRepositoryClient(firewallRepository.repoKey).removeComponent(asset.repoPath.path)
  }

  private RepositoryComponentEvaluationDataRequestList createEvalRequest(
      final String reason,
      final String format,
      final Collection<FirewallArtifactoryAsset> assets)
  {
    RepositoryComponentEvaluationDataRequestList list = new RepositoryComponentEvaluationDataRequestList(reason)
    assets.each { FirewallArtifactoryAsset asset ->
      list.components.add(new RepositoryComponentEvaluationDataRequest(format, asset.path, asset.hash))
    }
    return list
  }

  private RepositoryComponentEvaluationDataRequestList createEvalRequest(final FirewallArtifactoryAsset asset) {
    String reason = getReason(asset)
    String format = firewallRepositories.getEnabledFirewallRepoByKey(asset.repoKey).format
    return createEvalRequest(reason, format, [asset])
  }

  private static String getReason(final FirewallArtifactoryAsset asset) {
    return asset.isNew ? NEW_COMPONENT : REEVALUATION
  }

  UnquarantinedComponentList getUnquarantinedComponents(final FirewallRepository firewallRepository,
                                                        final long sinceUtcTimestamp)
  {
    return getRepositoryClient(firewallRepository.repoKey).getUnquarantinedComponents(sinceUtcTimestamp)
  }

  /**
   * Get the latest policy evaluation summary for the given repository
   */
  RepositoryPolicyEvaluationSummary getPolicyEvaluationSummary(final String repositoryName) {
    return maybeEnhanceReportUrlWithIqPublicUrl(getRepositoryClient(repositoryName).getPolicyEvaluationSummary())
  }

  def maybeEnhanceReportUrlWithIqPublicUrl(RepositoryPolicyEvaluationSummary summary) {
    if (summary.reportUrl) {
      summary.reportUrl = "${iqPublicUrl}/${summary.reportUrl}"
    }
    return summary
  }

  @VisibleForTesting
  Repository getRepositoryClient(final String repositoryName) {
    def repository = restClientFactory.forConfiguration(restClientConfiguration).forRepository(
        getRepositoryManagerId(repositoryName), repositoryName, ARTIFACTORY)
    return withErrorHandlerWrapper(repository)
  }

  /**
   * Returns the value for the 'Repository Manager ID' shown in IQ. In the 1.x versions of the plugin this method
   * incorrectly returned the SHA256 of the repository name. In 2.x + it uses the value configured in the properties.
   * If the value is not configured in the properties it will use the legacy method.
   * @param repositoryName The repository name which will only be used if the plugin is in a legacy setup
   * @return The repository manager ID to send to IQ
   */
  private String getRepositoryManagerId(final String repositoryName) {
    return repositoryManagerId ?: getUniqueId(repositoryName)
  }

   /**
   * Legacy method to get the value for the 'repository manager id'. Returns a unique value per repository instead of
   * for the Artifactory instance/cluster. Since a migration path is involved we need to keep this around
   * @param repositoryName
   * @return SHA256 of the repository name (incorrect)
   */
  @Deprecated
  private static String getUniqueId(final String repositoryName) {
    return Hashing.sha256().hashString(repositoryName, StandardCharsets.UTF_8).toString().substring(0, 50)
  }

  private RestClient.Base getRestClientBase() {
    def restClient = restClientFactory.forConfiguration(restClientConfiguration)
    return withErrorHandlerWrapper(restClient)
  }

  private HttpClientBuilder createHttpClientBuilder(final String iqUrl,
                                                    final String iqUsername,
                                                    final String iqPassword,
                                                    final Integer connectTimeoutInMillis,
                                                    final Integer socketTimeoutInMillis,
                                                    final String proxyHost,
                                                    final Integer proxyPort,
                                                    final String proxyUsername,
                                                    final String proxyPassword,
                                                    final String proxyNtlmDomain,
                                                    final String proxyNtlmWorkstation)
  {
    URI iqHttpHost = URI.create(iqUrl)

    CredentialsProvider credentialsProvider = new BasicCredentialsProvider()

    credentialsProvider.
        setCredentials(new AuthScope(new HttpHost(iqHttpHost.host, iqHttpHost.port, iqHttpHost.scheme)),
            new UsernamePasswordCredentials(iqUsername, iqPassword))

    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(connectTimeoutInMillis)
        .setSocketTimeout(socketTimeoutInMillis)
        .build()

    String userAgent = telemetrySupplier.getUserAgent()

    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
        .addInterceptorFirst(new PreemptiveAuthHttpRequestInterceptor())
        .setDefaultCredentialsProvider(credentialsProvider)
        .setUserAgent(userAgent)
        .setDefaultRequestConfig(requestConfig)
        // INT-1611 Manually set a cookie header to overcome issue in Artifactory bundled httpclient 4.5.1
        // Can be removed if we observe Artifactory bumping the httpclient version
        .setDefaultHeaders([new BasicHeader("Cookie", "CLM-CSRF-TOKEN=CSRF-REQUEST-TOKEN")])

    if (proxyHost) {
      log.info("Enabling http proxy for Firewall for Artifactory")
      // set proxy host settings
      httpClientBuilder
          .setProxy(new HttpHost(proxyHost, proxyPort))
          .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())

      if (proxyUsername != null) {
        // set proxy credentials for simple schemas (basic/digest/etc...)
        credentialsProvider.setCredentials(
            new AuthScope(proxyHost, proxyPort),
            new UsernamePasswordCredentials(proxyUsername, proxyPassword)
        )

        if (proxyNtlmDomain != null) {
          log.info("Firewall using NTLM authentication for proxy")
          // set proxy credentials for NTLM
          credentialsProvider.setCredentials(
              new AuthScope(proxyHost, proxyPort, null, "ntlm"),
              new NTCredentials(proxyUsername, proxyPassword, proxyNtlmWorkstation, proxyNtlmDomain)
          )
        }
      }
    }

    return httpClientBuilder
  }

  private HttpClientProvider getHttpClientProvider(
      String iqServerUrl,
      String iqServerUsername,
      String iqServerPassword,
      int connectTimeout,
      int socketTimeout,
      FirewallProperties firewallProperties)
  {
    return new HttpClientProvider() {
      @Override
      HttpClientBuilder createHttpClient(final RestClientConfiguration restClientConfiguration) {
        return createHttpClientBuilder(iqServerUrl, iqServerUsername, iqServerPassword, connectTimeout, socketTimeout,
            firewallProperties.proxyHostname, firewallProperties.proxyPort, firewallProperties.proxyUsername,
            firewallProperties.proxyPassword, firewallProperties.proxyNtlmDomain,
            firewallProperties.proxyNtlmWorkstation)
      }
    }
  }

}
