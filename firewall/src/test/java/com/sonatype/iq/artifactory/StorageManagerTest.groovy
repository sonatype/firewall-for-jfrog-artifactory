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

import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.slf4j.Logger
import spock.lang.Specification

class StorageManagerTest
    extends Specification
{
  final Repositories repositories = Mock()

  final PathFactoryImpl pathFactory = Mock()

  final FirewallRepositories firewallRepositories = Mock()

  final FirewallRepository firewallRepository = Mock()

  final Logger log = Mock()

  StorageManager storageManager = new StorageManager(repositories, pathFactory, firewallRepositories, log)

  def 'firewall properties cannot be modified'() {
    when:
      storageManager.checkPropertyAccess(repoName)

    then:
      CancelException thrown = thrown()
      thrown.errorCode == 403
      thrown.message == "Cannot modify read-only property '${repoName}'."

    where:
      repoName             | _
      'firewall.settings'  | _
      'firewall.something' | _
  }


  def 'non firewall properties can be modified'() {
    when:
      storageManager.checkPropertyAccess(repoName)

    then:
      noExceptionThrown()

    where:
      repoName          | _
      'other.settings'  | _
      'other.something' | _
  }

  def 'test iq repository url'() {
    setup:
      def repoPath = TestHelper.createRootRepo()
      def url = '/test/url'
      pathFactory.createRepoPath(repoPath.repoKey) >> repoPath

    when:
      storageManager.setIqRepositoryUrl(repoPath.repoKey, url)

    then:
      1 * repositories.setProperty(repoPath, StorageManager.IQ_REPOSITORY_URL, url)
  }

  def 'test remove firewall properties'() {
    setup:
      def repoPath = TestHelper.createRootRepo()
      pathFactory.createRepoPath(repoPath.repoKey) >> repoPath

    when:
      storageManager.removeFirewallPropertiesFromRepository(repoPath.repoKey)

    then:
      for (property in StorageManager.FIREWALL_PROPERTIES) {
        1 * repositories.deleteProperty(repoPath, property)
      }
  }

  def 'deletes quarantine property'() {
    setup:
      def repoPath = TestHelper.createRootRepo()
      pathFactory.createRepoPath(repoPath.repoKey) >> repoPath

    when: 'quarantine status is deleted'
      storageManager.deleteQuarantineStatus(repoPath)

    then: 'property is deleted in repository'
      1 * repositories.deleteProperty(repoPath, StorageManager.QUARANTINE)
  }
}
