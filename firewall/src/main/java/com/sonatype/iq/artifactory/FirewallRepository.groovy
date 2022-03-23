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

import com.sonatype.iq.artifactory.FirewallProperties.MODE

import org.artifactory.repo.RepositoryConfiguration

/**
 * Data class for a repository that is enabled for Firewall
 */
class FirewallRepository
{
  // Find the JFrog Artifactory supported formats/names here:
  // https://www.jfrog.com/confluence/display/RTF/Repository+Configuration+JSON
  // JFrog Artifactory is key, IQ is value
  static final def repoFormatTranslation = ['maven': 'maven2', 'gems': 'rubygems']

  String repoKey

  MODE mode

  String type

  String format
  
  boolean audited

  static FirewallRepository of(RepositoryConfiguration repositoryConfiguration, MODE mode) {
    FirewallRepository repo = new FirewallRepository()
    repo.repoKey = repositoryConfiguration.key
    repo.type = repositoryConfiguration.type
    repo.mode = mode
    repo.format = parseFormat(repositoryConfiguration.packageType)
    return repo
  }

  static String parseFormat(final String format) {
    return repoFormatTranslation[format] ?: format
  }

  boolean equals(final o) {
    if (this.is(o)) {
      return true
    }
    if (getClass() != o.class) {
      return false
    }

    FirewallRepository that = (FirewallRepository) o

    if (repoKey != that.repoKey) {
      return false
    }

    return true
  }

  int hashCode() {
    return repoKey.hashCode()
  }
}

