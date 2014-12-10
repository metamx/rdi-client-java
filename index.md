---
title: Home
layout: default
---

# Overview

The RdiClient for Java is a client library for posting event streams to Metamarkets' real-time data ingestion (RDI) API, which receives and processes event data in real-time.  The client comes with functionality to handle connecting to the HTTPS endpoint, authentication, serialization & batching events, connection pooling, HTTP transport, and error handling (w/ exponential-backoff retries).

# Usage

## Getting Started

To create the client, you'll need to pass an RdiClientConfig as shown in the example below.  Once your RdiClient is configured, use "send" to pass events to the client.  Passing events using "send" will post a new batch periodically when the events being buffered reach the max batch event count (flushCount) or the max batch size in bytes (flushBytes).  The method will return a future for every message sent before the POST actually finishes.  You should inspect the futures to determine whether or not the POST succeeded. The returned future may resolve into an exception if there are connection/network issues, or the client is unable to POST to the server (e.g. if your rate limit is exceeded or your credentials are incorrect).  In the event of an exception when POSTing a batch, all futures for the events in that batch will resolve into an exception.  As demonstrated in the example below, you can attach callbacks to the futures returned by "send".  Keep in mind that unless the you provide your own Executor, the callbacks will occur in I/O threads and you must make sure they execute quickly and do not block (e.g. If you are using RabbitMQ and need to ack after every message is sent).  Calling "flush" will also force all pending events to be batched and POSTed.  

For more information on the basic RdiClient API, please refer to the javadocs here. (TODO: ADD LINK)

The The following is an example of creating the client and using the basic API for passing event records:

{% highlight java %}
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.metamx.rdiclient.RdiClient;
import com.metamx.rdiclient.RdiClientConfig;
import com.metamx.rdiclient.RdiClients;
import com.metamx.rdiclient.RdiResponse;

import java.util.ArrayList;
import java.util.List;

public class Main
{
  public static void main(String[] args) throws Exception{
    final String endpoint = "<PROVIDED_BY_MMX>";
    final String username = "<PROVIDED_BY_MMX>";
    final String password = "<PROVIDED_BY_MMX>";

    final RdiClientConfig config = RdiClientConfig.builder()
                                                  .username(username)
                                                  .password(password)
                                                  .rdiUrl(endpoint)
                                                  .build();

    final RdiClient<String> client = RdiClients.usingJacksonSerializer(config);

    client.start();

    List<String> messages = new ArrayList<>();
    messages.add("foo");
    messages.add("bar");
    messages.add("baz");

    for (String message : messages) {
      // "send" method will return a future before POST actually finishes
      final ListenableFuture<RdiResponse> send = client.send(message);

      // Add a callback to inspect futures to determine whether or not the post succeeded.
      // The returned future may resolve into an exception if there are connection/network issues,
      // or if the client is unable to POST to the server (e.g. if your rate limit is exceeded or your
      // credentials are incorrect).
      Futures.addCallback(
          send,
          new FutureCallback<RdiResponse>()
          {
            @Override
            public void onSuccess(RdiResponse result) {}

            @Override
            public void onFailure(Throwable t)
            {
              // Add your own logic for handling exceptions here.
            }
          }
      );
    }

    client.flush();
    client.close();
  }

}
{% endhighlight %}

## Data Format & Serialization

Metamarkets API currently accepts JSON event data with UTF-8 encoding.  We have a jackson-based serialization library available ([link here](https://github.com/metamx/rad-tech-datatypes)) for generating the OpenRTB-based event records which may be used in conjunction with the RDI Client. 

For more information on the required data types, please contact your Metamarkets representative.  

The client requires a "serializer" class to be passed for converting messages to byte arrays prior to posting.  There are two built in serializers available:

- JacksonSerializer: Uses a Jackson ObjectMapper to serialize events to byte arrays.  This library comes with default methods for creating a client for posting [Metamarkets' standard data types](https://github.com/metamx/rad-tech-datatypes).  

{% highlight java %}
// For MmxAuctionSummary events:
final RdiClient<MmxAuctionSummary> client = RdiClients.usingJacksonSerializer(config);
{% endhighlight %}

- PassthroughSerializer: Use for passing pre-serialized events as a byte array.  This option may be used in cases where serialization is handled upstream.

{% highlight java %}
final RdiClient<byte[]> client = RdiClients.makeDefault(config, new PassthroughSerializer());
{% endhighlight %}

## Compression

The Metamarkets API currently only supports gzip compression.  You may enable compression by setting the "contentEncoding" configuration parameter to ".contentEncoding(RdiClientConfig.ContentEncoding.GZIP)".  Otherwise it will default to "NONE".  

Because events will be compressed by the library, you should not compress them prior to calling the client.

## Scaling

The client is thread-safe.  For best performance, you should generally share a single instance across all threads.  You can configure the number of connections using the "maxConnectionCount" parameter in your RdiClientConfig.

## Posting Data & Handling Responses

Data should be delivered in a smooth and continuous pattern.  When a new batch of data is posted, the Metamarkets API should respond with an HTTP 2xx response code within 50-100ms unless an error is encountered.  Typical causes for errors when posting event data to the API endpoint include connectivity/network issues, or bad HTTP responses from the server due to issues such as exceeding your rate limit (HTTP 420), incorrect credentials (HTTP 401), or server issues (HTTP 500s).  Attempting to deliver data in large batches (e.g. once every few minutes or hour) is likely to cause the volume rate to exceed your quota and result in a failed upload.

The client uses an exponential backoff retry strategy for all types of POST failures.  The maximum number of retries is set in your RdiClientConfig, where the default is 20 (which could lead to retries lasting up to 15 minutes).  

In the event that the client is unable to POST your data despite exhausting all retries, the futures for all events in the failed batch will resolve into exceptions, which you will need to parse and handle.  If the exception is caused by an unsuccessful attempt to POST data to the Metamarkets endpoint, the exception may be a wrapped RdiHttpResponseException, from which the response code can be obtained via the "getStatusCode" method.  You should parse the exceptions to build out your own custom error handling.

## Delivering Data "On Time"

Metamarkets RDI expects data be uploaded in real-time.  Although "late" data will still be accepted by the API, it may not be processed in real-time.  "Current data" is defined as data with events that have a timestamp no more than 10 minutes behind the time the data is posted to RDI.Data with a timestamp more than 10 minutes older than current time is saved and surfaced in your dashboard usually within 48 hours. 

If your data uploads fall behind, do not attempt to backfill all of the data by uploading it at once. That will likely exceed your volume quota and result in a failed upload (HTTP 420 error code). Instead, increase the overall throughput rate at which data is posted to a level between the normal rate and the quota (but no more than twice the normal rate), until data timestamps are near current time. If an HTTP 420 error code is returned, reduce the rate.

## Where to Implement the RdiClient

We strongly recommend that customers run the client safely out of band of mission-critical systems (e.g. servers conducting auctions or bidding).  If the maximum batch size is reached and no connections to the server are available, the method will block until a connection is available.  It is best practice to use a message queue (e.g. Kafka or RabbitMQ) for buffering data prior to delivery to the Metamarkets API.  Log retention should be set to a window long enough to allow for retention of data in the event of issues posting data to RDI.  We typically recommend that customers keep the data for 5-7 days.

# Kafka Implementation

## Overview
Although the RDI client does not require a particular upstream message queue, we have provided an implementation for Kafka users. 

The Kafka implementation assumes that you have already serialzed your event records to one of the formats required by Metamarkets RDI, so the connector will attempt to pass through the pre-serialzed byte arrays to the API.  Compression is disabled by default, but you can optionally enable it in the RdiClientConfig to save some network bandwidth at the expense of CPU.  Currently only GZIP is supported.

Commits are done manually to support a "guaranteed delivery" approach.

## Download

*** TODO: Recommend that people download the jar from the java docs -> one for core and one for the kafka example.

## Configuration & Usage

You'll need to create two configuration files (kafka.properties & rdi.properties), which will be passed via the command line when you run the program.

Your rdi.properties file should include the following:
{% highlight java %}
rdi.url = https://rt-test.mmx.io/events/your-endpoint
rdi.username = your-username
rdi.password = your-password
rdi.kafka.topic = your-topic-name
rdi.kafka.threads = consumer-threads # Optional - will default to using one less than the number of available processors
rdi.connection.count = 1 # Optional - the default is shown here
{% endhighlight %}

The Rdi Url, username, password and kafka topic name are required.  You may also customize additional RdiClient parameters via the properties file, but the Kafka implementation will otherwise use the defaults set by the RdiClientConfig.

Your kafka.properties config file should contain the following parameters:
{% highlight java %}
group.id = your-group-id
zookeeper.connect = your-zookeeper-endpoint
zookeeper.session.timeout.ms = 30000
{% endhighlight %}

Once you have downloaded the .jar file, 


## JARs

RdiClient artifacts are hosted on the Metamarkets maven repository: https://metamx.artifactoryonline.com/metamx/pub-libs-releases-local/.
If you set up your project to know about this repository, you can depend on one of the hosted versions.

The current stable version is:

{% highlight xml %}
<dependency>
  <groupId>com.metamx</groupId>
  <artifactId>update!!</artifactId>
  <version>update!!</version>
</dependency>
{% endhighlight %}
