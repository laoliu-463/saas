package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void applicationTaskExecutor_shouldBeBoundedAndUsedAsAsyncDefault() {
        AsyncConfig config = new AsyncConfig();

        ThreadPoolTaskExecutor executor = config.applicationTaskExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(200);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("app-task-");

        Executor asyncExecutor = config.getAsyncExecutor();
        assertThat(asyncExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        assertThat(((ThreadPoolTaskExecutor) asyncExecutor).getThreadNamePrefix()).isEqualTo("app-task-");

        executor.shutdown();
        ((ThreadPoolTaskExecutor) asyncExecutor).shutdown();
    }
}
