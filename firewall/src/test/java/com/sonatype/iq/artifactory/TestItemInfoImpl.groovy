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

import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

class TestItemInfoImpl
    implements ItemInfo {
  RepoPath repoPath
  String sha1
  long created = (new Date() - 1).time

  TestItemInfoImpl(RepoPath repoPath, String sha1, long created) {
    this(repoPath, sha1)
    this.created = created
  }

  TestItemInfoImpl(RepoPath repoPath, String sha1) {
    this.repoPath = repoPath
    this.sha1 = sha1
  }

  RepoPath getRepoPath() {
    return repoPath
  }

  String getSha1() {
    return sha1
  }

  @Override
  long getId() {
    return 0
  }

  @Override
  boolean isFolder() {
    return false
  }

  @Override
  String getName() {
    return null
  }

  @Override
  String getRepoKey() {
    return repoPath.repoKey
  }

  @Override
  String getRelPath() {
    return null
  }

  @Override
  long getCreated() {
    return created
  }

  @Override
  long getLastModified() {
    return 0
  }

  @Override
  String getModifiedBy() {
    return null
  }

  @Override
  String getCreatedBy() {
    return null
  }

  @Override
  long getLastUpdated() {
    return 0
  }

  @Override
  boolean isIdentical(final ItemInfo info) {
    return false
  }

  @Override
  int compareTo(final ItemInfo o) {
    return 0
  }
}
