package com.colonel.saas.gateway.logistics.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 物流查询统一结果 DTO。
 * <p>
 * 由 {@link LogisticsQueryGateway} 实现返回，封装物流轨迹查询的完整结果。
 * 业务层通过此对象获取物流状态、轨迹列表和签收时间等信息。
 * </p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>success=false 时，errorCode + errorMessage 提供失败原因</li>
 *   <li>signed=true 时，signedAt 表示签收时间</li>
 *   <li>rawPayload 保留上游原始响应，用于问题排查</li>
 * </ul>
 */
@Value
@Builder
public class LogisticsQueryResult {

    /** 查询是否成功 */
    boolean success;

    /** 物流服务商标识（如 KUAIDINIAO、KUAIDI100、MOCK） */
    String provider;

    /** 物流运单号 */
    String trackingNo;

    /** 快递公司名称或编码 */
    String logisticsCompany;

    /** 统一物流状态码（屏蔽不同服务商的状态差异） */
    LogisticsStatusCode statusCode;

    /** 状态名称（人类可读，如"运输中"、"已签收"） */
    String statusName;

    /** 是否已签收 */
    boolean signed;

    /** 签收时间（未签收时为 null） */
    LocalDateTime signedAt;

    /** 物流轨迹节点列表（按时间升序排列） */
    List<LogisticsTraceItem> traces;

    /** 错误编码（success=false 时有值，如 INVALID_PARAM、NOT_CONFIGURED） */
    String errorCode;

    /** 错误消息（success=false 时有值，面向排查使用） */
    String errorMessage;

    /** 上游原始响应负载（JSON 反序列化后的 Map，用于排查和审计） */
    Map<String, Object> rawPayload;

    /** 查询时间 */
    LocalDateTime queriedAt;

    /**
     * 物流轨迹节点。
     * <p>
     * 表示物流运输过程中的一个状态变更事件，如"已揽收"、"到达中转站"、"已签收"等。
     * </p>
     */
    @Value
    @Builder
    public static class LogisticsTraceItem {
        /** 轨迹发生时间 */
        LocalDateTime traceTime;
        /** 轨迹描述（如"快件已揽收，运输中"） */
        String traceContent;
        /** 轨迹发生地点 */
        String location;
    }

    /**
     * 构建"未配置"结果。
     * <p>
     * 当物流查询 provider 的 API 凭证未配置时使用，
     * 避免因缺少凭证而返回虚假的成功结果。
     * </p>
     *
     * @param provider         服务商标识
     * @param logisticsCompany 快递公司编码
     * @param trackingNo       运单号
     * @return 未配置状态的查询结果
     */
    public static LogisticsQueryResult notConfigured(String provider, String logisticsCompany, String trackingNo) {
        return LogisticsQueryResult.builder()
                .success(false)
                .provider(provider)
                .trackingNo(trackingNo)
                .logisticsCompany(logisticsCompany)
                .statusCode(LogisticsStatusCode.NOT_CONFIGURED)
                .statusName("未配置")
                .signed(false)
                .traces(List.of())
                .errorCode("NOT_CONFIGURED")
                .errorMessage("物流查询 provider 未配置或凭证缺失")
                .rawPayload(Map.of())
                .queriedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构建"查询失败"结果。
     * <p>
     * 用于参数校验失败、上游返回错误、网络异常等场景。
     * </p>
     *
     * @param provider         服务商标识
     * @param logisticsCompany 快递公司编码
     * @param trackingNo       运单号
     * @param errorCode        错误编码（如 INVALID_PARAM、UPSTREAM_FAILED）
     * @param errorMessage     错误描述
     * @return 查询失败的结果
     */
    public static LogisticsQueryResult queryFailed(
            String provider,
            String logisticsCompany,
            String trackingNo,
            String errorCode,
            String errorMessage) {
        return LogisticsQueryResult.builder()
                .success(false)
                .provider(provider)
                .trackingNo(trackingNo)
                .logisticsCompany(logisticsCompany)
                .statusCode(LogisticsStatusCode.ERROR)
                .statusName("查询失败")
                .signed(false)
                .traces(List.of())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .rawPayload(Map.of())
                .queriedAt(LocalDateTime.now())
                .build();
    }
}
