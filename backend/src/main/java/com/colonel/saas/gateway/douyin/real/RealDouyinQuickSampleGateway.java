package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抖店官方 quick_sample_apply 接口的真实环境实现。
 *
 * <p>功能描述：当前抖音 buyin SDK / 仓库未提供 quick_sample_apply 对应的寄样申请 API，
 * 因此真实环境实现固定返回 {@code UNSUPPORTED_BY_SDK} 状态。
 * 严格禁止在该实现中伪造上游调用或模拟返回结果。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>real 环境（app.test.enabled=false）：激活此实现</li>
 *   <li>test 环境（app.test.enabled=true）：由 {@link MockDouyinQuickSampleGateway} 接管</li>
 *   <li>当抖音官方后续开放该 API 时，需替换为真实 HTTP 调用实现</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：寄样域 / 抖音寄样申请适配层</p>
 *
 * @see DouyinQuickSampleGateway
 * @see MockDouyinQuickSampleGateway
 */
@Component
@ConditionalOnProperty(name = "app.test.enabled", havingValue = "false")
public class RealDouyinQuickSampleGateway implements DouyinQuickSampleGateway {

    /**
     * 执行寄样申请（当前不可用）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>直接返回失败结果，状态码为 UNSUPPORTED_BY_SDK</li>
     *   <li>不执行任何外部 HTTP 请求</li>
     *   <li>在 extraInfo 中标注 upstream=NOT_IMPLEMENTED 和 fallbackType=LOCAL_FALLBACK</li>
     * </ol>
     *
     * @param command 寄样申请命令（当前未使用，保留接口签名一致性）
     * @return 固定返回失败结果，success=false，errorCode=UNSUPPORTED_BY_SDK
     */
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

    /**
     * 查询是否支持寄样申请。
     *
     * @return 固定返回 {@code false}，表示当前 SDK 不支持该功能
     */
    @Override
    public boolean isSupported() {
        return false;
    }

    /**
     * 返回支持状态枚举。
     *
     * @return 固定返回 {@link SupportStatus#UNSUPPORTED_BY_SDK}
     */
    @Override
    public SupportStatus supportStatus() {
        return SupportStatus.UNSUPPORTED_BY_SDK;
    }
}
