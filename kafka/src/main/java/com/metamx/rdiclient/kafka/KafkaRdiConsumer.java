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

package com.metamx.rdiclient.kafka;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.metamx.common.lifecycle.LifecycleStart;
import com.metamx.common.lifecycle.LifecycleStop;
import com.metamx.common.logger.Logger;
import com.metamx.rdiclient.RdiClient;
import com.metamx.rdiclient.RdiResponse;
import kafka.consumer.KafkaStream;
import kafka.consumer.TopicFilter;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Kafka Consumer that produces/writes to RDI.
 */
public class KafkaRdiConsumer<T>
{
  private static final Logger log = new Logger(Main.class);
  private static final long COMMIT_MILLIS = 60000;

  private final ExecutorService consumerExec;
  private final Thread commitThread;
  private final ConsumerConnector consumerConnector;
  private final TopicFilter topicFilter;
  private final KafkaTranslator<T> kafkaTranslator;
  private final RdiClient<T> rdiClient;
  private final String feed;
  private final int numThreads;

  private final ReentrantReadWriteLock commitLock = new ReentrantReadWriteLock();
  private final AtomicBoolean shutdown = new AtomicBoolean();

  private final Object flushAwaitLock = new Object();
  private int pending = 0; // Use under flushAwaitLock

  public KafkaRdiConsumer(
      final ConsumerConnector consumerConnector,
      final TopicFilter topicFilter,
      final KafkaTranslator<T> kafkaTranslator,
      final RdiClient<T> rdiClient,
      final String feed,
      final int numThreads
  )
  {
    this.consumerConnector = consumerConnector;
    this.kafkaTranslator = kafkaTranslator;
    this.rdiClient = rdiClient;
    this.topicFilter = topicFilter;
    this.feed = feed;
    this.numThreads = numThreads;

    this.consumerExec = Executors.newFixedThreadPool(
        numThreads,
        new ThreadFactoryBuilder().setNameFormat("KafkaRdiConsumer-%d")
                                  .setDaemon(true)
                                  .build()
    );
    this.commitThread = new Thread(createCommitRunnable());
    this.commitThread.setName("KafkaRdiConsumer-CommitThread");
    this.commitThread.setDaemon(true);
  }

  @LifecycleStart
  public void start()
  {
    startCommitter();
    startConsumers();
  }

  public void join() throws InterruptedException
  {
    commitThread.join();
  }

  private Runnable createCommitRunnable()
  {
    return new Runnable()
    {
      @Override
      public void run()
      {
        long lastFlushTime = System.currentTimeMillis();
        try {
          while (!Thread.currentThread().isInterrupted()) {
            final long now = System.currentTimeMillis();
            Thread.sleep(Math.max(COMMIT_MILLIS - (now - lastFlushTime), 0));
            commitLock.writeLock().lockInterruptibly();
            try {
              final long flushStart = System.currentTimeMillis();
              rdiClient.flush();
              synchronized (flushAwaitLock) {
                while (pending != 0) {
                  flushAwaitLock.wait();
                }
              }

              final long commitStart = System.currentTimeMillis();
              consumerConnector.commitOffsets();

              final long finished = System.currentTimeMillis();
              log.debug(
                  "Flushed pending messages in %,dms and committed offsets in %,dms.",
                  finished - flushStart,
                  finished - commitStart
              );
              lastFlushTime = System.currentTimeMillis();
            }
            finally {
              commitLock.writeLock().unlock();
            }
          }
        }
        catch (InterruptedException e) {
          log.debug(e, "Commit thread interrupted.");
        }
        catch (Throwable e) {
          log.error(e, "Commit thread failed!");
          throw Throwables.propagate(e);
        }
        finally {
          stop();
        }
      }
    };
  }

  private void startCommitter()
  {
    commitThread.start();
  }

  private void startConsumers()
  {
    final List<KafkaStream<byte[], byte[]>> kafkaStreams = consumerConnector.createMessageStreamsByFilter(
        topicFilter,
        numThreads
    );
    for (final KafkaStream<byte[], byte[]> kafkaStream : kafkaStreams) {
      consumerExec.submit(
          new Runnable()
          {
            @Override
            public void run()
            {
              try {
                // Kafka consumer blocks on hasNext/next calls when there is no data available. However, we need upload thread to
                // run even in this case. That's why hasNext call is not synchronized with collector upload/offsets committing.
                final Iterator<MessageAndMetadata<byte[], byte[]>> iteratorIn = kafkaStream.iterator();
                while (iteratorIn.hasNext()) {
                  if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                  }

                  // Kafka consumer treats message as consumed and updates in-memory last offset when the message is returned by
                  // next() so we need to synchronize it with uploading and offsets committing (which will save in-memory offset to
                  // Zookeeper)
                  commitLock.readLock().lockInterruptibly();
                  try {
                    final MessageAndMetadata<byte[], byte[]> incoming = iteratorIn.next();
                    final List<T> outgoing = kafkaTranslator.translate(incoming);

                    incrementPending(outgoing.size());
                    for (T message : outgoing) {
                      Futures.addCallback(
                          rdiClient.send(feed, message),
                          new FutureCallback<RdiResponse>()
                          {
                            @Override
                            public void onSuccess(RdiResponse result)
                            {
                              decrementPending();
                            }

                            @Override
                            public void onFailure(Throwable t)
                            {
                              log.error(t, "Failed to send an event for topic[%s]. Stopping.", incoming.topic());
                              stop();
                            }
                          }
                      );
                    }
                  }
                  finally {
                    commitLock.readLock().unlock();
                  }
                }
              }
              catch (InterruptedException e) {
                log.debug(e, "Consumer thread interrupted.");
              }
              catch (Throwable e) {
                log.error(e, "OMG! An exception!!");
                throw Throwables.propagate(e);
              }
              finally {
                stop();
              }
            }
          }
      );
    }
  }

  private void incrementPending(int n)
  {
    synchronized (flushAwaitLock) {
      pending += n;
    }
  }

  private void decrementPending()
  {
    synchronized (flushAwaitLock) {
      pending--;
      if (pending == 0) {
        flushAwaitLock.notifyAll();
      }
    }
  }

  @LifecycleStop
  public void stop()
  {
    if (shutdown.compareAndSet(false, true)) {
      log.info("Shutting down.");
      consumerConnector.shutdown();
      commitThread.interrupt();
      consumerExec.shutdownNow();
    }
  }
}
