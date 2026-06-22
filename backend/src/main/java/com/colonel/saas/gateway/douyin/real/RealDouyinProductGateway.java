package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinAllianceActivityProductRows;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 抖音商品网关的生产环境实现。
 *
 * <p>功能描述：通过 {@link ProductApi} 调用抖音精选联盟的真实商品 API，
 * 提供活动商品查询、SKU 列表查询等功能。
 * 当 {@link DouyinUpstreamModeSupport} 判定为 contract 模式时，委托给
 * {@link DouyinContractFixtureProvider} 返回契约夹具数据，不发起真实 HTTP 请求。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>当 {@code douyin.test.enabled=false}（或未配置）时激活此实现（matchIfMissing=true）</li>
 *   <li>contract 模式下所有查询方法返回硬编码夹具数据</li>
 *   <li>与 {@link com.colonel.saas.gateway.douyin.test.TestDouyinProductGateway} 互斥</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：抖音网关 / 商品适配层</p>
 *
 * @see DouyinProductGateway
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 * @see com.colonel.saas.gateway.douyin.test.TestDouyinProductGateway
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinProductGateway implements DouyinProductGateway {

    /** SKU 规格键的临时存储键名，用于从 Map 型 SKU 结构中保留原始 Map key */
    private static final String SKU_SPEC_KEY = "_spec_key";

    /** 抖音商品 API 客户端，用于查询活动商品和 SKU 信息 */
    private final ProductApi productApi;

    /** 上游模式判断：live（真实 API）或 contract（契约夹具） */
    private final DouyinUpstreamModeSupport upstreamModeSupport;

    /** 契约测试夹具数据提供者，contract 模式下使用 */
    private final DouyinContractFixtureProvider contractFixtureProvider;

    /**
     * 构造函数（Spring 自动注入）。
     *
     * @param productApi              抖音商品 API 客户端
     * @param upstreamModeSupport     上游模式判断器
     * @param contractFixtureProvider 契约夹具数据提供者
     */
    public RealDouyinProductGateway(
            ProductApi productApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.productApi = productApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    /**
     * 查询指定活动下的商品列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>调用 {@link ProductApi#listProductsByActivity} 发起真实 API 请求</li>
     *   <li>通过 {@link DouyinAllianceActivityProductRows#extract} 从响应中提取商品行</li>
     *   <li>将原始 Map 列表转为 {@link ActivityProductItem}（通过 normalizeProductItem）</li>
     *   <li>返回包含总数、游标和商品列表的结果</li>
     * </ol>
     *
     * @param request 活动商品查询请求（appId、activityId、状态、分页游标等）
     * @return 活动商品列表结果（含 nextCursor 游标用于翻页）
     */
    @Override
    public ActivityProductListResult queryActivityProducts(ActivityProductQueryRequest request) {
        logGateway(request.appId());
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildProductListResult(request);
        }
        Map<String, Object> remote = productApi.listProductsByActivity(
                request.appId(),
                request.activityId(),
                request.searchType(),
                request.sortType(),
                request.count(),
                request.cooperationInfo(),
                request.cooperationType(),
                request.productInfo(),
                request.status(),
                request.retrieveMode(),
                request.cursor(),
                request.page()
        );
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> rawItems = DouyinAllianceActivityProductRows.extract(dataNode);
        List<ActivityProductItem> items = rawItems.stream().map(this::normalizeProductItem).toList();
        Long total = dataNode.containsKey("total") ? asLong(dataNode.get("total"), items.size()) : null;
        return new ActivityProductListResult(false, asLong(request.activityId(), 0L),
                asLong(dataNode.get("institution_id"), 0L), total, asString(dataNode.get("next_cursor")), items);
    }

    /**
     * 查询指定商品的 SKU 列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>调用 {@link ProductApi#getProductSkusV2} 获取商品 SKU 详情</li>
     *   <li>通过 {@link #buildSpecItemNameMap} 构建规格项 ID → 名称映射</li>
     *   <li>通过 {@link #extractBuyinSkuRows} 提取 SKU 行（兼容 List 和 Map 两种结构）</li>
     *   <li>逐行构建 {@link ProductSkuResult}：提取 skuId、规格名、价格、库存、图片</li>
     *   <li>SKU 名称回退链：sku_name → 规格明细拼接 → specKey 拼接 → skuId</li>
     *   <li>图片 URL 回退链：SKU 级图片 → 规格项图片 → 全局图片</li>
     * </ol>
     *
     * @param productId 抖音商品 ID
     * @return SKU 结果列表（含 skuId、名称、价格、库存、图片 URL）
     */
    @Override
    public List<ProductSkuResult> queryProductSkus(String productId) {
        logGateway(null);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildProductSkus(productId);
        }
        Map<String, Object> response = productApi.getProductSkusV2(productId);
        Map<String, Object> dataNode = asMap(response.get("data"));
        Map<String, String> specItemNames = buildSpecItemNameMap(dataNode);
        Map<String, Object> pictures = asMap(dataNode.get("pictures"));
        List<Map<String, Object>> skuRows = extractBuyinSkuRows(dataNode);
        return skuRows.stream().map(sku -> {
            String skuId = firstNonBlank(
                    asString(sku.get("sku_id")),
                    asString(sku.get("skuId")),
                    asString(sku.get("id"))
            );
            String spec1 = firstNonBlank(asString(sku.get("spec_detail_name1")), asString(sku.get("specName1")));
            String spec2 = firstNonBlank(asString(sku.get("spec_detail_name2")), asString(sku.get("specName2")));
            String spec3 = firstNonBlank(asString(sku.get("spec_detail_name3")), asString(sku.get("specName3")));
            String specKey = asString(sku.get(SKU_SPEC_KEY));
            String skuName = firstNonBlank(
                    asString(sku.get("sku_name")),
                    asString(sku.get("skuName")),
                    buildSkuName(null, spec1, spec2, spec3),
                    buildSkuNameFromSpecKey(specKey, specItemNames),
                    skuId
            );
            return new ProductSkuResult(
                    skuId,
                    skuName,
                    asLong(pick(sku, "effective_price", "effectivePrice", "price", "sku_price", "skuPrice"), 0L),
                    toInt(pick(sku, "stock_num", "stockNum", "stock", "stock_count"), 0),
                    firstNonBlank(
                            asString(pick(sku, "picture_url", "pictureUrl", "cover", "image", "pic")),
                            resolvePictureUrl(sku, pictures, specKey)
                    )
            );
        }).toList();
    }

    /**
     * 从商品 data 节点中提取 SKU 行列表。
     *
     * <p>兼容抖音 API 多种 SKU 结构：
     * <ul>
     *   <li>List 型：skus / sku_list / skuList / list</li>
     *   <li>Map 型：sku_map / skuMap — 将每个 entry 的 key 作为 _spec_key 存入 SKU Map</li>
     * </ul>
     * Map 型结构中，若 SKU 缺少 sku_id 字段，自动用 entry key 填充。</p>
     *
     * @param dataNode 商品详情响应的 data 节点
     * @return SKU 行 Map 列表，无数据时返回空列表
     */
    private List<Map<String, Object>> extractBuyinSkuRows(Map<String, Object> dataNode) {
        Object skus = pick(dataNode, "skus", "sku_map", "skuMap", "sku_list", "skuList", "list");
        if (skus instanceof List<?> list) {
            return castListMap(list);
        }
        if (skus instanceof Map<?, ?> map) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> sku = asMap(entry.getValue());
                if (sku.isEmpty()) {
                    continue;
                }
                if (entry.getKey() != null) {
                    sku = new LinkedHashMap<>(sku);
                    sku.put(SKU_SPEC_KEY, String.valueOf(entry.getKey()));
                }
                if (!sku.containsKey("sku_id") && entry.getKey() != null) {
                    sku.put("sku_id", String.valueOf(entry.getKey()));
                }
                result.add(sku);
            }
            return result;
        }
        return List.of();
    }

    /**
     * 从商品 data 节点中构建规格项 ID → 名称的映射表。
     *
     * <p>遍历 specs / spec_list 中的 spec_items，提取 id 和 name 字段。
     * 用于将 SKU 的 specKey（如 "123_456"）反解为可读规格名（如 "红色 / XL"）。</p>
     *
     * @param dataNode 商品详情响应的 data 节点
     * @return 规格项 ID → 名称的映射 Map，无规格数据时返回空 Map
     */
    private Map<String, String> buildSpecItemNameMap(Map<String, Object> dataNode) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Object specObject : asList(pick(dataNode, "specs", "spec_list", "specList"))) {
            Map<String, Object> spec = asMap(specObject);
            for (Object itemObject : asList(pick(spec, "spec_items", "specItems", "items", "list"))) {
                Map<String, Object> item = asMap(itemObject);
                String id = asString(pick(item, "id", "spec_item_id", "specItemId"));
                String name = asString(pick(item, "name", "spec_item_name", "specItemName"));
                if (id != null && name != null) {
                    result.put(id, name);
                }
            }
        }
        return result;
    }

    /**
     * 从 specKey（如 "123_456"）和规格项映射表反解出可读 SKU 名称。
     *
     * <p>将 specKey 按分隔符（下划线、逗号、竖线、分号、冒号、空白）拆分为多个规格项 ID，
     * 再从映射表中查找名称，用 " / " 拼接。</p>
     *
     * @param specKey        规格项 ID 拼接字符串（可为 null）
     * @param specItemNames  规格项 ID → 名称的映射表
     * @return 拼接后的规格名称，无法解析时返回 null
     */
    private String buildSkuNameFromSpecKey(String specKey, Map<String, String> specItemNames) {
        if (specKey == null || specItemNames.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String id : specKey.split("[_,|;:\\s]+")) {
            String name = specItemNames.get(id);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(" / ", names);
    }

    /**
     * 解析 SKU 图片 URL（多级回退）。
     *
     * <p>回退优先级：
     * <ol>
     *   <li>SKU 级图片（sku.pictures / sku.picture / sku.pic_info）</li>
     *   <li>按 specKey 匹配全局 pictures 中的规格项图片</li>
     *   <li>全局 pictures 中的任意图片</li>
     * </ol>
     *
     * @param sku       SKU 原始 Map
     * @param pictures  全局图片 Map（规格项 ID → 图片对象）
     * @param specKey   规格项 ID 拼接字符串（用于匹配图片）
     * @return 图片 URL，无图片时返回 null
     */
    private String resolvePictureUrl(Map<String, Object> sku, Map<String, Object> pictures, String specKey) {
        String fromSkuPicture = pickPictureUrl(pick(sku, "pictures", "picture", "pic_info", "picInfo"));
        if (fromSkuPicture != null) {
            return fromSkuPicture;
        }
        if (pictures.isEmpty()) {
            return null;
        }
        if (specKey != null) {
            for (String id : specKey.split("[_,|;:\\s]+")) {
                String picture = pickPictureUrl(pictures.get(id));
                if (picture != null) {
                    return picture;
                }
            }
        }
        return pickPictureUrl(pictures);
    }

    /**
     * 从图片对象中提取图片 URL。
     *
     * <p>若 value 是 Map，优先取 big_picture / bigPicture，回退到 little_picture / littlePicture，
     * 再回退到 picture_url / pictureUrl / url / cover / image / pic。
     * 若 value 是字符串，直接返回。</p>
     *
     * @param value 图片对象（Map 或 String）
     * @return 图片 URL，无法提取时返回 null
     */
    private String pickPictureUrl(Object value) {
        Map<String, Object> map = asMap(value);
        if (!map.isEmpty()) {
            return firstNonBlank(
                    asString(pick(map, "big_picture", "bigPicture")),
                    asString(pick(map, "little_picture", "littlePicture")),
                    asString(pick(map, "picture_url", "pictureUrl", "url", "cover", "image", "pic"))
            );
        }
        return asString(value);
    }

    /**
     * 从多个候选字符串中返回第一个非空白值。
     *
     * @param values 候选字符串列表
     * @return 第一个非空白字符串，全部为 null 或空白时返回 null
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 根据商品名称和规格明细拼接 SKU 名称。
     *
     * <p>将非空白的 spec1/spec2/spec3 用 " / " 拼接为规格名，
     * 再与 productName 组合返回。若规格部分为空则返回 productName。</p>
     *
     * @param productName 商品名称（可为 null）
     * @param spec1       规格 1（如颜色）
     * @param spec2       规格 2（如尺码）
     * @param spec3       规格 3（如材质）
     * @return 拼接后的 SKU 名称，全部为空时返回 null
     */
    private String buildSkuName(String productName, String spec1, String spec2, String spec3) {
        List<String> parts = new java.util.ArrayList<>();
        if (spec1 != null && !spec1.isBlank()) parts.add(spec1);
        if (spec2 != null && !spec2.isBlank()) parts.add(spec2);
        if (spec3 != null && !spec3.isBlank()) parts.add(spec3);
        if (parts.isEmpty()) {
            return productName;
        }
        String specName = String.join(" / ", parts);
        if (productName == null || productName.isBlank()) {
            return specName;
        }
        return productName + " " + specName;
    }

    /**
     * 将任意类型的值安全转换为 int，解析失败时返回默认值。
     *
     * <p>支持 Number（直接取 intValue）和 String（Integer.parseInt）类型。</p>
     *
     * @param value        待转换的值
     * @param defaultValue 默认值
     * @return 转换后的 int 值
     */
    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
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
        int status = normalizeProductStatus((int) asLong(pick(raw, "status"), 0L));
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
     * 将商品状态码转为中文描述。
     *
     * @param status 商品状态码（0=待审核, 1=推广中, 2=申请未通过, 3=合作已终止, 6=合作已到期；4 为历史兼容，按合作已终止展示）
     * @return 状态中文描述，未知状态返回 "未知状态"
     */
    private String productStatusText(int status) {
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "推广中";
            case 2 -> "申请未通过";
            case 3 -> "合作已终止";
            case 4 -> "合作已终止";
            case 6 -> "合作已到期";
            default -> "未知状态";
        };
    }

    private int normalizeProductStatus(int status) {
        return status == 4 ? 3 : status;
    }

    /**
     * 将任意类型的值安全转换为 boolean。
     *
     * <p>支持 Boolean、Number（非 0 = true）、String（"true"/"1"/"yes" = true）三种类型。
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
     * <p>用于兼容抖音 API 的 snake_case 和 camelCase 两种键名格式。</p>
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
     * <p>键统一转为 String，返回新的 LinkedHashMap 列表。</p>
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
     * <p>支持 Number（直接取 longValue）和 String（Long.parseLong，自动 trim）类型。
     * null 或解析异常时返回 defaultValue。</p>
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
     * 将任意类型的值安全转换为 String，null 或空白字符串时返回 null。
     *
     * <p>使用 {@link String#valueOf(Object)} 转换后 trim，
     * 空字符串视为 null，避免下游出现空白字符串误判。</p>
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
     * 记录网关调用日志，输出当前上游模式、脱敏后的 appId、shopId 和 authId。
     *
     * <p>日志格式示例：
     * {@code gateway=RealDouyinProductGateway, upstreamMode=live, appKey=abcd****efgh, shopId=123, authId=456}</p>
     *
     * @param appId 调用方传入的 appId；若为 null 则使用契约夹具配置的默认 appKey
     */
    private void logGateway(String appId) {
        log.info(
                "gateway=RealDouyinProductGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(appId == null ? contractFixtureProvider.appKey() : appId),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    /**
     * 对字符串进行脱敏处理，保留前 4 位和后 4 位，中间用 {@code ****} 替换。
     *
     * <p>用于日志输出时隐藏 appId 等敏感信息。
     * 长度不超过 8 的字符串不做脱敏直接返回。</p>
     *
     * @param value 待脱敏的字符串
     * @return 脱敏后的字符串；null 或空白时返回空字符串
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
