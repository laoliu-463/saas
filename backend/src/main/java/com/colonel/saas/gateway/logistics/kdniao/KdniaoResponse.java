package com.colonel.saas.gateway.logistics.kdniao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 快递鸟即时查询 API 响应体映射。
 *
 * <p>功能描述：对应快递鸟即时查询 API（指令 1002）的 JSON 响应结构，
 * 用于将快递鸟返回的 JSON 数据反序列化为 Java 对象。</p>
 *
 * <ul>
 *   <li>响应字段映射采用 {@code @JsonProperty} 注解，与快递鸟 API 的大驼峰命名字段一一对应</li>
 *   <li>通过 {@link LogisticsState} 枚举将快递鸟的状态码映射为内部统一状态</li>
 *   <li>轨迹节点列表 {@link LogisticsTrace} 按时间升序排列</li>
 * </ul>
 *
 * <p>在架构中的角色：寄样域 / 物流适配层 / 快递鸟响应 DTO，
 * 仅供 {@link KdniaoLogisticsGateway} 内部使用，不暴露给业务层。</p>
 *
 * @see KdniaoLogisticsGateway   使用此类进行响应解析的网关实现
 * @see <a href="https://www.kdniao.com/api-track">快递鸟即时查询 API 文档</a>
 */
@Data
public class KdniaoResponse {

    /**
     * 商户用户 ID（EBusinessID）。
     * <p>与请求中的 EBusinessID 对应，标识发起查询的商户身份。</p>
     */
    @JsonProperty("EBusinessID")
    private String eBusinessId;

    /**
     * 订单编号（OrderCode）。
     * <p>可选字段，与请求中的 OrderCode 对应，用于业务关联。即时查询场景通常为空。</p>
     */
    @JsonProperty("OrderCode")
    private String orderCode;

    /**
     * 快递公司编码（ShipperCode）。
     * <p>快递鸟标准编码，如 SF（顺丰）、ZTO（中通）、YTO（圆通）等。</p>
     */
    @JsonProperty("ShipperCode")
    private String shipperCode;

    /**
     * 物流运单号（LogisticCode）。
     * <p>与请求中的 LogisticCode 对应，标识查询的快递运单。</p>
     */
    @JsonProperty("LogisticCode")
    private String logisticCode;

    /**
     * 接口调用是否成功。
     * <p>true 表示查询成功且有轨迹数据；false 表示查询失败或无数据，失败原因在 {@link #reason} 中。</p>
     */
    @JsonProperty("Success")
    private Boolean success;

    /**
     * 失败原因（Reason）。
     * <p>当 {@link #success} 为 false 时，此字段说明失败原因（如"暂无轨迹信息"）；
     * 成功时可能为 null 或空字符串。</p>
     */
    @JsonProperty("Reason")
    private String reason;

    /**
     * 物流状态码（State）。
     * <p>快递鸟原始状态码，取值：</p>
     * <ul>
     *   <li>"0" - 无轨迹</li>
     *   <li>"1" - 已揽收</li>
     *   <li>"2" - 在途中（IN_TRANSIT）</li>
     *   <li>"3" - 已签收（SIGNED）</li>
     *   <li>"4" - 问题件（EXCEPTION）</li>
     *   <li>"5" - 疑难件</li>
     *   <li>"6" - 退件签收</li>
     * </ul>
     * <p>通过 {@link LogisticsState#fromCode(String)} 可转换为内部枚举。</p>
     */
    @JsonProperty("State")
    private String state;

    /**
     * 物流轨迹节点列表（Traces）。
     * <p>包含从揽收到签收的全部扫描事件，按时间升序排列。
     * 每个节点对应一次物流扫描，包含时间、站点和描述信息。
     * 查询失败或无轨迹时可能为 null 或空列表。</p>
     */
    @JsonProperty("Traces")
    private List<LogisticsTrace> traces;

    /**
     * 物流轨迹节点。
     * <p>表示物流运输过程中的一个状态变更事件，如"已揽收"、"到达中转站"、"已签收"等。</p>
     *
     * @see KdniaoResponse#traces  所属的轨迹节点列表
     * @see <a href="https://www.kdniao.com/api-track">快递鸟 API 文档 - 轨迹字段说明</a>
     */
    @Data
    public static class LogisticsTrace {

        /**
         * 轨迹发生时间。
         * <p>格式：yyyy/MM/dd HH:mm:ss（快递鸟标准格式），
         * 部分情况下可能为 yyyy-MM-dd HH:mm:ss（兼容格式）。</p>
         */
        @JsonProperty("AcceptTime")
        private String acceptTime;

        /**
         * 轨迹描述。
         * <p>描述当前状态变更的地点和事件，如"【深圳市】快件已到达深圳中转站"、
         * "【上海市】快件已签收，签收人：本人签收"等。</p>
         */
        @JsonProperty("AcceptStation")
        private String acceptStation;

        /**
         * 备注信息。
         * <p>附加说明，可为空。通常用于补充签收信息或异常原因。</p>
         */
        @JsonProperty("Remark")
        private String remark;
    }

    /**
     * 物流状态枚举。
     * <p>
     * 将快递鸟 API 的状态码映射为内部统一状态标识，便于业务层统一处理。
     * 通过 {@link #fromCode(String)} 方法实现状态码到枚举的转换。
     * </p>
     *
     * @see KdniaoResponse#state  原始状态码字段
     */
    public enum LogisticsState {
        /** 在途中（快递鸟状态码 "2"） */
        IN_TRANSIT("2", "IN_TRANSIT"),
        /** 已签收（快递鸟状态码 "3"） */
        SIGNED("3", "SIGNED"),
        /** 问题件（快递鸟状态码 "4"） */
        EXCEPTION("4", "EXCEPTION");

        /** 快递鸟原始状态码 */
        private final String code;

        /** 内部统一状态标识 */
        private final String status;

        /**
         * 构造函数。
         *
         * @param code   快递鸟原始状态码（如 "2"、"3"、"4"）
         * @param status 内部统一状态标识（如 "IN_TRANSIT"、"SIGNED"、"EXCEPTION"）
         */
        LogisticsState(String code, String status) {
            this.code = code;
            this.status = status;
        }

        /**
         * 获取快递鸟原始状态码。
         *
         * @return 状态码字符串（如 "2"、"3"、"4"）
         */
        public String getCode() {
            return code;
        }

        /**
         * 获取内部统一状态标识。
         *
         * @return 状态标识字符串（如 "IN_TRANSIT"、"SIGNED"、"EXCEPTION"）
         */
        public String getStatus() {
            return status;
        }

        /**
         * 根据快递鸟状态码查找对应的枚举值。
         * <p>
         * 遍历所有枚举值，匹配 {@link #code} 字段。
         * 未匹配到时返回 null（不抛出异常，由调用方处理兜底）。
         * </p>
         *
         * @param code 快递鸟原始状态码（如 "2"、"3"、"4"）
         * @return 对应的枚举值，未匹配时返回 null
         */
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
