package com.test.supertoken.controller;

import com.test.supertoken.task.HashTaskExecutor;
import com.test.supertoken.ratelimiter.RateLimiter;
import com.test.supertoken.model.HashInput;
import com.test.supertoken.model.HashOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.test.supertoken.ratelimiter.Request;

import java.util.concurrent.ExecutionException;

@RestController
public class HashingController {
    private static final String HASH_API_IDENTIFIER = "API_HASHING";
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private HashTaskExecutor hashTaskExecutor;
    
    @PostMapping("/hash")
    public HashOutput hash(@RequestBody HashInput input) {
        Request request = (() -> HASH_API_IDENTIFIER);
        for (; ; ) {
            if (rateLimiter.limit(request)) {
                try {
                    HashOutput hashOutput = hashTaskExecutor.hash(input).get();
                    return hashOutput;
                } catch (InterruptedException | ExecutionException e) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error please try later");
                } finally {
                    rateLimiter.clear(request);
                }
            }
            try {
                rateLimiter.waitForAllowedLimit();
            } catch (InterruptedException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error please try later");
            }
        }
    }
}
