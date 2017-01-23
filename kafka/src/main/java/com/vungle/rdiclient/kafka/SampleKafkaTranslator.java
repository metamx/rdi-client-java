package com.vungle.rdiclient.kafka;

import com.google.common.collect.ImmutableList;
import com.metamx.rdiclient.kafka.KafkaTranslator;
import kafka.message.MessageAndMetadata;

import java.util.List;
import java.util.Random;

/**
 * Created by Wei on 11/26/15.
 */
public class SampleKafkaTranslator implements KafkaTranslator<byte[]> {
    private float sampleRate;
    private Random random;

    public SampleKafkaTranslator(float sampleRate) {
        if (sampleRate > 1 || sampleRate < 0) {
            throw new IllegalArgumentException("Sample rate is " + sampleRate + ", should between 0-1.");
        }
        this.sampleRate = sampleRate;
        this.random = new Random();
    }

    private static KafkaTranslator INSTANCE;

    @Override
    public List<byte[]> translate(MessageAndMetadata<byte[], byte[]> message) {

        if (sampleRate > random.nextFloat()) {
            return ImmutableList.of(message.message());
        }
        return ImmutableList.of();
    }
}
