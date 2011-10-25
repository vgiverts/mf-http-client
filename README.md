MarsFog HTTP Client is a simple Scala wrapper on the Netty HTTP libraries. It also uses an object pool from Apache Commons to pool and re-use HTTP connections and an ArrayBlockingQueue from the JDK to queue requests if the connection pool is exhausted.

This was created to address bugs and deficiencies in the Ning Async HTTP Client.

Example
----

###Create a new instance

    val maxConnsPerHost = 5
    val queueLengthPerHost = 5
    val blockIfQueueFull = false
    val client: HttpClient = new HttpClient(maxConnsPerHost, queueLengthPerHost, blockIfQueueFull);`


###Make a call

    val future = client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET)


###Get the result

    val response:HttpResponse = future.get()


###Make lots of calls

    val futures = (0 to 9).map(i => client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET))

###You can fire-and-forget or you can check their results

    futures.foreach(_.get().getContent)

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
            <version>1.0</version>
        </dependency>
        ...
    </dependencies>

Credit
----

- The development of this project is supported by [MarsFog](http://marsfog.com).