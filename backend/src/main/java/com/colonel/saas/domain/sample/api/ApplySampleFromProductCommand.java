package com.colonel.saas.domain.sample.api;

import java.util.List;
import java.util.UUID;

/**
 * 商品域快速寄样命令 — 由商品域构建，传递给 {@link SampleApplicationPort}。
 * <p>
 * 包含寄样域完成寄样创建所需的全部上下文：商品快照信息、操作者信息、
 * 收件信息、外部网关状态等。寄样域不依赖商品域的任何 Mapper 或服务。
 * </p>
 *
 * @param relationId           商品 relationId（product_snapshot.id / product.id）
 * @param productId            外部商品 ID（product.product_id，如抖音商品 ID）
 * @param channelId            渠道部门 ID（deptId）
 * @param talentIds            达人外部 ID 列表（抖音 UID）
 * @param spec                 规格描述
 * @param quantity             寄样数量
 * @param remark               备注
 * @param receiverName         收货人姓名
 * @param receiverPhone        收货人电话
 * @param receiverAddress      收货地址
 * @param requestSource        申请来源标识（如 product_quick_sample）
 * @param userId               操作用户 ID
 * @param channelUserId        渠道归属用户 ID
 * @param roleCodes            操作用户角色集合
 * @param productSnapshotTitle 商品快照标题（用作寄样单商品名称）
 * @param productSnapshotPrice 商品快照价格（分）
 * @param activityId           活动 ID（用于寄样领域事件）
 * @param assigneeId           商品负责人 ID（用于寄样领域事件）
 * @param externalEnabled      外部网关配置开关
 * @param externalSupported    外部网关 SDK 是否支持
 * @param skuId                SKU ID
 */
public record ApplySampleFromProductCommand(
        UUID relationId,
        String productId,
        UUID channelId,
        List<String> talentIds,
        String spec,
        int quantity,
        String remark,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String requestSource,
        UUID userId,
        UUID channelUserId,
        Object roleCodes,
        String productSnapshotTitle,
        Long productSnapshotPrice,
        String activityId,
        UUID assigneeId,
        boolean externalEnabled,
        boolean externalSupported,
        String skuId
) {
}
