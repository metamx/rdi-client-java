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

import com.metamx.common.IAE;
import com.metamx.common.Props;

import java.io.IOException;
import java.util.Properties;

public class Examples
{
  private static final String PROPS_LOCATION = "conf/rdi.properties";
  private static final String FEED_PROPERTY = "rdi.tool.feed";

  public static Properties readProperties() throws IOException
  {
    return Props.fromFilename(PROPS_LOCATION);
  }

  public static String getFeed(final Properties props)
  {
    final String feed = props.getProperty(FEED_PROPERTY);
    if (feed == null) {
      throw new IAE("Property '%s' (destination feed) is required", FEED_PROPERTY);
    }
    return feed;
  }
}
