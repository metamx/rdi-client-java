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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;


/**
 * Uses a Jackson ObjectMapper to serialize events to byte arrays.
 *
 * @param <T> generic type returned by serializer.
 */
public class JacksonSerializer<T> implements Serializer<T>
{
  private final ObjectMapper objectMapper;

  public JacksonSerializer(ObjectMapper objectMapper)
  {
    this.objectMapper = objectMapper;
  }

  @Override
  public byte[] serialize(T event)
  {
    try {
      return objectMapper.writeValueAsBytes(event);
    }
    catch (JsonProcessingException e) {
      throw Throwables.propagate(e);
    }
  }
}
