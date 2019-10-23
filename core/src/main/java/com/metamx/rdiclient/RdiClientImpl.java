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

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.metamx.common.IAE;
import com.metamx.common.Pair;
import com.metamx.common.concurrent.ScheduledExecutors;
import com.metamx.common.lifecycle.Lifecycle;
import com.metamx.common.lifecycle.LifecycleStart;
import com.metamx.common.lifecycle.LifecycleStop;
import com.metamx.common.logger.Logger;
import com.metamx.http.client.HttpClient;
import com.metamx.http.client.Request;
import com.metamx.http.client.response.StatusResponseHandler;
import com.metamx.http.client.response.StatusResponseHolder;
import com.metamx.rdiclient.metrics.RdiMetrics;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

public class RdiClientImpl<T> implements RdiClient<T>
{
  private static final Logger log = new Logger(RdiClientImpl.class);

  private final ScheduledExecutorService retryExecutor = ScheduledExecutors.fixed(1, "RdiClientRetryTimer");
  private final RdiClientConfig config;
  private final Serializer<T> serializer;
  private final Lifecycle lifecycle;
  private final HttpClient httpClient;
  private final URL baseUrl;
  private final long retryDurationOverride;
  private final RdiMetrics metrics;

  private static final int MAX_EVENT_SIZE = 100 * 1024; // Set max event size of 100 KB

  private final Object bufferLock = new Object();
  private final Semaphore postSemaphore;
  private int queuedByteCount = 0; // Only use under bufferLock

  // feed -> list of (future for one message, that message); one per each outstanding future
  private Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> buffers; // Only use under bufferLock

  private final String rdiClientVersion;

  RdiClientImpl(
      final RdiClientConfig config,
      final Serializer<T> serializer,
      final Lifecycle lifecycle,
      final HttpClient httpClient
  )
  {
    this(config, serializer, lifecycle, httpClient, -1);
  }

  RdiClientImpl(
      final RdiClientConfig config,
      final Serializer<T> serializer,
      final Lifecycle lifecycle,
      final HttpClient httpClient,
      final long retryDurationOverride
  )
  {
    this.config = config;
    this.serializer = serializer;
    this.lifecycle = lifecycle;
    this.httpClient = httpClient;
    this.rdiClientVersion = RdiClient.class.getPackage().getImplementationVersion();
    this.postSemaphore = new Semaphore(config.getMaxConnectionCount());
    this.buffers = Maps.newHashMap();
    this.retryDurationOverride = retryDurationOverride;
    this.metrics = new RdiMetrics();
    try {
      this.baseUrl = new URL(config.getRdiUrl());
    }
    catch (MalformedURLException e) {
      throw new IAE(String.format("Invalid URL: %s", config.getRdiUrl()));
    }
  }

  @Override
  @LifecycleStart
  public void start()
  {
    try {
      lifecycle.start();
    }
    catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public RdiMetrics getMetrics()
  {
    return metrics;
  }

  @Override
  public ListenableFuture<RdiResponse> send(final String feed, final T event) throws InterruptedException
  {
    final SettableFuture<RdiResponse> retVal = SettableFuture.create();
    final byte[] eventBytes;

    try {
      eventBytes = serializer.serialize(event);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize message", e);
    }

    // Require that individual event size does not exceed limit.
    if (eventBytes.length > MAX_EVENT_SIZE) {
      throw new IllegalArgumentException(
          String.format(
              "Event size exceeded max event size (%,d > %,d): %s ...",
              eventBytes.length,
              MAX_EVENT_SIZE,
              new String(eventBytes, 0, MAX_EVENT_SIZE)
          )
      );
    }

    final Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> preSendSwappedBuffers;
    final Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> postSendSwappedBuffers;
    synchronized (bufferLock) {
      if (queuedByteCount + eventBytes.length >= config.getFlushBytes()) {
        preSendSwappedBuffers = swap();
      } else {
        preSendSwappedBuffers = Maps.newHashMap();
      }

      final ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>> buffer = getBuffer(feed);

      buffer.add(Pair.of(retVal, eventBytes));
      queuedByteCount += eventBytes.length;

      if (buffer.size() >= config.getFlushCount()) {
        postSendSwappedBuffers = swap();
      } else {
        postSendSwappedBuffers = Maps.newHashMap();
      }
    }

    flushMany(ImmutableList.of(preSendSwappedBuffers, postSendSwappedBuffers));

    return retVal;
  }

  @Override
  public void flush() throws InterruptedException
  {
    flushMany(ImmutableList.of(swap()));
  }

  // If you do *anything* with the buffer returned by this method, you *must* have already acquired the bufferLock.
  // This is because the buffers are not thread-safe.
  private ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>> getBuffer(final String feed)
  {
    synchronized (bufferLock) {
      ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>> buffer = buffers.get(feed);
      if (buffer == null) {
        buffer = Lists.newArrayListWithExpectedSize(config.getFlushCount());
        buffers.put(feed, buffer);
      }
      return buffer;
    }
  }

  private Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> swap()
  {
    synchronized (bufferLock) {
      final Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> exBuffers = buffers;
      buffers = Maps.newHashMap();
      queuedByteCount = 0;
      return exBuffers;
    }
  }

  // This method ensures that the SettableFutures in exBuffers will be resolved.
  private void flushMany(
      final List<Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>>> exBufferss
  )
  {
    final List<Exception> exceptions = Lists.newArrayList();

    for (Map<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> exBuffers : exBufferss) {
      for (Map.Entry<String, ArrayList<Pair<SettableFuture<RdiResponse>, byte[]>>> entry : exBuffers.entrySet()) {
        try {
          flushSingle(entry.getKey(), entry.getValue());
        } catch (Exception e) {
          // Remember the exception and keep flushing other buffers. It's important to flush all the buffers, because
          // otherwise the futures we have already given to the user will never resolve.
          exceptions.add(
              new RuntimeException(String.format("Flush failed for feed[%s]", entry.getKey()), e)
          );
        }
      }
    }

    // Throw exceptions, if any.
    if (!exceptions.isEmpty()) {
      final RuntimeException e = new RuntimeException("Flush failed");
      for (Exception e2 : exceptions) {
        e.addSuppressed(e2);
      }
      throw e;
    }
  }

  // This method ensures that the SettableFutures in exBuffer will be resolved.
  private void flushSingle(
      final String feed,
      final List<Pair<SettableFuture<RdiResponse>, byte[]>> exBuffer
  ) throws InterruptedException
  {
    if (exBuffer.isEmpty()) {
      // Nothing to do.
      return;
    }

    postSemaphore.acquire();
    final AtomicBoolean released = new AtomicBoolean(false);
    try {
      // Determine URL for this feed.
      final URL url;
      try {
        url = new URL(
            baseUrl.getProtocol(),
            baseUrl.getHost(),
            baseUrl.getPort(),
            (baseUrl.getPath().endsWith("/") ? baseUrl.getPath() + "events/" : baseUrl.getPath() + "/events/") + feed,
            null
        );
      } catch (MalformedURLException e) {
        throw Throwables.propagate(e);
      }

      // Batch events prior to posting.
      final byte[] serializedBatch = serializeBatch(exBuffer);

      RdiClientConfig.ContentEncoding contentEncoding = config.getContentEncoding();
      String username = config.getUsername();
      String password = config.getPassword();

      // Build new request using provided HttpClient
      Request newRequest = new Request(HttpMethod.POST, url);

      // Set client version if known.
      if (rdiClientVersion != null && !rdiClientVersion.isEmpty()) {
        newRequest.setHeader("X-RdiClient-Version", rdiClientVersion);
      } else {
        newRequest.setHeader("X-RdiClient-Version", "unknown");
      }

      // Set header for content encoding if specified.
      if (contentEncoding != RdiClientConfig.ContentEncoding.NONE) {
        newRequest.setHeader("Content-Encoding", contentEncoding.toString().toLowerCase());
      }

      log.debug("url[%s] feed[%s] events.size[%s]", url, feed, exBuffer.size());
      newRequest.setBasicAuthentication(username, password);
      newRequest.setContent("application/json", serializedBatch);  // In the future, may not be hard coded to json

      final long requestTime = System.currentTimeMillis();

      // POST data to endpoint.  On exceptions retry w/ exponential backoff.
      final ListenableFuture<HttpResponseStatus> status = retryingPost(
          httpClient,
          feed,
          newRequest,
          0,
          config.getMaxRetries()
      );

      Futures.addCallback(
          status,
          new FutureCallback<HttpResponseStatus>()
          {
            @Override
            public void onSuccess(HttpResponseStatus result)
            {
              if (released.compareAndSet(false, true)) {
                postSemaphore.release();
                for (Pair<SettableFuture<RdiResponse>, byte[]> pair : exBuffer) {
                  pair.lhs.set(new RdiResponse());
                  metrics.incSent(feed, pair.rhs.length);
                }
              } else {
                log.warn("Blocked attempted double-release of semaphore.");
              }

              log.debug(
                  "Received status[%s %s] for feed[%s] after %,dms.",
                  result.getCode(),
                  result.getReasonPhrase().trim(),
                  feed,
                  System.currentTimeMillis() - requestTime
              );
            }

            @Override
            public void onFailure(Throwable e)
            {
              if (released.compareAndSet(false, true)) {
                postSemaphore.release();
                for (Pair<SettableFuture<RdiResponse>, byte[]> pair : exBuffer) {
                  pair.lhs.setException(e);
                  metrics.incFailed(feed);
                }
              } else {
                log.warn("Blocked attempted double-release of semaphore.");
              }

              log.warn(e, "Got exception when posting events to urlString[%s] for feed[%s].", config.getRdiUrl(), feed);
            }
          }
      );
    }
    catch (Throwable e) {
      if (released.compareAndSet(false, true)) {
        postSemaphore.release();
        for (Pair<SettableFuture<RdiResponse>, byte[]> pair : exBuffer) {
          pair.lhs.setException(e);
          metrics.incFailed(feed);
        }
      } else {
        log.warn("Blocked attempted double-release of semaphore.");
      }
      throw e;
    }
  }

  private long retryDuration(final int attempt)
  {
    if (retryDurationOverride >= 0) {
      return retryDurationOverride;
    } else {
      final long baseSleepMillis = 1000;
      final long maxSleepMillis = 60000;
      final double fuzzyMultiplier = Math.min(Math.max(1.0 + 0.2 * new Random().nextGaussian(), 0.0), 2.0);
      return (long) (Math.min(maxSleepMillis, baseSleepMillis * Math.pow(2, attempt)) * fuzzyMultiplier);
    }
  }

  private ListenableFuture<HttpResponseStatus> retryingPost(
      final HttpClient httpClient,
      final String feed,
      final Request request,
      final int attempt,
      final int maxRetries
  )
  {
    final SettableFuture<HttpResponseStatus> retVal = SettableFuture.create();
    final ListenableFuture<HttpResponseStatus> response = Futures.transformAsync(
        httpClient.go(request, new StatusResponseHandler(Charsets.UTF_8)),
        new AsyncFunction<StatusResponseHolder, HttpResponseStatus>()
        {
          @Override
          public ListenableFuture<HttpResponseStatus> apply(StatusResponseHolder result) throws Exception
          {
            // Throw an RdiHttpResponseException in case of unexpected HTTP status codes.
            if (result.getStatus().getCode() / 100 == 2) {
              return Futures.immediateFuture(result.getStatus());
            } else {
              return Futures.immediateFailedFuture(new RdiHttpResponseException(result));
            }
          }
        }
    );
    Futures.addCallback(
        response,
        new FutureCallback<HttpResponseStatus>()
        {
          @Override
          public void onSuccess(HttpResponseStatus result)
          {
            retVal.set(result);
          }

          @Override
          public void onFailure(Throwable e)
          {
            final boolean shouldRetry;
            if (maxRetries <= 0) {
              shouldRetry = false;
            } else if (e instanceof IOException || e instanceof ChannelException) {
              shouldRetry = true;
            } else if (e instanceof RdiHttpResponseException) {
              final int statusCode = ((RdiHttpResponseException) e).getStatusCode();
              shouldRetry = statusCode / 100 == 5 || (statusCode / 100 == 4 && (statusCode != 400 || config.isRetryOnBadRequest()));
            } else {
              shouldRetry = false;
            }

            if (shouldRetry) {
              final long sleepMillis = retryDuration(attempt);
              log.warn(
                  e,
                  "Failed try #%d for feed[%s], retrying in %,dms (%,d tries left).",
                  attempt + 1,
                  feed,
                  sleepMillis,
                  maxRetries
              );
              metrics.incRetransmitted(feed);

              retryExecutor.schedule(
                  new Runnable()
                  {
                    @Override
                    public void run()
                    {
                      final ListenableFuture<HttpResponseStatus> nextTry = retryingPost(
                          httpClient,
                          feed,
                          request,
                          attempt + 1,
                          maxRetries - 1
                      );
                      Futures.addCallback(
                          nextTry,
                          new FutureCallback<HttpResponseStatus>()
                          {
                            @Override
                            public void onSuccess(HttpResponseStatus result2)
                            {
                              retVal.set(result2);
                            }

                            @Override
                            public void onFailure(Throwable e2)
                            {
                              retVal.setException(e2);
                            }
                          }
                      );
                    }
                  },
                  sleepMillis,
                  TimeUnit.MILLISECONDS
              );
            } else if (e instanceof RdiException || e instanceof Error) {
              retVal.setException(e);
            } else {
              retVal.setException(
                  new RdiException(
                      String.format(
                          "Got exception when posting events to urlString[%s].",
                          config.getRdiUrl()
                      ),
                      e
                  )
              );
            }
          }
        }
    );

    return retVal;
  }

  // Create batches of newline-separated events.  Compress batch if contentEncoding variable is set.
  private byte[] serializeBatch(List<Pair<SettableFuture<RdiResponse>, byte[]>> events)
  {
    try {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final OutputStream os;
      RdiClientConfig.ContentEncoding contentEncoding = config.getContentEncoding();

      if (RdiClientConfig.ContentEncoding.GZIP.equals(contentEncoding)) {
        os = new GZIPOutputStream(baos);
      } else if (RdiClientConfig.ContentEncoding.NONE.equals(contentEncoding)) {
        os = baos;
      } else {
        throw new IllegalArgumentException(
            String.format(
                "Unrecognized content encoding: %s.",
                contentEncoding.toString()
            )
        );
      }

      for (Pair<SettableFuture<RdiResponse>, byte[]> event : events) {
        os.write(event.rhs);
        os.write('\n');
      }
      os.close();
      return baos.toByteArray();
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  @LifecycleStop
  public void close()
  {
    try {
      lifecycle.stop();
    }
    catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}

