package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 推广链接生成幂等控制服务。
 * <p>
 * 使用两阶段锁机制防止同一推广链接请求被重复生成：
 * <ol>
 *   <li><b>tryAcquireInFlight</b> — 尝试获取"正在处理"槽位（2 分钟 TTL），成功则继续；失败则说明有并发请求正在处理</li>
 *   <li><b>markCompleted</b> — 处理完成后将结果写入"已完成"存储（24 小时 TTL），同时释放"正在处理"槽位</li>
 * </ol>
 * </p>
 * <p>
 * 存储优先使用 Redis（分布式部署）；当 Redis 不可用时降级为 {@link ConcurrentHashMap} 本地存储（仅限单元测试场景）。
 * </p>
 * <p>
 * 幂等 Key 的作用域由 {@code userId + activityId + productId + idempotencyKey} 四段拼接而成，
 * 确保同一用户对同一商品的同一幂等 Key 不会重复生成推广链接。
 * </p>
 *
 * @see ProductService#generatePromotionLink
 */
@Service
public class PromotionLinkIdempotencyService {

    /** 已完成结果的 TTL：24 小时 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    /** 正在处理中锁的 TTL：2 分钟（防止死锁） */
    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(2);
    /** 已完成存储的 Key 后缀 */
    private static final String COMPLETED_SUFFIX = ":completed";
    /** 正在处理中存储的 Key 后缀 */
    private static final String IN_FLIGHT_SUFFIX = ":inflight";
    /** 幂等 Key 最大允许长度 */
    private static final int MAX_KEY_LENGTH = 128;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;
    /** Redis 模板（可能为 null，此时降级到本地存储） */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 本地降级存储（仅用于单元测试或 Redis 不可用时） */
    private final ConcurrentHashMap<String, String> localStore = new ConcurrentHashMap<>();

    /**
     * 无 Redis 的构造函数（单元测试使用）。
     *
     * @param objectMapper JSON 序列化工具
     */
    public PromotionLinkIdempotencyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.redisTemplate = null;
    }

    /**
     * 带 Redis 的构造函数（生产环境使用）。
     * <p>
     * 通过 {@link ObjectProvider} 延迟注入，当 Spring 容器中没有 RedisTemplate Bean 时优雅降级。
     * </p>
     *
     * @param objectMapper          JSON 序列化工具
     * @param redisTemplateProvider Redis 模板提供者（可选）
     */
    @Autowired
    public PromotionLinkIdempotencyService(
            ObjectMapper objectMapper,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
    }

    /**
     * 构建幂等作用域 Key。
     * <p>
     * 格式：{@code userId:activityId:productId:idempotencyKey}，确保唯一性。
     * </p>
     *
     * @param userId          当前操作用户 ID
     * @param activityId      活动 ID（团长活动）
     * @param productId       商品 ID（抖店商品）
     * @param idempotencyKey  客户端传入的幂等 Key（非空，最长 128 字符）
     * @return 完整的作用域 Key
     * @throws BusinessException 当 idempotencyKey 为空或超过长度限制时
     */
    public String buildScopeKey(UUID userId, String activityId, String productId, String idempotencyKey) {
        String normalizedKey = normalizeKey(idempotencyKey);
        return userId + ":" + activityId + ":" + productId + ":" + normalizedKey;
    }

    /**
     * 查找已完成的幂等结果。
     * <p>
     * 如果之前相同 scopeKey 的请求已经完成，返回其结果（{@link DouyinPromotionGateway.PromotionLinkResult}）。
     * 反序列化失败时会清理损坏的存储条目。
     * </p>
     *
     * @param scopeKey 作用域 Key（由 {@link #buildScopeKey} 生成）
     * @return 已完成的结果；不存在或已过期时返回 {@code Optional.empty()}
     */
    public Optional<DouyinPromotionGateway.PromotionLinkResult> findCompleted(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return Optional.empty();
        }
        String payload = readValue(scopeKey + COMPLETED_SUFFIX);
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, DouyinPromotionGateway.PromotionLinkResult.class));
        } catch (JsonProcessingException ex) {
            deleteValue(scopeKey + COMPLETED_SUFFIX);
            return Optional.empty();
        }
    }

    /**
     * 标记请求已完成，并存储结果。
     * <p>
     * 将结果序列化后写入"已完成"存储（24 小时 TTL），同时释放"正在处理"锁。
     * 序列化失败时抛出冲突异常。
     * </p>
     *
     * @param scopeKey 作用域 Key
     * @param result   推广链接生成结果
     * @throws BusinessException 当结果序列化失败时
     */
    public void markCompleted(String scopeKey, DouyinPromotionGateway.PromotionLinkResult result) {
        if (!StringUtils.hasText(scopeKey) || result == null) {
            return;
        }
        try {
            writeValue(scopeKey + COMPLETED_SUFFIX, objectMapper.writeValueAsString(result), IDEMPOTENCY_TTL);
            deleteValue(scopeKey + IN_FLIGHT_SUFFIX);
        } catch (JsonProcessingException ex) {
            throw BusinessException.conflict("推广链接幂等结果序列化失败");
        }
    }

    /**
     * 尝试获取"正在处理中"的槽位。
     * <p>
     * 使用 Redis SETNX 或 ConcurrentHashMap.putIfAbsent 实现分布式/本地互斥：
     * <ul>
     *   <li>返回 {@code true}：当前请求成功获取锁，可以继续处理</li>
     *   <li>返回 {@code false}：已有并发请求正在处理相同 key，当前请求应等待或返回已有结果</li>
     * </ul>
     * 锁的 TTL 为 2 分钟，防止异常情况下的死锁。
     * </p>
     *
     * @param scopeKey 作用域 Key
     * @return {@code true} 表示成功获取锁；{@code false} 表示有并发请求正在处理
     */
    public boolean tryAcquireInFlight(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return true;
        }
        String inflightKey = scopeKey + IN_FLIGHT_SUFFIX;
        if (redisTemplate != null) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(inflightKey, "1", IN_FLIGHT_TTL);
            return Boolean.TRUE.equals(locked);
        }
        return localStore.putIfAbsent(inflightKey, "1") == null;
    }

    /**
     * 释放"正在处理中"的槽位。
     * <p>
     * 通常在异常回滚时调用，确保不会因处理失败而导致后续请求永远无法获取锁。
     * 正常完成流程通过 {@link #markCompleted} 释放。
     * </p>
     *
     * @param scopeKey 作用域 Key
     */
    public void releaseInFlight(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return;
        }
        deleteValue(scopeKey + IN_FLIGHT_SUFFIX);
    }

    /**
     * 标准化并验证幂等 Key。
     *
     * @param idempotencyKey 客户端传入的原始幂等 Key
     * @return 去除首尾空白后的 Key
     * @throws BusinessException 当 Key 为空或超过最大长度时
     */
    private String normalizeKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw BusinessException.param("Idempotency-Key 不能为空");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw BusinessException.param("Idempotency-Key 长度不能超过 " + MAX_KEY_LENGTH);
        }
        return trimmed;
    }

    /**
     * 从存储中读取值（Redis 优先，降级到本地 Map）。
     *
     * @param storageKey 完整的存储 Key
     * @return 存储的字符串值；不存在时返回 null
     */
    private String readValue(String storageKey) {
        if (redisTemplate != null) {
            Object value = redisTemplate.opsForValue().get(storageKey);
            return value == null ? null : String.valueOf(value);
        }
        return localStore.get(storageKey);
    }

    /**
     * 向存储中写入值（Redis 优先，降级到本地 Map）。
     * <p>
     * 注意：本地 Map 降级不支持 TTL 过期，仅用于单元测试等短生命周期场景。
     * </p>
     *
     * @param storageKey 完整的存储 Key
     * @param value      待写入的字符串值
     * @param ttl        过期时间（仅 Redis 生效）
     */
    private void writeValue(String storageKey, String value, Duration ttl) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(storageKey, value, ttl);
            return;
        }
        // 本地降级仅用于单元测试，条目随 JVM 生命周期自然销毁
        localStore.put(storageKey, value);
    }

    /**
     * 从存储中删除值（Redis 优先，降级到本地 Map）。
     *
     * @param storageKey 完整的存储 Key
     */
    private void deleteValue(String storageKey) {
        if (redisTemplate != null) {
            redisTemplate.delete(storageKey);
            return;
        }
        localStore.remove(storageKey);
    }
}
