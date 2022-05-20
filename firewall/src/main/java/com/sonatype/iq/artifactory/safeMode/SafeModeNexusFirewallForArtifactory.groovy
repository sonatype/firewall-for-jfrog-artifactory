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
package com.sonatype.iq.artifactory.safeMode

import com.sonatype.clm.dto.model.policy.RepositoryPolicyEvaluationSummary
import com.sonatype.iq.artifactory.NexusFirewallForArtifactory

import org.apache.http.HttpStatus
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPath
import org.slf4j.Logger

import static java.lang.String.format

/**
 * 'Safe mode' implementation of the plugin for when there is no firewall.properties configuration.
 * Puts the entire repository manager in a safe mode where no artifacts can be downloaded.
 */
class SafeModeNexusFirewallForArtifactory
    implements NexusFirewallForArtifactory
{
  final Logger log

  private final String ATTENTION_GRABBER = "***************************************************************************"

  private final String GRABBER_LINE = "***** "

  private final String SAFE_MODE = "Nexus Firewall for Artifactory is operating in safe mode and will deny ALL " +
      "artifact requests until this problem is fixed"

  private final String reason

  SafeModeNexusFirewallForArtifactory(final String reason, final Logger log) {
    this.reason = reason
    this.log = log
  }

  @Override
  void init() {
    logError(reason)
  }

  @Override
  String getArtifactoryEdition() {
    // no-op
  }

  @Override
  void loadIgnorePatterns() {
    // no-op
  }

  @Override
  RepositoryPolicyEvaluationSummary getFirewallEvaluationSummary(final String repositoryName) {
    logError("Unable to fetch Firewall evaluation summary report")
    // no-op
    return null
  }

  @Override
  void beforeDownloadHandler(final RepoPath repoPath) {
    def msg = format("Download of '%s' cancelled due to mis-configured firewall plugin", repoPath)
    logError(msg)
    throw new CancelException(msg, HttpStatus.SC_FORBIDDEN)
  }

  @Override
  void afterDeleteHandler(RepoPath itemInfo) {
    // no-op
  }

  @Override
  void verifyInit() {
    // no-op
  }

  @Override
  void propertyEventHandler(final String name) {
    // no-op
  }

  @Override
  def getIgnorePatternReloadCronExpression() {
    return '0 0 0 ? * * 1970'
  }

  @Override
  void auditRepositories() {
    // no-op
  }

  @Override
  boolean isReady() {
    return true
  }

  @Override
  boolean isInitializationRequired() {
    return false
  }

  private void logError(String error) {
    log.error("""
${ATTENTION_GRABBER}
${GRABBER_LINE} ${error}
${GRABBER_LINE} ${SAFE_MODE}
${ATTENTION_GRABBER}""")
  }
}
