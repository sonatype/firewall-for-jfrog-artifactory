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
import com.sonatype.clm.dto.model.repository.QuarantinedComponentReport
import com.sonatype.insight.brain.client.ConfigurationClient
import com.sonatype.insight.brain.client.FirewallClient
import com.sonatype.insight.client.utils.HttpClientUtils.Configuration
import com.sonatype.iq.artifactory.restclient.RestClient.Base
import com.sonatype.iq.artifactory.restclient.RestClient.Repository

import org.apache.http.client.HttpResponseException

class RestClientFactory
{
  Base forConfiguration(final RestClientConfiguration config) {
    if (config == null) {
      throw new IllegalArgumentException("REST client configuration missing")
    }
    return new BaseClient(config.getConfig())
  }

  private class BaseClient
      implements Base
  {
    private Closure<ConfigurationClient> configurationClientFactory

    private Closure<Repository> repositoryClientFactory

    BaseClient(final Configuration config) {
      configurationClientFactory = { new ConfigurationClient(config) }
      repositoryClientFactory = {
        String repositoryManagerInstanceId, String repositoryPublicId, RepositoryManagerType repositoryManagerType
          ->
          new RepositorySpecificClient(config, repositoryManagerInstanceId, repositoryPublicId,
              repositoryManagerType)
      }
    }

    @Override
    void validateConfiguration() throws IOException {
      try {
        configurationClientFactory().validateConfiguration()
      }
      catch (IOException e) {
        throw handleError(e)
      }
    }

    @Override
    void validateServerVersion(String version) throws IOException {
      try {
        configurationClientFactory().validateServerVersion(version)
      }
      catch (IOException e) {
        throw handleError(e)
      }
    }

    protected IOException handleError(IOException e) {
      if (e instanceof HttpResponseException) {
        HttpResponseException re = (HttpResponseException) e
        return new HttpException(re.getStatusCode(), re.getMessage(), re)
      }
      return e
    }

    @Override
    Repository forRepository(final String repositoryManagerInstanceId,
                             final String repositoryPublicId,
                             final RepositoryManagerType repositoryManagerType)
    {
      return repositoryClientFactory(repositoryManagerInstanceId, repositoryPublicId, repositoryManagerType)
    }

    @Override
    FirewallIgnorePatterns getFirewallIgnorePatterns() throws IOException {
      try {
        return configurationClientFactory().getFirewallIgnorePatterns()
      }
      catch (HttpResponseException e) {
        if (e.getStatusCode() == 404) {
          throw new UnsupportedOperationException("IQ Server doesn't support firewall ignore patterns, "
              + "upgrade it to version 1.35, or newer, to support it.", e)
        }
        throw handleError(e)
      }
    }
  }

  private class RepositorySpecificClient
      extends BaseClient
      implements Repository
  {
    private Closure<FirewallClient> firewallClientFactory

    RepositorySpecificClient(final Configuration config,
                             final String repositoryManagerInstanceId,
                             final String repositoryPublicId,
                             final RepositoryManagerType repositoryManagerType)
    {
      super(config)
      this.firewallClientFactory = {
        new FirewallClient(config, repositoryManagerInstanceId, repositoryPublicId, repositoryManagerType.resourcePath)
      }
    }

    @Override
    void setEnabled(boolean enabled) throws IOException {
      firewallClientFactory().setEnabled(enabled)
    }

    @Override
    void setQuarantine(final boolean enabled) throws IOException {
      firewallClientFactory().setQuarantine(enabled)
    }

    @Override
    void evaluateComponents(final RepositoryComponentEvaluationDataRequestList componentEvaluationDataRequestList)
        throws IOException
    {
      firewallClientFactory().evaluateComponents(componentEvaluationDataRequestList)
    }

    @Override
    RepositoryComponentEvaluationDataList evaluateComponentWithQuarantine(
        RepositoryComponentEvaluationDataRequestList repositoryComponentEvaluationDataRequestList) throws IOException
    {
      firewallClientFactory().evaluateComponentWithQuarantine(repositoryComponentEvaluationDataRequestList)
    }

    @Override
    RepositoryPolicyEvaluationSummary getPolicyEvaluationSummary() throws IOException {
      firewallClientFactory().getPolicyEvaluationSummary()
    }

    @Override
    QuarantinedComponentReport getQuarantinedComponentReportUrl(String pathname) throws IOException {
      firewallClientFactory().getQuarantinedComponentReport(pathname)
    }

    @Override
    void removeComponent(String pathname) throws IOException {
      firewallClientFactory().removeComponent(pathname)
    }

    @Override
    UnquarantinedComponentList getUnquarantinedComponents(final long sinceUtcTimestamp) throws IOException {
      try {
        firewallClientFactory().getUnquarantinedComponents(sinceUtcTimestamp)
      }
      catch (HttpResponseException e) {
        if (e.getStatusCode() == 405) {
          throw new UnsupportedOperationException("IQ Server doesn't support unquarantined component updates, " +
              "upgrade it to version 1.20, or newer, to support it.", e)
        }
        throw e
      }
    }
  }
}
