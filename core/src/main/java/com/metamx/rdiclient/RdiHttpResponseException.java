/*
 * Rdi-Client.
 * Copyright 2014 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.metamx.rdiclient;

import com.metamx.http.client.response.StatusResponseHolder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

/**
 * Class for making exceptions based off Http Response codes (e.g. 400s & 500s).
 * Extends RdiException.
 *
 * You can parse the the exception response HTTP response to get the status code.
 */
public class RdiHttpResponseException extends RdiException
{
  private final HttpResponseStatus status;
  private final String response;

  public RdiHttpResponseException(final StatusResponseHolder response)
  {
    super(
        String.format(
            "Failed to POST: %s %s",
            response.getStatus().getCode(),
            response.getStatus().getReasonPhrase()
        )
    );
    this.status = response.getStatus();
    this.response = response.getContent();
  }

  public int getStatusCode() { return status.getCode(); }

  public String getStatusReasonPhrase() { return status.getReasonPhrase(); }

  public String getResponse() { return response; }
}
