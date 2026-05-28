package com.colonel.saas.gateway.logistics;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * 物流轨迹订阅命令对象。
 * <p>
 * 用于向第三方物流服务商（如快递100）发起物流轨迹订阅请求，
 * 以便在物流状态变更时通过回调接口通知系统。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>寄样订单发货后，订阅物流轨迹以自动跟踪签收状态</li>
 *   <li>快递100订阅接口需要 companyCode + trackingNo + phone 等参数</li>
 * </ul>
 */
@Value
@Builder
public class LogisticsSubscribeCommand {

    /** 关联的寄样申请 ID，用于将订阅结果回溯到业务记录 */
    UUID sampleRequestId;

    /** 快递公司编码（如 SF=顺丰、ZTO=中通），不同服务商编码规范不同 */
    String companyCode;

    /** 物流运单号 */
    String trackingNo;

    /** 收件人/寄件人手机号（部分快递公司查询/订阅时需要，如顺丰、中通） */
    String phone;

    /** 寄件地址（可选，部分服务商支持地址辅助识别） */
    String from;

    /** 收件地址（可选，部分服务商支持地址辅助识别） */
    String to;
}
