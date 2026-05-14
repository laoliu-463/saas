package com.colonel.saas.gateway.douyin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface DouyinProductGateway {

    ActivityProductListResult queryActivityProducts(ActivityProductQueryRequest request);

    List<ProductSkuResult> queryProductSkus(String productId);

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

    record ProductSkuResult(
            String skuId,
            String skuName,
            Long price,
            Integer stock,
            String cover) {
    }
}
