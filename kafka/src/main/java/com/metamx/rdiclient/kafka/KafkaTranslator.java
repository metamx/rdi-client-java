package com.metamx.rdiclient.kafka;

import kafka.message.MessageAndMetadata;

import java.util.List;

public interface KafkaTranslator
{
  List<byte[]> translate(MessageAndMetadata<byte[], byte[]> message);
}
