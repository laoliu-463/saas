package com.colonel.saas.vo.sample;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点 VO（Value Object）。
 * <p>
 * 表示物流运输过程中的单条轨迹记录，描述包裹在某个时间节点的状态变化。
 * 作为 {@link SampleLogisticsVO#traces} 的列表元素使用，展示给用户完整的物流追踪时间线。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 物流追踪。
 * </p>
 */
@Data
public class LogisticsTraceVO {

    /**
     * 轨迹发生时间。
     * <p>
     * 物流事件的时间戳，精确到秒，用于按时间排序展示物流时间线。
     * </p>
     */
    private LocalDateTime traceTime;

    /**
     * 轨迹内容描述。
     * <p>
     * 例如 "已揽收"、"到达【北京转运中心】"、"派件中"、"已签收" 等，
     * 直接展示给用户阅读。
     * </p>
     */
    private String traceContent;

    /**
     * 轨迹状态编码。
     * <p>
     * 物流查询接口返回的标准化状态码，用于程序判断物流阶段（如 0=揽收、1=运输、2=派送、3=签收）。
     * </p>
     */
    private String statusCode;

    /**
     * 轨迹状态名称。
     * <p>
     * 状态编码的可读中文名称，如 "已揽收"、"运输中"、"派送中"、"已签收" 等。
     * </p>
     */
    private String statusName;
}
