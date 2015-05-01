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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.metamx.common.lifecycle.Lifecycle;
import com.metamx.datatypes.mmx.MmxAuctionSummary;
import com.metamx.datatypes.openrtb.App;
import com.metamx.datatypes.openrtb.Ext;
import com.metamx.datatypes.openrtb.Publisher;
import com.metamx.http.client.GoHandler;
import com.metamx.http.client.MockHttpClient;
import com.metamx.http.client.Request;
import com.metamx.http.client.response.HttpResponseHandler;
import com.metamx.http.client.response.StatusResponseHandler;
import com.metamx.http.client.response.StatusResponseHolder;
import junit.framework.Assert;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class RdiClientImplTest
{
  private static final String FEED = "dummy";

  final MockHttpClient mockClient = new MockHttpClient();
  final Lifecycle lifecycle = new Lifecycle();

  final MmxAuctionSummary sampleEventBasic = MmxAuctionSummary
      .builder()
      .timestamp(new DateTime("2014-01-01T00:00:00.000Z")).auctionType(2).build();

  final MmxAuctionSummary sampleEventLarge = MmxAuctionSummary
      .builder()
      .timestamp(new DateTime("2014-01-01T00:00:00.000Z"))
      .auctionType(2)
      .bcat(Arrays.asList("IAB26", "IAB25"))
      .requestId("AFEWSEBD5EB5FI32DASFCD452BB78DVE")
      .ext(
          Ext.builder().put("custFlag", 3).put("custStr", "Unicorns are the best!").build()
      ).app(
          App.builder()
             .bundle("bundlename")
             .cat(Arrays.asList("IAB1"))
             .domain("unicornssay.com")
             .id("12312312")
             .name("Unicornssay")
             .publisher(
                 Publisher.builder().id("DSA1394D42D3").name("Unicornssay").build()
             ).build()
      ).build();

  final String TARGET_URL = "http://metrics.foo.bar/foo-bar";

  private <T> RdiClientImpl<T> makeRdiClient(Serializer<T> serializer, int flushCount)
  {
    final RdiClientConfig config = RdiClientConfig.builder()
                                                  .flushCount(flushCount)
                                                  .maxRetries(3)
                                                  .rdiUrl(TARGET_URL)
                                                  .username("donkey")
                                                  .password("donkeys-love-flowers")
                                                  .postTimeoutMillis(60000)
                                                  .contentEncoding(RdiClientConfig.ContentEncoding.GZIP)
                                                  .flushBytes((int) (1024 * 1024 * 2.5))
                                                  .build();

    return new RdiClientImpl<>(config, serializer, lifecycle, mockClient, 0);
  }

  private static StatusResponseHolder okResponse()
  {
    return serverResponse(201, "Created");
  }

  private static StatusResponseHolder serverResponse(int code, String message)
  {
    return new StatusResponseHolder(new HttpResponseStatus(code, message), new StringBuilder("Yay"));
  }

  @Test
  public void testFlushCountBasedFlushing() throws Exception
  {
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final RdiClientImpl<byte[]> rdiClient = makeRdiClient(new PassthroughSerializer(), 1);

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            Assert.assertEquals(new URL(TARGET_URL + "/events/" + FEED), request.getUrl());
            Preconditions.checkArgument(
                httpResponseHandler instanceof StatusResponseHandler,
                "WTF?! Expected StatusResponseHandler."
            );
            return Futures.immediateFuture((Final) okResponse());
          }
        }.times(2)
    );

    rdiClient.start();
    final List<ListenableFuture<RdiResponse>> futures = Lists.newArrayList();
    for (MmxAuctionSummary event : Arrays.asList(sampleEventBasic, sampleEventBasic)) {
      futures.add(rdiClient.send(FEED, objectMapper.writeValueAsBytes(event)));
    }
    final List<RdiResponse> responses = Futures.allAsList(futures).get();
    Assert.assertEquals(Lists.newArrayList(RdiResponse.create(), RdiResponse.create()), responses);
    rdiClient.close();
    Assert.assertTrue("mockClient succeeded", mockClient.succeeded());
  }

  @Test
  public void testMaxQueueBytesFlushing() throws Exception
  {
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);
    final RdiClientConfig config = RdiClientConfig.builder()
                                                  .flushCount(500)
                                                  .maxRetries(3)
                                                  .rdiUrl(TARGET_URL)
                                                  .username("donkey")
                                                  .password("donkeys-love-flowers")
                                                  .postTimeoutMillis(60000)
                                                  .flushBytes(1000)
                                                  .build();

    final RdiClientImpl<MmxAuctionSummary> rdiClient = new RdiClientImpl<>(config, serializer, lifecycle, mockClient);

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            Assert.assertEquals(new URL(TARGET_URL + "/events/" + FEED), request.getUrl());
            Preconditions.checkArgument(
                httpResponseHandler instanceof StatusResponseHandler,
                "WTF?! Expected StatusResponseHandler."
            );
            return Futures.immediateFuture((Final) okResponse());
          }
        }.times(4)
    );

    rdiClient.start();
    for (MmxAuctionSummary event : Arrays.asList(
        sampleEventLarge,
        sampleEventLarge,
        sampleEventLarge,
        sampleEventLarge
    )) {
      rdiClient.send(FEED, event);
    }
    rdiClient.flush();
    rdiClient.close();
    Assert.assertTrue("mockClient succeeded", mockClient.succeeded());
  }

  @Test
  public void testManualFlush() throws Exception
  {
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);
    final RdiClientImpl<MmxAuctionSummary> rdiClient = makeRdiClient(serializer, 1000);

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            Assert.assertEquals(new URL(TARGET_URL + "/events/" + FEED), request.getUrl());
            Preconditions.checkArgument(
                httpResponseHandler instanceof StatusResponseHandler,
                "WTF?! Expected StatusResponseHandler."
            );
            return Futures.immediateFuture((Final) okResponse());
          }
        }.times(1)
    );

    rdiClient.start();
    for (MmxAuctionSummary event : Arrays.asList(
        sampleEventLarge,
        sampleEventLarge,
        sampleEventLarge,
        sampleEventLarge
    )) {
      rdiClient.send(FEED, event);
    }
    rdiClient.flush();
    rdiClient.close();
    Assert.assertTrue("mockClient succeeded", mockClient.succeeded());
  }

  @Rule
  public ExpectedException exception = ExpectedException.none();

  // Test MAX_EVENT_SIZE check
  @Test
  public void testOversizeEvent() throws Exception
  {
    final RdiClientImpl<byte[]> rdiClient = makeRdiClient(new PassthroughSerializer(), 1000);

    final byte[] oversizeEvent = new byte[201*1024];

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            Assert.assertEquals(new URL(TARGET_URL + "/events/" + FEED), request.getUrl());
            Preconditions.checkArgument(
                httpResponseHandler instanceof StatusResponseHandler,
                "WTF?! Expected StatusResponseHandler."
            );
            return Futures.immediateFuture((Final) okResponse());
          }
        }.times(1)
    );

    rdiClient.start();
    exception.expect(IllegalArgumentException.class);
    rdiClient.send(FEED, oversizeEvent);
    rdiClient.flush();
    rdiClient.close();
  }

  @Test
  public void testMmxAuctionSummary() throws Exception
  {

    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);
    final RdiClientImpl<MmxAuctionSummary> rdiClient = makeRdiClient(serializer, 1);

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            Assert.assertEquals(new URL(TARGET_URL + "/events/" + FEED), request.getUrl());
            Preconditions.checkArgument(
                httpResponseHandler instanceof StatusResponseHandler,
                "WTF?! Expected StatusResponseHandler."
            );
            Preconditions.checkArgument(
                ImmutableList.of("application/json").equals(request.getHeaders().get(HttpHeaders.Names.CONTENT_TYPE)),
                "WTF?! Content-type should have been JSON!"
            );
            Preconditions.checkArgument(
                ImmutableList.of("gzip").equals(request.getHeaders().get(HttpHeaders.Names.CONTENT_ENCODING)),
                "WTF?! Should have been gzip!"
            );
            return Futures.immediateFuture((Final) okResponse());
          }
        }.times(3)
    );

    final List<MmxAuctionSummary> events = Arrays.asList(sampleEventBasic, sampleEventBasic, sampleEventBasic);
    rdiClient.start();
    for (MmxAuctionSummary event : events) {
      rdiClient.send(FEED, event);
    }
    rdiClient.close();
    Assert.assertTrue("mockClient succeeded", mockClient.succeeded());
  }

  // Test exceptions from Http Response codes (e.g. 400 or 500 statuses)
  @Test
  public void testBadResponseCode() throws Exception
  {
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);
    final RdiClientImpl<MmxAuctionSummary> rdiClient = makeRdiClient(serializer, 1);

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            return Futures.immediateFuture((Final) serverResponse(503, "Internal Server Error."));
          }
        }.times(12)
    );
    final List<MmxAuctionSummary> events = Arrays.asList(sampleEventBasic, sampleEventBasic, sampleEventBasic);
    rdiClient.start();
    for (MmxAuctionSummary event : events) {
      final ListenableFuture<RdiResponse> result = rdiClient.send(FEED, event);
      Exception e = null;
      try {
        result.get();
      } catch (Exception e2) {
        e = e2;
      }
      Assert.assertTrue(e instanceof ExecutionException);
      Assert.assertTrue(e.getCause() instanceof RdiHttpResponseException);
      Assert.assertTrue(((RdiHttpResponseException) e.getCause()).getStatusCode() == 503);
      Assert.assertTrue(
          ((RdiHttpResponseException) e.getCause()).getStatusReasonPhrase()
                                                   .equals("Internal Server Error.")
      );
    }
    rdiClient.close();
  }

  // Test RdiException that is NOT an http response (e.g. network problems)
  @Test
  public void testExceptionOnPost() throws Exception
  {

    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);
    final RdiClientImpl<MmxAuctionSummary> rdiClient = makeRdiClient(serializer, 1);

    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            return Futures.immediateFailedFuture(new IOException("Something Crazy Happened!"));
          }
        }.times(4)
    );
    rdiClient.start();

    final ListenableFuture<RdiResponse> result = rdiClient.send(FEED, sampleEventBasic);
    Exception e = null;
    try {
      result.get();
    } catch (Exception e2) {
      e = e2;
    }
    Assert.assertTrue(e instanceof ExecutionException);
    Assert.assertTrue(e.getCause() instanceof RdiException);
    Assert.assertTrue(e.getCause().getCause() instanceof IOException);
    rdiClient.close();
  }

  @Test
  public void testExceptionOnPostRecoverable() throws Exception
  {

    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);
    final RdiClientImpl<MmxAuctionSummary> rdiClient = makeRdiClient(serializer, 1);

    final AtomicInteger failures = new AtomicInteger(0);
    mockClient.setGoHandler(
        new GoHandler()
        {
          @Override
          protected <Intermediate, Final> ListenableFuture<Final> go(
              Request request, HttpResponseHandler<Intermediate, Final> httpResponseHandler
          ) throws Exception
          {
            if (failures.getAndIncrement() == 0) {
              return Futures.immediateFailedFuture(new IOException("Something Crazy Happened!"));
            } else {
              return Futures.immediateFuture((Final) okResponse());
            }
          }
        }.times(2)
    );
    rdiClient.start();

    final ListenableFuture<RdiResponse> result = rdiClient.send(FEED, sampleEventBasic);
    Assert.assertEquals(RdiResponse.create(), result.get());
    rdiClient.close();
  }
}
