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

import com.sonatype.clm.dto.model.component.UnquarantinedComponentList

import org.artifactory.repo.RepoPath
import org.slf4j.Logger
import spock.lang.Specification

import static com.sonatype.iq.artifactory.TestHelper.createAsset
import static com.sonatype.iq.artifactory.TestHelper.createFirewallRepository
import static com.sonatype.iq.artifactory.TestHelper.createRootRepo
import static com.sonatype.iq.artifactory.TestHelper.createStruts2RepoPath

class UnquarantineComponentsUpdaterTest
    extends Specification
{
  final IqConnectionManager iqConnectionManager = Mock()

  final StorageManager storageManager = Mock()

  final QuarantineStatusManager quarantineStatusManager = Mock()

  final PathFactory pathFactory = Mock()

  final FirewallProperties firewallProperties = Mock()

  final Logger log = Mock()

  final Date quarantineLastUpdateTimestamp = new Date() - 1

  final String repoKey = 'repo'

  UnquarantinedComponentsUpdater unquarantinedComponentsUpdater

  FirewallRepository firewallRepository

  def setup() {
    unquarantinedComponentsUpdater = new UnquarantinedComponentsUpdater(
        iqConnectionManager, storageManager, quarantineStatusManager, pathFactory, firewallProperties, log)
    firewallRepository = createFirewallRepository(repoKey)
  }

  def 'test IQ has no unquarantined components since last update'() {
    when:
      unquarantinedComponentsUpdater.requestAndUpdateUnquarantinedComponents(firewallRepository)

    then:
      1 * storageManager.getFirewallLastQuarantineUpdateTimestamp('repo') >> quarantineLastUpdateTimestamp
      1 * iqConnectionManager.getUnquarantinedComponents(firewallRepository, _) >> []
  }

  def 'test IQ has unquarantined components since last update'() {
    setup:
      RepoPath root = createRootRepo(repoKey)
      RepoPath struts2RepoPath = createStruts2RepoPath(repoKey)
      RepoPath repoPath = createAsset(root, repoKey)
      String[] pathnames = [struts2RepoPath.path, repoPath.path]

      UnquarantinedComponentList unquarantinedComponentList = new UnquarantinedComponentList()
      unquarantinedComponentList.pathnames.addAll(pathnames)

    when:
      unquarantinedComponentsUpdater.requestAndUpdateUnquarantinedComponents(firewallRepository)

    then:
      1 * storageManager.getFirewallLastQuarantineUpdateTimestamp(repoKey) >> quarantineLastUpdateTimestamp
      1 * iqConnectionManager.getUnquarantinedComponents(firewallRepository, _) >> unquarantinedComponentList
      1 * pathFactory.createRepoPath(repoKey, pathnames[0]) >> struts2RepoPath
      1 * quarantineStatusManager.unQuarantine(struts2RepoPath)
      1 * pathFactory.createRepoPath(repoKey, pathnames[1]) >> repoPath
      1 * quarantineStatusManager.unQuarantine(repoPath)
      0 * quarantineStatusManager.unQuarantine(*_) // not more that the number of pathnames
      1 * storageManager.setQuarantineLastUpdateTimestamp(repoKey, _ as Long)
  }

  def 'test exit if no quarantine last update timestamp or enabled timestamp'() {
    when:
      unquarantinedComponentsUpdater.requestAndUpdateUnquarantinedComponents(firewallRepository)

    then:
      1 * log.trace('Firewall skipped request for latest unquarantined assets: quarantine is not enabled')
  }

  def 'test exit if quarantine last update timestamp within the last minute'() {
    def message

    expect:
      def messageStartsWith = 'Firewall skipped request for latest unquarantined assets: '
      def messageEndWith = 'ms since last update'

    when:
      unquarantinedComponentsUpdater.requestAndUpdateUnquarantinedComponents(firewallRepository)

    then:
      message.startsWith(messageStartsWith)
      message.endsWith(messageEndWith)
      message.length() > messageStartsWith.length() + messageEndWith.length()
      1 * storageManager.getFirewallLastQuarantineUpdateTimestamp(repoKey) >> new Date()
      1 * log.trace(_) >> { args -> message = args[0] }
  }

  def 'test log if IQ does not support unquarantine request'() {
    when:
      unquarantinedComponentsUpdater.requestAndUpdateUnquarantinedComponents(firewallRepository)

    then:
      1 * storageManager.getFirewallLastQuarantineUpdateTimestamp(repoKey) >> quarantineLastUpdateTimestamp
      1 * iqConnectionManager.getUnquarantinedComponents(_, _) >>
          { throw new UnsupportedOperationException("Unsupported") }
      1 * log.error('IQ Server doesn\'t support unquarantine request: Unsupported')
  }
}
