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
import com.metamx.common.lifecycle.Lifecycle;
import com.metamx.common.logger.Logger;
import com.metamx.rdiclient.RdiClient;
import com.metamx.rdiclient.RdiClientConfig;
import com.metamx.rdiclient.RdiClients;
import com.metamx.rdiclient.example.Examples;
import com.vungle.rdiclient.kafka.SampleKafkaTranslator;
import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.Whitelist;
import kafka.javaapi.consumer.ConsumerConnector;

import java.io.IOException;
import java.util.Properties;

/**
 * An implementation of the RdiClient for connecting a Kafka cluster to RDI.
 */
public class Main
{
  private static final Logger log = new Logger(Main.class);
  private static final String KAFKA_PROPERTY_PREFIX = "kafka.";

  public static void main(String[] args) throws Exception
  {
    final Lifecycle lifecycle = new Lifecycle();
    final Properties rdiProperties = Examples.readProperties();
    final Properties kafkaProperties = new Properties();
    final String feed = Examples.getFeed(rdiProperties);
    final float sampleRate = Examples.getSampleRate(rdiProperties);
    final ConsumerConnector consumerConnector;
    final KafkaRdiConsumer kafkaRdiConsumer;
    final RdiClient<byte[]> rdiClient;

    if (args.length > 0) {
      System.err.println(String.format("Usage: %s", Main.class.getCanonicalName()));
      System.exit(1);
      throw new AssertionError("#notreached");
    }

    for (final String propertyName : rdiProperties.stringPropertyNames()) {
      if (propertyName.startsWith(KAFKA_PROPERTY_PREFIX)) {
        kafkaProperties.setProperty(
            propertyName.substring(KAFKA_PROPERTY_PREFIX.length()),
            rdiProperties.getProperty(propertyName)
        );
      }
    }

    rdiClient = getRdiClient(lifecycle, rdiProperties);
    consumerConnector = getConsumerConnector(kafkaProperties);
    kafkaRdiConsumer = lifecycle.addManagedInstance(
        new KafkaRdiConsumer(
            consumerConnector,
            new Whitelist(getKafkaTopic(rdiProperties)),
            new SampleKafkaTranslator(sampleRate),
            rdiClient,
            feed,
            getKafkaThreads(rdiProperties)
        )
    );

    try {
      lifecycle.start();
    }
    catch (Throwable t) {
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
  }

  private static RdiClient<byte[]> getRdiClient(final Lifecycle lifecycle, final Properties props) throws IOException
  {
    return lifecycle.addManagedInstance(RdiClients.usingPassthroughSerializer(RdiClientConfig.fromProperties(props)));
  }

  private static ConsumerConnector getConsumerConnector(final Properties props)
  {
    final Properties newProps = new Properties(props);
    newProps.setProperty("auto.commit.enable", "false");

    final ConsumerConfig config = new ConsumerConfig(props);
    Preconditions.checkState(!config.autoCommitEnable(), "autocommit must be off");

    return Consumer.createJavaConsumerConnector(config);
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
