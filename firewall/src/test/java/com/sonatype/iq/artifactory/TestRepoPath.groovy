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

@ToString
class TestRepoPath implements RepoPath
{
  String repoKey

  String path

  String id

  String name

  RepoPath parent

  boolean file

  boolean folder

  @Override
  String toPath() {
    throw new RuntimeException("TODO - do we really need this?")
  }

  boolean isRoot() {
    return parent == null
  }

  static TestRepoPath createFileInstance(String repoKey, String path) {
    def parent = new TestRepoPath()
    parent.repoKey = repoKey
    parent.path = repoKey
    parent.name = repoKey
    parent.id = repoKey

    def result = new TestRepoPath()
    result.repoKey = repoKey
    result.path = path
    result.id = repoKey + ':' + path
    result.name = path
    result.file = true
    result.parent = parent

    return result
  }
}
