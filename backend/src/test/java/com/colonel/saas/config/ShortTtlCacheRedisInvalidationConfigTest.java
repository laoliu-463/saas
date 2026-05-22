package com.colonel.saas.config;

import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.util.ByteArrayWrapper;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShortTtlCacheRedisInvalidationConfigTest {

    @Test
    void shortTtlCacheRedisMessageListenerContainer_shouldRegisterEvictionListener() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ShortTtlCacheService cacheService = mock(ShortTtlCacheService.class);

        RedisMessageListenerContainer container = new ShortTtlCacheRedisInvalidationConfig()
                .shortTtlCacheRedisMessageListenerContainer(connectionFactory, cacheService);

        assertThat(ReflectionTestUtils.getField(container, "connectionFactory")).isSameAs(connectionFactory);
        @SuppressWarnings("unchecked")
        Map<?, Collection<?>> channelMapping =
                (Map<?, Collection<?>>) ReflectionTestUtils.getField(container, "channelMapping");
        String channel = new ChannelTopic(ShortTtlCacheService.EVICT_CHANNEL).getTopic();
        ByteArrayWrapper channelKey = new ByteArrayWrapper(channel.getBytes(StandardCharsets.UTF_8));
        assertThat(channelMapping.containsKey(channelKey)).isTrue();
        assertThat(channelMapping.get(channelKey)).hasSize(1);
    }

    @Test
    void registeredListener_shouldEvictLocalPrefixFromMessageBody() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ShortTtlCacheService cacheService = mock(ShortTtlCacheService.class);
        RedisMessageListenerContainer container = new ShortTtlCacheRedisInvalidationConfig()
                .shortTtlCacheRedisMessageListenerContainer(connectionFactory, cacheService);

        @SuppressWarnings("unchecked")
        Map<?, Collection<?>> channelMapping =
                (Map<?, Collection<?>>) ReflectionTestUtils.getField(container, "channelMapping");
        ByteArrayWrapper channelKey = new ByteArrayWrapper(
                ShortTtlCacheService.EVICT_CHANNEL.getBytes(StandardCharsets.UTF_8));
        Object listener = channelMapping.get(channelKey).iterator().next();
        if (listener instanceof MessageListenerAdapter adapter) {
            listener = ReflectionTestUtils.getField(adapter, "delegate");
        }

        org.springframework.data.redis.connection.MessageListener messageListener =
                (org.springframework.data.redis.connection.MessageListener) listener;
        messageListener.onMessage(
                new DefaultMessage(
                        ShortTtlCacheService.EVICT_CHANNEL.getBytes(StandardCharsets.UTF_8),
                        "dashboard:".getBytes(StandardCharsets.UTF_8)),
                null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cacheService).evictLocalByPrefix(captor.capture());
        assertThat(captor.getValue()).isEqualTo("dashboard:");
    }
}
