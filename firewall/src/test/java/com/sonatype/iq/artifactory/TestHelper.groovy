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

import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationData
import com.sonatype.clm.dto.model.component.RepositoryComponentEvaluationDataList
import com.sonatype.clm.dto.model.policy.RepositoryPolicyEvaluationSummary
import com.sonatype.iq.artifactory.FirewallProperties.MODE

import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

class TestHelper
{
  /**
   * Create an asset RepoPath. Note 'parent' is a required param
   */
  static RepoPath createAsset(def parent,
                              def repoKey = 'central',
                              def path = 'path/folder/foo',
                              def name = 'foo'
  )
  {
    def id = "${repoKey}:${path}"
    return new TestRepoPath(
        repoKey: repoKey,
        file: true,
        folder: false,
        id: id,
        path: path,
        name: name,
        parent: parent)
  }

  static RepoPath createFolder(def parent,
                               def repoKey = 'central',
                               def path = 'path/folder',
                               def name = 'folder'
  )
  {
    def id = "${repoKey}:${path}"
    return new TestRepoPath(
        repoKey: repoKey,
        file: false,
        folder: true,
        id: id,
        path: path,
        name: name,
        parent: parent)
  }

  /**
   * Create an root RepoPath
   */
  static RepoPath createRootRepo(def repoKey = 'central') {
    def id = "${repoKey}:"
    return new TestRepoPath(
        repoKey: repoKey,
        file: false,
        folder: true,
        id: id,
        path: '',
        name: '',
        parent: null)
  }

  /**
   * Create a RepoPath per the sample in the wiki on the
   * <a href="https://docs.sonatype.com/x/YiXwBw">'Artifactory Model Store Info' page</a>
   */
  static RepoPath createStruts2RepoPath(def repoKey = 'central', def fileExt = 'jar') {
    RepoPath root = createRootRepo(repoKey)
    RepoPath org = createFolder(root, repoKey, 'org', 'org')
    RepoPath orgApache = createFolder(org, repoKey, 'org/apache', 'apache')
    RepoPath orgApacheStruts = createFolder(orgApache, repoKey, 'org/apache/struts', 'struts')
    RepoPath orgApacheStrutsStruts2Core =
        createFolder(orgApacheStruts, repoKey, 'org/apache/struts/struts2-core', 'struts2-core')
    RepoPath orgApacheStrutsStruts2Core2_5_17 =
        createFolder(orgApacheStrutsStruts2Core, repoKey, 'org/apache/struts/struts2-core/2.5.17', '2.5.17')
    RepoPath orgApacheStrutsStruts2Core2_5_17File = createAsset(orgApacheStrutsStruts2Core2_5_17, repoKey,
        "org/apache/struts/struts2-core/2.5.17/struts2-core-2.5.17.${fileExt}", "struts2-core-2.5.17.${fileExt}")
    return orgApacheStrutsStruts2Core2_5_17File
  }

  static ItemInfo getTestItemInfo(RepoPath repoPath, String hash, long created) {
    return new TestItemInfoImpl(repoPath, hash, created)
  }

  static RepositoryComponentEvaluationDataList createRepositoryComponentEvaluationDataList(final boolean quarantine) {
    RepositoryComponentEvaluationDataList list = new RepositoryComponentEvaluationDataList()
    list.componentEvalResults.add(new RepositoryComponentEvaluationData(quarantine))
    return list
  }

  static RepositoryPolicyEvaluationSummary createRepositoryPolicyEvaluationSummary(String reportUrl = '/test/summary') {
    RepositoryPolicyEvaluationSummary repositoryPolicyEvaluationSummary =  new RepositoryPolicyEvaluationSummary()
    repositoryPolicyEvaluationSummary.reportUrl = reportUrl
    return repositoryPolicyEvaluationSummary
  }

  static FirewallRepository createFirewallRepository (String key, MODE mode = 'quarantine', String type = 'remote',
                                                      String format = 'maven')
  {
    FirewallRepository repo = new FirewallRepository()
    repo.repoKey = key
    repo.type = type
    repo.mode = mode
    repo.format = FirewallRepository.parseFormat(format)
    return repo
  }
}
