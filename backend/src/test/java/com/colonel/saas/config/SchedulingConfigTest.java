package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    @Test
    void taskScheduler_shouldUseConfiguredPoolAndThreadPrefix() {
        TaskScheduler scheduler = new SchedulingConfig().taskScheduler();

        assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = (ThreadPoolTaskScheduler) scheduler;
        assertThat(threadPoolTaskScheduler.getPoolSize()).isEqualTo(4);
        assertThat(threadPoolTaskScheduler.getThreadNamePrefix()).isEqualTo("saas-scheduler-");
    }
}
