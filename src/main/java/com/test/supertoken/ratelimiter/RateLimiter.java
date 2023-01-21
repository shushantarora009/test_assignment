package com.test.supertoken.ratelimiter;

public interface RateLimiter {

    boolean limit(Request request);

    boolean clear(Request request);

    void waitForAllowedLimit() throws InterruptedException;
}
