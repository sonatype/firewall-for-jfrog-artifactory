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

import org.artifactory.checksum.ChecksumInfo
import org.artifactory.checksum.ChecksumsInfo
import org.artifactory.fs.FileInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath

class TestFileInfoImpl implements FileInfo
{

  final RepoPath repoPath

  final String sha1

  TestFileInfoImpl(RepoPath repoPath, String sha1) {
  this.repoPath = repoPath
  this.sha1 = sha1
}

  @Override
  long getAge() {
    return 0
  }

  @Override
  String getMimeType() {
    return null
  }

  @Override
  ChecksumsInfo getChecksumsInfo() {
    return null
  }

  @Override
  Set<ChecksumInfo> getChecksums() {
    return null
  }

  @Override
  long getId() {
    return 0
  }

  @Override
  RepoPath getRepoPath() {
    return repoPath
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
    return null
  }

  @Override
  String getRelPath() {
    return null
  }

  @Override
  long getCreated() {
    return 0
  }

  @Override
  long getLastModified() {
    return 0
  }

  @Override
  long getSize() {
    return 0
  }

  @Override
  String getSha1() {
    return sha1
  }

  @Override
  String getSha2() {
    return null
  }

  @Override
  String getMd5() {
    return null
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
