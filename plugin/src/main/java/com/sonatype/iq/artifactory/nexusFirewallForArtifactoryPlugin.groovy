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

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

import com.sonatype.iq.artifactory.safeMode.SafeModeNexusFirewallForArtifactory

import groovy.json.JsonOutput
import groovy.transform.Field
import org.apache.http.HttpStatus
import org.artifactory.exception.CancelException
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.request.Request

import static java.lang.String.format

final String VERIFY_INIT_TIMEOUT = 'Verification of initial state failed to complete in a timely fashion'

@Field final ReentrantReadWriteLock configReloadLock = new ReentrantReadWriteLock()

@Field NexusFirewallForArtifactory nexusFirewallForArtifactory

@Field Repositories repositoriesApi

@Field Long lastPropertiesFileTimestamp

// We need to stop the ConfigurationMonitor in case the plugin is reloaded.
// This can happen when:
//   - Jfrog Artifactory reloads the plugins
//   - The integration tests create a new plugin instance
ConfigurationMonitor.stop()

initPlugin(repositories)

ConfigurationMonitor.start(this)

executions {
  /**
   * This will return a summary for the Firewall policy evaluation for a particular repository
   *
   * This can be triggered with the following curl command:
   * curl -u admin "http://ARTIFACTORY_SERVER/artifactory/api/plugins/execute/firewallEvaluationSummary?params=repo=maven-central"
   **/
  firewallEvaluationSummary(httpMethod: 'GET', groups: ['readers']) { Map<String, List<String>> params ->
    this.configReloadLock.readLock().lock()
    try {
      if (!params.repo || params.repo.size() != 1) {
        status = 500
        message = 'Must supply a single repo name as a query parameter, i.e. ?params=repo=maven-central'
        return
      }

      try {
        message = JsonOutput.toJson(this.nexusFirewallForArtifactory.getFirewallEvaluationSummary(params.repo.get(0)))
        status = 200
      }
      catch (e) {
        status = 500
        message = e.message
      }
    }
    finally {
      this.configReloadLock.readLock().unlock()
    }
  }

  /**
   * Return the plugin version
   *
   * curl -u admin "http://ARTIFACTORY_SERVER/artifactory/api/plugins/execute/firewallVersion"
   **/
  firewallVersion(httpMethod: 'GET', groups: ['readers']) { Map<String, List<String>> params ->
    try {
      message = JsonOutput.toJson([version: nexusFirewallForArtifactory.getPluginVersion()])
    }
    catch (e) {
      message = e.message
    }
  }

  /**
   *  Returns the Artifactory edition
   *
   *  curl -u admin "http://ARTIFACTORY_SERVER/artifactory/api/system/ping"
   */
  artifactoryEdition(httpMethod: 'GET', groups: ['readers']) { Map<String, List<String>> params ->
    try {
      asSystem {
        nexusFirewallForArtifactory.getArtifactoryEdition()
      }
    }
    catch (e) {
      message = e.message
    }
  }
}

download {
  beforeDownload { Request request, RepoPath repoPath ->
    this.configReloadLock.readLock().lock()
    try {
      runHandlerWithInitialisation(
          runOnceBeforeAllHandlers: { asSystem { this.nexusFirewallForArtifactory.verifyInit() } },
          handler: { this.nexusFirewallForArtifactory.beforeDownloadHandler(repoPath) },
          onRunOnceBeforeAllHandlersFailure: {
            this.log.error('beforeDownloadHandler - ' + VERIFY_INIT_TIMEOUT)
            throw new CancelException(
                format("Download of '%s' cancelled as firewall is not fully initialized", repoPath),
                HttpStatus.SC_INTERNAL_SERVER_ERROR)
          }
      )
    }
    finally {
      this.configReloadLock.readLock().unlock()
    }
  }
}

storage {
  beforePropertyCreate { ItemInfo item, String name, String[] values ->
    nexusFirewallForArtifactory.propertyEventHandler(name)
  }
  beforePropertyDelete { ItemInfo item, String name ->
    nexusFirewallForArtifactory.propertyEventHandler(name)
  }
  afterDelete { ItemInfo item ->
    this.configReloadLock.readLock().lock()
    try {
      runHandlerWithInitialisation(
        runOnceBeforeAllHandlers: { asSystem { this.nexusFirewallForArtifactory.verifyInit() }},
        handler: { nexusFirewallForArtifactory.afterDeleteHandler(item.repoPath) },
        onRunOnceBeforeAllHandlersFailure: { this.log.error('afterDeleteHandler - ' + VERIFY_INIT_TIMEOUT) }
      )
    }
    finally {
      this.configReloadLock.readLock().unlock()
    }
  }
}

private runHandlerWithInitialisation(Map params) {
  if (this.nexusFirewallForArtifactory.isInitializationRequired()) {
    params.runOnceBeforeAllHandlers()
  }
  if (this.nexusFirewallForArtifactory.isReady()) {
    params.handler()
  }
  else {
    params.onRunOnceBeforeAllHandlersFailure()
  }
}

private void initPlugin(Repositories repositories) {
  long start = System.currentTimeMillis()
  log.info('Initializing the FirewallForArtifactory plugin.')
  try {
    repositoriesApi = repositories

    nexusFirewallForArtifactory = new DefaultNexusFirewallForArtifactory(log, ctx.artifactoryHome.pluginsDir,
        repositories, searches, ctx.versionProvider?.running?.versionName)
  }
  catch (MissingPropertiesException | InvalidPropertiesException e) {
    nexusFirewallForArtifactory = new SafeModeNexusFirewallForArtifactory(e.message, log)
  }
  catch (e) {
    log.error(e.message)
    throw e
  }
  nexusFirewallForArtifactory.init()

  lastPropertiesFileTimestamp = getPropertiesFileTimestamp()
  log.info("Initialized the FirewallForArtifactory plugin in ${System.currentTimeMillis() - start} ms.")

  nexus
}

private void reloadConfigIfNeeded() {
  if (getPropertiesFileTimestamp() != lastPropertiesFileTimestamp) {
    log.info('Detected FirewallForArtifactory plugin config change. Reloading the configuration.')
    configReloadLock.writeLock().lock()
    try {
      initPlugin(repositoriesApi)
    }
    catch (e) {
      log.error('Error while reloading the FirewallForArtifactory plugin config: {}', e.message, e)
    }
    finally {
      configReloadLock.writeLock().unlock()
    }
  }
}

private Long getPropertiesFileTimestamp() {
  return FirewallProperties.getPropertiesFile().isFile() ? FirewallProperties.getPropertiesFile().lastModified() : null
}
