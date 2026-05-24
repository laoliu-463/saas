package com.colonel.saas.gateway.douyin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.test.enabled", havingValue = "true", matchIfMissing = true)
public class MockDouyinQuickSampleGateway implements DouyinQuickSampleGateway {

    @Override
    public QuickSampleApplyResult apply(QuickSampleApplyCommand command) {
        if (!isSupported()) {
            return new QuickSampleApplyResult(
                    false,
                    null,
                    "UNSUPPORTED",
                    "MOCK_UNSUPPORTED",
                    "Mock gateway: external quick_sample_apply not enabled",
                    Map.of("mock", true));
        }
        return new QuickSampleApplyResult(
                true,
                "MOCK-APPLY-" + command.talentId(),
                "SUBMITTED",
                null,
                null,
                Map.of("mock", true, "command", command.productId()));
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public SupportStatus supportStatus() {
        return SupportStatus.MOCK_ONLY;
    }
}
