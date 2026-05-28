package com.colonel.saas.gateway.logistics.query;

/**
 * 物流查询统一状态码枚举。
 * <p>
 * 将不同第三方物流服务商的状态码映射为统一的内部状态码，
 * 屏蔽快递鸟、快递100 等状态定义差异。
 * </p>
 *
 * <h3>状态流转</h3>
 * <pre>
 *   CREATED -> IN_TRANSIT -> DELIVERING -> SIGNED
 *                                    \-> REJECTED / EXCEPTION
 *   查询失败时: FAILED / ERROR
 *   凭证未配置: NOT_CONFIGURED
 * </pre>
 */
public enum LogisticsStatusCode {

    /** 未知状态（上游返回未识别的状态码） */
    UNKNOWN,

    /** 已创建（物流单已生成但尚未揽收） */
    CREATED,

    /** 运输中（快递已揽收，在途运输） */
    IN_TRANSIT,

    /** 派送中（到达收件城市，正在派件） */
    DELIVERING,

    /** 已签收（收件人已签收） */
    SIGNED,

    /** 查询失败（上游 API 返回失败） */
    FAILED,

    /** 已拒收 / 问题件（收件人拒收或物流异常） */
    REJECTED,

    /** 系统错误（参数错误、网络异常等） */
    ERROR,

    /** 未配置（物流查询 provider 凭证缺失） */
    NOT_CONFIGURED
}
