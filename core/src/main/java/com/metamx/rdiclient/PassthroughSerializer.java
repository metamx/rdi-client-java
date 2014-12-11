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

/**
 * Serializer that accepts a bytearray and returns it unaltered.  Should only be used when your data
 * is already in the format required by MMX RDI.
 */
public class PassthroughSerializer implements Serializer<byte[]>
{
  @Override
  public byte[] serialize(byte[] event)
  {
    return event;
  }
}
