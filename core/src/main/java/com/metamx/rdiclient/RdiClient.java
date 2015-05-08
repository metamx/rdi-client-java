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

import com.google.common.util.concurrent.ListenableFuture;
import com.metamx.rdiclient.metrics.RdiMetrics;

/**
 * Interface for creating a client to post data to Metamarkets real-time data ingestion API.
 * <p/>
 * Pass events to the client by calling "send".  Messages will be batched and posted by the client.  Passing events using
 * "send" will post a new batch periodically when the events being buffered reach the max batch event count (flushCount)
 * or the max batch size in bytes (flushBytes).
 * <p/>
 * Calling "flush" will also force all pending events to be batched and sent.
 * <p/>
 * The client handles retries for you using an exponential backoff strategy for exceptions on posting data, such as
 * network issues or invalid Http response codes.  The number of retries may be configured in the RdiClientConfig.
 * <p/>
 * The client is thread-safe. For best performance you should generally share a single instance across all threads.
 *
 * @param <T> generic event type
 */
public interface RdiClient<T>
{
  /**
   * Initialize the RdiClient. Must be called before calling send() or flush().
   */
  void start();

  /**
   * Return a metrics object that you can use to check on the progress of the RdiClient.
   */
  RdiMetrics getMetrics();

  /**
   * Send messages to the server. "Send" will queue events up and then POST a new batch periodically when the events
   * being buffered reach the max batch event count (flushCount) or the max batch size in bytes (flushBytes). "send" is
   * generally asynchronous, but if the maximum batch size is reached and no connections to the server are available,
   * the method will block until a connection is available.
   * <p />
   * This method will immediately throw an exception if your messages fail to serialize, or if your message's
   * serialized size is too large. The returned future may resolve into an exception if there are connection/network
   * issues, or the client is unable to POST to the server (e.g. if your rate limit is exceeded or your credentials
   * are incorrect).
   * <p />
   * You can attach callbacks to the futures returned by "send", but keep in mind that unless you provide your own
   * Executor, the callbacks will occur in I/O threads and you must make sure they execute quickly and do not block.
   *
   * @param feed name of the feed to which you want to send this message.
   * @param message event message of generic type to be posted via the client.
   * @return future representing the post result for this message.
   *
   * @throws java.lang.IllegalArgumentException if your message fails to serialize, or if its size is too large.
   * @throws java.lang.InterruptedException if sending needs to block, and is interrupted while doing so.
   */
  ListenableFuture<RdiResponse> send(String feed, T message) throws InterruptedException;

  /**
   * Calling "flush" will force all pending events to be batched and sent to the server immediately. This method may
   * return before all events have actually been received by the server. You must inspect the futures returned by
   * "send" to verify success.
   * <p />
   * If no connections to the server are available, this method will block until a connection is available.
   * <p />
   * NOTE: You can use "flush" periodically if you want to implement "checkpoint"-oriented logic.
   *
   * @throws java.lang.InterruptedException if flushing is interrupted.
   */
  void flush() throws InterruptedException;

  /**
   * Signal that event transmission is complete. Close the client.
   * </p>
   * Calling close will not call flush, so that should be done manually before calling close. If you want to make sure
   * that all your messages have been sent, you must wait for them to be acknowledged (i.e., the Futures resolve) before
   * closing the client.
   */
  void close();
}
