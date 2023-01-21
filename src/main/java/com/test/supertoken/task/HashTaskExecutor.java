package com.test.supertoken.task;

import com.test.supertoken.model.HashInput;
import com.test.supertoken.model.HashOutput;
import de.mkammerer.argon2.Argon2;
import org.assertj.core.util.VisibleForTesting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class HashTaskExecutor {

    private ExecutorService executorService;
    private Argon2 argon2;
    private static final Integer HASH_TASK_ITERATIONS = 8;
    private static final Integer HASH_TASK_MEM_LIMIT = 64 * 1024;
    private static final Integer HASH_TASK_PARALLEL_THREADS = 1;

    @VisibleForTesting
    public HashTaskExecutor() {
    }

    public HashTaskExecutor(ExecutorService executorService, Argon2 argon2) {
        this.executorService = executorService;
        this.argon2 = argon2;
    }

    public Future<HashOutput> hash(HashInput hashInput) {
        return this.executorService.submit(() -> {
            String hashedContent = argon2.hash(HASH_TASK_ITERATIONS,
                    HASH_TASK_MEM_LIMIT,
                    HASH_TASK_PARALLEL_THREADS,
                    hashInput.getContent().toCharArray());
            HashOutput hashOutput = new HashOutput(hashedContent);
            return hashOutput;
        });
    }

    public boolean verify(HashOutput hashOutput, HashInput hashInput) {
        return argon2.verify(hashOutput.getHash(), hashInput.getContent().toCharArray());
    }
}
