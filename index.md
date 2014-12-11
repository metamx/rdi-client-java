---
title: Home
layout: default
---

# Metamarkets' RdiClient for Java

## Overview

The RdiClient for Java is a client library for posting event streams to Metamarkets' real-time data ingestion (RDI) API, which receives and processes event data in real-time.  The client comes with functionality to handle connecting to the HTTPS endpoint, authentication, serialization & batching events, connection pooling, HTTP transport, and error handling (w/ exponential-backoff retries).

The RdiClient for Java is currently in beta and the current version is 0.1. [Click here](https://metamx.github.io/rdi-client-java/static/apidocs/0.1/) for the full javadocs.  You may also [view the source on Github](https://github.com/metamx/rdi-client-java/).

## Getting Started with the Client Library

The RdiClient artifacts are available on Metamarkets artifactory (see below for distribution information).  However, the library also comes with a built in testing tool that you can use to try out sending some data to the Metamarkets API right away from files that you pass to the tool.  Get started by downloading the tar file for the most recent distribution [here](https://metamx.artifactoryonline.com/metamx/pub-libs-releases-local/com/metamx/rdi-client-distribution/0.1/rdi-client-distribution-0.1-dist.tar.gz).

Once you've downloaded the tar file, unpack it in the directory from which you plan to run the program.  Once that's done, you should see the following directories:
{% highlight bash %}
$ ls
bin
conf
lib
{% endhighlight %}

Next, you'll need to update the the RDI configuration file that can be found at "conf/rdi.properties".  This file contains the configuration parameters required for setting up and tuning the RdiClient.  Contact your Metamarkets representative to obtain your RDI URL, username, and password. It should look like this:
{% highlight java %}
rdi.url = https://rt-test.mmx.io/events/your-endpoint
rdi.username = your-username
rdi.password = your-password
rdi.connection.count = 1
{% endhighlight %}

Once that's done, you're ready to start posting some data.  The testing utility allows you to post data from files that you specify at runtime.  The files should be uncompressed, plain text files containing newline separated json event records.  To kick off the process, run the following command from the directory where you unpacked the tar file, passing the names of your data files as space-delimited parameters:
{% highlight bash %}
$ bin/rdi-send-files.sh sample_data_1.txt sample_data_2.txt
{% endhighlight %}

When you're ready to start developing against the API, the Main method used in the testing utility will also serve as a good example to help you understand the basic API functionality. You can find the source code [here on Github](https://github.com/metamx/rdi-client-java/blob/master/core/src/main/java/com/metamx/rdiclient/example/FileInputMain.java).

## The Basic API

To create the client, you'll need to pass an RdiClientConfig as shown in testing utility example.  Once your RdiClient is configured, use "send" to pass events to the client.  Passing events using "send" will post a new batch periodically when the events being buffered reach the max batch event count (flushCount) or the max batch size in bytes (flushBytes).  The method will return a future for every message sent before the POST actually finishes.  You should inspect the futures to determine whether or not the POST succeeded. The returned future may resolve into an exception if there are connection/network issues, or if the client is unable to POST to the server (e.g. if your rate limit is exceeded or your credentials are incorrect).  In the event of an exception when POSTing a batch, all futures for the events in that batch will resolve into an exception.  As demonstrated in the testing utility example, you can attach callbacks to the futures returned by "send".  Keep in mind that unless the you provide your own Executor, the callbacks will occur in I/O threads and you must make sure they execute quickly and do not block.  Calling "flush" will also force all pending events to be batched and POSTed.

For more information on the core RdiClient API, please refer to the javadocs [here](https://metamx.github.io/rdi-client-java/static/apidocs/0.1/).

## Data Format & Serialization

Metamarkets API currently accepts JSON event data with UTF-8 encoding.  Our jackson-based serialization library, [RadTech Datatypes](https://github.com/metamx/rad-tech-datatypes), is available for generating the OpenRTB-based event records which may be used in conjunction with the RDI Client. 

For more information on the required data types, please contact your Metamarkets representative.  

The client requires a "serializer" class to be passed for converting messages to byte arrays prior to posting.  There are two built in serializers available:

- JacksonSerializer: Uses a Jackson ObjectMapper to serialize events to byte arrays.  The library comes with a default method for creating a client for posting [Metamarkets' standard data types](https://github.com/metamx/rad-tech-datatypes).

{% highlight java %}
// For MmxAuctionSummary events:
final RdiClient<MmxAuctionSummary> client = RdiClients.usingJacksonSerializer(config);
{% endhighlight %}

- PassthroughSerializer: Use for passing pre-serialized events as a byte array.  This option may be used in cases where serialization is handled upstream.

{% highlight java %}
final RdiClient<byte[]> client = RdiClients.usingPassthroughSerializer(config);
{% endhighlight %}

## Compression

The Metamarkets API currently only supports gzip compression.  You may enable compression by setting the "contentEncoding" configuration parameter to "RdiClientConfig.ContentEncoding.GZIP" as shown below:
{% highlight java %}
config.contentEncoding(RdiClientConfig.ContentEncoding.GZIP)
{% endhighlight %}
Otherwise it will default to "NONE".

Because events will be compressed by the library, you should not compress them prior to calling the client.

## Scaling

The client uses a connection pool to send data. You can configure the number of connections using the “maxConnectionCount” parameter in your RdiClientConfig.

The client is thread-safe. For best performance, you should generally share a single instance across all threads.

## Posting Data & Handling Responses

Data should be delivered in a smooth and continuous pattern.  When a new batch of data is posted, the Metamarkets API should respond with an HTTP 2xx response code within 50-100ms unless an error is encountered.  Typical causes for errors when posting event data to the API endpoint include connectivity/network issues, or bad HTTP responses from the server due to issues such as exceeding your rate limit (HTTP 420), incorrect credentials (HTTP 401), or server issues (HTTP 500s).  Attempting to deliver data in large batches (e.g. once every few minutes or hour) is likely to cause the volume rate to exceed your quota and result in a failed upload.

The client uses an exponential backoff retry strategy for all types of POST failures.  The maximum number of retries is set in your RdiClientConfig, where the default is 20 (which could lead to retries lasting up to 15 minutes).  

In the event that the client is unable to POST your data despite exhausting all retries, the futures for all events in the failed batch will resolve into exceptions, which you will need to parse and handle.  If the exception is caused by an unsuccessful attempt to POST data to the Metamarkets endpoint, the exception may be a wrapped RdiHttpResponseException, from which the response code can be obtained via the "getStatusCode" method.  You should parse the exceptions to build out your own custom error handling.

## Delivering Data "On Time"

Metamarkets RDI expects data be uploaded in real-time.  Although "late" data will still be accepted by the API, it may not be processed in real-time.  "Current data" is defined as data with events that have a timestamp no more than 10 minutes behind the time the data is posted to RDI.Data with a timestamp more than 10 minutes older than current time is saved and surfaced in your dashboard usually within 48 hours. 

If your data uploads fall behind, do not attempt to backfill all of the data by uploading it at once. That will likely exceed your volume quota and result in a failed upload (HTTP 420 error code). Instead, increase the overall throughput rate at which data is posted to a level between the normal rate and the quota (but no more than twice the normal rate), until data timestamps are near current time. If an HTTP 420 error code is returned, reduce the rate.

## Where to Implement the RdiClient

We strongly recommend that customers run the client safely out of band of mission-critical systems (e.g. servers conducting auctions or bidding).  If the maximum batch size is reached and no connections to the server are available, the method will block until a connection is available.  It is best practice to use a message queue (e.g. Kafka or RabbitMQ) for buffering data prior to delivery to the Metamarkets API.  Log retention should be set to a window long enough to allow for retention of data in the event of issues posting data to RDI.  We typically recommend that customers keep the data for 5-7 days.

## JARs

RdiClient artifacts are hosted on the Metamarkets maven repository: https://metamx.artifactoryonline.com/metamx/pub-libs-releases-local/.
If you set up your project to know about this repository, you can depend on one of the hosted versions.

The current version is:

{% highlight xml %}
<dependency>
  <groupId>com.metamx</groupId>
  <artifactId>rdi-client-core</artifactId>
  <version>0.1</version>
</dependency>
{% endhighlight %}

# Kafka Implementation

## Overview
Although the RDI client does not require a particular upstream message queue, we have provided an implementation for Kafka users. 

The Kafka implementation assumes that you have already serialized your event records to one of the formats required by Metamarkets RDI, so the connector will attempt to pass through the pre-serialized byte arrays to the API.  As mentioned above, our jackson-based serialization library, [RadTech Datatypes](https://github.com/metamx/rad-tech-datatypes), is available for generating the OpenRTB-based event records which may be used in conjunction with the RDI Client.  Compression is disabled by default, but you can optionally enable it in the RdiClientConfig to save some network bandwidth at the expense of CPU.  Currently only GZIP is supported.

Commits are done manually to support a "guaranteed delivery" approach.

## Downloading, Configuration & Usage

To get started, you can download the latest distribution [here](https://metamx.artifactoryonline.com/metamx/pub-libs-releases-local/com/metamx/rdi-client-distribution/0.1/rdi-client-distribution-0.1-dist.tar.gz).

Once you've downloaded the tar file, unpack it in the directory from which you plan to run the program.  Once that's done, you should see the following directories:
{% highlight bash %}
$ ls
bin
conf
lib
rdi-client-java-distribution-0.0.1-dist.tar.gz
{% endhighlight %}

Next, you'll need to update the two configuration files in the "conf" directory.  

The first, conf/rdi.properties, contains configuration parameters required by RdiClient and should include the following:
{% highlight java %}
rdi.url = https://rt-test.mmx.io/events/your-endpoint
rdi.username = your-username
rdi.password = your-password
rdi.connection.count = 1

rdi.kafka.topic = your-topic-name
rdi.kafka.threads = 1
{% endhighlight %}

The Rdi Url, username, password and kafka topic name are required.  The connection count parameter is optional and will default to one. The kafka threads parameter is also optional and will default to using one less than the number of available processors.  You may also customize additional RdiClient parameters via the properties file, but the Kafka implementation will otherwise use the defaults set by the RdiClientConfig.

The second file is conf/kafka.properties, which should contain the following:
{% highlight java %}
group.id = your-group-id
zookeeper.connect = your-zookeeper-endpoint
zookeeper.session.timeout.ms = 30000
{% endhighlight %}

Once that's done, you're ready to go.  To kick off the process, run the following command from the directory where you unpacked the tar file:
{% highlight bash %}
$ bin/kafka-rdi-consumer.sh
{% endhighlight %}
