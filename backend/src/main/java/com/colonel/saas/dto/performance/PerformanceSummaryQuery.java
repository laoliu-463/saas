package com.colonel.saas.dto.performance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业绩汇总查询请求 DTO。
 * <p>
 * 用于查询业绩汇总数据，支持按时间范围、渠道、招募人、活动、商品、订单状态、
 * 合作伙伴、达人等维度进行聚合筛选。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceSummaryQuery {
    /** 时间过滤类型（如 pay_time、settle_time） */
    private String timeFilterType;
    /** 时间范围起始 */
    private LocalDateTime timeStart;
    /** 时间范围结束 */
    private LocalDateTime timeEnd;
    /** 渠道 ID 筛选 */
    private UUID channelId;
    /** 招募人 ID 筛选 */
    private UUID recruiterId;
    /** 活动 ID 筛选 */
    private String activityId;
    /** 商品 ID 筛选 */
    private String productId;
    /** 订单状态筛选 */
    private String orderStatus;
    /** 合作伙伴（商家）ID 筛选 */
    private Long partnerId;
    /** 达人 ID 筛选 */
    private UUID talentId;
}
