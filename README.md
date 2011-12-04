MarsFog HTTP Client is a simple Scala wrapper on the Netty HTTP libraries.

It uses the `GenericObjectPool` from Apache Commons to pool and re-use HTTP connections and the `ArrayBlockingQueue` from the JDK to queue requests if the connection pool is exhausted.

It was created to address bugs and deficiencies in the Ning Async HTTP Client that become apparent under heavy load.

Example
----

###Create a new instance

    val maxConnsPerHost = 5
    val queueLengthPerHost = 5
    val blockIfQueueFull = false
    val client: HttpClient = new HttpClient(maxConnsPerHost, queueLengthPerHost, blockIfQueueFull);


###Make a call

    val future = client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET)


###Get the response

    val response:HttpResponse = future.get()


###Make lots of calls (just fire-and-forget)

    val futures = (0 to 9).map(i => client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET))


###Get the responses (later, if you like)

    val responses = futures.foreach(_.get().getContent)


Maven dependency
----

    <repositories>
        ...
        <repository>
            <id>mf-http-client</id>
            <url>https://github.com/vgiverts/mf-http-client/raw/master/repo</url>
        </repository>
        ...
    </repositories>

    <dependencies>
        ...
        <dependency>
            <groupId>com.marsfog</groupId>
            <artifactId>mf-http-client</artifactId>
            <version>1.1</version>
        </dependency>
        ...
    </dependencies>

Credit
----

- The development of this project is supported by [MarsFog](http://marsfog.com).