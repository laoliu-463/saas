package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TalentApi {

    private final DouyinApiClient douyinApiClient;

    public TalentApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> convertLink(String productUrl, String pickExtra) {
        Map<String, Object> params = new HashMap<>();
        params.put("product_url", productUrl);
        params.put("pick_extra", normalizePickExtra(pickExtra));
        return douyinApiClient.post("buyin.instPickSourceConvert", params);
    }

    private String normalizePickExtra(String pickExtra) {
        if (pickExtra == null) {
            return null;
        }
        if (pickExtra.length() <= 20) {
            return pickExtra;
        }
        return pickExtra.substring(pickExtra.length() - 20);
    }
}
