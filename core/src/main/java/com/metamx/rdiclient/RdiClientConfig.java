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

import com.google.common.base.Preconditions;

import java.util.Properties;

/**
 * RdiClient config class.  A builder is provided for creating an {@code RdiClientConfig}.  The rdiUrl, username,
 * and password are required.
 */

public class RdiClientConfig
{
  private final String rdiUrl;
  private final String username;
  private final String password;
  private final ContentEncoding contentEncoding;
  private final int maxRetries;
  private final int flushCount;
  private final int flushBytes;
  private final long postTimeoutMillis;
  private final int maxConnectionCount;

  public enum ContentEncoding {
    NONE, GZIP
  }

  private RdiClientConfig(
      final String rdiUrl,
      final String username,
      final String password,
      final ContentEncoding contentEncoding,
      final int maxRetries,
      final int flushCount,
      final int flushBytes,
      final long postTimeoutMillis,
      final int maxConnectionCount
  )
  {
    this.rdiUrl = Preconditions.checkNotNull(rdiUrl, "rdiUrl");
    this.username = Preconditions.checkNotNull(username, "username");
    this.password = Preconditions.checkNotNull(password, "password");
    this.contentEncoding = contentEncoding;
    this.maxRetries = maxRetries;
    this.flushCount = flushCount;
    this.flushBytes = flushBytes;
    this.postTimeoutMillis = postTimeoutMillis;
    this.maxConnectionCount = maxConnectionCount;
  }

  /**
   * This returns the Metamarkets API endpoint.  It is a required parameter.
   */
  public String getRdiUrl()
  {
    return rdiUrl;
  }

  /**
   * Your username, which is provided by Metamarkets.  It is a required parameter.
   */
  public String getUsername()
  {
    return username;
  }

  /**
   * Your passsword, which is provided by Metamarkets.  It is a required parameter.
   */
  public String getPassword()
  {
    return password;
  }

  /**
   * An enum for indicating whether or not the client should use encoding and, if so, which type.
   * Current options are "NONE" and "GZIP".  Because events will be compressed by the library,
   * you should not compress them prior to calling the client.
   * <p />
   * The default is {@code ContentEncoding.NONE}.
   */
  public ContentEncoding getContentEncoding()
  {
    return contentEncoding;
  }

  /**
   * The maximum number of retries the client should use when unable to successfully post data to the
   * API.  {@code RdiClient} will use exponential backoff retry strategy up to maxRetries.
   * <p />
   * The default is 20.
   */
  public int getMaxRetries()
  {
    return maxRetries;
  }

  /**
   * The maximum number of events to be queued by {@code RdiClient} when "send" is called.  When
   * "flushCount" is reached, the client will attempt to post a batch to the API.
   * <p />
   * The default is 2000.
   */
  public int getFlushCount()
  {
    return flushCount;
  }

  /**
   * The maximum number of bytes to be queued by {@code RdiClient} when "send" is called.  When
   * "flushBytes" is reached, the client will attempt to post a batch to the API.
   * <p />
   * The default is 921600 (900KiB).
   */
  public int getFlushBytes()
  {
    return flushBytes;
  }

  /**
   * The read timeout to configure for the {@code HttpClient } used by {@code RdiClient}.
   * <p />
   * The default is 30000 (30 seconds).
   */
  public long getPostTimeoutMillis()
  {
    return postTimeoutMillis;
  }

  /**
   * The maximum number of http connections used by {@code RdiClient}.
   * <p />
   * The default is 1.
   */
  public int getMaxConnectionCount()
  {
    return maxConnectionCount;
  }

  @Override
  public String toString()
  {
    return "RdiClientConfig{" +
           "rdiUrl='" + rdiUrl + '\'' +
           ", username='" + username + '\'' +
           ", password='" + password + '\'' +
           ", contentEncoding=" + contentEncoding +
           ", maxRetries=" + maxRetries +
           ", flushCount=" + flushCount +
           ", flushBytes=" + flushBytes +
           ", postTimeoutMillis=" + postTimeoutMillis +
           ", maxConnectionCount=" + maxConnectionCount +
           '}';
  }

  /**
   * Creates an {@link RdiClientConfig} from a properties file. These properties are recognized:
   * <p />
   * <ul>
   *   <li><b>rdi.url</b>: {@link com.metamx.rdiclient.RdiClientConfig#getRdiUrl}</li>
   *   <li><b>rdi.username</b>: {@link com.metamx.rdiclient.RdiClientConfig#getUsername}</li>
   *   <li><b>rdi.password</b>: {@link com.metamx.rdiclient.RdiClientConfig#getPassword}</li>
   *   <li><b>rdi.flush.count</b>: {@link com.metamx.rdiclient.RdiClientConfig#getFlushCount}</li>
   *   <li><b>rdi.flush.bytes</b>: {@link com.metamx.rdiclient.RdiClientConfig#getFlushBytes}</li>
   *   <li><b>rdi.connection.count</b>: {@link com.metamx.rdiclient.RdiClientConfig#getMaxConnectionCount}</li>
   *   <li><b>rdi.connection.timeout</b>: {@link com.metamx.rdiclient.RdiClientConfig#getPostTimeoutMillis}</li>
   *   <li><b>rdi.connection.retries</b>: {@link com.metamx.rdiclient.RdiClientConfig#getMaxRetries}</li>
   *   <li><b>rdi.content.encoding</b>: {@link com.metamx.rdiclient.RdiClientConfig#getContentEncoding}</li>
   * </ul>
   */
  public static RdiClientConfig fromProperties(final Properties props)
  {
    final RdiClientConfig.Builder builder = RdiClientConfig.builder();

    builder.rdiUrl(props.getProperty("rdi.url"));
    builder.username(props.getProperty("rdi.username"));
    builder.password(props.getProperty("rdi.password"));

    if (props.containsKey("rdi.flush.count")) {
      builder.flushCount(Integer.parseInt(props.getProperty("rdi.flush.count")));
    }

    if (props.containsKey("rdi.flush.bytes")) {
      builder.flushBytes(Integer.parseInt(props.getProperty("rdi.flush.bytes")));
    }

    if (props.containsKey("rdi.connection.count")) {
      builder.maxConnectionCount(Integer.parseInt(props.getProperty("rdi.connection.count")));
    }

    if (props.containsKey("rdi.connection.timeout")) {
      builder.postTimeoutMillis(Long.parseLong(props.getProperty("rdi.connection.timeout")));
    }

    if (props.containsKey("rdi.connection.retries")) {
      builder.maxRetries(Integer.parseInt(props.getProperty("rdi.connection.retries")));
    }

    if (props.containsKey("rdi.content.encoding")) {
      builder.contentEncoding(ContentEncoding.valueOf(props.getProperty("rdi.content.encoding").toUpperCase()));
    }

    return builder.build();
  }

  /**
   * Builder class for creating an {@link RdiClientConfig}.
   */
  public static Builder builder()
  {
    return new Builder();
  }

  public static class Builder
  {
    private String rdiUrl = null;
    private String username = null;
    private String password = null;
    private ContentEncoding contentEncoding = ContentEncoding.NONE;
    private int maxRetries = 20;
    private int flushCount = 2000;
    private int flushBytes = 921600;
    private long postTimeoutMillis = 30 * 1000;
    private int maxConnectionCount = 1;

    public Builder() {}

    public Builder flushCount(final int flushCount)
    {
      this.flushCount = flushCount;
      return this;
    }

    public Builder rdiUrl(final String rdiUrl)
    {
      this.rdiUrl = rdiUrl;
      return this;
    }

    public Builder username(final String username)
    {
      this.username = username;
      return this;
    }

    public Builder password(final String password)
    {
      this.password = password;
      return this;
    }

    public Builder contentEncoding(final ContentEncoding contentEncoding)
    {
      this.contentEncoding = contentEncoding;
      return this;
    }

    public Builder maxRetries(final int maxRetries)
    {
      this.maxRetries = maxRetries;
      return this;
    }

    public Builder flushBytes(final int maxQueueBytes)
    {
      this.flushBytes = maxQueueBytes;
      return this;
    }

    public Builder postTimeoutMillis(final long postTimeoutMillis)
    {
      this.postTimeoutMillis = postTimeoutMillis;
      return this;
    }

    public Builder maxConnectionCount(final int maxConnectionCount)
    {
      this.maxConnectionCount = maxConnectionCount;
      return this;
    }

    public RdiClientConfig build()
    {
      return new RdiClientConfig(
          rdiUrl,
          username,
          password,
          contentEncoding,
          maxRetries,
          flushCount,
          flushBytes,
          postTimeoutMillis,
          maxConnectionCount
      );
    }
  }
}
