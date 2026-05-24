package com.colonel.saas.config;

import com.colonel.saas.service.ShortTtlCacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(
        name = "app.short-ttl-cache.redis-invalidation-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ShortTtlCacheRedisInvalidationConfig {

    @Bean
    public RedisMessageListenerContainer shortTtlCacheRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ShortTtlCacheService shortTtlCacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) -> {
            String prefix = new String(message.getBody(), StandardCharsets.UTF_8);
            shortTtlCacheService.evictLocalByPrefix(prefix);
        }, new ChannelTopic(ShortTtlCacheService.EVICT_CHANNEL));
        return container;
    }
}
