package com.test.supertoken;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/*
The scenario is that there exists a web server which creates a new thread per request. Each request does a password hashing task (using argon2).

Each hashing operation takes 64 mb of memory (RAM).
 We need to figure out a way in which we can queue up the web server threads such that at any given time,
 only upto 10 threads are running the hashing operation,
  whilst the rest of the threads are queued up and waiting their turn.

For example, if we issue 100 concurrent requests to the web server, initially we would have 10 of those that do hashing whilst 90 of them wait.
As soon as one thread is done with the hashing, we will have 89 that are waiting and so on..
 until all of them are done with the hashing operation.

Note that you need to actually build an API using any Java web service framework.
 The API should take in a string and return the argon2 hash of it.
  The web server itself should not have any limit on number of requests in parallel (or some very high limit like 100 RPS).
  Therefore, the rate limiting needs to be done on the password hashing code.

You are required to write automated tests to prove that the password hashing rate limiting actually works.
 */
@SpringBootApplication
@ComponentScan({"com.test.supertoken"})
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class,args);
    }
}
