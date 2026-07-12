package com.colonel.saas.service;

import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动商品同步的跨实例并发槽。
 *
 * <p>槽位计数和 owner 租约放在同一个 Lua 脚本中更新，避免简单 DECR/INCR 在异常退出、
 * 重复释放或锁过期后产生槽位泄漏。Redis 不可用时只允许测试模式使用进程内降级，
 * real-pre 直接失败并由上层把任务记录为失败/排队。</p>
 */
@Slf4j
@Service
public class DistributedConcurrencyLimiter {

    public static final String SLOTS_KEY = "product:backfill:concurrency:slots";
    public static final String LEASES_KEY = "product:backfill:concurrency:leases";

    private static final DefaultRedisScript<Long> TRY_ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "local now=tonumber(ARGV[1]); "
                    + "local max=tonumber(ARGV[2]); "
                    + "local ttl=tonumber(ARGV[3]); "
                    + "local owner=ARGV[4]; "
                    + "local slots=tonumber(redis.call('get',KEYS[1]) or ARGV[2]); "
                    + "local expired=redis.call('zrangebyscore',KEYS[2],'-inf',now); "
                    + "for _,item in ipairs(expired) do "
                    + "if redis.call('zrem',KEYS[2],item)==1 and slots<max then slots=slots+1 end; "
                    + "end; "
                    + "if redis.call('zscore',KEYS[2],owner) then redis.call('set',KEYS[1],slots); return 1 end; "
                    + "if slots<=0 then redis.call('set',KEYS[1],slots); return 0 end; "
                    + "slots=slots-1; redis.call('set',KEYS[1],slots); "
                    + "redis.call('zadd',KEYS[2],now+ttl,owner); return 1",
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "local max=tonumber(ARGV[2]); "
                    + "if redis.call('zrem',KEYS[2],ARGV[1])==1 then "
                    + "local slots=tonumber(redis.call('get',KEYS[1]) or ARGV[2]); "
                    + "if slots<max then slots=slots+1 end; "
                    + "redis.call('set',KEYS[1],slots); return 1 end; return 0",
            Long.class);

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('zscore',KEYS[1],ARGV[1]) then "
                    + "redis.call('zadd',KEYS[1],tonumber(ARGV[2])+tonumber(ARGV[3]),ARGV[1]); return 1 end; return 0",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean testEnabled;
    private final int maxConcurrency;
    private final Map<String, Long> localLeases = new ConcurrentHashMap<>();

    public DistributedConcurrencyLimiter(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.test.enabled:false}") boolean testEnabled,
            @Value("${product.sync.activityProduct.manual-upstream-max-concurrency:2}") int configuredMaxConcurrency) {
        this.redisTemplate = redisTemplate;
        this.testEnabled = testEnabled;
        this.maxConcurrency = Math.min(Math.max(configuredMaxConcurrency, 1), 10);
    }

    /**
     * 尝试占用一个带 owner 的活动同步槽位。
     */
    public boolean tryAcquire(String owner, Duration leaseTtl) {
        if (!StringUtils.hasText(owner) || leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero()) {
            return false;
        }
        long ttlMillis = leaseTtl.toMillis();
        try {
            Long result = redisTemplate.execute(
                    TRY_ACQUIRE_SCRIPT,
                    List.of(SLOTS_KEY, LEASES_KEY),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(maxConcurrency),
                    String.valueOf(ttlMillis),
                    owner);
            return result != null && result > 0;
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when acquiring concurrency slot, owner={}: {}",
                        owner, ex.getMessage());
                return tryAcquireLocal(owner, ttlMillis);
            }
            throw ex;
        }
    }

    /**
     * 按 owner 释放槽位；重复释放不会重复归还槽位。
     */
    public void release(String owner) {
        if (!StringUtils.hasText(owner)) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(SLOTS_KEY, LEASES_KEY),
                    owner,
                    String.valueOf(maxConcurrency));
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when releasing concurrency slot, owner={}: {}",
                        owner, ex.getMessage());
                releaseLocal(owner);
                return;
            }
            throw ex;
        }
    }

    /**
     * 按 owner 续租；租约已不存在时返回 false，调用方不得继续无锁运行。
     */
    public boolean renew(String owner, Duration leaseTtl) {
        if (!StringUtils.hasText(owner) || leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero()) {
            return false;
        }
        long ttlMillis = leaseTtl.toMillis();
        try {
            Long result = redisTemplate.execute(
                    RENEW_SCRIPT,
                    List.of(LEASES_KEY),
                    owner,
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(ttlMillis));
            return result != null && result > 0;
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when renewing concurrency slot, owner={}: {}",
                        owner, ex.getMessage());
                return renewLocal(owner, ttlMillis);
            }
            throw ex;
        }
    }

    private synchronized boolean tryAcquireLocal(String owner, long ttlMillis) {
        long now = System.currentTimeMillis();
        localLeases.entrySet().removeIf(entry -> entry.getValue() <= now);
        Long currentExpiry = localLeases.get(owner);
        if (currentExpiry != null && currentExpiry > now) {
            return true;
        }
        if (localLeases.size() >= maxConcurrency) {
            return false;
        }
        localLeases.put(owner, now + ttlMillis);
        return true;
    }

    private synchronized void releaseLocal(String owner) {
        localLeases.remove(owner);
    }

    private synchronized boolean renewLocal(String owner, long ttlMillis) {
        Long expiry = localLeases.get(owner);
        if (expiry == null || expiry <= System.currentTimeMillis()) {
            localLeases.remove(owner);
            return false;
        }
        localLeases.put(owner, System.currentTimeMillis() + ttlMillis);
        return true;
    }
}
