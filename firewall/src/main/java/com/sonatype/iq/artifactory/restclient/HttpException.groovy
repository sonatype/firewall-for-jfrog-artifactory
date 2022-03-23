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

class HttpException
    extends IOException
{
  private final int status

  private final String reason

  HttpException(int status, String reason, Throwable cause) {
    super("Error code " + status + ": " + reason, cause)
    this.status = status
    this.reason = reason
  }

  int getStatus() {
    return status
  }

  String getReason() {
    return reason
  }
}
