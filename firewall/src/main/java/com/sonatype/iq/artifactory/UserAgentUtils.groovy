package com.sonatype.iq.artifactory

class UserAgentUtils {
    static String getDefaultUserAgent(String clientVersion) {
        return String.format("Artifactory/%s (%s; %s; %s; %s)",
                clientVersion,
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"));
    }
}
