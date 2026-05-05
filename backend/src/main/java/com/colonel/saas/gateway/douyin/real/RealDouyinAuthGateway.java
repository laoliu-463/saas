package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinConfig;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinAuthGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinAuthGateway implements DouyinAuthGateway {

    private static final String TOKEN_KEY_PREFIX = "douyin:token:";
    private static final String REFRESH_KEY_PREFIX = "douyin:refresh:";
    private static final String EXPIRE_AT_KEY_PREFIX = "douyin:token:expire_at:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DouyinConfig douyinConfig;
    private final DoudianTokenGateway doudianTokenGateway;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinAuthGateway(
            RedisTemplate<String, Object> redisTemplate,
            DouyinConfig douyinConfig,
            DoudianTokenGateway doudianTokenGateway,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.redisTemplate = redisTemplate;
        this.douyinConfig = douyinConfig;
        this.doudianTokenGateway = doudianTokenGateway;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public TokenPayload ensureToken(String appId) {
        String finalAppId = resolveAppId(appId);
        String accessToken = asTrimmedString(redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + finalAppId));
        String refreshToken = asTrimmedString(redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + finalAppId));
        long expireAt = asLong(redisTemplate.opsForValue().get(EXPIRE_AT_KEY_PREFIX + finalAppId), 0L);
        if (accessToken == null || refreshToken == null || expireAt <= 0L) {
            return null;
        }

        long expiresIn = expireAt - Instant.now().getEpochSecond();
        if (expiresIn <= 0L) {
            return null;
        }
        return new TokenPayload(accessToken, refreshToken, expiresIn, null, null, null);
    }

    @Override
    public TokenPayload refreshToken(String appId, String refreshToken) {
        logGateway(appId);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildTokenPayload(appId, refreshToken);
        }
        DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.refreshToken(refreshToken);
        return new TokenPayload(
                payload.accessToken(),
                payload.refreshToken(),
                payload.expiresIn(),
                payload.authorityId(),
                payload.authSubjectType(),
                payload.tokenType()
        );
    }

    @Override
    public TokenPayload createToken(TokenCreateCommand command) {
        logGateway(null);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildTokenPayload(null, null);
        }
        DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.createToken(
                new DoudianTokenGateway.TokenCreateCommand(
                        command.authorizationCode(),
                        command.grantType(),
                        command.testShop(),
                        command.shopId(),
                        command.authId(),
                        command.authSubjectType()
                )
        );
        return new TokenPayload(
                payload.accessToken(),
                payload.refreshToken(),
                payload.expiresIn(),
                payload.authorityId(),
                payload.authSubjectType(),
                payload.tokenType()
        );
    }

    private void logGateway(String appId) {
        log.info(
                "gateway=RealDouyinAuthGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(appId == null ? contractFixtureProvider.appKey() : appId),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String resolveAppId(String appId) {
        String explicitAppId = asTrimmedString(appId);
        if (explicitAppId != null) {
            return explicitAppId;
        }
        String clientKey = asTrimmedString(douyinConfig.getClientKey());
        if (clientKey != null) {
            return clientKey;
        }
        String configuredAppId = asTrimmedString(douyinConfig.getAppId());
        if (configuredAppId != null) {
            return configuredAppId;
        }
        return contractFixtureProvider.appKey();
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
