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

@Library(['private-pipeline-library', 'jenkins-shared', 'iq-pipeline-library']) _

make(
    javaVersion: 'OpenJDK 8',
    mavenVersion: 'Maven 3.6.x',
    usePMD: false,
    useCheckstyle: false,
    releaseRetentionPolicy: RetentionPolicy.TEN_BUILDS,
    deployCondition: { return false },
    downstreamJobName: 'integration-tests',
    artifactsForDownstream: '.zion/repository/com/sonatype/iq/artifactory/**',
    runFeatureBranchPolicyEvaluations: true,
    snapshotBuildAndTest: { mavenCommon, keystoreCredId, deployToRepo, useInstall4J ->
      buildAndTest(mavenCommon, keystoreCredId, deployToRepo, useInstall4J)

      archiveArtifacts(artifacts: 'pom.xml,assembly/**,firewall/**,plugin/**,.zion/repository/**/sonatype/**')
      parallel(['OpenJDK 11'].collectEntries { javaVersion -> [javaVersion, {
        node('ubuntu-zion') {
          stage("Build and Test additional JDK - ${javaVersion}") {
            try {
              def updatedMavenCommon = mavenCommon.collectEntries { key, value -> [key, key == 'javaVersion' ? javaVersion : value] }
              copyArtifacts(projectName: currentBuild.fullProjectName,
                  selector: specific(currentBuild.id),
                  flatten: false)
              buildAndTest(updatedMavenCommon, keystoreCredId, false, useInstall4J)
            }
            finally {
              archiveArtifacts(artifacts: '**/target/*-reports/**,**/module.xml')
              collectTestResults(['**/target/*-reports/*.xml'])
              deleteDir()
            }
          }
        }
      }]})
    },
    iqPolicyEvaluation: { stage ->
      nexusPolicyEvaluation iqApplication: 'firewall-for-jfrog-artifactory',
          iqScanPatterns: [[scanPattern: 'scan_nothing']],
          iqModuleExcludes: [[moduleExclude: '**/assembly/target/**']],
          iqStage: stage,
          failBuildOnNetworkError: true
    },
    additionalArtifacts: '**/module.xml,**/target/*-reports/**/*',
    distFiles: [
        includes: [
            'assembly/target/nexus-iq-artifactory-plugin-**.zip*',
        ]
    ]
)
