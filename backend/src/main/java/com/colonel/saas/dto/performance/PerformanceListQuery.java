package com.colonel.saas.dto.performance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业绩列表查询请求 DTO。
 * <p>
 * 用于业绩列表页的多条件筛选与分页查询，支持按订单、商品、合作伙伴、活动、达人、
 * 渠道、招募人、订单状态、时间范围、金额轨道等维度筛选。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceListQuery {
    /** 订单 ID 精确匹配 */
    private String orderId;
    /** 商品 ID 精确匹配 */
    private String productId;
    /** 商品名称模糊匹配 */
    private String productName;
    /** 合作伙伴（商家）ID */
    private Long partnerId;
    /** 合作伙伴名称模糊匹配 */
    private String partnerName;
    /** 活动 ID 精确匹配 */
    private String activityId;
    /** 达人 ID 精确匹配 */
    private UUID talentId;
    /** 渠道 ID 精确匹配 */
    private UUID channelId;
    /** 招募人 ID 精确匹配 */
    private UUID recruiterId;
    /** 订单状态筛选 */
    private String orderStatus;
    /** 时间过滤类型（如 pay_time、settle_time） */
    private String timeFilterType;
    /** 时间范围起始 */
    private LocalDateTime timeStart;
    /** 时间范围结束 */
    private LocalDateTime timeEnd;
    /** 金额轨道筛选（如 estimate、effective） */
    private String amountTrack;
    /** 页码，默认 1 */
    private long page = 1;
    /** 每页条数，默认 20 */
    private long pageSize = 20;
    /** 排序字段 */
    private String sortBy;
    /** 排序方向（asc/desc） */
    private String sortOrder;
}
