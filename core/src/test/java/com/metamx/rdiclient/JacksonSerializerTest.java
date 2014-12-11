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

package com.metamx.rdiclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.metamx.datatypes.mmx.MmxAuctionSummary;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;

/**
 */
public class JacksonSerializerTest
{

  private static final ObjectMapper objectMapper = new ObjectMapper();
  final MmxAuctionSummary sampleEventValid = MmxAuctionSummary
      .builder()
      .timestamp(new DateTime("2014-01-01T00:00:00.000Z")).auctionType(2).build();

  @Test
  public void testSimpleSerialization() throws Exception
  {
    final String outputString = "{\"timestamp\":\"2014-01-01T00:00:00.000Z\",\"at\":2}";
    objectMapper.registerModule(new JodaModule());
    final Serializer<MmxAuctionSummary> serializer = new JacksonSerializer<>(objectMapper);

    final byte[] eventBytes = serializer.serialize(sampleEventValid);
    final String eventString = objectMapper.writeValueAsString(
        objectMapper.readValue(
            eventBytes,
            MmxAuctionSummary.class
        )
    );
    Assert.assertEquals(eventString, outputString);

  }


}
