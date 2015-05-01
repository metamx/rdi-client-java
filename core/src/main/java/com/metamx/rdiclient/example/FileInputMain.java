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

package com.metamx.rdiclient.example;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.metamx.common.logger.Logger;
import com.metamx.rdiclient.RdiClient;
import com.metamx.rdiclient.RdiClientConfig;
import com.metamx.rdiclient.RdiClients;
import com.metamx.rdiclient.RdiResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

public class FileInputMain
{
  private static final Logger log = new Logger(FileInputMain.class);

  public static void main(String[] args) throws Exception
  {
    if (args.length == 0) {
      System.err.println(String.format("Usage: %s file1 [file2 ...]", FileInputMain.class.getCanonicalName()));
      System.exit(1);
    }

    final Properties props = Examples.readProperties();
    final String feed = Examples.getFeed(props);

    // Start up RdiClient.
    final RdiClientConfig rdiConfig = RdiClientConfig.fromProperties(props);
    final RdiClient<byte[]> rdiClient = RdiClients.usingPassthroughSerializer(rdiConfig);
    rdiClient.start();

    // Track how many messages are sent and how many successes and failures are received.
    final AtomicLong sends = new AtomicLong(0);
    final AtomicLong acks = new AtomicLong(0);
    final AtomicLong fails = new AtomicLong(0);

    // Send data from files (in args) through RdiClient.
    for (final String arg : args) {
      final File file = new File(arg);
      try (final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
        String line;
        while ((line = in.readLine()) != null) {
          // Asynchronously send a message.
          final ListenableFuture<RdiResponse> send = rdiClient.send(feed, line.getBytes(Charsets.UTF_8));
          sends.incrementAndGet();

          // When the message is acknowledged (or fails), increment the appropriate counter.
          Futures.addCallback(
              send,
              new FutureCallback<RdiResponse>()
              {
                @Override
                public void onSuccess(RdiResponse result)
                {
                  acks.incrementAndGet();
                }

                @Override
                public void onFailure(Throwable t)
                {
                  fails.incrementAndGet();
                }
              }
          );
        }
      }
    }

    // Done sending messages.
    rdiClient.flush();

    // Wait for all messages to be sent.
    while (sends.get() != acks.get() + fails.get()) {
      Thread.sleep(100);
    }

    // Close the client.
    rdiClient.close();

    // Log and exit.
    log.info("Sent %,d messages with %,d acks and %,d fails.", sends.get(), acks.get(), fails.get());
  }
}
