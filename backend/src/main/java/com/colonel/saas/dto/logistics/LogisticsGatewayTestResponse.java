package com.colonel.saas.dto.logistics;

import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import lombok.Builder;
import lombok.Data;

/**
 * 物流网关测试响应 DTO。
 * <p>
 * 返回物流网关查询测试结果，包含查询是否成功、供应商信息、状态、消息以及是否保存了原始响应数据。
 * 关联业务领域：物流域（Logistics）。
 * </p>
 */
@Data
@Builder
public class LogisticsGatewayTestResponse {
    /** 查询是否成功 */
    private boolean success;
    /** 物流服务供应商标识 */
    private String provider;
    /** 网关健康状态 */
    private LogisticsGatewayHealthStatus status;
    /** 测试结果描述信息 */
    private String message;
    /** 是否已保存原始响应载荷 */
    private boolean rawPayloadStored;
}
