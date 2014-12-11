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

import com.google.common.base.Preconditions;
import com.metamx.common.Props;
import com.metamx.common.lifecycle.Lifecycle;
import com.metamx.common.logger.Logger;
import com.metamx.rdiclient.RdiClient;
import com.metamx.rdiclient.RdiClientConfig;
import com.metamx.rdiclient.RdiClients;
import kafka.consumer.ConsumerConfig;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.javaapi.consumer.ZookeeperConsumerConnector;

import java.io.IOException;
import java.util.Properties;

/**
 *
 * An implementation of the RdiClient for connecting a Kafka cluster to RDI.
 *
 */
public class Main
{
  private static final Logger log = new Logger(Main.class);

  public static void main(String[] args) throws Exception
  {
    final Lifecycle lifecycle = new Lifecycle();
    final Properties rdiProperties;
    final Properties kafkaProperties;
    final ConsumerConnector consumerConnector;
    final KafkaRdiConsumer kafkaRdiConsumer;
    final RdiClient<byte[]> rdiClient;

    if (args.length == 0) {
      rdiProperties = Props.fromFilename("conf/rdi.properties");
      kafkaProperties = Props.fromFilename("conf/kafka.properties");
    } else if (args.length == 2) {
      rdiProperties = Props.fromFilename(args[0]);
      kafkaProperties = Props.fromFilename(args[1]);
    } else {
      System.err.println(String.format("Usage: %s rdi.properties kafka.properties", Main.class.getCanonicalName()));
      System.exit(1);
      throw new AssertionError("#notreached");
    }

    rdiClient = getRdiClient(lifecycle, rdiProperties);
    consumerConnector = getConsumerConnector(lifecycle, kafkaProperties);
    kafkaRdiConsumer = lifecycle.addManagedInstance(
        new KafkaRdiConsumer(
            consumerConnector,
            rdiClient,
            getKafkaTopic(rdiProperties),
            getKafkaThreads(rdiProperties)
        )
    );

    try {
      lifecycle.start();
    } catch (Throwable t) {
      log.error(t, "Error while starting up. Exiting.");
      System.exit(1);
    }

    Runtime.getRuntime().addShutdownHook(
        new Thread(
            new Runnable()
            {
              @Override
              public void run()
              {
                log.info("Running shutdown hook.");
                lifecycle.stop();
              }
            }
        )
    );

    kafkaRdiConsumer.join();
    System.exit(0);
  }

  private static RdiClient<byte[]> getRdiClient(final Lifecycle lifecycle, final Properties props) throws IOException
  {
    return lifecycle.addManagedInstance(RdiClients.usingPassthroughSerializer(RdiClientConfig.fromProperties(props)));
  }

  private static ConsumerConnector getConsumerConnector(final Lifecycle lifecycle, final Properties props)
  {
    final ConsumerConfig config = new ConsumerConfig(props);
    final ConsumerConnector consumerConnector = new ZookeeperConsumerConnector(config);
    lifecycle.addHandler(
        new Lifecycle.Handler()
        {
          @Override
          public void start() throws Exception
          {
            // Nothing
          }

          @Override
          public void stop()
          {
            consumerConnector.shutdown();
          }
        }
    );
    return consumerConnector;
  }

  private static String getKafkaTopic(final Properties props)
  {
    return Preconditions.checkNotNull(props.getProperty("rdi.kafka.topic"), "rdi.kafka.topic");
  }

  private static int getKafkaThreads(final Properties props)
  {
    final String value = props.getProperty("rdi.kafka.threads");
    if (value != null) {
      return Integer.parseInt(value);
    } else {
      return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }
  }
}
