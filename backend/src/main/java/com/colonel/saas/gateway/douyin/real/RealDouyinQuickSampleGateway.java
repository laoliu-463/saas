package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抖店官方 quick_sample_apply：当前 SDK/仓库未提供对应 buyin 寄样申请接口。
 * Real 实现仅返回明确 UNSUPPORTED，禁止伪造上游调用。
 */
@Component
@ConditionalOnProperty(name = "app.test.enabled", havingValue = "false")
public class RealDouyinQuickSampleGateway implements DouyinQuickSampleGateway {

    @Override
    public QuickSampleApplyResult apply(QuickSampleApplyCommand command) {
        return new QuickSampleApplyResult(
                false,
                null,
                "UNSUPPORTED_BY_SDK",
                "UNSUPPORTED_BY_SDK",
                "Douyin buyin quick_sample_apply API not available in current SDK integration",
                Map.of("upstream", "NOT_IMPLEMENTED", "fallbackType", "LOCAL_FALLBACK"));
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public SupportStatus supportStatus() {
        return SupportStatus.UNSUPPORTED_BY_SDK;
    }
}
