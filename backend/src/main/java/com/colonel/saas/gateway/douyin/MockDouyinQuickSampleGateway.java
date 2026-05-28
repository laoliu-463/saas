package com.colonel.saas.gateway.douyin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抖音寄样申请（quick_sample_apply）的 Mock 实现。
 *
 * <p>功能描述：当 {@code app.test.enabled=true}（默认值，即 matchIfMissing=true）时激活，
 * 模拟寄样申请的快速提交流程。由于当前抖音 buyin SDK 未提供 quick_sample_apply 对应接口，
 * 本实现固定返回 UNSUPPORTED 状态，不执行任何真实外部调用。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>test 环境（app.test.enabled=true）：激活此 Mock 实现</li>
 *   <li>real 环境（app.test.enabled=false）：由 {@link RealDouyinQuickSampleGateway} 接管</li>
 *   <li>matchIfMissing=true：未配置该属性时默认作为 test 环境处理</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：寄样域 / 抖音寄样申请适配层</p>
 *
 * @see DouyinQuickSampleGateway
 * @see RealDouyinQuickSampleGateway
 */
@Component
@ConditionalOnProperty(name = "app.test.enabled", havingValue = "true", matchIfMissing = true)
public class MockDouyinQuickSampleGateway implements DouyinQuickSampleGateway {

    /**
     * 执行 Mock 寄样申请。
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查 isSupported()，若为 false 则返回 UNSUPPORTED + MOCK_UNSUPPORTED 错误码</li>
     *   <li>若支持（当前固定返回 false，此分支不会执行），返回模拟的 SUBMITTED 结果</li>
     * </ol>
     *
     * @param command 寄样申请命令，包含达人 ID 和商品 ID
     * @return 申请结果，固定为 UNSUPPORTED 状态
     */
    @Override
    public QuickSampleApplyResult apply(QuickSampleApplyCommand command) {
        /* 当前固定返回 UNSUPPORTED，因为 isSupported() 始终为 false */
        if (!isSupported()) {
            return new QuickSampleApplyResult(
                    false,
                    null,
                    "UNSUPPORTED",
                    "MOCK_UNSUPPORTED",
                    "Mock gateway: external quick_sample_apply not enabled",
                    Map.of("mock", true));
        }
        /* 以下代码为预留逻辑，当前不可达 */
        return new QuickSampleApplyResult(
                true,
                "MOCK-APPLY-" + command.talentId(),
                "SUBMITTED",
                null,
                null,
                Map.of("mock", true, "command", command.productId()));
    }

    /**
     * 查询 Mock 网关是否支持寄样申请。
     *
     * @return 固定返回 {@code false}，表示当前不支持
     */
    @Override
    public boolean isSupported() {
        return false;
    }

    /**
     * 返回 Mock 网关的支持状态。
     *
     * @return 固定返回 {@link SupportStatus#MOCK_ONLY}
     */
    @Override
    public SupportStatus supportStatus() {
        return SupportStatus.MOCK_ONLY;
    }
}
