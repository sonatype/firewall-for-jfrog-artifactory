package com.sonatype.iq.artifactory.httpclient

class UserAgentUtils {

    static String getDefaultUserAgent(String clientVersion, String clientEdition) {
        return String.format("Artifactory/%s (%s; %s; %s; %s; %s)",
                clientVersion,
                clientEdition,
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"));
    }

}
