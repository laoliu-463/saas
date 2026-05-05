package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinConfig;
import com.colonel.saas.gateway.douyin.DouyinAuthGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealDouyinAuthGatewayTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private DouyinConfig douyinConfig;
    @Mock
    private DoudianTokenGateway doudianTokenGateway;
    @Mock
    private DouyinUpstreamModeSupport upstreamModeSupport;
    @Mock
    private DouyinContractFixtureProvider contractFixtureProvider;

    private RealDouyinAuthGateway gateway;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        gateway = new RealDouyinAuthGateway(
                redisTemplate,
                douyinConfig,
                doudianTokenGateway,
                upstreamModeSupport,
                contractFixtureProvider
        );
    }

    @Test
    void ensureToken_shouldReuseCachedRedisToken() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(String.valueOf(expireAt));

        DouyinAuthGateway.TokenPayload payload = gateway.ensureToken("app123");

        assertThat(payload).isNotNull();
        assertThat(payload.accessToken()).isEqualTo("cached-access");
        assertThat(payload.refreshToken()).isEqualTo("cached-refresh");
        assertThat(payload.expiresIn()).isBetween(1L, 600L);
    }

    @Test
    void ensureToken_shouldFallbackToConfiguredClientKey() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(douyinConfig.getClientKey()).thenReturn("client-key-1");
        when(valueOperations.get("douyin:token:client-key-1")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:client-key-1")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:client-key-1")).thenReturn(expireAt);

        DouyinAuthGateway.TokenPayload payload = gateway.ensureToken(null);

        assertThat(payload).isNotNull();
        assertThat(payload.accessToken()).isEqualTo("cached-access");
    }

    @Test
    void ensureToken_shouldReturnNullWhenTokenExpired() {
        long expireAt = Instant.now().getEpochSecond() - 5;
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(expireAt);

        DouyinAuthGateway.TokenPayload payload = gateway.ensureToken("app123");

        assertThat(payload).isNull();
    }

    @Test
    void ensureToken_shouldReturnNullWhenRefreshTokenMissing() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn(" ");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(expireAt);

        DouyinAuthGateway.TokenPayload payload = gateway.ensureToken("app123");

        assertThat(payload).isNull();
    }
}
