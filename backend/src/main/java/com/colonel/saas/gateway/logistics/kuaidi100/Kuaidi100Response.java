package com.colonel.saas.gateway.logistics.kuaidi100;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 快递100即时查询 API 响应体映射。
 *
 * <p>功能描述：对应快递100实时查询 API（POST https://poll.kuaidi100.com/poll/query.do）
 * 的 JSON 响应结构，用于将快递100返回的 JSON 数据反序列化为 Java 对象。</p>
 *
 * <ul>
 *   <li>响应字段映射采用 {@code @JsonProperty} 注解，与快递100 API 的蛇形/驼峰命名字段一一对应</li>
 *   <li>轨迹节点列表 {@link TraceNode} 按时间升序排列</li>
 *   <li>状态码 state 通过 {@link Kuaidi100LogisticsGateway#mapStatus(String)} 转换为内部统一状态</li>
 * </ul>
 *
 * <p>在架构中的角色：寄样域 / 物流适配层 / 快递100响应 DTO，
 * 仅供 {@link Kuaidi100LogisticsGateway} 内部使用，不暴露给业务层。</p>
 *
 * @see Kuaidi100LogisticsGateway  使用此类进行响应解析的网关实现
 * @see <a href="https://www.kuaidi100.com/openapi/">快递100 开放 API 文档</a>
 */
@Data
public class Kuaidi100Response {

    /**
     * 通讯状态码。
     * <p>"200" 表示查询成功且有轨迹数据，其他值表示查询失败或异常。</p>
     */
    @JsonProperty("status")
    private String status;

    /**
     * 状态信息。
     * <p>查询失败时说明失败原因（如"查询失败"、"无结果"等）；成功时可能为空。</p>
     */
    @JsonProperty("message")
    private String message;

    /**
     * 快递公司编码。
     * <p>快递100标准编码（小写英文），如 shunfeng（顺丰）、zhongtong（中通）等。</p>
     */
    @JsonProperty("com")
    private String companyCode;

    /**
     * 物流运单号。
     * <p>与请求中的运单号对应，标识查询的快递运单。</p>
     */
    @JsonProperty("nu")
    private String trackingNo;

    /**
     * 附加条件标识。
     * <p>快递100内部使用的条件标识，如 "F00" 表示正常查询，可为空。一般不用于业务逻辑。</p>
     */
    @JsonProperty("condition")
    private String condition;

    /**
     * 是否已校验标识。
     * <p>"0" 或 null 表示未校验，"1" 表示已校验（已确认签收）。一般不用于业务逻辑。</p>
     */
    @JsonProperty("ischeck")
    private String isCheck;

    /**
     * 快递单当前状态码。
     * <p>快递100原始状态码，取值：</p>
     * <ul>
     *   <li>"0" - 无轨迹</li>
     *   <li>"1" - 已揽收</li>
     *   <li>"2" - 揽收失败</li>
     *   <li>"3" - 已签收（SIGNED）</li>
     *   <li>"4" - 问题件（EXCEPTION）</li>
     *   <li>"5" - 派件中（DELIVERING）</li>
     *   <li>"6" - 退件</li>
     *   <li>"7" - 待揽收</li>
     *   <li>"8" - 疑难件</li>
     *   <li>"10" / "11" / "12" / "13" / "14" - 其他在途/退回状态</li>
     * </ul>
     * <p>通过 {@link Kuaidi100LogisticsGateway#mapStatus(String)} 可转换为内部统一状态。</p>
     */
    @JsonProperty("state")
    private String state;

    /**
     * 物流轨迹节点列表。
     * <p>包含从揽收到签收的全部扫描事件，按时间升序排列。
     * 每个节点对应一次物流扫描，包含时间、描述和位置信息。
     * 查询失败或无轨迹时可能为 null 或空列表。</p>
     */
    @JsonProperty("data")
    private List<TraceNode> data;

    /**
     * 物流轨迹节点。
     * <p>表示物流运输过程中的一个状态变更事件，如"已揽收"、"到达中转站"、"已签收"等。</p>
     *
     * @see Kuaidi100Response#data  所属的轨迹节点列表
     */
    @Data
    public static class TraceNode {

        /**
         * 轨迹发生时间（原始格式）。
         * <p>格式通常为 yyyy-MM-dd HH:mm:ss。</p>
         */
        @JsonProperty("time")
        private String time;

        /**
         * 格式化后的轨迹时间。
         * <p>格式为 yyyy-MM-dd HH:mm:ss，与 time 字段内容相同但经过格式化处理。
         * 优先使用此字段进行时间解析。</p>
         */
        @JsonProperty("ftime")
        private String formattedTime;

        /**
         * 轨迹描述。
         * <p>描述当前状态变更的地点和事件，如"【深圳市】快件已到达深圳集散中心"、
         * "【上海市】快件已签收，签收人：本人签收"等。</p>
         */
        @JsonProperty("context")
        private String context;

        /**
         * 轨迹发生地点。
         * <p>当前扫描事件的地理位置信息（如省市区），可能为空。</p>
         */
        @JsonProperty("location")
        private String location;

        /**
         * 节点状态描述。
         * <p>该节点对应的状态文本（如"已签收"、"派件中"等），可为空。</p>
         */
        @JsonProperty("status")
        private String status;

        /**
         * 节点状态码。
         * <p>该节点对应的状态码（如 "3" 表示签收），与顶层 state 编码规则一致，可为空。</p>
         */
        @JsonProperty("statusCode")
        private String statusCode;
    }
}
