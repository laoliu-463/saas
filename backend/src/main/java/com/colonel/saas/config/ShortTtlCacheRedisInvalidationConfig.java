package com.colonel.saas.config;

import com.colonel.saas.service.ShortTtlCacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 短 TTL 缓存的 Redis Pub/Sub 失效配置。
 * <p>
 * 配置 Redis 消息监听容器，订阅缓存失效通知频道。当集群中某个节点修改了数据并发布
 * 失效消息时，所有其他节点通过监听该频道来清除本地缓存，实现多实例间的缓存一致性。
 * </p>
 *
 * <p>工作原理：</p>
 * <ol>
 *   <li>数据变更节点通过 {@link ShortTtlCacheService} 发布缓存失效消息到 Redis 频道</li>
 *   <li>本配置的监听器接收到消息后，提取缓存键前缀</li>
 *   <li>调用 {@link ShortTtlCacheService#evictLocalByPrefix(String)} 清除本地内存缓存</li>
 * </ol>
 *
 * <p>条件装配：</p>
 * <ul>
 *   <li>通过 {@code app.short-ttl-cache.redis-invalidation-enabled} 配置开关控制</li>
 *   <li>默认启用（{@code matchIfMissing = true}），设为 {@code false} 可禁用</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link ShortTtlCacheService} —— 缓存服务，定义失效频道名和本地缓存淘汰逻辑</li>
 *   <li>{@link RedisConfig} —— Redis 连接和序列化配置</li>
 *   <li>{@link OrderDerivedCacheKeys} —— 使用短 TTL 缓存的业务键前缀</li>
 * </ul>
 *
 * @see ShortTtlCacheService
 * @see RedisConfig
 */
@Configuration
@ConditionalOnProperty(
        name = "app.short-ttl-cache.redis-invalidation-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ShortTtlCacheRedisInvalidationConfig {

    /**
     * 创建 Redis 消息监听容器，订阅缓存失效通知频道。
     * <p>
     * 监听 {@link ShortTtlCacheService#EVICT_CHANNEL} 频道，
     * 收到消息时将消息体（缓存键前缀）转换为 UTF-8 字符串，
     * 然后清除本地内存中匹配该前缀的所有缓存条目。
     * </p>
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @param shortTtlCacheService   短 TTL 缓存服务
     * @return 配置完成的 Redis 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer shortTtlCacheRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ShortTtlCacheService shortTtlCacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // 订阅缓存失效频道：消息体为缓存键前缀
        container.addMessageListener((message, pattern) -> {
            String prefix = new String(message.getBody(), StandardCharsets.UTF_8);
            shortTtlCacheService.evictLocalByPrefix(prefix);
        }, new ChannelTopic(ShortTtlCacheService.EVICT_CHANNEL));
        return container;
    }
}
