package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OrderApi {

    private final DouyinApiClient douyinApiClient;

    public OrderApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    public Map<String, Object> listSettlement(long startTime, long endTime, int pageSize, String cursor) {
        Map<String, Object> params = new HashMap<>();
        params.put("start_time", startTime);
        params.put("end_time", endTime);
        params.put("page_size", pageSize);
        params.put("cursor", cursor);
        return douyinApiClient.post("buyin.settlement.order.list", params);
    }
}
