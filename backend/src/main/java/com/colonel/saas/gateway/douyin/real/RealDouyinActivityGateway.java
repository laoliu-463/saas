package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinAllianceActivityProductRows;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 抖音活动网关的生产环境实现。
 *
 * <p>功能描述：通过 {@link ActivityApi} 和 {@link ProductApi} 调用抖音精选联盟的真实 API，
 * 提供活动列表查询、活动商品列表查询、活动详情、活动创建/更新、取消活动商品等功能。
 * 当 {@link DouyinUpstreamModeSupport} 判定为 contract 模式时，委托给
 * {@link DouyinContractFixtureProvider} 返回契约夹具数据，不发起真实 HTTP 请求。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>当 {@code douyin.test.enabled=false}（或未配置）时激活此实现（matchIfMissing=true）</li>
 *   <li>contract 模式下所有查询方法返回硬编码夹具数据，写入方法仍委托给 ActivityApi</li>
 *   <li>与 {@link com.colonel.saas.gateway.douyin.test.TestDouyinActivityGateway} 互斥</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：抖音网关 / 活动适配层</p>
 *
 * @see DouyinActivityGateway
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 * @see com.colonel.saas.gateway.douyin.test.TestDouyinActivityGateway
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinActivityGateway implements DouyinActivityGateway {

    /** 抖音活动 API 客户端，用于活动列表/详情/创建/取消等操作 */
    private final ActivityApi activityApi;

    /** 抖音商品 API 客户端，用于查询活动下的商品列表 */
    private final ProductApi productApi;

    /** 上游模式判断：live（真实 API）或 contract（契约夹具） */
    private final DouyinUpstreamModeSupport upstreamModeSupport;

    /** 契约测试夹具数据提供者，contract 模式下使用 */
    private final DouyinContractFixtureProvider contractFixtureProvider;

    /**
     * 构造函数（Spring 自动注入）。
     *
     * @param activityApi           抖音活动 API 客户端
     * @param productApi            抖音商品 API 客户端
     * @param upstreamModeSupport   上游模式判断器
     * @param contractFixtureProvider 契约夹具数据提供者
     */
    public RealDouyinActivityGateway(
            ActivityApi activityApi,
            ProductApi productApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.activityApi = activityApi;
        this.productApi = productApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    /**
     * 查询精选联盟活动列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，直接返回契约夹具数据</li>
     *   <li>调用 {@link ActivityApi#listActivities} 发起真实 API 请求</li>
     *   <li>从响应中提取 activity_list（兼容 data.activity_list / data.data / root.activity_list 三种位置）</li>
     *   <li>将原始 Map 列表转为 {@link ActivityItem}（通过 normalizeActivityItem）</li>
     *   <li>返回包含总数、机构 ID 和活动列表的结果</li>
     * </ol>
     *
     * @param query 活动列表查询参数（appId、状态、搜索类型、排序、分页等）
     * @return 活动列表结果（hasMore=false 表示已到末页）
     */
    @Override
    public ActivityListResult listActivities(ActivityListQuery query) {
        logGateway("RealDouyinActivityGateway", query.appId());
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityListResult(query);
        }
        Map<String, Object> remote = activityApi.listActivities(
                query.appId(), query.status(), query.searchType(), query.sortType(),
                query.page(), query.pageSize(), query.activityInfo()
        );
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> list = castListMap(asList(dataNode.get("activity_list")));
        if (list.isEmpty()) {
            list = castListMap(asList(dataNode.get("data")));
        }
        if (list.isEmpty()) {
            list = castListMap(asList(remote.get("activity_list")));
        }
        long total = asLong(dataNode.get("total"), asLong(remote.get("total"), list.size()));
        long institutionId = asLong(dataNode.get("institution_id"), 0L);
        return new ActivityListResult(false, institutionId, total, list.stream().map(this::normalizeActivityItem).toList());
    }

    /**
     * 查询指定活动下的商品列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，直接返回契约夹具数据</li>
     *   <li>调用 {@link ProductApi#listProductsByActivity} 发起真实 API 请求</li>
     *   <li>通过 {@link DouyinAllianceActivityProductRows#extract} 从响应中提取商品行</li>
     *   <li>将原始 Map 列表转为 {@link ActivityProductItem}（通过 normalizeProductItem）</li>
     *   <li>返回包含总数、游标和商品列表的结果</li>
     * </ol>
     *
     * @param query 活动商品查询参数（appId、activityId、状态、分页游标等）
     * @return 活动商品列表结果（含 nextCursor 游标用于翻页）
     */
    @Override
    public ActivityProductListResult listActivityProducts(ActivityProductListQuery query) {
        logGateway("RealDouyinActivityGateway", query.appId());
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityProductListResult(query);
        }
        Map<String, Object> remote = productApi.listProductsByActivity(
                query.appId(), query.activityId(), query.searchType(), query.sortType(), query.count(),
                query.cooperationInfo(), query.cooperationType(), query.productInfo(), query.status(),
                query.retrieveMode(), query.cursor(), query.page()
        );
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> rawItems = DouyinAllianceActivityProductRows.extract(dataNode);
        List<ActivityProductItem> items = rawItems.stream().map(this::normalizeProductItem).toList();
        Long total = dataNode.containsKey("total") ? asLong(dataNode.get("total"), items.size()) : null;
        return new ActivityProductListResult(false, asLong(query.activityId(), 0L),
                asLong(dataNode.get("institution_id"), 0L), total, asString(dataNode.get("next_cursor")), items);
    }

    /**
     * 创建或更新精选联盟活动（旧版接口）。
     *
     * <p>直接委托给 {@link ActivityApi#createOrUpdate}，无论 live 或 contract 模式均发起真实 API 调用。</p>
     *
     * @param command 活动创建/更新命令（含 appId、活动配置等）
     * @return 抖音 API 原始响应 Map
     */
    @Override
    public Map<String, Object> createOrUpdate(ActivityApi.ActivityCreateOrUpdateCommand command) {
        logGateway("RealDouyinActivityGateway.createOrUpdate", command == null ? null : command.appId());
        return activityApi.createOrUpdate(command);
    }

    /**
     * 取消活动中的指定商品。
     *
     * <p>直接委托给 {@link ActivityApi#cancelActivityProduct}，无论 live 或 contract 模式均发起真实 API 调用。</p>
     *
     * @param appId   应用 ID
     * @param payload 请求体（包含活动 ID、商品 ID 等取消信息）
     * @return 抖音 API 原始响应 Map
     */
    @Override
    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        logGateway("RealDouyinActivityGateway.cancelActivityProduct", appId);
        return activityApi.cancelActivityProduct(appId, payload);
    }

    /**
     * 查询活动详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>否则调用 {@link ActivityApi#detail} 发起真实 API 请求</li>
     * </ol>
     *
     * @param appId      应用 ID
     * @param activityId 活动 ID
     * @return 抖音 API 原始响应 Map（含活动详情字段）
     */
    @Override
    public Map<String, Object> activityDetail(String appId, String activityId) {
        logGateway("RealDouyinActivityGateway.activityDetail", appId);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityDetailResponse(appId, activityId);
        }
        return activityApi.detail(appId, activityId);
    }

    /**
     * 创建或更新精选联盟活动（新版接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>将 Gateway 层 {@link ActivityMutateCommand} 转换为 API 层 {@link ActivityApi.ActivityCreateOrUpdateCommand}</li>
     *   <li>委托给 {@link ActivityApi#createOrUpdate} 发起真实 API 调用</li>
     * </ol>
     *
     * @param command 活动变更命令（含完整活动配置字段）
     * @return 抖音 API 原始响应 Map
     */
    @Override
    public Map<String, Object> createOrUpdateActivity(ActivityMutateCommand command) {
        logGateway("RealDouyinActivityGateway.createOrUpdateActivity", command.appId());
        ActivityApi.ActivityCreateOrUpdateCommand apiCmd = new ActivityApi.ActivityCreateOrUpdateCommand(
                command.appId(),
                command.activityId(),
                command.applicationLimited(),
                command.isNewShop(),
                command.shopType(),
                command.activityName(),
                command.activityDesc(),
                command.applyStartTime(),
                command.applyEndTime(),
                command.commissionRate(),
                command.serviceRate(),
                command.wechatId(),
                command.phoneNum(),
                command.estimatedSingleSale(),
                command.activityType(),
                command.specifiedShopIds(),
                command.online(),
                command.categories(),
                command.shopScore(),
                command.minPromotionDays(),
                command.thresholdCrossBorder(),
                command.minExclusionDuration(),
                command.adCommissionRate(),
                command.adServiceRate(),
                command.cosLimitType()
        );
        return activityApi.createOrUpdate(apiCmd);
    }

    /**
     * 将抖音 API 返回的原始活动 Map 转换为 {@link ActivityItem}。
     *
     * <p>兼容 snake_case 和 camelCase 两种键名格式，状态码转为中文描述。</p>
     *
     * @param raw 抖音 API 返回的活动原始 Map
     * @return 标准化的 ActivityItem 实例
     */
    private ActivityItem normalizeActivityItem(Map<String, Object> raw) {
        int status = (int) asLong(pick(raw, "status"), 0L);
        return new ActivityItem(
                asLong(pick(raw, "activity_id", "activityId"), 0L),
                asString(pick(raw, "activity_name", "activityName")),
                asString(pick(raw, "activity_start_time", "activityStartTime")),
                asString(pick(raw, "activity_end_time", "activityEndTime")),
                status,
                activityStatusText(status),
                asString(pick(raw, "application_start_time", "applicationStartTime")),
                asString(pick(raw, "application_end_time", "applicationEndTime")),
                pick(raw, "categories_limit", "categoriesLimit"),
                asLong(pick(raw, "colonel_buyin_id", "colonelBuyinId"), 0L)
        );
    }

    /**
     * 将抖音 API 返回的原始活动商品 Map 转换为 {@link ActivityProductItem}。
     *
     * <p>处理流程：
     * <ol>
     *   <li>提取 status、cosType、price、activityCosRatio 等数值字段</li>
     *   <li>计算 priceText（分转元）和 activityCosRatioText（基点转百分比）</li>
     *   <li>将原始 Map 复制为 rawPayload 保留全部上游字段</li>
     * </ol>
     *
     * @param raw 抖音 API 返回的商品原始 Map
     * @return 标准化的 ActivityProductItem 实例
     */
    private ActivityProductItem normalizeProductItem(Map<String, Object> raw) {
        int status = (int) asLong(pick(raw, "status"), 0L);
        int cosType = (int) asLong(pick(raw, "cos_type", "cosType"), 0L);
        long price = asLong(pick(raw, "price"), 0L);
        long activityCosRatio = asLong(pick(raw, "activity_cos_ratio"), 0L);
        Long activityAdCosRatio = raw.containsKey("activity_ad_cos_ratio") ? asLong(pick(raw, "activity_ad_cos_ratio"), 0L) : null;
        String originColonelBuyinId = asString(pick(raw, "origin_colonel_buyin_id", "originColonelBuyinId"));
        return new ActivityProductItem(
                asLong(pick(raw, "product_id", "productId"), 0L),
                asString(pick(raw, "title")),
                asString(pick(raw, "cover")),
                price,
                String.format(Locale.ROOT, "%.2f", price / 100.0),
                asLong(pick(raw, "cos_ratio"), 0L),
                asLong(pick(raw, "cos_fee"), 0L),
                activityCosRatio,
                String.format(Locale.ROOT, "%.2f%%", activityCosRatio / 100.0),
                cosType,
                cosType == 1 ? "双佣金" : "固定佣金",
                asString(pick(raw, "ad_service_ratio")),
                activityAdCosRatio,
                toBool(pick(raw, "has_douin_goods_tag")),
                toBool(pick(raw, "in_stock")),
                asLong(pick(raw, "sales"), 0L),
                asLong(pick(raw, "shop_id"), 0L),
                asString(pick(raw, "shop_name")),
                asString(pick(raw, "shop_score")),
                status,
                productStatusText(status),
                asString(pick(raw, "category_name")),
                asString(pick(raw, "product_stock")),
                asString(pick(raw, "colonel_coupon_info")),
                asString(pick(raw, "activity_start_time")),
                asString(pick(raw, "activity_end_time")),
                asString(pick(raw, "promotion_start_time")),
                asString(pick(raw, "promotion_end_time")),
                asString(pick(raw, "detail_url")),
                originColonelBuyinId,
                new LinkedHashMap<>(raw)
        );
    }

    /**
     * 将活动状态码转为中文描述。
     *
     * @param status 活动状态码（1=未上线, 2=报名未开始, 3=报名中, 4=推广未开始, 5=推广中, 7=报名结束）
     * @return 状态中文描述，未知状态返回 "任意状态"
     */
    private String activityStatusText(int status) {
        return switch (status) {
            case 1 -> "未上线";
            case 2 -> "报名未开始";
            case 3 -> "报名中";
            case 4 -> "推广未开始";
            case 5 -> "推广中";
            case 7 -> "报名结束";
            default -> "任意状态";
        };
    }

    /**
     * 将商品状态码转为中文描述。
     *
     * @param status 商品状态码（0=待审核, 1=推广中, 2=申请未通过, 3=合作已终止, 4=合作前取消, 6=合作已到期）
     * @return 状态中文描述，未知状态返回 "未知状态"
     */
    private String productStatusText(int status) {
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "推广中";
            case 2 -> "申请未通过";
            case 3 -> "合作已终止";
            case 4 -> "合作前取消";
            case 6 -> "合作已到期";
            default -> "未知状态";
        };
    }

    /**
     * 将任意类型的值安全转换为 boolean。
     *
     * <p>支持 Boolean、Number（非0=true）、String（"true"/"1"/"yes"=true）三种类型。
     * null 或其他类型返回 false。</p>
     *
     * @param value 待转换的值
     * @return 转换后的 boolean 值
     */
    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    /**
     * 从 Map 中按多个候选键名提取值，返回第一个匹配的值。
     *
     * <p>用于兼容抖音 API 的 snake_case（如 activity_id）和 camelCase（如 activityId）两种键名格式。</p>
     *
     * @param raw  原始数据 Map
     * @param keys 候选键名列表（按优先级排列）
     * @return 第一个匹配的值，无匹配时返回 null
     */
    private Object pick(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                return raw.get(key);
            }
        }
        return null;
    }

    /**
     * 将任意类型的值安全转换为 Map&lt;String, Object&gt;。
     *
     * <p>若 value 是 Map 类型，将所有键转为 String 后返回新 LinkedHashMap；
     * 否则返回空不可变 Map。</p>
     *
     * @param value 待转换的值
     * @return 转换后的 Map，非 Map 类型返回空 Map
     */
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }
        return Map.of();
    }

    /**
     * 将任意类型的值安全转换为 List。
     *
     * @param value 待转换的值
     * @return List 实例，非 List 类型返回空不可变 List
     */
    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    /**
     * 将 List 中的每个元素安全转换为 Map&lt;String, Object&gt;，跳过非 Map 元素。
     *
     * <p>与 {@link #asMap(Object)} 类似，但作用于列表中的每个元素。
     * 键统一转为 String，返回新的 LinkedHashMap 列表。</p>
     *
     * @param list 待转换的列表
     * @return 仅包含 Map 元素的列表
     */
    private List<Map<String, Object>> castListMap(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        converted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                result.add(converted);
            }
        }
        return result;
    }

    /**
     * 将任意类型的值安全转换为 long，解析失败时返回默认值。
     *
     * <p>支持 Number（直接取 longValue）和 String（Long.parseLong）类型。</p>
     *
     * @param value        待转换的值
     * @param defaultValue 默认值
     * @return 转换后的 long 值
     */
    private long asLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将任意类型的值安全转换为非空白字符串。
     *
     * @param value 待转换的值
     * @return 非空白字符串，null 或空白时返回 null
     */
    private String asString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 记录网关调用日志（含上游模式、脱敏 appId、shopId、authId）。
     *
     * @param gatewayName 网关名称（用于日志标识）
     * @param appId       应用 ID（日志中会脱敏处理）
     */
    private void logGateway(String gatewayName, String appId) {
        log.info(
                "gateway={}, upstreamMode={}, appKey={}, shopId={}, authId={}",
                gatewayName,
                upstreamModeSupport.value(),
                mask(appId == null ? contractFixtureProvider.appKey() : appId),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    /**
     * 对字符串进行脱敏处理，保留前 4 位和后 4 位，中间用 **** 替代。
     *
     * <p>长度 <= 8 的字符串原样返回，null/空白返回空字符串。</p>
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
}
