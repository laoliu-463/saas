package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class InstitutionApi {

    private final DouyinApiClient douyinApiClient;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public InstitutionApi(
            DouyinApiClient douyinApiClient,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.douyinApiClient = douyinApiClient;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    public Map<String, Object> info(String appId) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildInstitutionInfoResponse(appId);
        }
        Map<String, Object> params = new HashMap<>();
        if (appId != null && !appId.isBlank()) {
            params.put("appId", appId.trim());
        }
        return douyinApiClient.post("buyin.institutionInfo", params);
    }
}
