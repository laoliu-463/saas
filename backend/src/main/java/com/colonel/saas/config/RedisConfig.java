package com.colonel.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化与模板配置。
 * <p>
 * 自定义 {@link RedisTemplate} 的序列化策略。本项目中 Redis 主要用于：
 * <ul>
 *   <li>JWT Token 缓存（纯字符串）</li>
 *   <li>短 TTL 缓存（配合 {@link ShortTtlCacheRedisInvalidationConfig}）</li>
 *   <li>分布式消息订阅（配置变更广播）</li>
 *   <li>系统配置值存储</li>
 * </ul>
 *
 * <p>序列化策略说明：</p>
 * 键和值均使用 {@link StringRedisSerializer}，而非默认的 JDK 序列化或 Jackson JSON 序列化。
 * 这是因为项目中大量 Token 值包含 {@code -XX} 等后缀，如果使用 Jackson 序列化器，
 * 会被错误地解析为负数；使用 String 序列化器可避免此类问题，同时在 Redis CLI 中也能直接查看内容。
 */
@Configuration
public class RedisConfig {

    /**
     * 创建全局 RedisTemplate 实例并配置序列化策略。
     * <p>
     * 将 Key、Hash Key、Value、Hash Value 四个序列化器全部设置为
     * {@link StringRedisSerializer}，确保数据以可读的字符串形式存储在 Redis 中。
     * </p>
     *
     * @param redisConnectionFactory Redis 连接工厂，由 Spring Boot 自动配置注入
     * @return 配置完成的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 键使用 String 序列化器，保证在 Redis 中以可读字符串存储
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // Token 是纯字符串，用 StringRedisSerializer 避免 Jackson 把含 -XX 后缀的 token 当作负数解析
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        // 属性校验，确保连接工厂和序列化器已正确设置
        template.afterPropertiesSet();
        return template;
    }
}
