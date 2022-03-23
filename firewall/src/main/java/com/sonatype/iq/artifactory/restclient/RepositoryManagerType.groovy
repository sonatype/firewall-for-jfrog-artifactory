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

import com.sonatype.insight.brain.client.FirewallClient

enum RepositoryManagerType
{
  NEXUS(FirewallClient.NEXUS_RESOURCE_PATH), ARTIFACTORY(FirewallClient.ARTIFACTORY_RESOURCE_PATH);

  RepositoryManagerType(final String resourcePath) {
    this.resourcePath = resourcePath
  }

  public final String resourcePath
}
