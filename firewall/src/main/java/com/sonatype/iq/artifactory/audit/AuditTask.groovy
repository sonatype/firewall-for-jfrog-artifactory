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
package com.sonatype.iq.artifactory.audit

import java.time.Duration
import java.time.temporal.Temporal
import java.util.concurrent.Callable

import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallRepository
import com.sonatype.iq.artifactory.IqConnectionManager
import com.sonatype.iq.artifactory.StorageManager

import groovy.transform.Canonical
import groovy.transform.ToString

import static com.google.common.base.Preconditions.checkNotNull
import static java.time.Duration.between
import static java.time.LocalDateTime.now

/**
 * Handles the specifics of requesting audit details from IQ and updating the local state of components once they're 
 * evaluated.
 */
class AuditTask
    implements Callable<AuditResult>
{
  final IqConnectionManager iqConnectionManager

  final StorageManager storageManager

  final FirewallRepository firewallRepository

  final Collection<FirewallArtifactoryAsset> assets

  AuditTask(final FirewallRepository firewallRepository, final Collection<FirewallArtifactoryAsset> assets,
            final StorageManager storageManager, final IqConnectionManager iqConnectionManager)
  {
    this.firewallRepository = checkNotNull(firewallRepository)
    this.assets = checkNotNull(assets)
    this.storageManager = checkNotNull(storageManager)
    this.iqConnectionManager = checkNotNull(iqConnectionManager)
  }

  @Override
  AuditResult call() throws Exception {
    Temporal start = now()
    iqConnectionManager.evaluateWithAudit(firewallRepository, assets)
    assets.each { FirewallArtifactoryAsset asset ->
      storageManager.assignAuditTimestamp(asset.repoPath)
    }
    return new AuditResult(repository: firewallRepository.repoKey, componentCount: assets.size(),
        duration: between(start, now()))
  }
}

@Canonical
@ToString(includeNames = true)
class AuditResult
{
  String repository

  long componentCount

  Duration duration
}
