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

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import com.sonatype.iq.artifactory.FirewallRepository
import com.sonatype.iq.artifactory.IqConnectionException
import com.sonatype.iq.artifactory.IqConnectionManager
import com.sonatype.iq.artifactory.StorageManager

import com.google.common.util.concurrent.ListenableFuture
import spock.lang.Specification
import spock.lang.Subject

import static org.awaitility.Awaitility.await

class AuditExecutorTest
    extends Specification
{
  StorageManager storageManager = Mock()

  IqConnectionManager iqConnectionManager = Mock()

  FirewallRepository firewallRepository = Mock()

  @Subject
  AuditExecutor auditExecutor = new AuditExecutor(iqConnectionManager, storageManager)

  def 'task execution returns a result when successful'() {
    given:
      def assets = []

    when:
      ListenableFuture<AuditResult> future = auditExecutor.auditRepository(firewallRepository, assets)
      await().atMost(1, TimeUnit.SECONDS).until({ future.isDone() })

    then:
      AuditResult auditResult = future.get()
      auditResult.componentCount == 0
  }

  def 'task execution with failure'() {
    given:
      def assets = []
      IqConnectionException iqConnectionException = new IqConnectionException('test', null)

    when:
      ListenableFuture<AuditResult> future = auditExecutor.auditRepository(firewallRepository, assets)
      await().atMost(1, TimeUnit.SECONDS).until({ future.isDone() })
      future.get()

    then:
      1 * iqConnectionManager.evaluateWithAudit(firewallRepository, assets) >> { throw iqConnectionException }
      ExecutionException outer = thrown()
      outer.cause == iqConnectionException
  }
}
