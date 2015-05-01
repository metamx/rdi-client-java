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
