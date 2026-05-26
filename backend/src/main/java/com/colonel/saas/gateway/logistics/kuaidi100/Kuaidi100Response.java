package com.colonel.saas.gateway.logistics.kuaidi100;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 快递100即时查询API响应
 * 文档：https://www.kuaidi100.com/openapi/
 *
 * 响应字段说明：
 * - status: 通讯状态（200=成功）
 * - message: 状态信息
 * - com: 快递公司编码
 * - nu: 运单号
 * - state: 快递单当前状态，0=在途，3=签收，5=派件等
 * - data: 物流轨迹列表（按时间升序）
 */
@Data
public class Kuaidi100Response {

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("com")
    private String companyCode;

    @JsonProperty("nu")
    private String trackingNo;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("ischeck")
    private String isCheck;

    @JsonProperty("state")
    private String state;

    @JsonProperty("data")
    private List<TraceNode> data;

    @Data
    public static class TraceNode {

        @JsonProperty("time")
        private String time;

        @JsonProperty("ftime")
        private String formattedTime;

        @JsonProperty("context")
        private String context;

        @JsonProperty("location")
        private String location;

        @JsonProperty("status")
        private String status;

        @JsonProperty("statusCode")
        private String statusCode;
    }
}
