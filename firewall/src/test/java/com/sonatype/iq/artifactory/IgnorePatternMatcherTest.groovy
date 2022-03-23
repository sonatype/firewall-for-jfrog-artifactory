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

import com.google.gson.Gson
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class IgnorePatternMatcherTest
    extends Specification
{

  @Shared 
  FirewallIgnorePatterns testPatterns = new Gson().fromJson(
      $/{
    "regexpsByRepositoryFormat": {        
        "yum": [".*\\.asc", ".*\\.xml", ".*\\.xml\\.gz", ".*\\.xml\\.xz",".*\\.xml\\.bz2", ".*\\.sqlite", 
        ".*\\.sqlite.gz", ".*\\.sqlite\\.xz", ".*\\.sqlite\\.bz2"],
        "p2": ["(.+/)?artifacts\\.(jar|xml)", "(.+/)?compositeArtifacts\\.(jar|xml)", "(.+/)?compositeContent\\.(jar|xml)", 
        "(.+/)?content\\.(jar|xml)", "(.+/)?site\\.xml"],
        "pypi": ["simple/.*", ".*\\.asc"],
        "maven2": ["(.+/)?archetype-catalog\\.xml", "(.+/)?maven-metadata\\.xml", ".*\\.(asc|pom|md5|sha1)"],
        "npm": ["(@[^/]+/)?[^/]+", "-/all"],
        "rubygems": [".*\\.gz", ".*\\.rz", ".*\\.ruby"]
    }
}/$
      , FirewallIgnorePatterns.class
  )

  @Shared
  IgnorePatternMatcher matcher = IgnorePatternMatcher.instance

  def setupSpec() {
    matcher.ignorePatterns = testPatterns
  }

  /**
   * Mirrors a subset of test cases from 
   * https://github.com/sonatype/chaos-report/blob/master/insight-portal-webapp/src/test/java/com/sonatype/insight/portal/rest/service/component/FirewallComponentDetailsResourceIT.java
   */
  @Unroll
  def 'check ignore pattern #filename for #format : ignored = #shouldIgnore'() {
    when:
      def result = matcher.isIgnored(format, new TestRepoPath(name: filename))

    then:
      result == shouldIgnore

    where:
      shouldIgnore | format     | filename
      true         | 'maven2'   | 'ignoreMatch.sha1'
      true         | 'maven2'   | 'my.pom'
      false        | 'maven2'   | '/path/path/library.jar'
      false        | 'maven2'   | 'my.war'
      false        | 'maven2'   | 'my.zip'
      true         | 'rubygems' | '/quick/Marshal.4.8/rdoc-6.0.1.gemspec.rz'
      true         | 'rubygems' | '/latest_specs.4.8.gz'
      true         | 'rubygems' | '/dependencies/bla.ruby'
      true         | 'rubygems' | 'quick/Marshal.4.8/rdoc-6.0.1.gemspec.rz'
      true         | 'rubygems' | 'latest_specs.4.8.gz'
      true         | 'rubygems' | 'dependencies/bla.ruby'
      false        | 'rubygems' | '/gems/somegem-1.2.3.gem'
      false        | 'rubygems' | '/gems/gem-name-with-hyphens-0.1.2.gem'
      false        | 'rubygems' | 'gems/somegem-1.2.3.gem'
      false        | 'rubygems' | 'gems/gem-name-with-hyphens-0.1.2.gem'
      true         | 'pypi'     | 'packages/12/34/somehash/app1-1.0.0.zip.asc'
      true         | 'pypi'     | 'packages/12/34/somehash/app1-1.0.0.tar.gz.asc'
      true         | 'pypi'     | 'simple/app1/'
      false        | 'pypi'     | 'packages/12/34/somehash/app1-1.0.0.zip'
      false        | 'pypi'     | 'packages/56/78/somehash/app2-1.0.0.tar.gz'
      false        | 'pypi'     | 'not/simple/app1/'
      true         | 'yum'      | 'somedata.xml'
      true         | 'yum'      | 'somedata.xml.asc'
      true         | 'yum'      | 'somedata.xml.gz'
      true         | 'yum'      | 'somedata.xml.bz2'
      true         | 'yum'      | 'somedata.xml.xz'
      true         | 'yum'      | 'somedata.sqlite'
      true         | 'yum'      | 'somedata.sqlite.gz'
      true         | 'yum'      | 'somedata.sqlite.bz2'
      true         | 'yum'      | 'somedata.sqlite.xz'
      false        | 'yum'      | 'component.rpm'
      false        | 'yum'      | 'some/path/component.rpm'
      true         | 'p2'       | 'artifacts.jar'
      true         | 'p2'       | 'artifacts.xml'
      true         | 'p2'       | 'compositeArtifacts.jar'
      true         | 'p2'       | 'compositeArtifacts.xml'
      true         | 'p2'       | 'compositeContent.jar'
      true         | 'p2'       | 'compositeContent.xml'
      true         | 'p2'       | 'content.jar'
      true         | 'p2'       | 'content.xml'
      true         | 'p2'       | 'site.xml'
      true         | 'p2'       | 'some/path/artifacts.jar'
      true         | 'p2'       | 'some/path/artifacts.xml'
      true         | 'p2'       | 'some/path/compositeArtifacts.jar'
      true         | 'p2'       | 'some/path/compositeArtifacts.xml'
      true         | 'p2'       | 'some/path/compositeContent.jar'
      true         | 'p2'       | 'some/path/compositeContent.xml'
      true         | 'p2'       | 'some/path/content.jar'
      true         | 'p2'       | 'some/path/content.xml'
      true         | 'p2'       | 'some/path/site.xml'
      false        | 'p2'       | 'features/feature.jar'
      false        | 'p2'       | 'plugins/plugin.jar'
      false        | 'p2'       | 'some/path/features/feature.jar'
      false        | 'p2'       | 'some/path/plugins/plugin.jar'
      false        | 'unknown'  | 'filename'
  }

  @Unroll
  def 'check npm ignore pattern for #path/#name for : ignore = #shouldIgnore'() {
    when:
      def result = matcher.isIgnored(format, new TestRepoPath(path: path, name: name))

    then:
      result == shouldIgnore

    where:
      shouldIgnore | format     | path | name
      true         | 'npm'      | 'app' | ''
      true         | 'npm'      | '@org/app' | ''
      true         | 'npm'      | '-/all' |''
      false        | 'npm'      | 'app/-' | 'app-1.0.0.tgz'
      false        | 'npm'      | '@org/app/-' | 'app-1.0.0.tgz'
  }

  def 'ignore patterns are optional, and may be unset in case of connectivity failure to IQ'() {
    setup:
      matcher.ignorePatterns = null

    expect:
      matcher.isIgnored('format', new TestRepoPath(name:'filename')) == false
  }
}
