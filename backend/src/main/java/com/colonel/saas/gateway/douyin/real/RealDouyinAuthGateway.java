package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.gateway.douyin.DouyinAuthGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "douyin.mock.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinAuthGateway implements DouyinAuthGateway {

    private final DoudianTokenGateway doudianTokenGateway;

    public RealDouyinAuthGateway(DoudianTokenGateway doudianTokenGateway) {
        this.doudianTokenGateway = doudianTokenGateway;
    }

    @Override
    public TokenPayload ensureToken(String appId) {
        return null;
    }

    @Override
    public TokenPayload refreshToken(String appId, String refreshToken) {
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
}
