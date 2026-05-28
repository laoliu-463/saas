package com.colonel.saas.douyin.api;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 精选联盟商品管理 API 客户端。
 * <p>
 * 封装抖音精选联盟活动商品查询与 SKU 查询接口，
 * 支持 contract（合同模式）与真实上游两种调用路径，
 * 内置双佣金字段适配逻辑。
 *
 * <ul>
 *   <li>活动列表查询 — 查询活动列表（供商品关联使用）</li>
 *   <li>活动商品查询 — 按活动 ID 分页/游标查询商品列表</li>
 *   <li>商品 SKU 查询 — 获取商品的 SKU 明细信息</li>
 *   <li>双佣金适配 — 自动补充 cos_type、dual_commission_enabled 等字段</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 商品管理
 *
 * @see DouyinApiClient
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 */
@Service
public class ProductApi {

    /** 默认每页数量 */
    private static final int DEFAULT_COUNT = 20;
    private static final int MAX_COUNT = 20;

    private final DouyinApiClient douyinApiClient;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public ProductApi(
            DouyinApiClient douyinApiClient,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.douyinApiClient = douyinApiClient;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    /**
     * 按条件查询活动列表（供商品关联使用）。
     * <p>
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     *
     * @param appId        应用 ID（可为空）
     * @param status       活动状态筛选，可选值：0/1/2/3/4/5/7，默认 0
     * @param searchType   搜索类型，可选值：0/1/2，默认 0
     * @param sortType     排序类型，0-降序，1-升序，默认 1
     * @param page         页码，从 1 开始
     * @param pageSize     每页数量，范围 1~20，默认 20
     * @param activityInfo 活动名称关键词
     * @return 活动列表响应
     */
    public Map<String, Object> listActivities(
            String appId,
            Integer status,
            Long searchType,
            Long sortType,
            Long page,
            Long pageSize,
            String activityInfo) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityListResponse(appId, status, searchType, sortType, page, pageSize, activityInfo);
        }
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        params.put("status", normalizeStatus(status));
        params.put("search_type", normalizeSearchType(searchType));
        params.put("sort_type", normalizeSortType(sortType));
        params.put("page", normalizePage(page));
        params.put("page_size", normalizePageSize(pageSize));
        putIfNotBlank(params, "activity_info", activityInfo);
        return douyinApiClient.post("alliance.instituteColonelActivityList", params);
    }

    /**
     * 按活动 ID 查询商品列表（默认参数）。
     *
     * @param appId      应用 ID
     * @param activityId 活动 ID（数字字符串）
     * @return 商品列表响应
     */
    public Map<String, Object> listByActivity(String appId, String activityId) {
        return listProductsByActivity(appId, activityId, null, null);
    }

    /**
     * 按活动 ID 分页查询商品列表（简化参数）。
     *
     * @param appId      应用 ID
     * @param activityId 活动 ID
     * @param count      每页数量
     * @param cursor     分页游标
     * @return 商品列表响应（含双佣金字段适配）
     */
    public Map<String, Object> listProductsByActivity(String appId, String activityId, Integer count, String cursor) {
        return listProductsByActivity(
                appId,
                activityId,
                4L,
                1L,
                count,
                null,
                null,
                null,
                null,
                1L,
                cursor,
                null
        );
    }

    /**
     * 按活动 ID 查询商品列表（完整参数）。
     * <p>
     * 支持分页模式（retrieve_mode=0）和游标模式（retrieve_mode=1）两种翻页方式。
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     * 响应结果会经过双佣金字段适配处理。
     *
     * @param appId            应用 ID
     * @param activityId       活动 ID
     * @param searchType       搜索类型，可选值：0/1/2/4，默认 4
     * @param sortType         排序类型，0-降序，1-升序，默认 1
     * @param count            每页数量，上限 20
     * @param cooperationInfo  合作信息关键词
     * @param cooperationType  合作类型，可选值：0/1/2，默认 0
     * @param productInfo      商品名称关键词
     * @param status           商品状态筛选，可选值：0/1/2/3/4/6
     * @param retrieveMode     翻页模式：0-分页，1-游标，默认 1
     * @param cursor           游标值（retrieveMode=1 时使用）
     * @param page             页码（retrieveMode=0 时使用）
     * @return 商品列表响应（含双佣金字段适配）
     * @throws BusinessException 当参数校验不通过时抛出
     */
    public Map<String, Object> listProductsByActivity(
            String appId,
            String activityId,
            Long searchType,
            Long sortType,
            Integer count,
            String cooperationInfo,
            Integer cooperationType,
            String productInfo,
            Integer status,
            Long retrieveMode,
            String cursor,
            Long page) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildProductListResponse(appId, activityId, count, cursor, productInfo, status, retrieveMode, page);
        }
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        params.put("activity_id", parseActivityId(activityId));
        params.put("count", normalizeCount(count));
        params.put("search_type", normalizeProductSearchType(searchType));
        params.put("sort_type", normalizeProductSortType(sortType));
        putIfNotBlank(params, "cooperation_info", cooperationInfo);
        params.put("cooperation_type", normalizeCooperationType(cooperationType));
        putIfNotBlank(params, "product_info", productInfo);
        putIfNotNull(params, "status", normalizeProductStatus(status));

        long mode = normalizeRetrieveMode(retrieveMode);
        params.put("retrieve_mode", mode);
        if (mode == 0L) {
            params.put("page", normalizePage(page));
        } else {
            putIfNotBlank(params, "cursor", cursor);
        }
        Map<String, Object> response = douyinApiClient.post("alliance.colonelActivityProduct", params);
        return adaptDualCommissionFields(response);
    }

    /**
     * 查询精选联盟商品 SKU（/buyin/productSkus/v2）。
     * @param productId 精选联盟商品 ID（19位数字字符串）
     */
    public Map<String, Object> getProductSkusV2(String productId) {
        Map<String, Object> params = new HashMap<>();
        params.put("product_id", parseProductId(productId));
        return douyinApiClient.post("buyin.productSkus.v2", params);
    }

    private long parseProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw BusinessException.param("productId 不能为空");
        }
        try {
            return Long.parseLong(productId.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.param("productId 必须为数字类型", e);
        }
    }

    private void putIfNotBlank(Map<String, Object> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> params, String key, Object value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    private int normalizeCount(Integer count) {
        if (count == null || count <= 0) {
            return DEFAULT_COUNT;
        }
        return Math.min(count, MAX_COUNT);
    }

    private long parseActivityId(String activityId) {
        if (activityId == null || activityId.isBlank()) {
            throw BusinessException.param("activityId 不能为空，且必须为数字");
        }
        try {
            return Long.parseLong(activityId.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.param("activityId 必须为数字类型", e);
        }
    }

    /**
     * 适配商品列表响应中的双佣金字段。
     * <p>
     * 遍历响应中 data.data 列表的每个商品项，补充以下字段：
     * <ul>
     *   <li>cos_type — 数字类型标准化</li>
     *   <li>dual_commission_enabled — cos_type==1 时为 true</li>
     *   <li>ad_service_ratio — 广告服务费率</li>
     *   <li>activity_ad_cos_ratio — 活动广告 COS 比率</li>
     * </ul>
     *
     * @param response 原始 API 响应
     * @return 适配后的响应
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> adaptDualCommissionFields(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object dataObj = response.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMapRaw)) {
            return response;
        }
        Object listObj = dataMapRaw.get("data");
        if (!(listObj instanceof Iterable<?> list)) {
            return response;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawItem)) {
                continue;
            }
            Map<String, Object> itemMap = (Map<String, Object>) rawItem;
            int cosType = toInt(itemMap.get("cos_type"), 0);
            itemMap.put("cos_type", cosType);
            itemMap.put("dual_commission_enabled", cosType == 1);
            itemMap.put("ad_service_ratio", toStringOrNull(itemMap.get("ad_service_ratio")));
            itemMap.put("activity_ad_cos_ratio", toStringOrNull(itemMap.get("activity_ad_cos_ratio")));
        }
        return response;
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private int normalizeStatus(Integer status) {
        int value = status == null ? 0 : status;
        List<Integer> valid = List.of(0, 1, 2, 3, 4, 5, 7);
        if (!valid.contains(value)) {
            throw BusinessException.param("status 非法，可选值：0/1/2/3/4/5/7");
        }
        return value;
    }

    private long normalizeProductSearchType(Long searchType) {
        long value = searchType == null ? 4L : searchType;
        List<Long> valid = List.of(0L, 1L, 2L, 4L);
        if (!valid.contains(value)) {
            throw BusinessException.param("search_type invalid, expected one of 0/1/2/4");
        }
        return value;
    }

    private long normalizeProductSortType(Long sortType) {
        long value = sortType == null ? 1L : sortType;
        if (value != 0L && value != 1L) {
            throw BusinessException.param("sort_type invalid, expected one of 0/1");
        }
        return value;
    }

    private int normalizeCooperationType(Integer cooperationType) {
        int value = cooperationType == null ? 0 : cooperationType;
        List<Integer> valid = List.of(0, 1, 2);
        if (!valid.contains(value)) {
            throw BusinessException.param("cooperation_type invalid, expected one of 0/1/2");
        }
        return value;
    }

    private Integer normalizeProductStatus(Integer status) {
        if (status == null) {
            return null;
        }
        List<Integer> valid = List.of(0, 1, 2, 3, 4, 6);
        if (!valid.contains(status)) {
            throw BusinessException.param("status invalid, expected one of 0/1/2/3/4/6");
        }
        return status;
    }

    private long normalizeRetrieveMode(Long retrieveMode) {
        long value = retrieveMode == null ? 1L : retrieveMode;
        if (value != 0L && value != 1L) {
            throw BusinessException.param("retrieve_mode invalid, expected one of 0/1");
        }
        return value;
    }

    private long normalizeSearchType(Long searchType) {
        long value = searchType == null ? 0L : searchType;
        List<Long> valid = List.of(0L, 1L, 2L);
        if (!valid.contains(value)) {
            throw BusinessException.param("search_type 非法，可选值：0/1/2");
        }
        return value;
    }

    private long normalizeSortType(Long sortType) {
        long value = sortType == null ? 1L : sortType;
        if (value != 0L && value != 1L) {
            throw BusinessException.param("sort_type 非法，可选值：0/1");
        }
        return value;
    }

    private long normalizePage(Long page) {
        long value = page == null ? 1L : page;
        if (value < 1L) {
            throw BusinessException.param("page 必须大于等于 1");
        }
        return value;
    }

    private long normalizePageSize(Long pageSize) {
        long value = pageSize == null ? 20L : pageSize;
        if (value < 1L || value > 20L) {
            throw BusinessException.param("page_size 必须在 1~20 之间");
        }
        return value;
    }

}
