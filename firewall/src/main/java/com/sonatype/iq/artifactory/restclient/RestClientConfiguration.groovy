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
package com.sonatype.iq.artifactory.restclient

import com.sonatype.insight.client.utils.HttpClientUtils
import com.sonatype.insight.client.utils.HttpClientUtils.Configuration

import org.apache.http.impl.client.HttpClientBuilder

class RestClientConfiguration
{
  private final Configuration config

  RestClientConfiguration() {
    config = new Configuration()
  }

  Configuration getConfig() {
    return config
  }

  RestClientConfiguration setServerUrl(final String serverUrl) {
    config.setServerUrl(serverUrl)
    return this
  }

  RestClientConfiguration setHttpClientProvider(final HttpClientProvider httpClientProvider) {
    config.setHttpClientProvider(new HttpClientUtils.HttpClientProvider()
    {
      @Override
      HttpClientBuilder create(final Configuration config) {
        return httpClientProvider.createHttpClient(RestClientConfiguration.this)
      }
    })
    return this
  }

  static interface HttpClientProvider
  {
    HttpClientBuilder createHttpClient(RestClientConfiguration config)
  }
}
