package com.test.supertoken.conf;

import com.test.supertoken.ratelimiter.CountBasedRateLimiter;
import com.test.supertoken.task.HashTaskExecutor;
import com.test.supertoken.ratelimiter.RateLimiter;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {
    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(10);
    }

    @Bean
    public Argon2 argon2() {
        return Argon2Factory.create();
    }

    @Bean
    public HashTaskExecutor hashTaskExecutor(ExecutorService executorService, Argon2 argon2) {
        return new HashTaskExecutor(executorService, argon2);
    }

    @Bean
    public Object lock(){
        return new Object();
    }
    @Bean
    public RateLimiter rateLimiter(Object lock) {
        return new CountBasedRateLimiter(10,lock);
    }

}
