package com.colonel.saas.vo.sample;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 寄样单物流详情 VO（Value Object）。
 * <p>
 * 用于展示寄样单的完整物流追踪信息，包括物流单号、承运商、当前状态、
 * 查询结果以及物流轨迹明细。支持前端物流详情页和物流卡片组件的数据渲染。
 * </p>
 * <p>
 * 主要职责：
 * <ul>
 *   <li>承载寄样单物流查询的聚合结果</li>
 *   <li>包含物流查询接口的返回状态（成功/失败、错误码、错误信息）</li>
 *   <li>关联物流轨迹明细列表 {@link LogisticsTraceVO}</li>
 *   <li>记录物流数据提供方和最近查询时间</li>
 * </ul>
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 物流追踪。
 * </p>
 *
 * @see LogisticsTraceVO 物流轨迹明细
 */
@Data
public class SampleLogisticsVO {

    /** 关联的寄样单 ID（UUID）。 */
    private UUID sampleRequestId;

    /** 寄样单业务编号，如 "SR20250101001"。 */
    private String requestNo;

    /** 物流单号（快递面单号）。 */
    private String trackingNo;

    /** 物流公司名称（中文），如"顺丰速运"。 */
    private String logisticsCompany;

    /**
     * 物流状态码。
     * <p>
     * 物流查询接口返回的状态标识，如 "0"（在途）、"1"（揽收）、"2"（签收）。
     * </p>
     */
    private String logisticsStatus;

    /** 物流状态中文名称，如"在途"、"已签收"、"派件中"。 */
    private String logisticsStatusName;

    /** 最近一次物流查询的时间。 */
    private LocalDateTime logisticsLastQueryAt;

    /** 最近一次物流查询的错误信息，查询成功时为 null。 */
    private String logisticsLastError;

    /** 达人签收时间。 */
    private LocalDateTime signedAt;

    /**
     * 寄样单当前状态。
     * <p>
     * 对应 {@link com.colonel.saas.common.enums.SampleStatus} 的 API 状态值，
     * 用于上下文展示物流在整个寄样流程中的位置。
     * </p>
     */
    private String status;

    /**
     * 本次物流查询是否成功。
     * <p>
     * {@code true} 表示物流查询接口调用成功并获取到最新数据；
     * {@code false} 表示查询失败，具体错误见 {@link #queryErrorCode} 和 {@link #queryErrorMessage}。
     * </p>
     */
    private Boolean querySuccess;

    /** 物流查询失败时的错误码，查询成功时为 null。 */
    private String queryErrorCode;

    /** 物流查询失败时的错误信息，查询成功时为 null。 */
    private String queryErrorMessage;

    /**
     * 物流数据提供方。
     * <p>
     * 标识物流查询使用的服务提供商，如 "kdniao"（快递鸟）、"sf"（顺丰官方）等。
     * </p>
     */
    private String provider;

    /**
     * 物流轨迹明细列表。
     * <p>
     * 按时间倒序排列的物流节点信息，每个节点包含时间、地点、描述等。
     * </p>
     */
    private List<LogisticsTraceVO> traces;
}
