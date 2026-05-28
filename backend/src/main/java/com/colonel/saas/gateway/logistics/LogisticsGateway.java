package com.colonel.saas.gateway.logistics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 物流网关统一接口。
 *
 * <p>功能描述：定义物流操作的标准契约，包括创建发货单、订阅物流轨迹、查询物流状态与轨迹。
 * 具体实现由各物流服务商 Gateway 提供（如快递鸟 {@link com.colonel.saas.gateway.logistics.kdniao.KdniaoLogisticsGateway}、
 * 快递100 {@link com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway}），
 * 或由兜底实现 {@link com.colonel.saas.gateway.logistics.fallback.ManualFallbackLogisticsGateway} 处理。</p>
 *
 * <p>环境说明：接口本身不区分环境，通过不同实现类配合 Spring {@code @ConditionalOnProperty}
 * 在 real / test 环境中选择对应的具体实现。</p>
 *
 * <p>所属业务领域：寄样域 / 物流适配层</p>
 *
 * @see com.colonel.saas.gateway.logistics.kdniao.KdniaoLogisticsGateway
 * @see com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway
 * @see com.colonel.saas.gateway.logistics.fallback.ManualFallbackLogisticsGateway
 */
public interface LogisticsGateway {

    /**
     * 创建发货单。
     *
     * @param command 发货命令，包含寄样请求 ID、商品 ID、收件人信息
     * @return 发货结果，包含快递单号、快递公司、发货时间等
     */
    LogisticsResult createShipment(LogisticsCommand command);

    /**
     * 订阅物流轨迹推送。
     *
     * <p>默认实现抛出 UnsupportedOperationException，需物流服务商（如快递100）覆盖。</p>
     *
     * @param command 订阅命令，包含快递公司编码、单号、回调地址等
     * @return 订阅结果
     * @throws UnsupportedOperationException 当前网关不支持轨迹订阅时抛出
     */
    default LogisticsSubscribeResult subscribeTrack(LogisticsSubscribeCommand command) {
        throw new UnsupportedOperationException("当前物流网关不支持轨迹订阅");
    }

    /**
     * 查询物流轨迹（需指定快递公司编码）。
     *
     * @param companyCode 快递公司编码（如 SF、ZTO、YD 等）
     * @param trackingNo  快递单号
     * @return 物流轨迹结果，包含签收状态、轨迹节点列表、原始响应等
     */
    LogisticsTrackResult queryTrack(String companyCode, String trackingNo);

    /**
     * 查询物流轨迹（接受 {@link LogisticsTrackCommand} 对象，含更多可选参数）。
     *
     * <p>默认实现委托给 {@link #queryTrack(String, String)}，具体实现可覆盖以使用额外参数
     * （如手机号、出发地/目的地等）。</p>
     *
     * @param command 物流轨迹查询命令
     * @return 物流轨迹结果
     */
    default LogisticsTrackResult queryTrack(LogisticsTrackCommand command) {
        if (command == null) {
            return queryTrack(null, null);
        }
        return queryTrack(command.getCompanyCode(), command.getTrackingNo());
    }

    /**
     * 发货命令：封装创建发货单所需的全部信息。
     */
    record LogisticsCommand(
            /** 寄样请求 ID */
            UUID sampleRequestId,
            /** 抖音商品 ID */
            String productId,
            /** 收件人姓名 */
            String recipientName,
            /** 收件人手机号 */
            String recipientPhone,
            /** 收件人详细地址 */
            String recipientAddress) {
    }

    /**
     * 发货结果：创建发货单后返回的快递信息。
     */
    record LogisticsResult(
            /** 快递单号 */
            String trackingNo,
            /** 快递公司名称 */
            String company,
            /** 发货状态 */
            String status,
            /** 发货时间 */
            LocalDateTime shipTime) {
    }

    record LogisticsStatusResult(
            /** 快递单号 */
            String trackingNo,
            /** 快递公司编码 */
            String company,
            /** 物流状态码 */
            String status,
            /** 状态说明信息 */
            String message,
            /** 最后更新时间 */
            LocalDateTime updateTime) {
    }

    /**
     * 物流轨迹查询结果：包含完整的轨迹节点列表与签收信息。
     */
    record LogisticsTrackResult(
            /** 快递公司编码 */
            String companyCode,
            /** 快递单号 */
            String trackingNo,
            /** 查询是否成功 */
            boolean success,
            /** 失败原因（成功时为 null） */
            String reason,
            /** 上游返回的原始状态（如 Kdniao 的 State 字段） */
            String externalState,
            /** 内部统一状态码（如 IN_TRANSIT、SIGNED、EXCEPTION 等） */
            String internalStatus,
            /** 是否已签收 */
            boolean signed,
            /** 签收时间（未签收时为 null） */
            LocalDateTime signedAt,
            /** 轨迹节点列表，按时间升序排列 */
            List<LogisticsTraceNode> traces,
            /** 上游原始响应数据（用于排查问题） */
            Map<String, Object> rawResponse) {
    }

    /**
     * 物流轨迹节点：代表一个物流扫描/中转事件。
     */
    record LogisticsTraceNode(
            /** 节点发生时间 */
            LocalDateTime acceptTime,
            /** 节点所在站点/位置 */
            String acceptStation,
            /** 节点说明（如"已揽收"、"运输中"、"已签收"等） */
            String remark) {
    }
}
