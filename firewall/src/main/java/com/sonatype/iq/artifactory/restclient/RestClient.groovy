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
package com.sonatype.iq.artifactory.restclient

import com.sonatype.clm.dto.model.component.FirewallIgnorePatterns
import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataList
import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataRequestList
import com.sonatype.clm.dto.model.component.UnquarantinedComponentList
import com.sonatype.clm.dto.model.policy.RepositoryPolicyEvaluationSummary

interface RestClient
{
  interface Base
  {
    void validateConfiguration() throws IOException;

    void validateServerVersion(String version) throws IOException;

    Repository forRepository(final String repositoryManagerInstanceId,
                             final String repositoryPublicId,
                             final RepositoryManagerType repositoryManagerType);

    FirewallIgnorePatterns getFirewallIgnorePatterns() throws IOException;
  }

  interface Repository
  {
    void setEnabled(boolean enabled) throws IOException;

    void setQuarantine(final boolean enabled) throws IOException;

    void removeComponent(String pathname) throws IOException;

    void evaluateComponents(final RepositoryComponentEvaluationDataRequestList componentEvaluationDataRequestList)
        throws IOException;

    RepositoryComponentEvaluationDataList evaluateComponentWithQuarantine(
        final RepositoryComponentEvaluationDataRequestList repositoryComponentEvaluationDataRequest) throws IOException;

    RepositoryPolicyEvaluationSummary getPolicyEvaluationSummary() throws IOException;

    UnquarantinedComponentList getUnquarantinedComponents(long sinceUtcTimestamp) throws IOException;
  }
}
