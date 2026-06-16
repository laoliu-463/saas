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
 * 定时任务分布式锁服务，基于 Redis SET NX + TTL 实现，测试环境 Redis 不可用时回退进程内锁。
 *
 * <p>提供两种锁获取模式：</p>
 * <ul>
 *   <li><b>严格模式</b>（{@link #tryAcquireStrict}）：Redis 不可用时直接抛异常，不回退，
 *       适用于订单同步等强一致性场景</li>
 *   <li><b>宽松模式</b>（{@link #tryAcquire}）：测试环境下 Redis 不可用时回退到进程内
 *       {@link ConcurrentHashMap} 本地锁，保证单实例测试可正常运行</li>
 * </ul>
 *
 * <p><b>业务领域：</b>基础设施 — 分布式锁</p>
 * <p><b>协作关系：</b>被 {@link OrderSyncService}、{@link PerformanceBackfillService} 等定时任务调用</p>
 *
 * @see OrderSyncService
 */
@Slf4j
@Service
public class DistributedJobLockService {

    /** Redis 操作模板，用于执行 SET NX 分布式锁命令 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 测试模式开关，决定 Redis 不可用时是否回退到本地锁 */
    private final boolean testEnabled;

    /** 进程内本地锁映射，仅在测试模式下作为 Redis 的降级替代 */
    private final ConcurrentHashMap<String, AtomicBoolean> localLocks = new ConcurrentHashMap<>();

    public DistributedJobLockService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.redisTemplate = redisTemplate;
        this.testEnabled = testEnabled;
    }

    /**
     * 严格模式获取分布式锁，Redis 不可用时直接抛异常。
     *
     * <ol>
     *   <li>第一步：校验 lockKey 和 ttl 参数合法性</li>
     *   <li>第二步：通过 Redis SET NX + TTL 原子操作尝试获取锁</li>
     *   <li>第三步：Redis 连接失败时记录错误日志并抛出异常（不降级）</li>
     * </ol>
     *
     * @param lockKey 锁键名，不可为空
     * @param ttl     锁过期时间，必须为正数
     * @return {@code true} 表示成功获取锁，{@code false} 表示锁已被持有或参数无效
     * @throws RedisConnectionFailureException Redis 连接失败时抛出
     * @throws RedisCommandExecutionException   Redis 命令执行失败时抛出
     */
    public boolean tryAcquireStrict(String lockKey, Duration ttl) {
        // 第一步：参数校验
        if (!StringUtils.hasText(lockKey) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        try {
            // 第二步：Redis SET NX 原子获取锁
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    Objects.requireNonNull(lockKey),
                    "1",
                    Objects.requireNonNull(ttl));
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            // 第三步：严格模式不降级，直接抛出
            log.error("Redis unavailable when acquiring strict job lock {}: {}", lockKey, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 宽松模式获取分布式锁，测试环境下 Redis 不可用时回退到进程内本地锁。
     *
     * <ol>
     *   <li>第一步：校验 lockKey 和 ttl 参数合法性</li>
     *   <li>第二步：尝试 Redis SET NX 获取锁</li>
     *   <li>第三步：若 Redis 不可用且处于测试模式，回退到 ConcurrentHashMap 本地锁</li>
     *   <li>第四步：非测试模式下 Redis 不可用则直接抛出异常</li>
     * </ol>
     *
     * @param lockKey 锁键名，不可为空
     * @param ttl     锁过期时间，必须为正数（本地锁模式下不使用过期时间）
     * @return {@code true} 表示成功获取锁，{@code false} 表示锁已被持有或参数无效
     * @throws RedisConnectionFailureException 非测试模式 Redis 连接失败时抛出
     * @throws RedisCommandExecutionException   非测试模式 Redis 命令失败时抛出
     */
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

    /**
     * 兼容历史调用：旧实现允许传入任意锁值并在 Redis 中存储。
     *
     * <p>当前业务不依赖锁值语义，统一改为只按 key/ttl 判断锁状态。
     * 该重载保留给单测和历史调用，忽略 value 入参。</p>
     *
     * @param lockKey 锁键名，不可为空
     * @param ttl     锁过期时间，必须为正数
     * @param ignored 锁值占位参数（兼容旧签名，当前忽略）
     * @return {@code true} 表示获取成功
     * @deprecated 请使用 {@link #tryAcquire(String, Duration)}。
     */
    @Deprecated
    public boolean tryAcquire(String lockKey, Duration ttl, Object ignored) {
        return tryAcquire(lockKey, ttl);
    }

    /**
     * 释放分布式锁，同时清理 Redis 锁和本地回退锁。
     *
     * <ol>
     *   <li>第一步：校验 lockKey 参数</li>
     *   <li>第二步：若存在本地锁，先释放本地锁（设为 false）</li>
     *   <li>第三步：尝试从 Redis 删除锁键</li>
     *   <li>第四步：测试模式下 Redis 不可用时仅记录警告，不抛出异常</li>
     * </ol>
     *
     * @param lockKey 要释放的锁键名
     */
    public void release(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            return;
        }
        // 注意：先释放本地锁，再清理 Redis 锁
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

    /**
     * 兼容历史调用：旧实现会携带锁 owner 标识并按 owner 释放。
     *
     * <p>当前锁服务使用固定 key 语义，owner 无法参与核验，因此本方法仅做兼容，
     * 等价于 {@link #release(String)}。</p>
     *
     * @param lockKey 锁键名
     * @param ignored 锁值占位参数（当前忽略）
     * @deprecated 请使用 {@link #release(String)}。
     */
    @Deprecated
    public void releaseWithOwner(String lockKey, Object ignored) {
        release(lockKey);
    }

    /**
     * 兼容历史实现：返回锁持有方信息，当前实现不持久化 owner，因此返回 {@code null}。
     */
    public String currentLockValue(String lockKey) {
        return null;
    }

    /**
     * 兼容历史实现：返回锁 TTL 秒数，当前实现不在应用侧可观测。
     */
    public long currentLockTtlSeconds(String lockKey) {
        return 0L;
    }
}
