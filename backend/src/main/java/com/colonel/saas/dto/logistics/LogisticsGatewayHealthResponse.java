package com.colonel.saas.dto.logistics;

import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流网关健康检查响应 DTO。
 * <p>
 * 返回物流网关的健康状态，包括供应商名称、是否启用、是否已配置、当前状态及检查时间。
 * 用于运维监控页面展示物流服务可用性。
 * 关联业务领域：物流域（Logistics）。
 * </p>
 */
@Data
@Builder
public class LogisticsGatewayHealthResponse {
    /** 物流服务供应商名称 */
    private String provider;
    /** 该供应商是否已启用 */
    private boolean enabled;
    /** 该供应商是否已完成配置（如 API Key 等） */
    private boolean configured;
    /** 健康状态枚举 */
    private LogisticsGatewayHealthStatus status;
    /** 状态描述信息 */
    private String message;
    /** 检查时间 */
    private LocalDateTime checkedAt;
}
