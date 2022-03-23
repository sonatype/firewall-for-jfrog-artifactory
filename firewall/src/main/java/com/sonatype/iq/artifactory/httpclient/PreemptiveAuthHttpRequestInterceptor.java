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
/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package com.sonatype.iq.artifactory.httpclient;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HttpContext;

/**
 * NOTE: Taken from NXRM 3
 *
 * {@link HttpRequestInterceptor} to force use of preemptive authentication.
 *
 * Use with caution, as this might provide leak of sensitive information! Usually, this should be used to access
 * trusted but protected HTTP services (ie. internal services, within same firewall area, "in house").
 *
 * @since 2.8
 * @see <a href="https://github.com/sonatype/nexus-internal/blob/master/components/nexus-httpclient/src/main/java/org/sonatype/nexus/httpclient/PreemptiveAuthHttpRequestInterceptor.java">original NXRM source here</a>
 */
public class PreemptiveAuthHttpRequestInterceptor
    implements HttpRequestInterceptor
{
  @Override
  public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
    HttpClientContext clientContext = HttpClientContext.adapt(context);
    AuthState authState = clientContext.getTargetAuthState();
    if (authState.getAuthScheme() == null) {
      CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
      HttpHost targetHost = clientContext.getTargetHost();
      Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
      if (creds != null) {
        authState.update(new BasicScheme(), creds);
      }
    }
  }
}
