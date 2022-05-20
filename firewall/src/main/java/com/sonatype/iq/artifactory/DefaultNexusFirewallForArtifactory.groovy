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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import com.sonatype.clm.dto.model.policy.RepositoryPolicyEvaluationSummary
import com.sonatype.iq.artifactory.Commons.QuarantineStatus
import com.sonatype.iq.artifactory.FirewallProperties.MODE
import com.sonatype.iq.artifactory.audit.AuditManager

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import org.apache.http.HttpStatus
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.search.Searches
import org.slf4j.Logger

import static com.google.common.base.Preconditions.checkState
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.DENY
import static java.lang.String.format

class DefaultNexusFirewallForArtifactory
    implements NexusFirewallForArtifactory
{
  // every six hours starting 0:00
  public static final String DEFAULT_IGNORE_PATTERN_RELOAD_CRON_EXPRESSION = "0 0 0/6 * * ?"

  public static final int DEFAULT_VERIFY_INIT_WAIT = 60000

  final Logger log

  final FirewallRepositories firewallRepositories

  final IqConnectionManager iqConnectionManager

  final StorageManager storageManager

  final IgnorePatternMatcher ignorePatternMatcher

  final QuarantineStatusManager quarantineStatusManager

  final RepositoryManager repositoryManager

  final UnquarantinedComponentsUpdater unquarantinedComponentsUpdater

  /**
   * Latch for a 2-stage initialization process:
   * <ul>
   * <li>2: (start value) initialization not started and all downloads are blocked</li>
   * <li>1: initialization in progress and all downloads are blocked</li>
   * <li>0: initialization complete and downloads are processed normally</li>
   * </ul>
   */
  final CountDownLatch initializationVerified = new CountDownLatch(2)

  final static int UNINITIALIZED = 2

  final static int INITIALIZATION_IN_PROGRESS = 1

  boolean auditPassAccomplished

  final String ignorePatternReloadCronExpression

  final Searches searches

  final AuditManager auditManager

  final long verifyInitWaitInMillis

  DefaultNexusFirewallForArtifactory(final Logger log, final File pluginDirectory, final Repositories repositories,
                                     final Searches searches, final String artifactoryVersion)
  {
    FirewallProperties firewallProperties = FirewallProperties.load(pluginDirectory, log)

    PathFactory pathFactory = new PathFactoryImpl()

    this.log = log
    this.firewallRepositories = new FirewallRepositories()
    this.iqConnectionManager = new IqConnectionManager(firewallProperties, firewallRepositories, log,
        getPluginVersion(), artifactoryVersion, getArtifactoryEdition())
    this.storageManager = new StorageManager(repositories, pathFactory, firewallRepositories, log)
    this.ignorePatternMatcher = IgnorePatternMatcher.instance
    this.ignorePatternReloadCronExpression = firewallProperties.ignorePatternReloadCronExpression ?:
        DEFAULT_IGNORE_PATTERN_RELOAD_CRON_EXPRESSION
    def iqQuarantineStatusLoader = new IqQuarantineStatusLoader(iqConnectionManager, storageManager, log)
    def quarantineStatusManagerProperties = new QuarantineStatusManagerProperties(firewallProperties, log)
    def loadingCache = quarantineStatusManagerProperties.cacheStrategy
        .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, log, firewallProperties)
    this.quarantineStatusManager = new QuarantineStatusManager(loadingCache, iqConnectionManager, firewallRepositories,
        pathFactory, log)
    this.repositoryManager = new RepositoryManager(log, repositories, firewallProperties, iqConnectionManager,
        storageManager, firewallRepositories)
    this.unquarantinedComponentsUpdater = new UnquarantinedComponentsUpdater(iqConnectionManager, storageManager,
        quarantineStatusManager, pathFactory, firewallProperties, log)
    this.searches = searches
    this.auditManager = new AuditManager(iqConnectionManager, storageManager, ignorePatternMatcher, searches,
        pathFactory)
    this.verifyInitWaitInMillis = firewallProperties.getVerifyInitWaitInMillis() ?: DEFAULT_VERIFY_INIT_WAIT
    createIgnorePatternScheduler()
  }

  @VisibleForTesting
  DefaultNexusFirewallForArtifactory(
      final Logger log,
      final FirewallRepositories firewallRepositories,
      final IqConnectionManager iqConnectionManager,
      final FirewallProperties firewallProperties,
      final QuarantineStatusManagerProperties quarantineStatusManagerProperties,
      final IgnorePatternMatcher ignorePatternMatcher,
      final StorageManager storageManager,
      final RepositoryManager repositoryManager,
      final UnquarantinedComponentsUpdater unquarantinedComponentsUpdater,
      final PathFactory pathFactory,
      final Searches searches,
      final IqQuarantineStatusLoader iqQuarantineStatusLoader)
  {
    this.log = log
    this.firewallRepositories = firewallRepositories
    this.iqConnectionManager = iqConnectionManager
    this.storageManager = storageManager
    this.ignorePatternMatcher = ignorePatternMatcher
    def loadingCache = quarantineStatusManagerProperties.cacheStrategy
        .buildCache(storageManager, iqQuarantineStatusLoader, pathFactory, log, firewallProperties)
    this.quarantineStatusManager = new QuarantineStatusManager(loadingCache, iqConnectionManager, firewallRepositories,
        pathFactory, log)
    this.repositoryManager = repositoryManager
    this.unquarantinedComponentsUpdater = unquarantinedComponentsUpdater
    this.searches = searches
    this.auditManager = new AuditManager(iqConnectionManager, storageManager, ignorePatternMatcher, searches,
        pathFactory)
    this.verifyInitWaitInMillis = firewallProperties.getVerifyInitWaitInMillis() ?: DEFAULT_VERIFY_INIT_WAIT
  }

  void init() {
    log.info("Initializing Nexus Firewall for Artifactory plugin version '{}'...", getPluginVersion())

    // First load the repositories defined in the properties. It is of primary importance to have the plugin load
    // the list of repositories so we can enter a 'safe mode' of not accidentally allowing artifacts/ through while
    // other initializations occur. If this fails, the exception needs to propagate up to the groovy script so the
    // plugin will entirely fail to load
    repositoryManager.loadRepositoriesFromProperties()

    // The rest of the init cannot throw an exception otherwise the plugin entirely fails to load and artifacts might
    // get through that should not. Default should be DENY.
    try {
      if (iqConnectionManager.tryInitializeConnection()) {
        loadIgnorePatterns()

        repositoryManager.enableRepositoriesInIq()
      }
    }
    catch (Exception e) {
      log.error("Firewall plugin failed to initialize", e)
    }
  }

  @Override
  String getArtifactoryEdition() {
    def baseUrl = new URL('http://localhost:8082/artifactory/api/system/ping')
    HttpURLConnection connection = (HttpURLConnection) baseUrl.openConnection();
    connection.with {
      doOutput = true
      requestMethod = 'GET'
      log.info("-------------------------- GET ARTIFACTORY EDITION --------------------")
      log.info(content)
      log.info(content.text)
    }
    return "Unknown"
  }

  private CronExecutorService createIgnorePatternScheduler(final Logger log) {
    def scheduler = new CronExecutorService(0)
    scheduler.scheduleWithCronExpression({ -> loadIgnorePatterns() }, getIgnorePatternReloadCronExpression(),
        DEFAULT_IGNORE_PATTERN_RELOAD_CRON_EXPRESSION, log)
    return scheduler
  }

  void loadIgnorePatterns() {
    log.info("Loading ignore patterns")
    try {
      ignorePatternMatcher.ignorePatterns = iqConnectionManager.firewallIgnorePatterns
    }
    catch (IqConnectionException e) {
      log.error(e.message, e.cause)
    }
  }

  RepositoryPolicyEvaluationSummary getFirewallEvaluationSummary(String repositoryName) {
    return iqConnectionManager.getPolicyEvaluationSummary(repositoryName)
  }

  void beforeDownloadHandler(final RepoPath repoPath) {
    if (initializationVerified.getCount() != 0) {
      throw new CancelException('Invoked beforeDownloadHandler before initialisation.', 500)
    }

    QuarantineStatus quarantineStatus = handleDownload(repoPath)
    if (ALLOW != quarantineStatus) {
      throw new CancelException(format("Download of '%s' cancelled due to quarantine", repoPath),
          HttpStatus.SC_FORBIDDEN)
    }
  }

  @Override
  void afterDeleteHandler(final RepoPath repoPath) {
    if (initializationVerified.getCount() != 0) {
      throw new CancelException('Invoked afterDeleteHandler before initialisation.', 500)
    }

    log.trace("Handling delete request for repoKey: '$repoPath.repoKey' repoPath: '$repoPath.path'")
    handleDelete(repoPath)
  }

  @VisibleForTesting
  void handleDelete(final RepoPath repoPath) {
    if (!repoPath.isFile()) {
      log.trace("Delete event on path '{}' ignored because it is not a file.", repoPath)
      return
    }
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(repoPath.repoKey)
    if (firewallRepository == null) {
      log.trace("Delete event on path '{}' ignored because Firewall is not configured for this repository", repoPath)
      return
    }

    quarantineStatusManager.removeIqComponent(repoPath)
  }

  @VisibleForTesting
  QuarantineStatus handleDownload(final RepoPath repoPath) {
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(repoPath.repoKey)

    if (firewallRepository != null) {
      log.trace("Firewall enabled for '{}'", firewallRepository.repoKey)
      try {
        return processFirewallableAsset(firewallRepository, repoPath)
      }
      catch (IqConnectionException e) {
        // This handler is typically invoked when we need to DENY access but want to bypass saving the DENY state.
        // For example during temporary network problems.
        if (firewallRepository.mode == MODE.quarantine) {
          log.error("Denying download of '${repoPath.id}' due to problems communicating with the IQ server. Reason: ${e.message}", e)
          return DENY
        }
        log.error("Allowing download of '${repoPath.id}', but auditing failed due to problems communicating with the IQ server. Reason: ${e.message}", e)
        return ALLOW
      }
      catch (Exception e) {
        log.error("The Firewall plugin received an unhandled exception. Cannot determine if the asset should be allowed so returning a DENY.", e)
        return DENY
      }
    }
    else {
      log.debug("Firewall allowing '{}' because Firewall is not configured for this repository", repoPath)
      return ALLOW
    }
  }

  private QuarantineStatus processFirewallableAsset(
      final FirewallRepository firewallRepository,
      final RepoPath repoPath)
  {
    if (isAssetAuditable(firewallRepository, repoPath)) {
      return processAuditableAsset(firewallRepository, repoPath)
    }
    else {
      log.info(
          "Firewall allowing '{}' because Firewall is configured to ignore files of this type for this repository",
          repoPath
      )
      return ALLOW
    }
  }

  private QuarantineStatus processAuditableAsset(final FirewallRepository firewallRepository, final RepoPath repoPath) {
    if (isAssetQuarantinable(firewallRepository, repoPath)) {
      return processQuarantineableAsset(firewallRepository, repoPath)
    }
    else {
      log.info("Firewall allowing '{}' because it's not a quarantinable asset", repoPath)
      updateAuditStatusIfNeeded(repoPath)
      return ALLOW
    }
  }

  private QuarantineStatus processQuarantineableAsset(
      final FirewallRepository firewallRepository,
      final RepoPath repoPath)
  {
    unquarantinedComponentsUpdater.requestAndUpdateUnquarantinedComponents(firewallRepository)

    QuarantineStatus quarantineStatus = checkQuarantineStatus(repoPath)
    if (quarantineStatus != ALLOW) {
      log.info("Firewall blocking '{}' because quarantine status = {}",
          repoPath.path, quarantineStatus != null ? quarantineStatus : "PENDING")
      return DENY
    }
    else {
      log.info("Firewall allowing '{}' because it passed policy checks", repoPath)
      return ALLOW
    }
  }

  @VisibleForTesting
  boolean isAssetAuditable(final FirewallRepository firewallRepository, final RepoPath repoPath) {
    return auditManager.isAssetAuditable(firewallRepository, repoPath)
  }

  boolean isAssetQuarantinable(final FirewallRepository firewallRepository, final RepoPath repoPath) {
    ItemInfo itemInfo = storageManager.getItemInfo(repoPath)
    if (null == itemInfo) {
      log.warn("Firewall is unable to obtain item info for '{}' and therefore will treat it as quarantinable", repoPath)
      return true
    }

    Date firewallEnabledTimestamp = storageManager.getFirewallEnabledTimestamp(firewallRepository.repoKey)
    Date artifactCreated = new Date(itemInfo.created)

    log.trace("Firewall quarantinable check for '{}', fw enabled TS = {}, artifact created = {}",
        repoPath,
        firewallEnabledTimestamp,
        artifactCreated
    )

    return (repositoryManager.isQuarantineEnabledForRepo(firewallRepository.repoKey)
        && null != firewallEnabledTimestamp
        && artifactCreated.after(firewallEnabledTimestamp))
  }

  QuarantineStatus checkQuarantineStatus(final RepoPath repoPath) {
    return quarantineStatusManager.getQuarantineStatus(repoPath)
  }

  /**
   * determines if the given repo artifact has already been marked for audit and, if not, will mark it so in IQ
   */
  def updateAuditStatusIfNeeded(final RepoPath repoPath) {
    auditManager.updateAuditStatusIfNeeded(repoPath)
  }

  void propertyEventHandler(final String name) {
    //storageManager.checkPropertyAccess(name)
  }

  /**
   * Audit all configured repositories.
   */
  @Override
  void auditRepositories() {
    if (!auditPassAccomplished) {
      auditManager.audit(firewallRepositories)
      auditPassAccomplished = true
    }
    else {
      log.debug('audit pass already ran since startup, skipping')
    }
  }

  /**
   * Ensure that the plugin is fully initialized and ready to work.
   */
  boolean isReady() {
    if (isInitializationRequired()) {
      verifyInit()
    }
    initializationVerified.await(verifyInitWaitInMillis, TimeUnit.MILLISECONDS)
  }


  /**
   * The first thread reaching this function will call countDown() and return true.
   * All subsequent invocations will return false.
   */
  boolean isInitializationRequired() {
    synchronized (initializationVerified) { // only one thread will countDown() and return true
      if (initializationVerified.getCount() == UNINITIALIZED) {
        initializationVerified.countDown()
        return true
      }
    }
    return false
  }

  /**
   * during plugin initialization we are currently unable to read repository properties but they are available to
   * the event handlers;  therefore, we will defer some of the initialization to the event handlers (i.e. here)
   *
   * NOTES:
   *
   * We disable repositories only when they are marked as disabled in firewall.properties. If the repository was
   * previously marked as audit, we disable the repo in IQ. If the repository was previously marked as quarantine, we
   * disable the repo and disable quarantine in IQ.
   *
   * We load repositories from storage in case any were missing from firewall.properties. We set the last
   * firewall mode in storage when verifyRepositoryInitProperties is called. Therefore, loadRepositoriesFromStorage
   * must be called before verifyRepositoryInitProperties; otherwise, we will lose the firewall mode for repositories
   * that were missing from firewall.properties.
   */
  void verifyInit() {
    synchronized (initializationVerified) {
      log.debug('Beginning verification of initialization data')
      if (initializationVerified.getCount() != INITIALIZATION_IN_PROGRESS) {
        throw new CancelException("Initialisation already ran since startup. initializationVerified has invalid state" +
            " $initializationVerified.count", 500)
      }

      repositoryManager.initialize()
      initializationVerified.countDown() // latch is now 0 and downloads can be processed
      auditRepositories()
      log.info('Firewall initialization completed')
    }
  }
}
