package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ProductApi {

    private final DouyinApiClient douyinApiClient;

    public ProductApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> listByActivity(String activityId) {
        Map<String, Object> params = new HashMap<>();
        params.put("activity_id", activityId);
        params.put("page_size", 20);
        params.put("cursor", 0);
        return douyinApiClient.post("buyin.colonel.product.list", params);
    }
}
