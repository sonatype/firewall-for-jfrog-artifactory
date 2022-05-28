package com.sonatype.iq.artifactory

import spock.lang.Specification

class UserAgentUtilsTest extends Specification {

    def 'test get default user agent'() {
         setup:
            def expectedUserAgent = String.format("Artifactory/2.3-SNAPSHOT (7.37.15) (%s; %s; %s; %s)",
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                    System.getProperty("java.version"))

        when:
            def userAgent = UserAgentUtils.getDefaultUserAgent("2.3-SNAPSHOT (7.37.15)")

        then:
            userAgent == expectedUserAgent
    }

}
