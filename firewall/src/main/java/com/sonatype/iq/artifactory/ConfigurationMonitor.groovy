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

import groovy.util.logging.Slf4j

@Slf4j
public class ConfigurationMonitor
{
  public static final int CONFIG_RELOAD_CHECK_INTERVAL_IN_MS = 1000

  private static volatile Thread monitorThread

  private static volatile boolean running

  static synchronized start(plugin) {
    log.info("Starting FirewallForArtifactory plugin config monitoring.")
    if (monitorThread != null) {
      throw new IllegalStateException("The FirewallForArtifactory plugin config monitor is already running")
    }

    running = true
    monitorThread = Thread.startDaemon('FirewallForArtifactory plugin config monitor') {
      log.info('Started FirewallForArtifactory plugin config monitoring.')
      while (running) {
        Thread.sleep(CONFIG_RELOAD_CHECK_INTERVAL_IN_MS)

        plugin.reloadConfigIfNeeded()
      }
    }
  }

  static synchronized stop() {
    log.info("Stopping FirewallForArtifactory plugin config monitoring.")
    running = false
    if (monitorThread != null) {
      monitorThread.join()
      monitorThread = null
    }
    log.info("Stopped FirewallForArtifactory plugin config monitoring.")
  }
}
