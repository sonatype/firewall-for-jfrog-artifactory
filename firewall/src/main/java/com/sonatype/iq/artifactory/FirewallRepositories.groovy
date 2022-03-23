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

import groovyx.gpars.GParsPool

import static com.sonatype.iq.artifactory.FirewallProperties.MODE.disabled

/**
 * Data class to hold a map of {@link FirewallRepository} instances that the plugin is aware of
 */
class FirewallRepositories
{
  private final Map<String, FirewallRepository> repos = new HashMap<>()

  private final Set<FirewallRepository> uniqueRepos = new HashSet<>()

  boolean add(final String repoKey, final FirewallRepository firewallRepository) {
    boolean addFirewallRepository = !repos.containsKey(repoKey)
    if (addFirewallRepository) {
      repos.put(repoKey, firewallRepository)

      // Also store with '-cache' suffix (so when the '-cache' name is used, we automatically return the real repo)
      repos.put("${repoKey}-cache".toString(), firewallRepository)

      uniqueRepos.add(firewallRepository)
    }
    return addFirewallRepository
  }

  FirewallRepository getEnabledFirewallRepoByKey(final String repoKey) {
    FirewallRepository firewallRepository = repos.get(repoKey)
    if (firewallRepository == null || firewallRepository.mode == disabled) {
      return null
    }
    return firewallRepository
  }

  FirewallRepository getEnabledOrDisabledFirewallRepoByKey(final String repoKey) {
    return repos.get(repoKey)
  }

  /**
   * Returns the normalized repoKey by first getting the registered firewall repository for the repoKey.
   */
  String getNormalizedRepoKey(final String repoKey) {
    FirewallRepository firewallRepository = getEnabledOrDisabledFirewallRepoByKey(repoKey)
    return firewallRepository?.repoKey ?: repoKey
  }

  void each(Closure closure) {
    uniqueRepos.each(closure)
  }

  void eachParallel(Closure closure) {
    GParsPool.withPool(20) {
      uniqueRepos.eachParallel(closure)
    }
  }

  def find(Closure closure) {
    uniqueRepos.find(closure)
  }
  
  def findAll(Closure closure) {
    uniqueRepos.findAll(closure)
  }

  int count() {
    return repos.size()
  }

  /**
   * A Repository is qualified for a full audit if it hasn't had one before and isn't presently disabled.
   */
  def reposToAudit() {
    uniqueRepos.findAll { FirewallRepository firewallRepository ->
      !firewallRepository.audited && firewallRepository.mode != disabled
    }
  }
}
