package com.colonel.saas.gateway.douyin;

import com.colonel.saas.douyin.api.ActivityApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抖音活动管理 Gateway 接口。
 * <p>
 * 封装抖店精选联盟活动的 CRUD 能力，包括活动列表查询、活动商品管理、
 * 活动创建/更新、活动详情获取等。业务层（活动服务）依赖此接口操作抖店活动。
 * </p>
 *
 * <h3>实现切换</h3>
 * <p>
 * 通过配置 {@code douyin.test.enabled} 控制注入的实现：
 * <ul>
 *   <li>{@code true} - {@link com.colonel.saas.gateway.douyin.test.TestDouyinActivityGateway}，
 *       返回循环生成的 Mock 活动数据</li>
 *   <li>{@code false} - {@link com.colonel.saas.gateway.douyin.real.RealDouyinActivityGateway}，
 *       调用抖店 SDK 真实 API</li>
 * </ul>
 * </p>
 *
 * <h3>包含的内部 Record</h3>
 * <ul>
 *   <li>{@link ActivityMutateCommand} - 活动创建/更新命令</li>
 *   <li>{@link ActivityListQuery} - 活动列表查询条件</li>
 *   <li>{@link ActivityProductListQuery} - 活动商品列表查询条件</li>
 *   <li>{@link ActivityListResult} - 活动列表结果</li>
 *   <li>{@link ActivityItem} - 单个活动条目</li>
 *   <li>{@link ActivityProductListResult} - 活动商品列表结果</li>
 *   <li>{@link ActivityProductItem} - 单个活动商品条目</li>
 * </ul>
 */
public interface DouyinActivityGateway {

    /**
     * 查询活动列表。
     *
     * @param query 查询条件（含状态、搜索类型、分页参数等）
     * @return 活动列表结果（含总数、活动列表、机构 ID）
     */
    ActivityListResult listActivities(ActivityListQuery query);

    /**
     * 查询活动下的商品列表。
     *
     * @param query 查询条件（含活动 ID、搜索类型、游标分页等）
     * @return 活动商品列表结果（含总数、下一页游标、商品明细）
     */
    ActivityProductListResult listActivityProducts(ActivityProductListQuery query);

    /**
     * 创建或更新活动（旧接口，使用 ActivityApi 命令对象）。
     *
     * @param command 活动创建/更新命令
     * @return 上游原始响应 Map
     */
    Map<String, Object> createOrUpdate(ActivityApi.ActivityCreateOrUpdateCommand command);

    /**
     * 获取活动详情（上游原始格式）。
     * <p>
     * 调用 buyin.colonelActivityDetail 获取活动详情原始响应，
     * 用于元数据回填和管理后台探针。
     * </p>
     *
     * @param appId      应用 ID
     * @param activityId 活动 ID
     * @return 上游原始响应 Map
     */
    Map<String, Object> activityDetail(String appId, String activityId);

    /**
     * 取消活动商品。
     * <p>
     * 调用 alliance.colonelActivityProductCancel 取消活动中的商品。
     * </p>
     *
     * @param appId   应用 ID
     * @param payload 请求参数 Map（含活动 ID、商品 ID 等）
     * @return 上游原始响应 Map
     */
    Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload);

    /**
     * 创建或更新活动（新接口，使用 alliance 域命令）。
     * <p>
     * 调用 alliance.colonelActivityCreateOrUpdate 创建或更新活动。
     * </p>
     *
     * @param command 活动创建/更新命令
     * @return 上游原始响应 Map
     */
    Map<String, Object> createOrUpdateActivity(ActivityMutateCommand command);

    /**
     * 活动创建/更新命令。
     * <p>
     * 封装活动的所有可配置字段，用于 {@link #createOrUpdateActivity} 方法。
     * </p>
     *
     * @param appId               应用 ID
     * @param activityId          活动 ID（更新时必填，创建时为空）
     * @param applicationLimited  是否限制报名
     * @param isNewShop           是否新店专属
     * @param shopType            店铺类型
     * @param activityName        活动名称
     * @param activityDesc        活动描述
     * @param applyStartTime      报名开始时间
     * @param applyEndTime        报名结束时间
     * @param commissionRate      佣金比例
     * @param serviceRate         服务费率
     * @param wechatId            微信号
     * @param phoneNum            联系电话
     * @param estimatedSingleSale 预估单量
     * @param activityType        活动类型
     * @param specifiedShopIds    指定店铺 ID 列表
     * @param online              是否上线
     * @param categories          品类限制
     * @param shopScore           店铺评分要求
     * @param minPromotionDays    最少推广天数
     * @param thresholdCrossBorder 跨境门槛
     * @param minExclusionDuration 最短排他期限
     * @param adCommissionRate    广告佣金比例
     * @param adServiceRate       广告服务费率
     * @param cosLimitType        佣金限制类型
     */
    record ActivityMutateCommand(
            String appId,
            Long activityId,
            Boolean applicationLimited,
            Boolean isNewShop,
            String shopType,
            String activityName,
            String activityDesc,
            String applyStartTime,
            String applyEndTime,
            String commissionRate,
            String serviceRate,
            String wechatId,
            String phoneNum,
            String estimatedSingleSale,
            Integer activityType,
            String specifiedShopIds,
            Boolean online,
            String categories,
            Integer shopScore,
            Integer minPromotionDays,
            Integer thresholdCrossBorder,
            Integer minExclusionDuration,
            String adCommissionRate,
            String adServiceRate,
            Integer cosLimitType) {
    }

    /**
     * 活动列表查询条件。
     *
     * @param appId        应用 ID
     * @param status       活动状态筛选
     * @param searchType   搜索类型
     * @param sortType     排序类型
     * @param page         页码
     * @param pageSize     每页条数
     * @param activityInfo 活动搜索关键词
     */
    record ActivityListQuery(
            String appId,
            Integer status,
            Long searchType,
            Long sortType,
            Long page,
            Long pageSize,
            String activityInfo) {
    }

    /**
     * 活动商品列表查询条件。
     *
     * @param appId           应用 ID
     * @param activityId      活动 ID
     * @param searchType      搜索类型
     * @param sortType        排序类型
     * @param count           每页条数
     * @param cooperationInfo 合作信息
     * @param cooperationType 合作类型
     * @param productInfo     商品搜索关键词
     * @param status          商品状态筛选
     * @param retrieveMode    数据获取模式
     * @param cursor          分页游标
     * @param page            页码
     */
    record ActivityProductListQuery(
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
    }

    /**
     * 活动列表查询结果。
     *
     * @param test          是否为测试环境数据
     * @param institutionId 机构 ID
     * @param total         活动总数
     * @param activityList  活动条目列表
     */
    record ActivityListResult(
            boolean test,
            long institutionId,
            long total,
            List<ActivityItem> activityList) {

        /**
         * 将结果转换为 Map 格式，便于 JSON 序列化。
         *
         * @return 包含所有字段的有序 Map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("test", test);
            result.put("institutionId", institutionId);
            result.put("total", total);
            result.put("activityList", activityList.stream().map(ActivityItem::toMap).toList());
            return result;
        }
    }

    /**
     * 单个活动条目。
     * <p>
     * 包含活动基本信息、状态、报名时间窗口等。{@link #toMap()} 额外输出兼容字段
     * （activityStatus、startTime、endTime）。
     * </p>
     *
     * @param activityId            活动 ID
     * @param activityName          活动名称
     * @param activityStartTime     活动开始时间
     * @param activityEndTime       活动结束时间
     * @param status                活动状态码
     * @param statusText            活动状态文本
     * @param applicationStartTime  报名开始时间
     * @param applicationEndTime    报名结束时间
     * @param categoriesLimit       品类限制
     * @param colonelBuyinId        团长百应 ID
     */
    record ActivityItem(
            long activityId,
            String activityName,
            String activityStartTime,
            String activityEndTime,
            int status,
            String statusText,
            String applicationStartTime,
            String applicationEndTime,
            Object categoriesLimit,
            long colonelBuyinId) {

        public Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityId", activityId);
            item.put("activityName", activityName);
            item.put("activityStartTime", activityStartTime);
            item.put("activityEndTime", activityEndTime);
            item.put("status", status);
            item.put("statusText", statusText);
            item.put("applicationStartTime", applicationStartTime);
            item.put("applicationEndTime", applicationEndTime);
            item.put("categoriesLimit", categoriesLimit);
            item.put("colonelBuyinId", colonelBuyinId);
            item.put("activityStatus", status);
            item.put("startTime", activityStartTime);
            item.put("endTime", activityEndTime);
            return item;
        }
    }

    record ActivityProductListResult(
            boolean test,
            long activityId,
            long institutionId,
            Long total,
            String nextCursor,
            List<ActivityProductItem> items) {

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("test", test);
            result.put("activityId", activityId);
            result.put("institutionId", institutionId);
            result.put("total", total);
            result.put("nextCursor", nextCursor);
            result.put("items", items.stream().map(ActivityProductItem::toMap).toList());
            return result;
        }

        public List<Map<String, Object>> toSnapshotItems() {
            return items.stream().map(ActivityProductItem::toMap).toList();
        }
    }

    record ActivityProductItem(
            long productId,
            String title,
            String cover,
            long price,
            String priceText,
            long cosRatio,
            long cosFee,
            long activityCosRatio,
            String activityCosRatioText,
            int cosType,
            String cosTypeText,
            String adServiceRatio,
            Long activityAdCosRatio,
            boolean hasDouinGoodsTag,
            boolean inStock,
            long sales,
            long shopId,
            String shopName,
            String shopScore,
            int status,
            String statusText,
            String categoryName,
            String productStock,
            String colonelCouponInfo,
            String activityStartTime,
            String activityEndTime,
            String promotionStartTime,
            String promotionEndTime,
            String detailUrl,
            String originColonelBuyinId,
            Map<String, Object> rawPayload) {

        public Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            if (rawPayload != null && !rawPayload.isEmpty()) {
                item.putAll(rawPayload);
            }
            item.put("productId", productId);
            item.put("title", title);
            item.put("cover", cover);
            item.put("price", price);
            item.put("priceText", priceText);
            item.put("cosRatio", cosRatio);
            item.put("cosFee", cosFee);
            item.put("activityCosRatio", activityCosRatio);
            item.put("activityCosRatioText", activityCosRatioText);
            item.put("cosType", cosType);
            item.put("cosTypeText", cosTypeText);
            item.put("adServiceRatio", adServiceRatio);
            item.put("activityAdCosRatio", activityAdCosRatio);
            item.put("hasDouinGoodsTag", hasDouinGoodsTag);
            item.put("inStock", inStock);
            item.put("sales", sales);
            item.put("shopId", shopId);
            item.put("shopName", shopName);
            item.put("shopScore", shopScore);
            item.put("status", status);
            item.put("statusText", statusText);
            item.put("categoryName", categoryName);
            item.put("productStock", productStock);
            item.put("colonelCouponInfo", colonelCouponInfo);
            item.put("activityStartTime", activityStartTime);
            item.put("activityEndTime", activityEndTime);
            item.put("promotionStartTime", promotionStartTime);
            item.put("promotionEndTime", promotionEndTime);
            item.put("detailUrl", detailUrl);
            if (originColonelBuyinId != null && !originColonelBuyinId.isBlank()) {
                item.put("origin_colonel_buyin_id", originColonelBuyinId);
                item.put("originColonelBuyinId", originColonelBuyinId);
            }
            return item;
        }
    }
}
