package com.colonel.saas.gateway.douyin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抖音商品 Gateway 接口。
 * <p>
 * 封装抖店活动商品查询和 SKU 查询能力。业务层只依赖此接口，
 * 不感知底层是真实 SDK 调用（{@link com.colonel.saas.gateway.douyin.real.RealDouyinProductGateway}）
 * 还是测试 Mock 实现（{@link com.colonel.saas.gateway.douyin.test.TestDouyinProductGateway}）。
 * </p>
 *
 * <h3>实现切换</h3>
 * <p>
 * 通过配置 {@code douyin.test.enabled} 控制注入的实现：
 * <ul>
 *   <li>{@code true} - 注入 Test 实现，返回 Mock 数据</li>
 *   <li>{@code false} - 注入 Real 实现，调用抖店 SDK API</li>
 * </ul>
 * </p>
 *
 * <h3>包含的内部 Record</h3>
 * <ul>
 *   <li>{@link ActivityProductQueryRequest} - 活动商品查询请求参数</li>
 *   <li>{@link ActivityProductListResult} - 活动商品列表结果（含分页游标）</li>
 *   <li>{@link ActivityProductItem} - 单个活动商品明细（含佣金、库存、店铺等信息）</li>
 *   <li>{@link ProductSkuResult} - 商品 SKU 信息</li>
 * </ul>
 */
public interface DouyinProductGateway {

    /**
     * 查询活动下的商品列表。
     *
     * @param request 查询请求参数（含活动 ID、搜索条件、分页游标等）
     * @return 活动商品列表结果（含总条数、下一页游标、商品明细列表）
     */
    ActivityProductListResult queryActivityProducts(ActivityProductQueryRequest request);

    /**
     * 查询指定商品的 SKU 列表。
     *
     * @param productId 抖音商品 ID
     * @return 该商品的 SKU 列表（含 SKU ID、名称、价格、库存、封面）
     */
    List<ProductSkuResult> queryProductSkus(String productId);

    /**
     * 活动商品查询请求参数。
     * <p>
     * 用于 {@link #queryActivityProducts} 方法，支持按搜索条件、排序方式、分页游标等筛选活动商品。
     * </p>
     *
     * @param appId            应用 ID
     * @param activityId       活动 ID
     * @param searchType       搜索类型（抖店侧枚举值）
     * @param sortType         排序类型（抖店侧枚举值）
     * @param count            每页条数
     * @param cooperationInfo  合作信息
     * @param cooperationType  合作类型
     * @param productInfo      商品搜索关键词
     * @param status           商品状态筛选
     * @param retrieveMode     数据获取模式
     * @param cursor           分页游标（首次查询留空，后续传上次返回的 nextCursor）
     * @param page             页码（与 cursor 二选一使用）
     */
    record ActivityProductQueryRequest(
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
     * 活动商品列表查询结果。
     * <p>
     * 封装分页查询返回的商品列表，包含总条数和下一页游标。
     * {@link #toMap()} 方法用于将结果转为 Map 格式，便于 API 层直接序列化返回。
     * </p>
     *
     * @param test         是否为测试环境数据
     * @param activityId   活动 ID
     * @param institutionId 机构 ID
     * @param total        符合条件的商品总数
     * @param nextCursor   下一页游标（为 null 或空表示已无更多数据）
     * @param items        当前页商品明细列表
     */
    record ActivityProductListResult(
            boolean test,
            long activityId,
            long institutionId,
            Long total,
            String nextCursor,
            List<ActivityProductItem> items) {

        /**
         * 将结果转换为 Map 格式，便于 JSON 序列化。
         *
         * @return 包含所有字段的有序 Map
         */
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
    }

    /**
     * 单个活动商品明细。
     * <p>
     * 包含商品基本信息（ID、标题、封面）、价格与佣金信息、库存与销售数据、
     * 店铺信息、活动时间窗口等。{@link #toMap()} 方法将所有字段（含原始负载）合并输出。
     * </p>
     *
     * @param productId              商品 ID
     * @param title                  商品标题
     * @param cover                  封面图片 URL
     * @param price                  商品价格（单位：分）
     * @param priceText              价格文本展示
     * @param cosRatio               佣金比例（万分比）
     * @param cosFee                 佣金金额（单位：分）
     * @param activityCosRatio       活动佣金比例（万分比）
     * @param activityCosRatioText   活动佣金比例文本
     * @param cosType                佣金类型
     * @param cosTypeText            佣金类型文本
     * @param adServiceRatio         广告服务费率
     * @param activityAdCosRatio     活动广告佣金比例
     * @param hasDouinGoodsTag       是否有抖店商品标签
     * @param inStock                是否有库存
     * @param sales                  销量
     * @param shopId                 店铺 ID
     * @param shopName               店铺名称
     * @param shopScore              店铺评分
     * @param status                 商品状态码
     * @param statusText             商品状态文本
     * @param categoryName           分类名称
     * @param productStock           库存文本
     * @param colonelCouponInfo      团长优惠券信息
     * @param activityStartTime      活动开始时间
     * @param activityEndTime        活动结束时间
     * @param promotionStartTime     推广开始时间
     * @param promotionEndTime       推广结束时间
     * @param detailUrl              商品详情页 URL
     * @param originColonelBuyinId   原始团长百应 ID（跨活动继承场景）
     * @param rawPayload             上游原始响应数据（用于排查和透传）
     */
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

    /**
     * 商品 SKU 信息。
     * <p>
     * 由 {@link #queryProductSkus} 方法返回，描述商品的单个规格变体。
     * </p>
     *
     * @param skuId   SKU ID
     * @param skuName SKU 名称（如"红色 / XL"）
     * @param price   SKU 价格（单位：分）
     * @param stock   SKU 库存数量
     * @param cover   SKU 封面图片 URL
     */
    record ProductSkuResult(
            String skuId,
            String skuName,
            Long price,
            Integer stock,
            String cover) {
    }
}
