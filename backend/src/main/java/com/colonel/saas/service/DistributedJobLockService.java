package com.colonel.saas.service;

import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务分布式锁（Redis SET NX + TTL），测试环境 Redis 不可用时回退进程内锁。
 */
@Slf4j
@Service
public class DistributedJobLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean testEnabled;
    private final ConcurrentHashMap<String, AtomicBoolean> localLocks = new ConcurrentHashMap<>();

    public DistributedJobLockService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.redisTemplate = redisTemplate;
        this.testEnabled = testEnabled;
    }

    /**
     * 集群锁：Redis 不可用时直接抛出异常，不回退进程内锁（用于订单同步等强一致场景）。
     */
    public boolean tryAcquireStrict(String lockKey, Duration ttl) {
        if (!StringUtils.hasText(lockKey) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    Objects.requireNonNull(lockKey),
                    "1",
                    Objects.requireNonNull(ttl));
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            log.error("Redis unavailable when acquiring strict job lock {}: {}", lockKey, ex.getMessage());
            throw ex;
        }
    }

    public boolean tryAcquire(String lockKey, Duration ttl) {
        if (!StringUtils.hasText(lockKey) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    Objects.requireNonNull(lockKey),
                    "1",
                    Objects.requireNonNull(ttl));
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when acquiring job lock {}, fallback to local lock: {}",
                        lockKey, ex.getMessage());
                return localLocks
                        .computeIfAbsent(lockKey, ignored -> new AtomicBoolean(false))
                        .compareAndSet(false, true);
            }
            throw ex;
        }
    }

    public void release(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            return;
        }
        AtomicBoolean local = localLocks.get(lockKey);
        if (local != null) {
            local.set(false);
        }
        try {
            redisTemplate.delete(lockKey);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when releasing job lock {}, local lock already released: {}",
                        lockKey, ex.getMessage());
                return;
            }
            throw ex;
        }
    }
}
