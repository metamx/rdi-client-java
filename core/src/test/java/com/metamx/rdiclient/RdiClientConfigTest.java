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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Properties;

/**
 */
public class RdiClientConfigTest
{

  private final String url = "https://unicornssay.com";
  private final String userName = "donkey";
  private final String password = "kong";
  private final RdiClientConfig.ContentEncoding encoding = RdiClientConfig.ContentEncoding.GZIP;
  private final int maxRetries = 2;
  private final int flushCount = 2000;
  private final long postTimeoutMillis = 1000;
  private final int flushBytes = 1024;
  private final int maxConnectionCount = 5;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testBuilderAllParams()
  {
    final RdiClientConfig config = RdiClientConfig.builder()
                                                  .rdiUrl(url)
                                                  .username(userName)
                                                  .password(password)
                                                  .contentEncoding(encoding)
                                                  .maxRetries(maxRetries)
                                                  .flushCount(flushCount)
                                                  .postTimeoutMillis(postTimeoutMillis)
                                                  .flushBytes(flushBytes)
                                                  .maxConnectionCount(maxConnectionCount)
                                                  .build();

    Assert.assertEquals(url, config.getRdiUrl());
    Assert.assertEquals(userName, config.getUsername());
    Assert.assertEquals(password, config.getPassword());
    Assert.assertEquals(encoding, config.getContentEncoding());
    Assert.assertEquals(maxRetries, config.getMaxRetries());
    Assert.assertEquals(flushCount, config.getFlushCount());
    Assert.assertEquals(postTimeoutMillis, config.getPostTimeoutMillis());
    Assert.assertEquals(flushBytes, config.getFlushBytes());
    Assert.assertEquals(maxConnectionCount, config.getMaxConnectionCount());
  }

  @Test
  public void testBuilderDefaultParams()
  {
    final RdiClientConfig config = RdiClientConfig.builder()
                                                  .rdiUrl(url)
                                                  .username(userName)
                                                  .password(password)
                                                  .build();

    Assert.assertEquals(url, config.getRdiUrl());
    Assert.assertEquals(userName, config.getUsername());
    Assert.assertEquals(password, config.getPassword());
    Assert.assertEquals(RdiClientConfig.ContentEncoding.NONE, config.getContentEncoding());
    Assert.assertEquals(20, config.getMaxRetries());
    Assert.assertEquals(2000, config.getFlushCount());
    Assert.assertEquals(30 * 1000, config.getPostTimeoutMillis());
    Assert.assertEquals(921600, config.getFlushBytes());
    Assert.assertEquals(1, config.getMaxConnectionCount());
  }

  @Test
  public void testPropertiesAllParams()
  {
    final Properties props = new Properties();
    props.setProperty("rdi.url", url);
    props.setProperty("rdi.username", userName);
    props.setProperty("rdi.password", password);
    props.setProperty("rdi.content.encoding", String.valueOf(encoding));
    props.setProperty("rdi.connection.retries", String.valueOf(maxRetries));
    props.setProperty("rdi.flush.count", String.valueOf(flushCount));
    props.setProperty("rdi.connection.timeout", String.valueOf(postTimeoutMillis));
    props.setProperty("rdi.flush.bytes", String.valueOf(flushBytes));
    props.setProperty("rdi.connection.count", String.valueOf(maxConnectionCount));

    final RdiClientConfig config = RdiClientConfig.fromProperties(props);
    Assert.assertEquals(url, config.getRdiUrl());
    Assert.assertEquals(userName, config.getUsername());
    Assert.assertEquals(password, config.getPassword());
    Assert.assertEquals(encoding, config.getContentEncoding());
    Assert.assertEquals(maxRetries, config.getMaxRetries());
    Assert.assertEquals(flushCount, config.getFlushCount());
    Assert.assertEquals(postTimeoutMillis, config.getPostTimeoutMillis());
    Assert.assertEquals(flushBytes, config.getFlushBytes());
    Assert.assertEquals(maxConnectionCount, config.getMaxConnectionCount());
  }

  @Test
  public void testPropertiesDefaultParams()
  {
    final Properties props = new Properties();
    props.put("rdi.url", url);
    props.put("rdi.username", userName);
    props.put("rdi.password", password);

    final RdiClientConfig config = RdiClientConfig.fromProperties(props);
    Assert.assertEquals(url, config.getRdiUrl());
    Assert.assertEquals(userName, config.getUsername());
    Assert.assertEquals(password, config.getPassword());
    Assert.assertEquals(RdiClientConfig.ContentEncoding.NONE, config.getContentEncoding());
    Assert.assertEquals(20, config.getMaxRetries());
    Assert.assertEquals(2000, config.getFlushCount());
    Assert.assertEquals(30 * 1000, config.getPostTimeoutMillis());
    Assert.assertEquals(921600, config.getFlushBytes());
    Assert.assertEquals(1, config.getMaxConnectionCount());
  }

  @Test
  public void testUrlNotNullCheck()
  {
    thrown.expect(NullPointerException.class);
    RdiClientConfig.builder().username("foo").password("bar").build();
  }

  @Test
  public void testUsernameNotNullCheck()
  {
    thrown.expect(NullPointerException.class);
    RdiClientConfig.builder().rdiUrl("http://example.com/").password("bar").build();
  }

  @Test
  public void testPasswordNotNullCheck()
  {
    thrown.expect(NullPointerException.class);
    RdiClientConfig.builder().rdiUrl("http://example.com/").username("foo").build();
  }
}
