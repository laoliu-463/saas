package com.colonel.saas.gateway.douyin;

import java.util.Map;

/**
 * 抖音快速寄样申请 Gateway 接口。
 * <p>
 * 封装抖店快速寄样申请能力。该功能依赖抖店 SDK 特定版本，
 * 不同实现对支持程度有差异，通过 {@link #supportStatus()} 查询实际可用性。
 * </p>
 *
 * <h3>实现切换</h3>
 * <p>
 * <ul>
 *   <li>Real 实现（{@link com.colonel.saas.gateway.douyin.real.RealDouyinQuickSampleGateway}）
 *       当前返回 UNSUPPORTED_BY_SDK（SDK 暂未支持）</li>
 *   <li>Mock 实现（{@link com.colonel.saas.gateway.douyin.MockDouyinQuickSampleGateway}）
 *       默认 Mock，返回成功结果</li>
 *   <li>Test 实现走 Mock 通道</li>
 * </ul>
 * </p>
 */
public interface DouyinQuickSampleGateway {

    /**
     * 提交快速寄样申请。
     *
     * @param command 寄样申请命令（含商品、SKU、收件人等信息）
     * @return 申请结果（含外部申请 ID、状态、错误信息等）
     */
    QuickSampleApplyResult apply(QuickSampleApplyCommand command);

    /**
     * 当前实现是否支持快速寄样。
     *
     * @return true 表示可用，false 表示不支持
     */
    boolean isSupported();

    /**
     * 获取快速寄样功能的支持状态。
     * <p>
     * 比 {@link #isSupported()} 提供更细粒度的状态信息，便于业务层做差异化处理。
     * </p>
     *
     * @return 支持状态枚举
     */
    SupportStatus supportStatus();

    /**
     * 快速寄样功能支持状态枚举。
     * <p>
     * 用于标识当前实现对快速寄样功能的支持程度。
     * </p>
     */
    enum SupportStatus {
        /** 真实 SDK 已连接且可用 */
        REAL_CONNECTED,
        /** SDK 版本暂不支持快速寄样 */
        UNSUPPORTED_BY_SDK,
        /** 缺少快速寄样所需权限授权 */
        NOT_AUTHORIZED,
        /** 仅 Mock 模式可用（test 环境） */
        MOCK_ONLY,
        /** 功能已禁用 */
        DISABLED
    }

    /**
     * 快速寄样申请命令。
     * <p>
     * 封装提交寄样申请所需的全部参数，包括商品信息和收件人信息。
     * </p>
     *
     * @param relationId      关系 ID（达人与商品的关联标识）
     * @param productId       商品 ID
     * @param activityId      活动 ID
     * @param talentId        达人 ID
     * @param skuId           SKU ID
     * @param spec            规格信息
     * @param quantity        申请数量
     * @param receiverName    收件人姓名
     * @param receiverPhone   收件人电话
     * @param receiverAddress 收件地址
     * @param remark          备注
     * @param channelUserId   渠道用户 ID
     */
    record QuickSampleApplyCommand(
            String relationId,
            String productId,
            String activityId,
            String talentId,
            String skuId,
            String spec,
            int quantity,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String remark,
            String channelUserId) {
    }

    /**
     * 快速寄样申请结果。
     *
     * @param success       申请是否成功
     * @param externalApplyId 外部申请 ID（抖店侧标识）
     * @param externalStatus  外部状态（抖店侧申请状态）
     * @param errorCode       错误编码（失败时有值）
     * @param errorMessage    错误消息（失败时有值）
     * @param rawPayload      上游原始响应数据（用于排查）
     */
    record QuickSampleApplyResult(
            boolean success,
            String externalApplyId,
            String externalStatus,
            String errorCode,
            String errorMessage,
            Map<String, Object> rawPayload) {
    }
}
