package com.metamx.rdiclient.kafka;

import com.google.common.collect.ImmutableList;
import kafka.message.MessageAndMetadata;

import java.util.List;

public class PassthroughKafkaTranslator implements KafkaTranslator
{
  private static KafkaTranslator INSTANCE = new PassthroughKafkaTranslator();

  @Override
  public List<byte[]> translate(MessageAndMetadata<byte[], byte[]> message)
  {
    return ImmutableList.of(message.message());
  }

  public static KafkaTranslator instance() {
    return INSTANCE;
  }
}
