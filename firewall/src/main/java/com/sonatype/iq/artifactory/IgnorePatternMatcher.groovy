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

import com.sonatype.clm.dto.model.component.FirewallIgnorePatterns

import org.artifactory.repo.RepoPath

@Singleton(lazy = true)
class IgnorePatternMatcher
{
  private FirewallIgnorePatterns ignorePatterns

  private static final Map<String, Closure> FORMAT_HANDLERS = [
      'npm': { RepoPath repoPath -> repoPath.path }
  ].withDefault { { RepoPath repoPath -> repoPath.name } }

  boolean isIgnored(final String format, final RepoPath repoPath) {
    getPatterns(format)?.find {
      FORMAT_HANDLERS[format].call(repoPath) ==~ it
    }
  }

  void setIgnorePatterns(final FirewallIgnorePatterns ignorePatterns) {
    this.ignorePatterns = ignorePatterns
  }

  private getPatterns(final String format) {
    ignorePatterns?.regexpsByRepositoryFormat?.get(format)
  }
}
