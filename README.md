MarsFog HTTP Client is a simple Scala wrapper on the Netty HTTP libraries. It also uses an object pool from Apache Commons to pool and re-use HTTP connections and the JDK ArrayBlockingQueue to queue requests if the connection pool is exhausted.

This was created to address bugs and deficiencies in the Ning Async HTTP Client.

Example
----

###Create a new instance

    val maxConnsPerHost = 5
    val queueLengthPerHost = 5
    val blockIfQueueFull = false
    val client: HttpClient = new HttpClient(maxConnsPerHost, queueLengthPerHost, blockIfQueueFull);`


###Make a call

    client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET)


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