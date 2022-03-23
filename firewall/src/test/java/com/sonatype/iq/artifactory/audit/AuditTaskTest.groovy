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
import java.time.LocalDateTime
import java.time.temporal.Temporal

import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallRepository
import com.sonatype.iq.artifactory.IqConnectionManager
import com.sonatype.iq.artifactory.StorageManager

import org.artifactory.repo.RepoPath
import spock.lang.Specification


class AuditTaskTest
    extends Specification
{
  
  FirewallRepository firewallRepository = Mock()

  StorageManager storageManager = Mock()

  IqConnectionManager iqConnectionManager = Mock()

  RepoPath repoPath = Mock()
  
  def 'task will call IQ and update local state'()
  {
    given: 'two assets to audit'
      Temporal start = LocalDateTime.now()
      def assets = [
          new FirewallArtifactoryAsset(repoPath, 'hash1'),
          new FirewallArtifactoryAsset(repoPath, 'hash2')
      ]

      AuditTask task = new AuditTask(firewallRepository, assets, storageManager, iqConnectionManager)

    when: 'we run the task'
      AuditResult result = task.call()
      sleep(100)
      Temporal finish = LocalDateTime.now()

    then:
      1 * iqConnectionManager.evaluateWithAudit(firewallRepository, assets)
      2 * storageManager.assignAuditTimestamp(repoPath)
      1 * firewallRepository.repoKey >> 'test'
      result.duration.compareTo(Duration.between(start, finish)) < 0
      result.componentCount == 2
      result.repository == 'test'
  }
  
}
