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

import java.util.function.Function

import com.sonatype.iq.artifactory.Commons.QuarantineStatus

import org.artifactory.exception.CancelException
import org.slf4j.Logger

import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.ALLOW
import static com.sonatype.iq.artifactory.Commons.QuarantineStatus.DENY
import static org.apache.http.HttpStatus.SC_FORBIDDEN

/**
 * Fetches the quarantine status for a given repository asset.
 */
class IqQuarantineStatusLoader
    implements Function<FirewallArtifactoryAsset, QuarantineStatus>
{
  private final Logger log

  private final IqConnectionManager iqConnectionManager

  private final StorageManager storageManager

  IqQuarantineStatusLoader(
      final IqConnectionManager iqConnectionManager,
      final StorageManager storageManager,
      final Logger log) {
    this.storageManager = storageManager
    this.iqConnectionManager = iqConnectionManager
    this.log = log
  }

  private QuarantineStatus getQuarantineStatusFromIQ(FirewallArtifactoryAsset asset) {
    try {
      def result = iqConnectionManager.evaluateWithQuarantine(asset)
      def quarantineStatus = result.componentEvalResults.first()?.quarantine ? DENY : ALLOW
      log.trace("Firewall quarantine status evaluation result for '${asset.id}' is '${quarantineStatus}'")
      return quarantineStatus
    }
    catch (IqConnectionException e) {
      throw e
    }
    catch (Exception e) {
      log.error(e.getMessage(), e)
      throw new CancelException("Firewall is unable to get quarantine status from IQ for: ${asset.id}", SC_FORBIDDEN)
    }
  }

  @Override
  QuarantineStatus apply(final FirewallArtifactoryAsset it) {
    log.trace("Firewall quarantine status not found for '${it.id}'")
    it.hash = storageManager.getHash(it.repoPath)
    return getQuarantineStatusFromIQ(it)
  }
}
