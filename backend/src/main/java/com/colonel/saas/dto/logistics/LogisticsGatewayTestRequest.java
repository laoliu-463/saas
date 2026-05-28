package com.colonel.saas.dto.logistics;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 物流网关测试请求 DTO。
 * <p>
 * 用于手动测试物流网关查询能力，通过指定物流供应商、快递公司和运单号进行物流轨迹查询验证。
 * 关联业务领域：物流域（Logistics）。
 * </p>
 */
@Data
public class LogisticsGatewayTestRequest {
    /** 物流服务供应商标识，不能为空 */
    @NotBlank
    private String provider;
    /** 快递公司编码，不能为空 */
    @NotBlank
    private String logisticsCompany;
    /** 运单号，不能为空 */
    @NotBlank
    private String trackingNo;
    /** 收件人手机号（部分物流查询接口需要） */
    private String phone;
    /** 发货地 */
    private String from;
    /** 目的地 */
    private String to;
}
