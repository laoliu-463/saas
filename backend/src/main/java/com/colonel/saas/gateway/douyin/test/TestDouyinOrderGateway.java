package com.colonel.saas.gateway.douyin.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试环境抖店订单网关适配器。
 * <p>
 * 实现 {@link DouyinOrderGateway} 接口，在 {@code douyin.test.enabled=true} 时替代真实的
 * 抖店订单网关，为 test 环境提供不依赖真实抖店 API 的 Mock 订单数据。
 * 该适配器会从数据库读取最新的 {@link PickSourceMapping} 记录，生成与真实转链数据关联的
 * Mock 订单，用于验证订单归因（attribution）和 Webhook 同步流程。
 * </p>
 *
 * <ul>
 *   <li><b>结算订单列表（listSettlement）</b>：生成 3 条 Mock 订单 —— 一条成功归因、一条映射缺失、一条未带推广参数，覆盖订单归因的三种典型场景</li>
 *   <li><b>时间窗口查询（listSettlementWindow）</b>：委托给 listSettlement，默认查询最近 1 小时</li>
 *   <li><b>按订单号查询（listSettlementByOrderIds）</b>：根据传入的订单号列表逐条生成 Mock 订单，用于 Webhook 定向同步场景</li>
 * </ul>
 *
 * <p>架构角色：Gateway 测试适配器（Test Double），所属领域：订单域。
 * 与真实网关的关系：实现同一 {@link DouyinOrderGateway} 接口，通过 {@code douyin.test.enabled}
 * 属性切换。不同于纯内存 Mock，该适配器会查询 {@code PickSourceMappingMapper} 获取真实转链数据，
 * 使 Mock 订单的 pick_source / talent_id 等字段与数据库中的转链记录一致。</p>
 *
 * @see DouyinOrderGateway
 * @see PickSourceMapping
 */
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinOrderGateway implements DouyinOrderGateway {

    /** 转链映射 Mapper，用于查询最新的 PickSourceMapping 记录以生成关联的 Mock 订单 */
    private final PickSourceMappingMapper pickSourceMappingMapper;

    /**
     * 构造函数，注入 PickSourceMappingMapper。
     *
     * @param pickSourceMappingMapper 转链映射数据库访问对象
     */
    public TestDouyinOrderGateway(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    /**
     * 查询 Mock 结算订单列表，覆盖订单归因的三种典型场景。
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：从数据库查询最新的有效转链映射记录（status=1）</li>
     *   <li>第二步：基于转链映射生成「归因成功」订单 —— pick_source 与数据库一致，可被订单归因流程正确识别</li>
     *   <li>第三步：生成「映射缺失」订单 —— 使用 UNKNOWN_PICK_SOURCE，模拟转链记录被删除或未创建的场景</li>
     *   <li>第四步：生成「未带推广参数」订单 —— pick_source 为 null，模拟上游订单未携带推广参数的场景</li>
     * </ol>
     *
     * @param request 查询请求，包含 startTime、endTime 和 count
     * @return 包含 3 条 Mock 订单的结果对象，hasMore=false，额外标记 test=true
     */
    @Override
    public OrderListResult listSettlement(DouyinOrderQueryRequest request) {
        List<DouyinOrderItem> orders = new ArrayList<>();

        // 第一步：查询最新有效转链映射，用于生成关联的 Mock 订单数据
        PickSourceMapping latestMapping = pickSourceMappingMapper.selectOne(
                new LambdaQueryWrapper<PickSourceMapping>()
                        .eq(PickSourceMapping::getStatus, 1)
                        .orderByDesc(PickSourceMapping::getUpdateTime)
                        .last("limit 1")
        );

        // 基准时间：使用请求中的 startTime，未提供则取当前时间前推 1 小时
        long baseTime = request.startTime() > 0 ? request.startTime() : Instant.now().getEpochSecond() - 3600;

        // 第二步：生成归因成功的订单 —— pick_source / talent_id 与数据库转链记录一致
        if (latestMapping != null) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("product_name", "主演示商品-已转链出单");
            raw.put("colonel_activity_id", latestMapping.getActivityId());
            raw.put("pick_source", latestMapping.getPickSource());
            raw.put("pick_extra", latestMapping.getPickExtra());
            raw.put("merchant_id", "M_001");
            raw.put("talent_uid", latestMapping.getTalentId());
            raw.put("author_id", latestMapping.getTalentId());
            orders.add(new DouyinOrderItem(
                    "MOCK_ORD_ATTR_" + latestMapping.getShortId(),
                    latestMapping.getProductId(),
                    latestMapping.getProductId(),
                    "800001",
                    "主演示商家-归因成功",
                    latestMapping.getTalentId(),
                    latestMapping.getTalentName(),
                    latestMapping.getPickSource(),
                    9900L,
                    1200L,
                    1,
                    baseTime + 3600,
                    null,
                    raw
            ));
        }

        // 第三步：生成映射缺失的订单 —— pick_source 使用 UNKNOWN_PICK_SOURCE，模拟归因失败
        Map<String, Object> unknownRaw = new LinkedHashMap<>();
        unknownRaw.put("product_name", "排查演示商品-推广映射缺失");
        unknownRaw.put("merchant_id", "M_002");
        unknownRaw.put("pick_source", "UNKNOWN_PICK_SOURCE");
        unknownRaw.put("pick_extra", "UNKNOWN_PICK_SOURCE");
        unknownRaw.put("talent_uid", "talent_test_b");
        orders.add(new DouyinOrderItem(
                "MOCK_ORD_UNATTR_1",
                "EXT_P2",
                latestMapping != null ? latestMapping.getProductId() : "1002",
                "800002",
                "排查演示商家-映射缺失",
                "T_002",
                "达人B-映射缺失订单",
                "UNKNOWN_PICK_SOURCE",
                5900L,
                800L,
                1,
                baseTime + 7200,
                null,
                unknownRaw
        ));

        // 第四步：生成未带推广参数的订单 —— pick_source 为 null，模拟上游未携带推广参数
        Map<String, Object> noPickRaw = new LinkedHashMap<>();
        noPickRaw.put("product_name", "排查演示商品-未带推广参数");
        noPickRaw.put("merchant_id", "M_003");
        noPickRaw.put("talent_uid", "talent_test_c");
        orders.add(new DouyinOrderItem(
                "MOCK_ORD_UNATTR_2",
                "EXT_P3",
                latestMapping != null ? latestMapping.getProductId() : "1003",
                "800003",
                "排查演示商家-未带推广参数",
                "T_003",
                "达人C-他人已认领",
                null,
                12900L,
                2000L,
                1,
                baseTime + 10800,
                null,
                noPickRaw
        ));

        return new OrderListResult(
                orders,
                false,
                "0",
                Map.of("test", true, "order_count", orders.size())
        );
    }

    /**
     * 按时间窗口查询 Mock 结算订单。
     * <p>默认查询最近 1 小时的订单，委托给 {@link #listSettlement} 实现。</p>
     *
     * @param cursor 游标（未使用，保留接口兼容）
     * @param count  返回条数，null 时默认 100
     * @return Mock 结算订单列表结果
     */
    @Override
    public OrderListResult listSettlementWindow(String cursor, Integer count) {
        long now = System.currentTimeMillis() / 1000;
        return listSettlement(new DouyinOrderQueryRequest(now - 3600, now, count == null ? 100 : count, cursor));
    }

    /**
     * 按订单号列表查询 Mock 结算订单，用于 Webhook 定向同步场景。
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：规范化订单号列表（去重、去空、trim）</li>
     *   <li>第二步：查询最新有效转链映射</li>
     *   <li>第三步：为每个订单号生成 Mock 订单数据，关联转链映射的 pick_source 和 talent_id</li>
     * </ol>
     *
     * @param orderIds 订单号列表
     * @return 包含与传入订单号一一对应的 Mock 订单列表结果
     */
    @Override
    public OrderListResult listSettlementByOrderIds(List<String> orderIds) {
        // 第一步：规范化订单号列表，去重去空
        List<String> normalized = normalizeOrderIds(orderIds);
        if (normalized.isEmpty()) {
            return new OrderListResult(List.of(), false, "0", Map.of("test", true, "order_ids", List.of()));
        }

        // 第二步：查询最新有效转链映射
        PickSourceMapping latestMapping = pickSourceMappingMapper.selectOne(
                new LambdaQueryWrapper<PickSourceMapping>()
                        .eq(PickSourceMapping::getStatus, 1)
                        .orderByDesc(PickSourceMapping::getUpdateTime)
                        .last("limit 1")
        );
        long baseTime = Instant.now().getEpochSecond() - 300L;
        List<DouyinOrderItem> orders = new ArrayList<>();
        int index = 0;
        for (String orderId : normalized) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("product_name", "Webhook定向同步订单");
            raw.put("merchant_id", "M_WEBHOOK");
            raw.put("order_id", orderId);
            if (latestMapping != null) {
                raw.put("colonel_activity_id", latestMapping.getActivityId());
                raw.put("pick_source", latestMapping.getPickSource());
                raw.put("pick_extra", latestMapping.getPickExtra());
                raw.put("talent_uid", latestMapping.getTalentId());
                raw.put("author_id", latestMapping.getTalentId());
            }
            orders.add(new DouyinOrderItem(
                    orderId,
                    latestMapping == null ? "EXT_WEBHOOK_" + index : latestMapping.getProductId(),
                    latestMapping == null ? "WEBHOOK_PRODUCT_" + index : latestMapping.getProductId(),
                    "800900",
                    "Webhook测试商家",
                    latestMapping == null ? "T_WEBHOOK" : latestMapping.getTalentId(),
                    latestMapping == null ? "Webhook达人" : latestMapping.getTalentName(),
                    latestMapping == null ? null : latestMapping.getPickSource(),
                    9900L,
                    1200L,
                    1,
                    baseTime + index,
                    null,
                    raw
            ));
            index++;
        }
        return new OrderListResult(orders, false, "0", Map.of("test", true, "order_ids", normalized));
    }

    /**
     * 规范化订单号列表：去重、去空值、trim。
     *
     * @param orderIds 原始订单号列表
     * @return 规范化后的不可变列表，保持插入顺序
     */
    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String orderId : orderIds) {
            if (orderId != null && !orderId.isBlank()) {
                normalized.add(orderId.trim());
            }
        }
        return List.copyOf(normalized);
    }

}
