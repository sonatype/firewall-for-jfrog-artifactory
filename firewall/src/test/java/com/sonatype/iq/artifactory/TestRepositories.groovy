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

import org.artifactory.common.StatusHolder
import org.artifactory.fs.FileInfo
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.fs.StatsInfo
import org.artifactory.md.Properties
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.resource.ResourceStreamHandle

class TestRepositories
    implements Repositories
{
  def properties = [:]

  Map<String, ItemInfo> itemInformation = [:]

  Map<String, FileInfo> fileInformation = [:]

  Map<String, RepositoryConfiguration> configurations = [:]

  @Override
  List<String> getLocalRepositories() {
    return null
  }

  @Override
  List<String> getRemoteRepositories() {
    return null
  }

  @Override
  List<String> getVirtualRepositories() {
    return null
  }

  @Override
  RepositoryConfiguration getRepositoryConfiguration(final String repoKey) {
    return configurations[repoKey]
  }

  TestRepositories withConfiguration(final String repoKey, final RepositoryConfiguration repoConfig) {
    configurations[repoKey] = repoConfig
    return this
  }

  @Override
  ItemInfo getItemInfo(final RepoPath repoPath) {
    return itemInformation[repoPath.path]
  }

  TestRepositories withItemInfo(final RepoPath repoPath, final ItemInfo itemInfo) {
    itemInformation[repoPath.path] = itemInfo
    return this
  }

  @Override
  FileInfo getFileInfo(final RepoPath repoPath) {
    return fileInformation[repoPath.path]
  }

  TestRepositories withFileInfo(final RepoPath repoPath, final FileInfo fileInfo) {
    fileInformation[repoPath.path] =  fileInfo
    return this
  }

  @Override
  List<ItemInfo> getChildren(final RepoPath repoPath) {
    return null
  }

  @Override
  String getStringContent(final FileInfo fileInfo) {
    return null
  }

  @Override
  String getStringContent(final RepoPath repoPath) {
    return null
  }

  @Override
  ResourceStreamHandle getContent(final RepoPath repoPath) {
    return null
  }

  @Override
  Properties getProperties(final RepoPath repoPath) {
    return null
  }

  @Override
  boolean hasProperty(final RepoPath repoPath, final String propertyName) {
    return false
  }

  @Override
  Set<String> getPropertyValues(final RepoPath repoPath, final String propertyName) {
    return null
  }

  @Override
  String getProperty(final RepoPath repoPath, final String propertyName) {
    return properties[repoPath.path] ? properties[repoPath.path][propertyName] : null
  }

  @Override
  Properties setProperty(final RepoPath repoPath, final String propertyName, final String... values) {
    if (!properties[repoPath.path]) {
      properties[repoPath.path] = [:]
    }
    properties[repoPath.path][propertyName] = values[0]
    return null
  }

  TestRepositories withProperty(final RepoPath repoPath, final String propertyName, final String value) {
    setProperty(repoPath, propertyName, value)
    return this
  }

  @Override
  Properties setPropertyRecursively(final RepoPath repoPath, final String propertyName, final String... values) {
    return null
  }

  @Override
  void deleteProperty(final RepoPath repoPath, final String propertyName) {

  }

  @Override
  boolean exists(final RepoPath repoPath) {
    return false
  }

  @Override
  StatusHolder deploy(final RepoPath repoPath, final InputStream inputStream) {
    return null
  }

  @Override
  StatusHolder delete(final RepoPath repoPath) {
    return null
  }

  @Override
  StatusHolder deleteAtomic(final RepoPath repoPath) {
    return null
  }

  @Override
  StatusHolder undeploy(final RepoPath repoPath) {
    return null
  }

  @Override
  boolean isRepoPathHandled(final RepoPath repoPath) {
    return false
  }

  @Override
  boolean isLcoalRepoPathHandled(final RepoPath repoPath) {
    return false
  }

  @Override
  boolean isRepoPathAccepted(final RepoPath repoPath) {
    return false
  }

  @Override
  boolean isLocalRepoPathAccepted(final RepoPath repoPath) {
    return false
  }

  @Override
  StatusHolder move(final RepoPath source, final RepoPath target) {
    return null
  }

  @Override
  StatusHolder moveAtomic(final RepoPath source, final RepoPath target) {
    return null
  }

  @Override
  StatusHolder copy(final RepoPath source, final RepoPath target) {
    return null
  }

  @Override
  StatusHolder copyAtomic(final RepoPath source, final RepoPath target) {
    return null
  }

  @Override
  FileLayoutInfo getLayoutInfo(final RepoPath repoPath) {
    return null
  }

  @Override
  String translateFilePath(final RepoPath source, final String targetRepoKey) {
    return null
  }

  @Override
  RepoPath getArtifactRepoPath(final FileLayoutInfo layoutInfo, final String repoKey) {
    return null
  }

  @Override
  RepoPath getDescriptorRepoPath(final FileLayoutInfo layoutInfo, final String repoKey) {
    return null
  }

  @Override
  long getArtifactsCount(final RepoPath repoPath) {
    return 0
  }

  @Override
  long getArtifactsSize(final RepoPath repoPath) {
    return 0
  }

  @Override
  StatsInfo getStats(final RepoPath repoPath) {
    return null
  }
}
