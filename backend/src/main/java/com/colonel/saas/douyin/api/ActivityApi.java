package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class ActivityApi {

    private final DouyinApiClient douyinApiClient;

    public ActivityApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> list() {
        long endTime = Instant.now().getEpochSecond();
        long startTime = endTime - 7 * 24 * 3600;
        Map<String, Object> params = new HashMap<>();
        params.put("start_time", startTime);
        params.put("end_time", endTime);
        params.put("page_size", 20);
        return douyinApiClient.post("buyin.colonel.activity.list", params);
    }
}
