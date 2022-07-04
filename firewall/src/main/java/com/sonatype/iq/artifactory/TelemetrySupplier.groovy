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

import com.sonatype.insight.brain.client.UnsupportedServerVersionException
import com.sonatype.iq.artifactory.restclient.RestClientConfiguration
import com.sonatype.iq.artifactory.restclient.RestClientFactory

import org.slf4j.Logger

class TelemetrySupplier
{
  private static final String REQUIRED_IQ_VERSION_FOR_TELEMETRY = '1.141.0'

  private final RestClientFactory restClientFactory

  private final RestClientConfiguration restClientConfiguration

  private final String artifactoryVersion

  private final String pluginVersion

  private boolean telemetryIsEnabled

  private final Logger log

  TelemetrySupplier(
      final RestClientFactory restClientFactory,
      final RestClientConfiguration restClientConfiguration,
      final String artifactoryVersion,
      final String pluginVersion,
      final Logger log)
  {
    this.restClientFactory = restClientFactory
    this.restClientConfiguration = restClientConfiguration
    this.artifactoryVersion = artifactoryVersion
    this.pluginVersion = pluginVersion
    this.log = log
  }

  String getUserAgent() {
    return telemetryIsEnabled
        ? "Firewall_For_Jfrog_Artifactory/${pluginVersion} (; " +
        "${System.getProperty('os.name')}; " +
        "${System.getProperty('os.version')}; " +
        "${System.getProperty('os.arch')}; " +
        "${System.getProperty("java.version")}; ${artifactoryVersion})"
        : "Firewall_For_Jfrog_Artifactory/${pluginVersion}"
  }

  /**
   * Check if IQ Server version is >= 141 to avoid generating invalid telemetry data (see CLM-21411)
   */
  private boolean telemetryServerVersionCheck() {
    try {
      restClientFactory.forConfiguration(restClientConfiguration).validateServerVersion(
          REQUIRED_IQ_VERSION_FOR_TELEMETRY)
      log.debug("Enabling telemetry.")
      return true
    }
    catch (UnsupportedServerVersionException e) {
      log.warn("Disabling telemetry. Requires IQ version {} or newer.", REQUIRED_IQ_VERSION_FOR_TELEMETRY)
      return false
    }
  }

  void enableIfSupported() {
    telemetryIsEnabled = telemetryServerVersionCheck()
  }
}
