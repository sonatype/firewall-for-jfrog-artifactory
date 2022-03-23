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

import com.google.common.util.concurrent.Striped
import org.slf4j.Logger

class UnquarantinedComponentsUpdater
{
  final IqConnectionManager iqConnectionManager

  final StorageManager storageManager

  final QuarantineStatusManager quarantineStatusManager

  final PathFactory pathFactory

  final Logger log

  final long unquarantineUpdateMillis

  final Striped<Lock> striped = Striped.lock(32)

  UnquarantinedComponentsUpdater(final IqConnectionManager iqConnectionManager, final StorageManager storageManager,
                                 final QuarantineStatusManager quarantineStatusManager, final PathFactory pathFactory,
                                 final FirewallProperties firewallProperties, final Logger log)
  {
    this.iqConnectionManager = iqConnectionManager
    this.storageManager = storageManager
    this.quarantineStatusManager = quarantineStatusManager
    this.pathFactory = pathFactory
    this.unquarantineUpdateMillis = firewallProperties.getUnquarantineUpdateInMillis() ?: 60000
    this.log = log
  }

  void requestAndUpdateUnquarantinedComponents(final FirewallRepository firewallRepository) {
    Lock lock = striped.get(firewallRepository.repoKey)

    if (!lock.tryLock()) {
      return
    }

    try {
      Date lastUpdateTimestamp = storageManager.getFirewallLastQuarantineUpdateTimestamp(firewallRepository.repoKey)

      if (lastUpdateTimestamp == null) {
        lastUpdateTimestamp = storageManager.getFirewallEnabledTimestamp(firewallRepository.repoKey)
      }

      if (lastUpdateTimestamp == null) {
        log.trace("Firewall skipped request for latest unquarantined assets: quarantine is not enabled")
        return
      }

      long timestamp = System.currentTimeMillis()
      if (timestamp - lastUpdateTimestamp.time < unquarantineUpdateMillis) {
        log.trace("Firewall skipped request for latest unquarantined assets: " +
            "${timestamp - lastUpdateTimestamp.time}ms since last update")
        return
      }

      log.info('Firewall is checking IQ server for unquarantined artifacts...')

      Set<String> pathnames

      try {
        pathnames = iqConnectionManager.
            getUnquarantinedComponents(firewallRepository, lastUpdateTimestamp.time).pathnames
      }
      catch (UnsupportedOperationException e) {
        log.error("IQ Server doesn't support unquarantine request: ${e.message}")
        return
      }

      pathnames.each { path ->
        quarantineStatusManager.unQuarantine(pathFactory.createRepoPath(firewallRepository.repoKey, path))
        log.info("Firewall unquarantined '{}' because it was unquarantined in IQ", path)
      }

      storageManager.setQuarantineLastUpdateTimestamp(firewallRepository.repoKey, timestamp)
      log.info('...Firewall unquarantined artifact check complete.  {} artifacts were unquarantined', pathnames.size())
    }
    finally {
      lock.unlock()
    }
  }
}
