package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinConfig;
import com.colonel.saas.douyin.api.InstitutionApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinTokenGateway implements DouyinTokenGateway {

    private static final String TOKEN_KEY_PREFIX = "douyin:token:";
    private static final String REFRESH_KEY_PREFIX = "douyin:refresh:";
    private static final String EXPIRE_AT_KEY_PREFIX = "douyin:token:expire_at:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DouyinConfig douyinConfig;
    private final DoudianTokenGateway doudianTokenGateway;
    private final InstitutionApi institutionApi;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinTokenGateway(
            RedisTemplate<String, Object> redisTemplate,
            DouyinConfig douyinConfig,
            DoudianTokenGateway doudianTokenGateway,
            @Lazy
            InstitutionApi institutionApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.redisTemplate = redisTemplate;
        this.douyinConfig = douyinConfig;
        this.doudianTokenGateway = doudianTokenGateway;
        this.institutionApi = institutionApi;
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

    @Override
    public Map<String, Object> institutionInfo(String appId) {
        logGateway(appId);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildInstitutionInfoResponse(appId);
        }
        return institutionApi.info(appId);
    }

    @Override
    public ProbeTokenCreateResult probeCreateToken(TokenCreateCommand command) {
        logGateway(null);
        if (upstreamModeSupport.isContract()) {
            return new ProbeTokenCreateResult(
                    command.grantType(),
                    command.authorizationCode() == null || command.authorizationCode().isBlank() ? "absent" : "present",
                    command.testShop(),
                    command.shopId(),
                    command.authId() != null && !command.authId().isBlank(),
                    command.authSubjectType(),
                    new TokenProbeResponseView(
                            "10000",
                            "success",
                            null,
                            null,
                            "****",
                            "****",
                            7200L,
                            contractFixtureProvider.authId(),
                            "Colonel",
                            1L
                    )
            );
        }
        DoudianTokenGateway.TokenCreateProbeResult probe = doudianTokenGateway.probeCreateToken(
                new DoudianTokenGateway.TokenCreateCommand(
                        command.authorizationCode(),
                        command.grantType(),
                        command.testShop(),
                        command.shopId(),
                        command.authId(),
                        command.authSubjectType()
                ));
        DoudianTokenGateway.TokenCreateResponseView v = probe.responseView();
        TokenProbeResponseView view = new TokenProbeResponseView(
                v.code(),
                v.msg(),
                v.subCode(),
                v.subMsg(),
                v.maskedAccessToken(),
                v.maskedRefreshToken(),
                v.expiresIn(),
                v.authorityId(),
                v.authSubjectType(),
                v.tokenType()
        );
        return new ProbeTokenCreateResult(
                probe.grantType(),
                probe.codeState(),
                probe.testShop(),
                probe.shopId(),
                probe.authIdPresent(),
                probe.authSubjectType(),
                view
        );
    }

    private void logGateway(String appId) {
        log.info(
                "gateway=RealDouyinTokenGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
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
