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

import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * Metrics for an {@link com.metamx.rdiclient.RdiClient}.
 */
public class RdiMetrics
{
  private final ConcurrentMap<String, FeedMetrics> metricss = Maps.newConcurrentMap();

  /**
   * Increment the number of sent messages for a feed (for internal use).
   */
  public void incSent(String feed, long newBytes)
  {
    getFeedMetrics(feed).incSent(newBytes);
  }

  /**
   * Increment the number of retransmitted messages for a feed (for internal use).
   */
  public void incRetransmitted(String feed)
  {
    getFeedMetrics(feed).incRetransmitted();
  }

  /**
   * Increment the number of failed messages for a feed (for internal use).
   */
  public void incFailed(String feed)
  {
    getFeedMetrics(feed).incFailed();
  }

  /**
   * Return metrics for all feeds. This method can be used to retrieve metrics for reporting. The list returned
   * by this method is not a snapshot; it will continue to update over time.
   */
  public Collection<FeedMetrics> all()
  {
    return metricss.values();
  }

  private FeedMetrics getFeedMetrics(String feed)
  {
    FeedMetrics metrics;
    metrics = metricss.get(feed);
    if (metrics == null) {
      metricss.putIfAbsent(feed, new FeedMetrics(feed));
      metrics = metricss.get(feed);
    }
    return metrics;
  }
}
