package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeCommand;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeResult;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenProbeResponseView;
import com.colonel.saas.domain.shared.application.port.DouyinTokenDiagnosticPort;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抖音 Token 联调诊断端口的 Gateway 适配器。
 */
@Component
public class DouyinTokenDiagnosticGatewayAdapter implements DouyinTokenDiagnosticPort {

    private final DouyinTokenGateway douyinTokenGateway;

    public DouyinTokenDiagnosticGatewayAdapter(DouyinTokenGateway douyinTokenGateway) {
        this.douyinTokenGateway = douyinTokenGateway;
    }

    @Override
    public Map<String, Object> institutionInfo(String appId) {
        return douyinTokenGateway.institutionInfo(appId);
    }

    @Override
    public DouyinTokenCreateProbeResult probeCreateToken(DouyinTokenCreateProbeCommand command) {
        DouyinTokenGateway.ProbeTokenCreateResult result = douyinTokenGateway.probeCreateToken(
                new DouyinTokenGateway.TokenCreateCommand(
                        command.authorizationCode(),
                        command.grantType(),
                        command.testShop(),
                        command.shopId(),
                        command.authId(),
                        command.authSubjectType()));
        return new DouyinTokenCreateProbeResult(
                result.grantType(),
                result.codeState(),
                result.testShop(),
                result.shopId(),
                result.authIdPresent(),
                result.authSubjectType(),
                toResponseView(result.response()));
    }

    private DouyinTokenProbeResponseView toResponseView(DouyinTokenGateway.TokenProbeResponseView response) {
        if (response == null) {
            return null;
        }
        return new DouyinTokenProbeResponseView(
                response.code(),
                response.msg(),
                response.subCode(),
                response.subMsg(),
                response.maskedAccessToken(),
                response.maskedRefreshToken(),
                response.expiresIn(),
                response.authorityId(),
                response.authSubjectType(),
                response.tokenType());
    }
}
