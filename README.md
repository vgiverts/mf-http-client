MarsFog HTTP Client is a simple Scala wrapper on the Netty HTTP libraries. It also uses an object pool from Apache Commons to pool and re-use HTTP connections and the JDK ArrayBlockingQueue to queue requests if the connection pool is exhausted.

This was created to address bugs and deficiencies in the Ning Async HTTP Client.

Examples
----

###Create a new instance

`val maxConnsPerHost = 5
val queueLengthPerHost = 5
val blockIfQueueFull = false

val client: HttpClient = new HttpClient(maxConnsPerHost, queueLengthPerHost, blockIfQueueFull);`


###Make a call

`client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET)`


Embed Jemcache
----

`JemcacheServer jemcacheServer = new JemcacheServer(port, memoryLimitBytes);`
###or
`JemcacheNioServer jemcacheNioServer = new JemcacheNioServer(port, memoryLimitBytes, numThreads);`


Maven dependency
----

`<repositories>
    ...
    <repository>
        <id>jemcache</id>
        <url>https://github.com/vgiverts/mf-http-client/raw/master/repo</url>
    </repository>
    ...
</repositories>

<dependencies>
    ...
    <dependency>
        <groupId>org.jemcache</groupId>
        <artifactId>jemcache</artifactId>
        <version>0.1.6</version>
    </dependency>
    ...
</dependencies>`

Credit
----

- The development of this project is supported by [MarsFog](http://marsfog.com).