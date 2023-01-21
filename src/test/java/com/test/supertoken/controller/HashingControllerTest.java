package com.test.supertoken.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.supertoken.model.HashInput;
import com.test.supertoken.model.HashOutput;
import com.test.supertoken.ratelimiter.CountBasedRateLimiter;
import com.test.supertoken.ratelimiter.RateLimiter;
import com.test.supertoken.task.HashTaskExecutor;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"})
@WebAppConfiguration
public class HashingControllerTest {
    @TestConfiguration
    public static class TestBeanConfiguration {

        @Bean
        public Argon2 argon2() {
            return mock(Argon2.class);
        }

        @Bean
        public HashTaskExecutor hashTaskExecutor(ExecutorService executorService, Argon2 argon2) {
            return new HashTaskExecutor(executorService, argon2);
        }

        @Bean
        public Object lock() {
            return new Object();
        }

        @Bean
        public RateLimiter rateLimiter(Object lock) {
            return new CountBasedRateLimiter(1, lock);
        }

        @Bean
        public ExecutorService executorService() {
            return Executors.newSingleThreadExecutor();
        }
    }

    @Autowired
    Argon2 argon2;
    protected MockMvc mvc;
    @Autowired
    WebApplicationContext webApplicationContext;
    @Autowired
    HashTaskExecutor hashTaskExecutor;

    @Before
    public void setUp() {

        this.mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        Mockito.when(this.argon2.hash(anyInt(), anyInt(), anyInt(), any(char[].class))).
                thenAnswer(i -> Argon2Factory.create().hash((int) i.getArgument(0),
                        (int) i.getArgument(1),
                        (int) i.getArgument(2), (char[]) i.getArgument(3)));
        Mockito.when(argon2.verify(anyString(), any(char[].class)))
                .thenAnswer(i -> Argon2Factory.create().verify((String) i.getArgument(0), (char[]) i.getArgument(1)));
    }

    protected String mapToJson(Object obj) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(obj);
    }

    protected <T> T mapFromJson(String json, Class<T> clazz)
            throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, clazz);
    }

    @Test
    public void hash() throws Exception {
        String uri = "/hash";
        HashInput hashInput = new HashInput();
        hashInput.setContent("input:dummy");
        String inputJson = mapToJson(hashInput);
        MvcResult mvcResult = mvc.perform(MockMvcRequestBuilders.post(uri)
                .contentType(MediaType.APPLICATION_JSON_VALUE).content(inputJson)).andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);
        String content = mvcResult.getResponse().getContentAsString();
        HashOutput hashOutput = mapFromJson(content, HashOutput.class);
        assertTrue(hashTaskExecutor.verify(hashOutput, hashInput));
    }

    @Test
    public void concurrentHashWhenLimitReachedBlocksRequest() throws Exception {

        String uri = "/hash";
        HashInput hashInput = new HashInput();
        hashInput.setContent("input:dummy");
        String inputJson = mapToJson(hashInput);

        Thread thread1 = new Thread(() -> {
            try {
                MvcResult mvcResult = mvc.perform(MockMvcRequestBuilders.post(uri)
                        .contentType(MediaType.APPLICATION_JSON_VALUE).content(inputJson)).andReturn();
                int status = mvcResult.getResponse().getStatus();
                assertEquals(200, status);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread thread2 = new Thread(() -> {
            try {
                MvcResult mvcResult = mvc.perform(MockMvcRequestBuilders.post(uri)
                        .contentType(MediaType.APPLICATION_JSON_VALUE).content(inputJson)).andReturn();
                int status = mvcResult.getResponse().getStatus();
                assertEquals(200, status);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        thread1.setName("Thread1");
        thread2.setName("Thread2");
        thread1.setDaemon(true);
        thread2.setDaemon(true);

        AtomicInteger currentHashProcessesCount = new AtomicInteger(0);
        AtomicBoolean thread1Flag = new AtomicBoolean(false);
        AtomicBoolean thread2Flag = new AtomicBoolean(false);
        AtomicReference<Thread> hashingThread = new AtomicReference<>();

        Mockito.when(this.argon2.hash(anyInt(), anyInt(), anyInt(), any(char[].class))).thenAnswer(i -> {
            currentHashProcessesCount.incrementAndGet();
            Thread curThread = Thread.currentThread();
            hashingThread.set(curThread);
            //mimicking long running hash.
            while ((runningThreadName().equals("thread-1") && !thread1Flag.get())
                    || (runningThreadName().equals("thread-2") && !thread2Flag.get())) ;
            currentHashProcessesCount.decrementAndGet();
            return "output:dummy";
        });

        thread1.start();
        thread2.start();

        //waiting for both threads to start running and make requests.
        while (!(thread1.isAlive() && thread2.isAlive() && currentHashProcessesCount.get() == 1)) ;
        assertEquals(1, currentHashProcessesCount.get());
        //signal hashThread to proceed
        if (runningThreadName(hashingThread.get()).equals("thread-1")) {
            thread1Flag.set(true);
        } else {
            thread2Flag.set(true);
        }
        while (currentHashProcessesCount.get() == 1) ;
    }

    private static String runningThreadName(Thread runningThread) {
        return runningThread.getName().endsWith("thread-1") ? "thread-1" : "thread-2";
    }

    private static String runningThreadName() {
        return Thread.currentThread().getName().endsWith("thread-1") ? "thread-1" : "thread-2";
    }
}
