package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.service.AttributionSourceNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抖音订单网关的生产环境实现。
 *
 * <p>功能描述：通过 {@link OrderApi} 调用抖音精选联盟的真实订单 API，
 * 提供 1603 团长订单查询，以及 2704 fallback/probe 查询等功能。
 * 当 {@link DouyinUpstreamModeSupport} 判定为 contract 模式时，委托给
 * {@link DouyinContractFixtureProvider} 返回契约夹具数据。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>当 {@code douyin.test.enabled=false}（或未配置）时激活此实现（matchIfMissing=true）</li>
 *   <li>contract 模式下所有查询方法返回硬编码夹具数据</li>
 *   <li>与 {@link com.colonel.saas.gateway.douyin.test.TestDouyinOrderGateway} 互斥</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：抖音网关 / 订单适配层</p>
 *
 * @see DouyinOrderGateway
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 * @see com.colonel.saas.gateway.douyin.test.TestDouyinOrderGateway
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinOrderGateway implements DouyinOrderGateway {

    /** 日期时间格式化器，用于将 epoch 秒数与字符串互转 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 抖音订单 API 客户端，用于调用订单结算查询接口 */
    private final OrderApi orderApi;

    /** 上游模式判断：live（真实 API）或 contract（契约夹具） */
    private final DouyinUpstreamModeSupport upstreamModeSupport;

    /** 契约测试夹具数据提供者，contract 模式下使用 */
    private final DouyinContractFixtureProvider contractFixtureProvider;

    /**
     * 构造函数（Spring 自动注入）。
     *
     * @param orderApi              抖音订单 API 客户端
     * @param upstreamModeSupport   上游模式判断器
     * @param contractFixtureProvider 契约夹具数据提供者
     */
    public RealDouyinOrderGateway(
            OrderApi orderApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.orderApi = orderApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    /**
     * 查询 2704 多结算订单列表（buyin.colonelMultiSettlementOrders 接口）。
     *
     * <p>该方法仅供 fallback / probe / 对照，不再作为默认结算写库主链路。</p>
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>将游标、时间范围格式化后调用 {@link OrderApi#listColonelMultiSettlementOrders}</li>
     *   <li>通过 {@link #toOrderListResult} 将原始响应转为标准结果</li>
     * </ol>
     *
     * @param request 订单查询请求（时间范围、分页大小、游标）
     * @return 订单列表结果
     */
    @Override
    public OrderListResult listSettlement(DouyinOrderQueryRequest request) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderListResult(request);
        }
        Map<String, Object> response = orderApi.listColonelMultiSettlementOrders(
                null,
                request.count(),
                normalizeCursor(request.cursor()),
                request.resolvedTimeType(),
                formatEpochSecond(request.startTime()),
                formatEpochSecond(request.endTime()),
                null
        );
        return toOrderListResult(response);
    }

    /**
     * 查询 1603 团长订单列表（buyin.instituteOrderColonel 接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>调用 {@link OrderApi#listSettlement} 发起真实 API 请求</li>
     *   <li>通过 {@link #toOrderListResult} 将原始响应转为标准结果</li>
     * </ol>
     *
     * @param request 订单查询请求（时间范围、分页大小、游标）
     * @return 订单列表结果
     */
    @Override
    public OrderListResult listInstituteOrders(DouyinOrderQueryRequest request) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderListResult(request);
        }
        Map<String, Object> response = orderApi.listSettlement(
                request.startTime(),
                request.endTime(),
                request.count(),
                request.cursor()
        );
        return toOrderListResult(response);
    }

    /**
     * 按最近 1 小时时间窗口查询结算订单。
     *
     * <p>自动计算最近 1 小时的起止时间（epoch 秒），委托给 {@link #listSettlement} 查询。</p>
     *
     * @param cursor 分页游标
     * @param count  每页大小（null 时默认 100）
     * @return 订单列表结果
     */
    @Override
    public OrderListResult listSettlementWindow(String cursor, Integer count) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            long endTime = System.currentTimeMillis() / 1000;
            long startTime = endTime - 3600;
            return contractFixtureProvider.buildOrderListResult(
                    new DouyinOrderQueryRequest(startTime, endTime, count == null ? 100 : count, cursor)
            );
        }
        long endTime = System.currentTimeMillis() / 1000;
        long startTime = endTime - 3600;
        return listSettlement(new DouyinOrderQueryRequest(startTime, endTime, count == null ? 100 : count, cursor));
    }

    /**
     * 按订单 ID 列表查询结算订单。
     *
     * <p>处理流程：
     * <ol>
     *   <li>通过 {@link #normalizeOrderIds} 去重并清理订单 ID 列表</li>
     *   <li>若清理后列表为空，返回空结果</li>
     *   <li>若为 contract 模式，委托给契约夹具构建响应后转为标准结果</li>
     *   <li>将订单 ID 用逗号拼接，调用 {@link OrderApi#listColonelMultiSettlementOrders}</li>
     *   <li>通过 {@link #toOrderListResult} 将原始响应转为标准结果</li>
     * </ol>
     *
     * @param orderIds 待查询的订单 ID 列表（可为 null 或空，内部会去重和清理）
     * @return 订单列表结果（订单去重后的合并结果）
     */
    @Override
    public OrderListResult listSettlementByOrderIds(List<String> orderIds, String timeType) {
        logGateway();
        List<String> normalized = normalizeOrderIds(orderIds);
        if (normalized.isEmpty()) {
            return new OrderListResult(List.of(), false, "0", Map.of("order_ids", ""));
        }
        String resolvedTimeType = normalizeTimeType(timeType);
        if (upstreamModeSupport.isContract()) {
            return toOrderListResult(contractFixtureProvider.buildOrderSettlementResponse(
                    null,
                    normalized.size(),
                    "0",
                    resolvedTimeType,
                    null,
                    null,
                    String.join(",", normalized)
            ));
        }
        Map<String, Object> response = orderApi.listColonelMultiSettlementOrders(
                null,
                normalized.size(),
                "0",
                resolvedTimeType,
                null,
                null,
                String.join(",", normalized)
        );
        return toOrderListResult(response);
    }

    /**
     * 记录网关调用日志（含上游模式、脱敏 appKey、shopId、authId）。
     *
     * <p>每次网关方法入口调用，便于排查请求链路和上游配置。</p>
     */
    private void logGateway() {
        log.info(
                "gateway=RealDouyinOrderGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(contractFixtureProvider.appKey()),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    /**
     * 对字符串进行脱敏处理，保留前 4 位和后 4 位，中间用 **** 替代。
     *
     * <p>长度 &lt;= 8 的字符串原样返回，null/空白返回空字符串。</p>
     *
     * @param value 待脱敏的字符串
     * @return 脱敏后的字符串
     */
    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    /**
     * 将抖音 API 原始响应转换为标准 {@link OrderListResult}。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从响应根节点提取 {@code data} 子节点</li>
     *   <li>通过 {@link #extractOrderRows} 提取订单行列表</li>
     *   <li>通过 {@link #pageData} 合并嵌套分页信息</li>
     *   <li>将每一行原始 Map 通过 {@link #toOrderItem} 转为 {@link DouyinOrderItem}</li>
     *   <li>过滤掉外部订单号为空的无效记录</li>
     *   <li>返回包含订单列表、hasMore、nextCursor 和原始响应的结果</li>
     * </ol>
     *
     * @param response 抖音 API 原始响应 Map
     * @return 标准化的订单列表结果
     */
    private OrderListResult toOrderListResult(Map<String, Object> response) {
        Map<String, Object> data = asMap(response == null ? null : response.get("data"));
        List<Map<String, Object>> rows = extractOrderRows(data);
        Map<String, Object> pageData = pageData(data);
        List<DouyinOrderItem> orders = rows.stream()
                .map(this::toOrderItem)
                .filter(item -> StringUtils.hasText(item.externalOrderId()))
                .toList();
        return new OrderListResult(
                orders,
                asBoolean(pick(pageData, "has_more", "hasMore", "has_next", "hasNext")),
                firstNonBlank(
                        asString(pick(pageData, "next_cursor", "nextCursor", "cursor", "next_page", "nextPage")),
                        "0"
                ),
                response
        );
    }

    /**
     * 从 data 节点中合并嵌套分页信息。
     *
     * <p>抖音 API 的分页字段（has_more、next_cursor 等）可能位于
     * {@code data} 或 {@code data.data} 两层嵌套中。
     * 此方法将两层合并为一个扁平 Map，外层字段优先，内层字段补充。</p>
     *
     * @param data 抖音 API 响应的 data 节点
     * @return 合并后的分页数据 Map
     */
    private Map<String, Object> pageData(Map<String, Object> data) {
        Map<String, Object> nested = asMap(data == null ? null : data.get("data"));
        if (nested.isEmpty()) {
            return data == null ? Map.of() : data;
        }
        Map<String, Object> result = new LinkedHashMap<>(data);
        result.putAll(nested);
        return result;
    }

    /**
     * 从 data 节点中提取订单行列表。
     *
     * <p>兼容抖音 API 多种返回结构：
     * <ul>
     *   <li>data.order_list / data.orderList / data.orders / data.list / data.data</li>
     *   <li>data.data.order_list / ... （嵌套一层）</li>
     * </ul>
     *
     * @param data 抖音 API 响应的 data 节点
     * @return 订单行 Map 列表，无数据时返回空列表
     */
    private List<Map<String, Object>> extractOrderRows(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        Object rows = pick(data, "order_list", "orderList", "orders", "list", "data");
        if (rows instanceof List<?> list) {
            return convertListRows(list);
        }
        if (rows instanceof Map<?, ?> nestedMap) {
            Object nestedRows = pick(asMap(nestedMap), "order_list", "orderList", "orders", "list", "data");
            if (nestedRows instanceof List<?> nestedList) {
                return convertListRows(nestedList);
            }
        }
        return List.of();
    }

    /**
     * 将 List 中的每个元素安全转换为 Map&lt;String, Object&gt;，跳过非 Map 元素。
     *
     * @param rows 原始列表（元素可能是任意类型）
     * @return 仅包含 Map 元素的列表
     */
    private List<Map<String, Object>> convertListRows(List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> converted = asMap(row);
            if (!converted.isEmpty()) {
                result.add(converted);
            }
        }
        return result;
    }

    /**
     * 将抖音 API 返回的原始订单 Map 转换为 {@link DouyinOrderItem}。
     *
     * <p>处理流程：
     * <ol>
     *   <li>通过 {@link AttributionSourceNormalizer#normalize} 统一归因来源键名</li>
     *   <li>兼容 snake_case 和 camelCase 两种键名格式提取各字段</li>
     *   <li>通过 {@link #resolveServiceFee} 解析服务费（含嵌套 colonel_order_info 结构）</li>
     *   <li>通过 {@link #asEpochSecond} 将时间戳统一转为 epoch 秒</li>
     *   <li>创建时间优先取 create_time，回退到 settle_time，最后默认当前时间</li>
     * </ol>
     *
     * @param raw 抖音 API 返回的订单原始 Map
     * @return 标准化的 DouyinOrderItem 实例
     */
    private DouyinOrderItem toOrderItem(Map<String, Object> raw) {
        // 第一步：归一化归因来源字段（将不同上游格式的 pick_source 统一为标准格式）
        raw = AttributionSourceNormalizer.normalize(raw);
        return new DouyinOrderItem(
                // 第二步：提取订单 ID（兼容 order_id / orderId / order_id_str / orderIdStr 四种键名）
                asString(pick(raw, "order_id", "orderId", "order_id_str", "orderIdStr")),
                // 第三步：提取外部商品 ID（兼容 external_product_id / externalProductId / product_id / productId）
                asString(pick(raw, "external_product_id", "externalProductId", "product_id", "productId")),
                // 第四步：提取抖音侧商品 ID（仅取 product_id / productId 两种键名）
                asString(pick(raw, "product_id", "productId")),
                // 第五步：提取商家 ID（兼容 merchant_id / merchantId / shop_id / shopId）
                asString(pick(raw, "merchant_id", "merchantId", "shop_id", "shopId")),
                // 第六步：提取商家名称（兼容 merchant_name / merchantName / shop_name / shopName）
                asString(pick(raw, "merchant_name", "merchantName", "shop_name", "shopName")),
                // 第七步：提取达人 ID（兼容 8 种键名：talent_id / talent_uid / author_id / author_buyin_id 及对应驼峰形式）
                asString(pick(raw, "talent_id", "talentId", "talent_uid", "talentUid", "author_id", "authorId", "author_buyin_id", "authorBuyinId")),
                // 第八步：提取达人名称（兼容 talent_name / author_name / author_account 及对应驼峰形式）
                asString(pick(raw, "talent_name", "talentName", "author_name", "authorName", "author_account", "authorAccount")),
                // 第九步：提取推广来源标识 pick_source（兼容 snake_case 和 camelCase）
                asString(pick(raw, "pick_source", "pickSource")),
                // 第十步：提取订单金额（兼容 9 种键名覆盖不同上游返回格式，返回分值 Long）
                asLongObject(pick(raw, "order_amount", "orderAmount", "total_amount", "totalAmount", "pay_amount", "payAmount", "total_pay_amount", "totalPayAmount", "pay_goods_amount", "payGoodsAmount")),
                // 第十一步：解析服务费（通过 resolveServiceFee 处理嵌套 colonel_order_info 结构）
                resolveServiceFee(raw),
                // 第十二步：解析订单状态（asInteger 可处理数字和字符串两种格式的状态码）
                asInteger(pick(raw, "order_status", "orderStatus", "status", "flow_point", "flowPoint")),
                // 第十三步：解析订单创建时间（优先级：create_time > settle_time > 当前时间）
                firstNonNull(
                        asEpochSecond(pick(raw, "create_time", "createTime", "order_create_time", "orderCreateTime", "pay_success_time", "paySuccessTime")),
                        asEpochSecond(pick(raw, "settle_time", "settleTime", "update_time", "updateTime")),
                        Instant.now().getEpochSecond()
                ),
                // 第十四步：解析结算时间（仅 settle_time，不回退 update_time）
                asEpochSecond(pick(raw, "settle_time", "settleTime")),
                // 第十五步：保留原始 Map 用于下游扩展字段透传
                raw
        );
    }

    /**
     * 将任意类型的值安全转换为 Map&lt;String, Object&gt;。
     *
     * <p>若 source 是 Map 类型，将所有键转为 String 后返回新 LinkedHashMap；
     * 否则返回空不可变 Map。</p>
     *
     * @param source 待转换的值
     * @return 转换后的 Map，非 Map 类型返回空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 从 Map 中按多个候选键名提取值，返回第一个匹配的值。
     *
     * <p>用于兼容抖音 API 的 snake_case 和 camelCase 两种键名格式。</p>
     *
     * @param source 原始数据 Map（可为 null）
     * @param keys   候选键名列表（按优先级排列）
     * @return 第一个匹配的值，无匹配时返回 null
     */
    private Object pick(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    /**
     * 从多个候选字符串中返回第一个非空白值。
     *
     * @param values 候选字符串列表
     * @return 第一个非空白字符串，全部为空白时返回 null
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将任意类型的值安全转换为字符串。
     *
     * @param value 待转换的值
     * @return 字符串表示，null 输入返回 null
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将 epoch 秒数格式化为 {@code yyyy-MM-dd HH:mm:ss} 字符串（使用系统默认时区）。
     *
     * <p>用于将查询请求的时间范围转换为抖音 API 要求的日期时间格式。</p>
     *
     * @param epochSecond epoch 秒数
     * @return 格式化后的日期时间字符串
     */
    private String formatEpochSecond(long epochSecond) {
        return DATE_TIME_FORMATTER.format(com.colonel.saas.common.time.AppZone.fromEpochSecond(epochSecond));
    }

    /**
     * 规范化分页游标，空白游标默认为 {@code "0"}（首页）。
     *
     * @param cursor 原始游标字符串（可为 null 或空白）
     * @return 规范化后的游标字符串
     */
    private String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "0";
    }

    private String normalizeTimeType(String timeType) {
        if (!StringUtils.hasText(timeType)) {
            return "update";
        }
        String normalized = timeType.trim().toLowerCase();
        if (!"settle".equals(normalized) && !"update".equals(normalized)) {
            throw new IllegalArgumentException("timeType must be settle or update");
        }
        return normalized;
    }

    /**
     * 规范化订单 ID 列表：去 null、去空白、去重、trim。
     *
     * @param orderIds 原始订单 ID 列表（可为 null）
     * @return 去重后的不可变订单 ID 列表，null/空输入返回空列表
     */
    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String orderId : orderIds) {
            if (StringUtils.hasText(orderId)) {
                deduped.add(orderId.trim());
            }
        }
        return List.copyOf(deduped);
    }

    /**
     * 将任意类型的值安全转换为 Boolean。
     *
     * <p>支持三种类型：
     * <ul>
     *   <li>{@link Boolean}：直接返回</li>
     *   <li>{@link Number}：非 0 为 true</li>
     *   <li>{@link String}："true" 或 "1" 为 true（忽略大小写）</li>
     * </ul>
     * null 或其他类型返回 false。</p>
     *
     * @param value 待转换的值
     * @return 转换后的 Boolean 值
     */
    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    /**
     * 将任意类型的值安全转换为 Long（包装类型）。
     *
     * <p>支持 Number（直接取 longValue）和 String（Long.parseLong）类型。
     * null 或解析失败返回 null。</p>
     *
     * @param value 待转换的值
     * @return 转换后的 Long 值，null 或解析失败返回 null
     */
    private Long asLongObject(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * 将任意类型的值安全转换为 Integer。
     *
     * <p>支持抖音订单状态字符串映射：
     * <ul>
     *   <li>{@code "PAY_SUCC"} → 1</li>
     *   <li>{@code "REFUND"} / {@code "REFUND_SUCCESS"} / {@code "CLOSED"} → 4</li>
     *   <li>其他字符串尝试 Long.parseLong 后取 intValue</li>
     * </ul>
     * Number 类型直接取 intValue，null 或解析失败返回 null。</p>
     *
     * @param value 待转换的值（数字、字符串或 null）
     * @return 转换后的 Integer 值，null 或解析失败返回 null
     */
    private Integer asInteger(Object value) {
        if (value instanceof String text) {
            return switch (text.trim()) {
                case "PAY_SUCC" -> 1;
                case "REFUND", "REFUND_SUCCESS", "CLOSED" -> 4;
                default -> {
                    Long parsed = asLongObject(text);
                    yield parsed == null ? null : parsed.intValue();
                }
            };
        }
        Long longValue = asLongObject(value);
        return longValue == null ? null : longValue.intValue();
    }

    /**
     * 将任意类型的值安全转换为 epoch 秒数。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>若为数字且 &gt; 9,999,999,999（毫秒级），自动除以 1000 转为秒</li>
     *   <li>若为数字且在合理范围内，直接作为秒数返回</li>
     *   <li>若为字符串，尝试按 {@code yyyy-MM-dd HH:mm:ss} 解析为 LocalDateTime 再转 epoch 秒</li>
     *   <li>null 或解析失败返回 null</li>
     * </ol>
     *
     * @param value 待转换的值（数字、日期时间字符串或 null）
     * @return epoch 秒数，null 或解析失败返回 null
     */
    private Long asEpochSecond(Object value) {
        Long longValue = asLongObject(value);
        if (longValue != null) {
            return longValue > 9_999_999_999L ? longValue / 1000L : longValue;
        }
        if (value == null) {
            return null;
        }
        try {
            return com.colonel.saas.common.time.AppZone.toEpochSecond(
                    LocalDateTime.parse(String.valueOf(value).trim(), DATE_TIME_FORMATTER));
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    /**
     * 从多个候选值中返回第一个非 null 值。
     *
     * <p>用于多字段回退取值场景，如创建时间优先取 paySuccessTime，回退到 settleTime。</p>
     *
     * @param values 候选值列表
     * @param <T>    值类型
     * @return 第一个非 null 值，全部为 null 时返回 null
     */
    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从原始订单 Map 中解析服务费金额。
     *
     * <p>解析优先级：
     * <ol>
     *   <li>顶层字段：settle_colonel_commission / settleColonelCommission / colonel_commission / colonelCommission / service_fee / serviceFee</li>
     *   <li>嵌套字段：colonel_order_info（或 colonelOrderInfo）中的 estimated_commission / estimatedCommission / real_commission / realCommission</li>
     * </ol>
     *
     * @param raw 抖音 API 返回的订单原始 Map
     * @return 服务费金额（单位：分），未找到时返回 null
     */
    private Long resolveServiceFee(Map<String, Object> raw) {
        Long direct = asLongObject(pick(raw, "settle_colonel_commission", "settleColonelCommission", "colonel_commission", "colonelCommission", "service_fee", "serviceFee"));
        if (direct != null) {
            return direct;
        }
        Map<String, Object> colonelInfo = asMap(pick(raw, "colonel_order_info", "colonelOrderInfo"));
        return asLongObject(pick(colonelInfo, "estimated_commission", "estimatedCommission", "real_commission", "realCommission"));
    }
}
