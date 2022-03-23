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

import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import com.sonatype.iq.artifactory.FirewallArtifactoryAsset
import com.sonatype.iq.artifactory.FirewallRepository
import com.sonatype.iq.artifactory.IqConnectionManager
import com.sonatype.iq.artifactory.StorageManager

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import groovy.util.logging.Slf4j
import org.checkerframework.checker.nullness.compatqual.NullableDecl

import static com.google.common.base.Preconditions.checkNotNull
import static com.google.common.util.concurrent.Futures.addCallback
import static com.google.common.util.concurrent.MoreExecutors.getExitingExecutorService
import static java.util.concurrent.Executors.newFixedThreadPool

@Slf4j
class AuditExecutor
{
  final ListeningExecutorService listenableThreadPool = MoreExecutors.listeningDecorator(
      getExitingExecutorService((ThreadPoolExecutor) newFixedThreadPool(1), 100, TimeUnit.MILLISECONDS))

  final StorageManager storageManager

  final IqConnectionManager iqConnectionManager
  
  final Map<String, List<ListenableFuture<AuditResult>>> futuresByRepo = [:].asSynchronized().withDefault {[]}

  AuditExecutor(final IqConnectionManager iqConnectionManager,
                final StorageManager storageManager)
  {
    this.iqConnectionManager = checkNotNull(iqConnectionManager)
    this.storageManager = checkNotNull(storageManager)
  }

  ListenableFuture<AuditResult> auditRepository(FirewallRepository firewallRepository,
                                                Collection<FirewallArtifactoryAsset> assets)
  {
    final ListenableFuture<AuditResult> future = listenableThreadPool.
        submit(new AuditTask(firewallRepository, assets, storageManager, iqConnectionManager))
    createFutureCallback(future, firewallRepository)
    return future
  }

  private createFutureCallback(ListenableFuture<AuditResult> future, FirewallRepository repo) {
    futuresByRepo.get(repo.repoKey) << future
    addCallback(future, new FutureCallback<AuditResult>() {
      @Override
      void onSuccess(@NullableDecl final AuditResult result) {
        log.debug("Audit successful: {}", result)
        removeFuture(repo, future)
      }

      @Override
      void onFailure(final Throwable t) {
        log.error("Failure evaluating audit on repository : {}", repo.repoKey, t)
        removeFuture(repo, future)
      }
    }, listenableThreadPool)
    return future
  }

  /**
   * Remove the future and if it is the last one queued for the given FirewallRepository,
   * mark that FirewallRepository as fully audited both in memory and persistent storage.
   */
  private void removeFuture( FirewallRepository repo, ListenableFuture<AuditResult> future) {
    List<ListenableFuture<AuditResult>> repoFutures = futuresByRepo.get(repo.repoKey)
    repoFutures.remove(future)
    if(!repoFutures) {
      futuresByRepo.remove(repo.repoKey)
      storageManager.setRepositoryAudited(repo, System.currentTimeMillis())
    }
  } 
  
  boolean isProcessing() {
    !futuresByRepo.isEmpty()
  }
}
