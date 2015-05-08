package com.metamx.rdiclient.kafka;

import kafka.message.MessageAndMetadata;

import java.util.List;

public interface KafkaTranslator<T>
{
  List<T> translate(MessageAndMetadata<byte[], byte[]> message);
}
