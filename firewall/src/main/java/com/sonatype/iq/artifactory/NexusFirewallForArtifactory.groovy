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

import com.sonatype.clm.dto.model.policy.RepositoryPolicyEvaluationSummary

import org.artifactory.repo.RepoPath

trait NexusFirewallForArtifactory
{
  abstract void init()

  String getPluginVersion() {
    // note: this only returns a value when bundled as a jar (i.e. in a live environment). Tests will return 'unknown'
    return this.getClass().getPackage()?.getImplementationVersion() ?: 'unknown'
  }

  abstract String getArtifactoryEdition()

  abstract void loadIgnorePatterns()

  abstract RepositoryPolicyEvaluationSummary getFirewallEvaluationSummary(String repositoryName)

  abstract void beforeDownloadHandler(final RepoPath repoPath)

  abstract void afterDeleteHandler(RepoPath repoPath)

  abstract void verifyInit()

  abstract void propertyEventHandler(final String name)

  abstract def getIgnorePatternReloadCronExpression()

  abstract void auditRepositories()

  /**
   * Determine if the plugin initialization state is such that component requests can be correctly evaluated.
   */
  abstract boolean isReady()

  /**
   * Checks initialisation status.
   *
   * @return true if the initialisation has been started, otherwise false
   */
  abstract boolean isInitializationRequired()
}
