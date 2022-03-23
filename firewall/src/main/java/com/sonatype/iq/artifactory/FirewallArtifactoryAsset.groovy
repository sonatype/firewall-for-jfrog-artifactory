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

import groovy.transform.ToString
import org.artifactory.repo.RepoPath

/**
 * A representation of the firewall asset as stored in the JFrog Artifactory artifact metadata.
 *
 * This is also stored in the cache. An optimization will be to reduce the memory footprint
 * for instances of this class.
 */
@ToString
class FirewallArtifactoryAsset
{
  RepoPath repoPath

  String hash

  boolean isNew

  private FirewallArtifactoryAsset(final RepoPath repoPath) {
    this.repoPath = repoPath
  }

  private FirewallArtifactoryAsset(final RepoPath repoPath, final String hash) {
    this.repoPath = repoPath
    this.hash =  hash
    this.isNew = true
  }

  String getId() {
    return repoPath.id
  }

  String getRepoKey() {
    return repoPath.repoKey
  }

  String getPath() {
    return repoPath.path
  }

  String getHash() {
    return hash
  }

  boolean isNew() {
    return isNew
  }

  @Override
  boolean equals(final Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false
    }
    FirewallArtifactoryAsset other = (FirewallArtifactoryAsset) obj
    return Objects.equals(id, other.id)
  }

  @Override
  int hashCode() {
    return id.hashCode()
  }

  /**
   * Create a representation of a RepoPath stored in the cache.
   */
  static FirewallArtifactoryAsset of(final RepoPath repoPath) {
    return new FirewallArtifactoryAsset(repoPath)
  }

  /**
   * This is called when the artifact will be added to the cache as a new instance. The isNew flag will be
   * set on the instance causing the instance to be invalidated in the cache to force storage.
   */
  static FirewallArtifactoryAsset of(final RepoPath repoPath, final String hash) {
    return new FirewallArtifactoryAsset(repoPath, hash)
  }
}
