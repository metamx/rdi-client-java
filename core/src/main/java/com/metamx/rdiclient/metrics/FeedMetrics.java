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

package com.metamx.rdiclient.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for messages sent to a particular feed.
 */
public class FeedMetrics
{
  private final String feed;
  private final AtomicLong sentMessages = new AtomicLong();
  private final AtomicLong sentBytes = new AtomicLong();
  private final AtomicLong retransmittedMessages = new AtomicLong();
  private final AtomicLong failedMessages = new AtomicLong();

  public FeedMetrics(String feed)
  {
    this.feed = feed;
  }

  /**
   * Increment the number of sent messages for this feed (for internal use).
   */
  public void incSent(long newBytes)
  {
    sentMessages.incrementAndGet();
    sentBytes.addAndGet(newBytes);
  }

  /**
   * Increment the number of retransmitted messages for this feed (for internal use).
   */
  public void incRetransmitted()
  {
    sentMessages.incrementAndGet();
  }

  /**
   * Increment the number of failed messages for this feed (for internal use).
   */
  public void incFailed()
  {
    sentMessages.incrementAndGet();
  }

  /**
   * Get the number of messages successfully sent to the server.
   */
  public long getSentMessages()
  {
    return sentMessages.get();
  }

  /**
   * Get the number of bytes successfully sent to the server.
   */
  public long getSentBytes()
  {
    return sentBytes.get();
  }

  /**
   * Get the number of message retransmissions caused by errors. This can potentially be larger than the total number
   * of messages, since each message may be retransmitted multiple times. Retransmitted messages will eventually end
   * up either "sent" or "failed".
   */
  public long getRetransmittedMessages()
  {
    return retransmittedMessages.get();
  }

  /**
   * Get the number of messages that could not be sent to the server.
   */
  public long getFailedMessages()
  {
    return failedMessages.get();
  }

  /**
   * Get the name of the feed that these metrics are for.
   */
  public String getFeed()
  {
    return feed;
  }

  @Override
  public String toString()
  {
    return "FeedMetrics{" +
           "sentMessages=" + sentMessages +
           ", sentBytes=" + sentBytes +
           ", retransmittedMessages=" + retransmittedMessages +
           ", failedMessages=" + failedMessages +
           '}';
  }
}
