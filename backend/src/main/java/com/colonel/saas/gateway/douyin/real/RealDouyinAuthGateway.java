package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinAuthGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinAuthGateway implements DouyinAuthGateway {

    private final DoudianTokenGateway doudianTokenGateway;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinAuthGateway(
            DoudianTokenGateway doudianTokenGateway,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.doudianTokenGateway = doudianTokenGateway;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public TokenPayload ensureToken(String appId) {
        return null;
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
}
