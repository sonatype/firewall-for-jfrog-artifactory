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

import com.sonatype.iq.artifactory.FirewallProperties.MODE

import org.artifactory.repo.Repositories
import org.artifactory.repo.RepositoryConfiguration
import org.slf4j.Logger

import static com.sonatype.iq.artifactory.FirewallProperties.MODE.audit
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.disabled
import static com.sonatype.iq.artifactory.FirewallProperties.MODE.quarantine

/**
 * Encapsulate working with repositories within JFrog Artifactory and IQ.
 *
 * Important details
 * <li>Firewall only supports remote repositories (what NXRM calls a proxy)</li>
 * <li>Artifactory creates a '-cache' repository for every remote. e.g. 'central' and 'central-cache'</li>
 * <li>This 'cache' repository is real (i.e. physical)</li>
 * <li>This 'cache' repository is <b>NOT</b> returned by any of the `get*Repositories` methods of the Repositories
 * class</li>
 * <li>Metadata is stored against the cache repository. However, we can get/set properties through the main
 * definition</li>
 * <li>On the IQ side we don't care or need to know about this special repo. It should not have or use the '-cache'</li>
 *
 * How it works
 * <li>Only real remote/proxy repos can be used</li>
 * <li>It doesn't matter if the real remote, or the cache remote behind the real remote is used. In IQ the real will
 * be used.</li>
 * <li>If the call to initialize the repo in IQ fails, the repo will be in a default DENY state</li>
 */
class RepositoryManager
{
  private final Logger log

  private final Repositories artifactoryRepositories

  private final FirewallProperties firewallProperties

  private final IqConnectionManager iqConnectionManager

  private final StorageManager storageManager

  private final FirewallRepositories firewallRepositories

  private List<String> reposToVerifyForFirewallEnabledAfterInit = Collections.synchronizedList([])

  private List<DeferredRepoPropertyUpdates> reposToVerifyForRepositoryPropertiesAfterInit = Collections.synchronizedList([])

  private List<String> reposToDisableAfterInit = Collections.synchronizedList([])

  Date startTime

  RepositoryManager(Logger log, Repositories artifactoryRepositories, FirewallProperties firewallProperties,
                    IqConnectionManager iqConnectionManager, StorageManager storageManager,
                    FirewallRepositories firewallRepositories)
  {
    this.log = log
    this.artifactoryRepositories = artifactoryRepositories
    this.firewallProperties = firewallProperties
    this.iqConnectionManager = iqConnectionManager
    this.storageManager = storageManager
    this.firewallRepositories = firewallRepositories
    this.startTime = new Date()
  }

  void loadRepositoriesFromProperties() {
    firewallProperties.getRepositories().each {
      maybeAddFirewallRepository(it.key, it.value)
    }
  }

  private boolean maybeAddFirewallRepository(String givenRepoKey, MODE mode) {
    // First, verify repository even exists in Artifactory by loading its configuration
    RepositoryConfiguration repositoryConfiguration = getRepositoryConfiguration(givenRepoKey)
    if (!repositoryConfiguration) {
      log.warn("Repository '${givenRepoKey}' was not found in Artifactory. Skipping...")
      return
    }

    FirewallRepository firewallRepository = FirewallRepository.of(repositoryConfiguration, mode)

    // The real repo key may be different for cache repos which back remotes
    String realRepoKey = firewallRepository.repoKey

    log.debug(
        "Found repository '${realRepoKey}' of type '${firewallRepository.type}' using repoKey '${givenRepoKey}'")

    // Second, verify it is a supported repository type
    if (!isSupportedRepository(firewallRepository.type)) {
      log.warn("Repository '${realRepoKey}' is not a supported type. Found type " +
          "'${firewallRepository.type}', but Firewall can only be enabled on type 'remote'. Skipping...")
      return
    }
    log.debug("Verified repository '${realRepoKey}' in Artifactory")

    return firewallRepositories.add(realRepoKey, firewallRepository)
  }

  void enableRepositoriesInIq() {
    log.debug("Enabling repositories in IQ")
    firewallRepositories.eachParallel { FirewallRepository firewallRepository ->
      try {
        enableRepositoryInIQ(firewallRepository)
      }
      catch (IqConnectionException e) {
        log.error(e.message, e.cause)
      }
      catch (Exception e) {
        log.error("Firewall was unable to initialize repository '${firewallRepository.repoKey}' in IQ", e)
        log.error("Repository '${firewallRepository.repoKey}' will be in default DENY state")
        // NOTE: we do NOT return here as the repo still needs be firewalled, so needs to be put in the map
      }
    }
    log.debug("Enabled repositories in IQ")
  }

  /**
   * Check if Firewall is enabled for a repository.
   * @return true for any repo in audit mode <b>OR</b> quarantine mode,
   *         false if repository not found <b>OR</b> disabled mode
   */
  boolean isFirewallEnabledForRepo(final String repoKey) {
    // if the repo is in our local map, then we know it is a firewall repo
    def firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(repoKey)
    return firewallRepository?.mode == audit || firewallRepository?.mode == quarantine
  }

  /**
   * Check if quarantine is enabled for a repository
   * @return true for any repo in quarantine mode, false for a repository in audit mode, false if repository not found
   * @see #isFirewallEnabledForRepo(String) to check if either is enabled (i.e. Firewall on)
   */
  boolean isQuarantineEnabledForRepo(final String repoKey) {
    FirewallRepository firewallRepository = firewallRepositories.getEnabledFirewallRepoByKey(repoKey)

    // if we aren't aware of it return false
    if (!firewallRepository) {
      return false
    }

    return firewallRepository.mode == quarantine
  }

  private RepositoryConfiguration getRepositoryConfiguration(final String repoKey) {
    RepositoryConfiguration repositoryConfiguration = artifactoryRepositories.getRepositoryConfiguration(repoKey)

    if (repositoryConfiguration && isCacheRepoKey(repoKey)) {
      // We have a repo name whose name ends with '-cache'. Attempt to load the real remote repo for it as that is
      // the one we want to use with the cache & IQ communication.
      // Note if a remote is named 'foo-cache' then the cache repo name is 'foo-cache-cache'. If 'foo-cache-cache' is
      // used in config, then 'foo-cache' is the real repo which will be used.
      // Also note that while Artifactory allows a '-cache' suffix to be used in both remote and virtual repository
      // types, it does not allow it for local repos. The `isSupportedRepository` method will verify the repo type
      String realRepoKey = repoKey.substring(0, repoKey.lastIndexOf('-cache'))
      log.trace("Firewall interpreting repo with key '{}' as '{}'", repoKey, realRepoKey)
      repositoryConfiguration = artifactoryRepositories.
          getRepositoryConfiguration(realRepoKey) ?: repositoryConfiguration
    }
    return repositoryConfiguration
  }

  private void enableRepositoryInIQ(final FirewallRepository firewallRepository) {
    switch (firewallRepository.mode) {
      case audit:
        enableAudit(firewallRepository.repoKey)
        break
      case quarantine:
        enableQuarantine(firewallRepository.repoKey)
        break
      case disabled:
        disableRepository(firewallRepository.repoKey)
        break
      default:
        log.warn("Firewall was unable to initialize repository '${firewallRepository.repoKey}. Unknown mode " +
            "'${firewallRepository.mode}'")
    }
  }

  private void enableAudit(final String repoKey) {
    log.info("Firewall is enabling repository '${repoKey}' in IQ with mode 'audit'")
    iqConnectionManager.enableAudit(repoKey)
    log.info("Firewall enabled repository '${repoKey}' in IQ with mode 'audit'")
    reposToVerifyForRepositoryPropertiesAfterInit.add(
        new DeferredRepoPropertyUpdates(repoKey, getFirewallMode(repoKey), getIqRepositoryUrl(repoKey)))
  }

  private void enableQuarantine(final String repoKey) {
    log.info("Firewall is enabling repository '${repoKey}' in IQ with mode 'quarantine'")
    iqConnectionManager.enableQuarantine(repoKey)
    log.info("Firewall enabled repository '${repoKey}' in IQ with mode 'quarantine'")
    reposToVerifyForFirewallEnabledAfterInit.add(repoKey)
    reposToVerifyForRepositoryPropertiesAfterInit.add(
        new DeferredRepoPropertyUpdates(repoKey, getFirewallMode(repoKey), getIqRepositoryUrl(repoKey)))
  }

  private void disableRepository(final String repoKey) {
    log.info("Firewall is setting repository '${repoKey}' to mode 'disabled'")
    reposToDisableAfterInit.add(repoKey)
  }

  private static boolean isSupportedRepository(final String type) {
    // Firewall currently only supports remote(aka proxy) repositories
    return "remote" == type
  }

  private static boolean isCacheRepoKey(final String repoKey) {
    return repoKey.endsWith('-cache')
  }

  void disableRepositories() {
    reposToDisableAfterInit.each { String repoKey ->
      disableRepositoryInIQ(repoKey)
      storageManager.removeFirewallPropertiesFromRepository(repoKey)
    }
  }

  private void disableRepositoryInIQ(final String repoKey) {
    switch (storageManager.getFirewallMode(repoKey)) {
      case audit:
        iqConnectionManager.disableAudit(repoKey)
        break
      case quarantine:
        iqConnectionManager.disableQuarantine(repoKey)
        break
    }
  }

  void verifyFirewallEnabled() {
    reposToVerifyForFirewallEnabledAfterInit.each { String repoKey ->
      storageManager.markFirewallEnabledIfNecessary(repoKey, startTime)
    }
  }

  void verifyRepositoryInitProperties() {
    reposToVerifyForRepositoryPropertiesAfterInit.each { DeferredRepoPropertyUpdates update ->
      storageManager.setFirewallMode(update.repoKey, update.firewallMode.name())
      if (update.reportUrl) {
        storageManager.setIqRepositoryUrl(update.repoKey, update.reportUrl)
      }
    }
  }

  private MODE getFirewallMode(final String repoKey) {
    FirewallRepository firewallRepository = firewallRepositories.getEnabledOrDisabledFirewallRepoByKey(repoKey)
    return firewallRepository.mode
  }

  private String getIqRepositoryUrl(String repoKey) {
    def normalizedRepoKey = firewallRepositories.getNormalizedRepoKey(repoKey)
    return iqConnectionManager.getPolicyEvaluationSummary(normalizedRepoKey).reportUrl
  }

  def loadRepositoriesFromStorage() {
    artifactoryRepositories.remoteRepositories.each { String repoKey ->
      MODE mode = storageManager.getFirewallMode(repoKey)
      if (mode != null && maybeAddFirewallRepository(repoKey, mode)) {
        log.warn("Repository configuration for '${repoKey}' in mode '${mode}' missing from firewall.properties")
      }
    }
  }

  def verifyInitialAudits() {
    firewallRepositories.each{ FirewallRepository repo ->
      def firewallAuditPass = storageManager.getFirewallAuditPass(repo.repoKey)
      log.trace("firewallAuditPass for ${repo.repoKey} = $firewallAuditPass")
      if (firewallAuditPass) {
        repo.audited = true
      }
    }
  }

  /**
   * Perform deferred initialization that cannot be done during plugin startup due to
   * lack of access to Properties on Repositories.
   */
  void initialize() {
    disableRepositories()
    loadRepositoriesFromStorage()
    verifyFirewallEnabled()
    verifyRepositoryInitProperties()
    verifyInitialAudits()
  }
}
