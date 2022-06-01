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
