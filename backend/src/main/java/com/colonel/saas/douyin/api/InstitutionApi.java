package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 精选联盟机构信息查询 API 客户端。
 * <p>
 * 封装抖音精选联盟机构信息的查询接口，支持 contract（合同模式）与真实上游两种调用路径。
 *
 * <ul>
 *   <li>机构信息查询 — 获取当前授权机构的基本信息</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 机构管理
 *
 * @see DouyinApiClient
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 */
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

    /**
     * 查询当前授权机构的基本信息。
     * <p>
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     *
     * @param appId 应用 ID（可为空，为空时使用全局默认 appId）
     * @return 机构信息响应，包含机构名称、等级等字段
     */
    public Map<String, Object> info(String appId) {
        // 第一步：contract 模式下直接返回契约桩数据，不调用真实上游
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildInstitutionInfoResponse(appId);
        }
        // 第二步：组装请求参数，可选传入目标应用 ID
        Map<String, Object> params = new HashMap<>();
        if (appId != null && !appId.isBlank()) {
            params.put("appId", appId.trim());
        }
        // 第三步：调用抖音精选联盟机构信息查询接口
        return douyinApiClient.post("buyin.institutionInfo", params);
    }
}
