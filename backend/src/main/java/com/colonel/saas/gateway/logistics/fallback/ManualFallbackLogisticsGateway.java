package com.colonel.saas.gateway.logistics.fallback;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 物流网关手动回退实现。
 *
 * <p>功能描述：当系统中未配置任何真实物流服务商（如快递鸟、快递100）时，
 * 由 {@link ManualFallbackLogisticsGatewayConfig} 通过 {@code @ConditionalOnMissingBean} 自动注入。
 * 所有物流操作均返回"未配置"状态，创建发货操作直接抛出业务异常，
 * 提醒运营人员通过线下方式手动录入物流信息。</p>
 *
 * <p>环境说明：此实现在任何环境均可作为兜底策略生效，只要没有其他
 * {@link LogisticsGateway} Bean 注册（如 Kdniao 或 Kuaidi100 未启用）。</p>
 *
 * <p>所属业务领域：寄样域 / 物流适配层</p>
 *
 * @see LogisticsGateway
 * @see ManualFallbackLogisticsGatewayConfig
 */
public class ManualFallbackLogisticsGateway implements LogisticsGateway {

    /** 快递公司编码占位符，表示人工处理 */
    private static final String COMPANY_MANUAL = "MANUAL";

    /** 状态码：物流网关未配置 */
    private static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";

    /** 状态说明信息：提示保持人工跟进 */
    private static final String REASON_NOT_CONFIGURED = "物流网关未配置，请保持人工跟进";

    /**
     * 创建发货单（不支持，直接抛出异常）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>直接抛出 {@link BusinessException}，提示"物流网关未配置，请手动录入物流信息"</li>
     *   <li>不执行任何外部 API 调用</li>
     * </ol>
     *
     * @param command 发货命令（包含寄样请求 ID、商品 ID、收件人信息等）
     * @return 永不返回，总是抛出异常
     * @throws BusinessException 物流网关未配置时抛出
     */
    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        throw BusinessException.stateInvalid("物流网关未配置，请手动录入物流信息");
    }

    /**
     * 查询物流状态（已废弃，返回未配置占位结果）。
     *
     * @param trackingNo 快递单号
     * @return 包含 NOT_CONFIGURED 状态的占位结果
     * @deprecated 快递鸟等物流服务商需要快递公司编码，请使用 {@link #queryTrack(String, String)}
     */
    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        return new LogisticsStatusResult(
                trackingNo,
                COMPANY_MANUAL,
                STATUS_NOT_CONFIGURED,
                REASON_NOT_CONFIGURED,
                LocalDateTime.now()
        );
    }

    /**
     * 查询物流轨迹（返回未配置占位结果）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若传入的 companyCode 有值则使用，否则降级为 "MANUAL"</li>
     *   <li>返回 NOT_CONFIGURED 状态，success=false，无轨迹节点</li>
     *   <li>在 rawResponse 中标注 provider=manual-fallback, configured=false</li>
     * </ol>
     *
     * @param companyCode 快递公司编码（可为空）
     * @param trackingNo  快递单号
     * @return 包含 NOT_CONFIGURED 状态的占位轨迹结果，轨迹节点列表为空
     */
    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        String normalizedCompany = StringUtils.hasText(companyCode) ? companyCode : COMPANY_MANUAL;
        return new LogisticsTrackResult(
                normalizedCompany,
                trackingNo,
                false,
                REASON_NOT_CONFIGURED,
                null,
                STATUS_NOT_CONFIGURED,
                false,
                null,
                List.of(),
                Map.of("provider", "manual-fallback", "configured", false)
        );
    }
}
