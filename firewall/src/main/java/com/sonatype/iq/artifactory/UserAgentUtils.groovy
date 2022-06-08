/*
 * Copyright 2022, Sonatype, Inc.
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

class UserAgentUtils {
  static String getDefaultUserAgent(String clientVersion, String clientEdition, String repository) {
    return String.format("Firewall_For_Jfrog_Artifactory/%s (%s; %s; %s; %s; %s; %s)",
        clientVersion,
        clientEdition,
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch"),
        System.getProperty("java.version"),
        repository
        );
  }
}
