<!--

    Copyright 2019, Sonatype, Inc.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
# Firewall for JFrog Artifactory Product Overview

The Firewall for JFrog Artifactory plugin extends jFrog Artifactory with the functionality provided by Nexus Firewall:

* Quarantine of components which fail policy evaluation
* Repository audit mode

The [documentation](https://help.sonatype.com/integrations/iq-server-and-repository-management/iq-server-and-firewall-for-artifactory)
contains information about installation and configuration of the plugin.

## High-Level Technical description

Blocking of quarantined components is implemented by hooking into the 'Download'
[(link)](https://www.jfrog.com/confluence/display/JFROG/User+Plugins#UserPlugins-Download) event of JFrog Artifactory. When
the policy evaluation of the requested component fails then an CancelException is thrown which will cause the Download
to be blocked. The plugin doesn't make use of a database or access the JFrog Artifactory database directly. However,
JFrog Artifactory 'properties' are used to store component metadata.

## Supported features

* Quarantine mode
* Audit mode
* Release components from quarantine
* Ignore patterns for audit paths
* Caching of policy evaluation result
* Safe mode
* Proprietary components

## Architecture Diagram

```
  .-----------. (2)    (3) .-------------------. (1)   (4) .--------.
  | IQ Server | <--------> | JFrog Artifactory | <-------> | Client |
  `-----------`    REST    `-------------------`   HTTP    `--------`
```

1) Request for a component received by JFrog Artifactory
2) Component evaluated on IQ Server
3) ALLOW / DENY status received and saved to cache
4) Access to component is allowed or denied

## About JFrog Artifactory plugins

To keep this plugin as stable as possible across JFrog Artifactory releases we only use functionality provided by the
official [plugin APIs](https://www.jfrog.com/confluence/display/JFROG/User+Plugins).

## Build dependencies on external projects

The plugin requires dependencies from the [jcenter repository](https://jcenter.bintray.com/) or [JFrogJars](https://dl.bintray.com/jfrog/jfrog-jars/).

## Project directory layout

* **assembly**: Builds artifact using maven-assembly-plugin
* **docs**: This directory
* **firewall**: Builds the firewall-plugin's jar file
* **plugin**: Contains the groovy file implementing the plugin
* **vars**: build scripts
