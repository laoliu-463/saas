package com.colonel.saas.gateway.douyin;

import java.util.List;
import java.util.Map;

/**
 * 抖店订单 Gateway 接口。
 * <p>
 * 封装抖店订单结算查询能力，业务层只依赖此接口，
 * 不感知底层是 Test 还是真实 SDK 调用。
 * </p>
 *
 * <h3>实现切换</h3>
 * <p>
 * 通过配置 {@code douyin.test.enabled} 控制注入的实现：
 * <ul>
 *   <li>{@code true} - {@link com.colonel.saas.gateway.douyin.test.TestDouyinOrderGateway}，
 *       从本地数据库读取 Mock 订单</li>
 *   <li>{@code false} - {@link com.colonel.saas.gateway.douyin.real.RealDouyinOrderGateway}，
 *       调用抖店 SDK 查询真实结算订单</li>
 * </ul>
 * </p>
 *
 * <h3>设计决策</h3>
 * <p>
 * 新结算主链路通过 {@code SettlementOrderGateway} 调用 {@code buyin.instituteOrderColonel / 1603}。
 * 本接口中的 {@link #listSettlement} 保留给 legacy、fallback、probe 和测试适配。
 * </p>
 */
public interface DouyinOrderGateway {

    /**
     * 按时间范围拉取 legacy/fallback 分次结算订单列表。
     * <p>
     * 默认实现可调用 buyin.colonelMultiSettlementOrders；不得作为 1603 结算默认写库主链路。
     * </p>
     *
     * @param request 查询请求（含起止时间、分页参数）
     * @return 订单列表结果（含订单明细、是否有下一页、游标）
     */
    OrderListResult listSettlement(DouyinOrderQueryRequest request);

    /**
     * 按时间范围拉取团长订单接口。
     * <p>
     * 真实实现覆盖为 1603；默认委托仅用于旧测试/模拟实现兼容。
     * </p>
     *
     * @param request 查询请求
     * @return 订单列表结果
     */
    default OrderListResult listInstituteOrders(DouyinOrderQueryRequest request) {
        return listSettlement(request);
    }

    /**
     * 按当前时间窗口拉取最近订单。
     * <p>
     * 使用默认时间窗口（通常为最近 24 小时），适合定时任务同步场景。
     * </p>
     *
     * @param cursor 分页游标（首次查询留空）
     * @param count  每页条数
     * @return 订单列表结果
     */
    OrderListResult listSettlementWindow(String cursor, Integer count);

    /**
     * 按指定订单号定向拉取订单（默认 {@code time_type=update}）。
     *
     * @param orderIds 订单号列表
     * @return 订单列表结果
     */
    default OrderListResult listSettlementByOrderIds(List<String> orderIds) {
        return listSettlementByOrderIds(orderIds, "update");
    }

    /**
     * 按指定订单号定向拉取订单。
     *
     * @param orderIds  订单号列表
     * @param timeType  fallback/probe 时间类型：{@code settle} 或 {@code update}
     * @return 订单列表结果
     */
    OrderListResult listSettlementByOrderIds(List<String> orderIds, String timeType);

    /**
     * 订单查询请求参数。
     *
     * @param startTime 查询起始时间（Unix 时间戳，秒）
     * @param endTime   查询结束时间（Unix 时间戳，秒）
     * @param count     每页条数
     * @param cursor    分页游标（首次查询留空，后续传上次返回的 nextCursor）
     * @param timeType  时间类型：{@code settle} 或 {@code update}，默认 {@code update}
     */
    record DouyinOrderQueryRequest(
            long startTime,
            long endTime,
            int count,
            String cursor,
            String timeType
    ) {
        public DouyinOrderQueryRequest(long startTime, long endTime, int count, String cursor) {
            this(startTime, endTime, count, cursor, "update");
        }

        /** 归一化 time_type，空白时回退 {@code update}。 */
        public String resolvedTimeType() {
            if (timeType == null || timeType.isBlank()) {
                return "update";
            }
            return timeType.trim().toLowerCase();
        }
    }

    /**
     * 单个订单条目。
     * <p>
     * 封装抖店结算订单的核心字段，rawPayload 保留上游原始响应用于排查。
     * </p>
     *
     * @param externalOrderId   外部订单号（抖店侧订单 ID）
     * @param externalProductId 外部商品 ID（抖店侧商品 ID）
     * @param productId         内部商品 ID
     * @param merchantId        商家 ID
     * @param merchantName      商家名称
     * @param talentId          达人 ID
     * @param talentName        达人名称
     * @param pickSource        推广来源标识（用于订单归属追踪）
     * @param orderAmount       订单金额（单位：分）
     * @param serviceFee        服务费（单位：分）
     * @param orderStatus       订单状态码
     * @param createTime        下单时间（Unix 时间戳，毫秒）
     * @param settleTime        结算时间（Unix 时间戳，毫秒）
     * @param rawPayload        上游原始响应数据（用于排查和审计）
     */
    record DouyinOrderItem(
            String externalOrderId,
            String externalProductId,
            String productId,
            String merchantId,
            String merchantName,
            String talentId,
            String talentName,
            String pickSource,
            Long orderAmount,
            Long serviceFee,
            Integer orderStatus,
            Long createTime,
            Long settleTime,
            Map<String, Object> rawPayload
    ) {}

    /**
     * 订单列表查询结果。
     *
     * @param orders      订单条目列表
     * @param hasMore     是否还有更多数据（用于分页判断）
     * @param nextCursor  下一页游标（为 null 表示已无更多数据）
     * @param rawResponse 上游原始响应（用于排查和审计）
     */
    record OrderListResult(
            List<DouyinOrderItem> orders,
            boolean hasMore,
            String nextCursor,
            Map<String, Object> rawResponse
    ) {
    }
}
