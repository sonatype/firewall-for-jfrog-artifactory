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

import java.text.ParseException

import com.sonatype.iq.artifactory.FirewallProperties.MODE

import org.apache.http.HttpStatus
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.slf4j.Logger

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.DENY
import static java.lang.System.currentTimeMillis

class StorageManager
{
  static final String FIREWALL_PREFIX = 'firewall.'

  static final String QUARANTINE = FIREWALL_PREFIX + 'quarantine'

  static final String IQ_REPOSITORY_URL = FIREWALL_PREFIX + 'iqRepositoryUrl'

  static final String FIREWALL_MODE = FIREWALL_PREFIX + 'mode'

  static final String HASH = FIREWALL_PREFIX + 'sha1'

  static final String AUDIT_TIMESTAMP = FIREWALL_PREFIX + 'auditTimestamp'

  static final String FIREWALL_ENABLED_TIMESTAMP = FIREWALL_PREFIX + 'enabledTimestamp'

  static final String FIREWALL_QUARANTINE_LAST_UPDATED_TIMESTAMP = FIREWALL_PREFIX + 'quarantineLastUpdatedTimestamp'
  
  static final String REPOSITORY_AUDIT_COMPLETED = FIREWALL_PREFIX + 'repositoryAuditCompleted'

  static final String TIMESTAMP_FORMAT = 'yyyy-MM-dd HH:mm:ss.SSS Z'

  static final String[] FIREWALL_PROPERTIES = [
      AUDIT_TIMESTAMP,
      FIREWALL_ENABLED_TIMESTAMP,
      FIREWALL_MODE,
      FIREWALL_QUARANTINE_LAST_UPDATED_TIMESTAMP,
      IQ_REPOSITORY_URL,
      QUARANTINE,
      REPOSITORY_AUDIT_COMPLETED
  ]

  final Repositories repositories

  final PathFactory pathFactory

  final FirewallRepositories firewallRepositories

  private final Logger log

  StorageManager(Repositories repositories, PathFactory pathFactory, FirewallRepositories firewallRepositories, Logger log) {
    this.log = log
    this.repositories = repositories
    this.pathFactory = pathFactory
    this.firewallRepositories = firewallRepositories
  }

  def quarantine(RepoPath repoPath) {
    setProperty(repoPath, QUARANTINE, DENY.name())
  }

  def unQuarantine(RepoPath repoPath) {
    setProperty(repoPath, QUARANTINE, ALLOW.name())
  }

  def maybeGetQuarantineStatus(final RepoPath repoPath) {
    String quarantineStatus = getProperty(repoPath, QUARANTINE)
    if (quarantineStatus == null) {
      return null
    }
    return quarantineStatus != ALLOW.name() ? DENY : ALLOW
  }

  String getHash(RepoPath repoPath) {
    return repositories.getFileInfo(repoPath).sha1
  }

  def assignAuditTimestamp(RepoPath repoPath) {
    setProperty(repoPath, AUDIT_TIMESTAMP, '' + currentTimeMillis())
  }

  boolean hasAuditTimestamp(RepoPath repoPath) {
    return getProperty(repoPath, AUDIT_TIMESTAMP)
  }

  Date getFirewallEnabledTimestamp(String repo) {
    RepoPath repoPath = pathFactory.createRepoPath(repo)
    String enabledTimestamp = getProperty(repoPath, FIREWALL_ENABLED_TIMESTAMP)
    try {
      return null != enabledTimestamp ? Date.parse(TIMESTAMP_FORMAT, enabledTimestamp) : null
    }
    catch (ParseException pe) {
      return null
    }
  }

  void markFirewallEnabledIfNecessary(final String repo, final Date firewallStartTimestamp) {
    Date enabledTime = getFirewallEnabledTimestamp(repo)
    if (null == enabledTime) {
      RepoPath repoPath = pathFactory.createRepoPath(repo)
      setProperty(repoPath, FIREWALL_ENABLED_TIMESTAMP, formatDate(firewallStartTimestamp.time))
    }
  }

  void setIqRepositoryUrl(final String repoKey, final String iqRepositoryUrl) {
    setProperty(pathFactory.createRepoPath(repoKey), IQ_REPOSITORY_URL, iqRepositoryUrl)
  }

  /**
   * look up the ItemInfo for the given RepoPath
   *
   * @return ItemInfo if item exists in the repo, null otherwise
   */
  ItemInfo getItemInfo(RepoPath repoPath) {
    try {
      return repositories.getItemInfo(repoPath)
    }
    catch (Exception e) {
      return null
    }
  }

  def checkPropertyAccess(final String name) {
    if (name.startsWith(FIREWALL_PREFIX)) {
      throw new CancelException("Cannot modify read-only property '${name}'.", HttpStatus.SC_FORBIDDEN)
    }
  }

  String getProperty(final RepoPath repoPath, final String property) {
    return repositories.getProperty(repoPath, property)
  }

  def setProperty(final RepoPath repoPath, final String property, final String value) {
    repositories.setProperty(repoPath, property, value)
  }

  void deleteQuarantineStatus(final RepoPath repoPath) {
    repositories.deleteProperty(repoPath, QUARANTINE)
  }

  def setQuarantineLastUpdateTimestamp(final String repo, final long timestamp) {
    RepoPath repoPath = pathFactory.createRepoPath(repo)
    setProperty(repoPath, FIREWALL_QUARANTINE_LAST_UPDATED_TIMESTAMP, formatDate(timestamp))
  }

  Date getFirewallLastQuarantineUpdateTimestamp(final String repo) {
    RepoPath repoPath = pathFactory.createRepoPath(repo)
    String quarantineLastUpdatedTimestamp = getProperty(repoPath, FIREWALL_QUARANTINE_LAST_UPDATED_TIMESTAMP)
    try {
      return null != quarantineLastUpdatedTimestamp ?
          Date.parse(TIMESTAMP_FORMAT, quarantineLastUpdatedTimestamp) : null
    }
    catch (ParseException pe) {
      return null
    }
  }

  /**
   * Mark with a property the time this repository completed a full audit. As a side-effect it also marks the in-memory
   * instance as audited.
   */
  def setRepositoryAudited(final FirewallRepository repo, final long timestamp) {
    RepoPath repoPath = pathFactory.createRepoPath(repo.repoKey)
    setProperty(repoPath, REPOSITORY_AUDIT_COMPLETED, formatDate(timestamp))
    repo.audited = true
  }

  /**
   * Look up this property directly, bypassing cache
   */
  def getFirewallAuditPass(final String repoKey) {
    return repositories.getProperty(pathFactory.createRepoPath(repoKey), REPOSITORY_AUDIT_COMPLETED)
  }

  private String formatDate(long timestamp) {
    new Date(timestamp).format(TIMESTAMP_FORMAT)
  }

  def setFirewallMode(final String repoKey, final String mode) {
    setProperty(pathFactory.createRepoPath(repoKey), FIREWALL_MODE, mode)
  }

  MODE getFirewallMode(final String repoKey) {
    def firewallMode = getProperty(pathFactory.createRepoPath(repoKey), FIREWALL_MODE)
    return firewallMode ? MODE.valueOf(firewallMode) : null
  }

  def removeFirewallPropertiesFromRepository(final String repoKey) {
    FIREWALL_PROPERTIES.each { property ->
      repositories.deleteProperty(pathFactory.createRepoPath(repoKey), property)
    }
  }
}
