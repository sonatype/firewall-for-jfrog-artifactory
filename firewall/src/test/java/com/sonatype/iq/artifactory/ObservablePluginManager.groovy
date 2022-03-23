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

import org.artifactory.exception.CancelException
import org.artifactory.fs.FileInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.DENY

class ObservablePluginManager
{
  final String repoKey
  final RepoPath repoPath
  final NexusFirewallForArtifactory firewall
  final StorageManager storageManager
  final String sha1
  final ItemInfo itemInfo
  final FileInfo fileInfo

  Exception lastException
  boolean wasCancelled

  ObservablePluginManager(final String repoKey,
                          final RepoPath repoPath,
                          final NexusFirewallForArtifactory firewall,
                          final StorageManager storageManager,
                          final String sha1,
                          final ItemInfo itemInfo,
                          final FileInfo fileInfo) {
    this.repoKey = repoKey
    this.repoPath = repoPath
    this.firewall = firewall
    this.storageManager = storageManager
    this.sha1 = sha1
    this.itemInfo = itemInfo
    this.fileInfo = fileInfo
  }

  def init() {
    clearLastResult()
    firewall.init()
  }

  def verifyInit() {
    firewall.verifyInit()
  }

  def getInitializationVerified() {
    return firewall.initializationVerified
  }

  boolean hasFirewallEnabledTimestamp() {
    return null != storageManager.getFirewallEnabledTimestamp(repoKey)
  }

  boolean isFirewallEnabled() {
    return firewall.repositoryManager.isFirewallEnabledForRepo(repoKey) // && null != timestamp && timestamp <= new Date()
  }

  boolean isMarkedQuarantined() {
    return DENY.name() == storageManager.repositories.getProperty(repoPath, StorageManager.QUARANTINE)
  }

  boolean isMarkedAllowed() {
    return ALLOW.name() == storageManager.repositories.getProperty(repoPath, StorageManager.QUARANTINE)
  }

  boolean hasVerifyInitRun() {
    return firewall.initializationVerified.count == 0
  }

  def onBeforeDownload() {
    clearLastResult()
    try {
      firewall.beforeDownloadHandler(repoPath)
    } catch(CancelException e) {
      wasCancelled = true
    } catch(Exception e) {
      lastException = e
    }
  }

  def clearLastResult() {
    lastException = null
  }

  boolean hasException() {
    return null != lastException
  }

  boolean wasCancelled() {
    return wasCancelled
  }
}
