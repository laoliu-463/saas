package com.colonel.saas.gateway.logistics.kdniao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 快递鸟即时查询API响应
 * 来源：https://www.kdniao.com/api-track
 */
@Data
public class KdniaoResponse {

    /**
     * 用户ID
     */
    @JsonProperty("EBusinessID")
    private String eBusinessId;

    /**
     * 订单编号
     */
    @JsonProperty("OrderCode")
    private String orderCode;

    /**
     * 快递公司编码
     */
    @JsonProperty("ShipperCode")
    private String shipperCode;

    /**
     * 物流运单号
     */
    @JsonProperty("LogisticCode")
    private String logisticCode;

    /**
     * 成功与否
     */
    @JsonProperty("Success")
    private Boolean success;

    /**
     * 失败原因
     */
    @JsonProperty("Reason")
    private String reason;

    /**
     * 物流状态：2-在途中, 3-签收, 4-问题件
     */
    @JsonProperty("State")
    private String state;

    /**
     * 物流轨迹节点列表
     */
    @JsonProperty("Traces")
    private List<LogisticsTrace> traces;

    /**
     * 物流轨迹节点
     * 来源：https://www.kdniao.com/api-track
     */
    @Data
    public static class LogisticsTrace {

        /**
         * 时间（轨迹发生时间，格式：YYYY/MM/DD HH:mm:ss）
         */
        @JsonProperty("AcceptTime")
        private String acceptTime;

        /**
         * 描述（轨迹发生地点/状态描述）
         */
        @JsonProperty("AcceptStation")
        private String acceptStation;

        /**
         * 备注（附加说明，可为空）
         */
        @JsonProperty("Remark")
        private String remark;
    }

    /**
     * 物流状态枚举
     */
    public enum LogisticsState {
        /** 在途中 */
        IN_TRANSIT("2", "IN_TRANSIT"),
        /** 签收 */
        SIGNED("3", "SIGNED"),
        /** 问题件 */
        EXCEPTION("4", "EXCEPTION");

        private final String code;
        private final String status;

        LogisticsState(String code, String status) {
            this.code = code;
            this.status = status;
        }

        public String getCode() {
            return code;
        }

        public String getStatus() {
            return status;
        }

        public static LogisticsState fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (LogisticsState state : values()) {
                if (state.code.equals(code)) {
                    return state;
                }
            }
            return null;
        }
    }
}
