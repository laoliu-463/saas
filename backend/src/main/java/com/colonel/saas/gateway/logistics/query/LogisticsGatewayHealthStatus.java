package com.colonel.saas.gateway.logistics.query;

/**
 * 物流网关健康状态枚举。
 * <p>
 * 用于监控物流查询网关的可用性状态，在运维看板上展示。
 * </p>
 */
public enum LogisticsGatewayHealthStatus {

    /** 仅 Mock 模式（test 环境，不请求真实 API） */
    MOCK_ONLY,

    /** 未配置（缺少 API 凭证或配置项） */
    NOT_CONFIGURED,

    /** 就绪（凭证已配置，等待首次调用） */
    READY,

    /** 沙箱通过（沙箱环境调用成功） */
    SANDBOX_PASSED,

    /** 真实连接成功（生产环境调用成功） */
    REAL_CONNECTED,

    /** 调用失败（网络超时、凭证无效等） */
    FAILED
}
