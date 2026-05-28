package com.colonel.saas.gateway.logistics;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 物流轨迹订阅结果。
 * <p>
 * 封装第三方物流服务商订阅接口的响应结果。
 * 快递100订阅成功的 returnCode 为 "200" 或 "501"（已订阅），其余视为失败。
 * </p>
 */
@Value
@Builder
public class LogisticsSubscribeResult {

    /** 物流服务商标识（如 KUAIDI100） */
    String provider;

    /** 快递公司编码 */
    String companyCode;

    /** 物流运单号 */
    String trackingNo;

    /** 订阅是否成功（快递100: returnCode 为 200 或 501 时为 true） */
    boolean success;

    /** 服务商返回码（如 "200"=成功, "501"=已订阅） */
    String returnCode;

    /** 服务商返回消息（如 "订阅成功"、"已订阅"） */
    String message;

    /** 服务商原始响应（JSON 反序列化后的 Map，用于排查问题） */
    Map<String, Object> rawResponse;
}
