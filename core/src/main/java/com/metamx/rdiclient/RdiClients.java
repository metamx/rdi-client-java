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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Throwables;
import com.metamx.common.lifecycle.Lifecycle;
import com.metamx.http.client.HttpClient;
import com.metamx.http.client.HttpClientConfig;
import com.metamx.http.client.HttpClientInit;
import org.joda.time.Duration;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

/**
 * This class contains methods for creating an RdiClient.
 *
 * Each requires an {@code RdiClientConfig}.  There are methods provided for generating RdiClients using a
 * {@code JacksonSerializer} or an {@code PassthroughSerializer}. Using the "usingCustomSerializer" method allows
 * you to also pass in your own serializer.
 *
 */

public class RdiClients
{

  /**
   * Create an RdiClient for posting events of generic type.
   *
   * @param serializer serializer class for serializing event messages
   * @param config client config
   * @param <T> generic type of event message to be serialized and emitted
   * @return return a default RdiClientImpl for generic type
   */
  public static <T> RdiClient<T> usingCustomSerializer(
      final RdiClientConfig config,
      final Serializer<T> serializer
  )
  {
    final Lifecycle lifecycle = new Lifecycle();
    final HttpClient httpClient = makeDefaultHttpClient(config, lifecycle);
    return new RdiClientImpl<>(config, serializer, lifecycle, httpClient);
  }

  /**
   * Create RdiClient for posting Jackson serialized event records.
   *
   * @param config client config
   * @return return default client for jackson-serialzed events.
   */
  public static <T> RdiClient<T> usingJacksonSerializer(final RdiClientConfig config)
  {
    final Serializer<T> serializer = makeJacksonSerializer();
    return usingCustomSerializer(config, serializer);
  }

  /**
   * Create RdiClient for posting pre-serialized event records as byte arrays.
   *
   * @param config client config
   * @return return client for events serialized with the passthrough serializer.
   */
  public static RdiClient<byte[]> usingPassthroughSerializer(final RdiClientConfig config)
  {
    return usingCustomSerializer(config, new PassthroughSerializer());
  }


  /**
   * Generate HttpClient with default settings
   *
   * @param config default config.  1 connection. Timeout duration set in RdiClientConfig.
   * @param lifecycle lifecycle for HttpClient
   * @return HttpClient
   */
  private static HttpClient makeDefaultHttpClient(
      final RdiClientConfig config,
      final Lifecycle lifecycle
  )
  {
    try {
      final HttpClientConfig httpClientConfig = new HttpClientConfig(
          config.getMaxConnectionCount(),
          SSLContext.getDefault(),
          new Duration(config.getPostTimeoutMillis())
      );
      return HttpClientInit.createClient(httpClientConfig, lifecycle);
    }
    catch (NoSuchAlgorithmException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Makes a Joda-aware {@code JacksonSerializer} for generic type events.
   */
  private static <T> Serializer<T> makeJacksonSerializer()
  {
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    return new JacksonSerializer<>(objectMapper);
  }
}
