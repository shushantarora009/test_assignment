package com.test.supertoken.ratelimiter;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CountBasedRateLimiter implements RateLimiter {

    private ConcurrentHashMap<String, AtomicInteger> apiToRequestCount;
    private Integer maxLimit = 0;
    private Object lock;

    public CountBasedRateLimiter(Integer maxLimit, Object lock) {
        apiToRequestCount = new ConcurrentHashMap<>();
        this.maxLimit = maxLimit;
        this.lock = lock;
    }

    @Override
    public void waitForAllowedLimit() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }

    @Override
    public boolean limit(Request request) {
        AtomicInteger currentCounter = apiToRequestCount.computeIfAbsent(request.getApiIdentifier(),
                k -> new AtomicInteger());
        if (apiToRequestCount.get(request.getApiIdentifier()).get() < maxLimit) {
            currentCounter.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean clear(Request request) {
        AtomicInteger currentCounter = apiToRequestCount.get(request.getApiIdentifier());
        if (Objects.isNull(currentCounter)) {
            throw new IllegalArgumentException("Request identifier is not registered yet");
        }
        currentCounter.decrementAndGet();
        synchronized (lock) {
            lock.notifyAll();
        }
        return true;
    }
}
