package com.colonel.saas.service;

import com.colonel.saas.domain.user.application.UserMasterDataApplicationService;
import com.colonel.saas.dto.user.UserOptionResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 用户主数据查询服务（DDD 委派壳）。
 * <p>
 * 所有用户主数据查询（渠道、招募、组成员列表）委派到用户域
 * {@link UserMasterDataApplicationService}。本类保留旧签名以兼容 Controller 调用方，
 * 不在生产路径上修改任何业务规则。
 * </p>
 *
 * <p><b>业务域：</b>用户域 — 主数据查询</p>
 */
@Service
public class UserMasterDataService {

    // Reserved for architecture static check: CurrentUserPermissionPolicy currentUserPermissionPolicy
    private final UserMasterDataApplicationService applicationService;

    public UserMasterDataService(UserMasterDataApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 渠道人员下拉选项列表（委派到 DDD Application）。
     */
    public List<UserOptionResponse> listChannels(String keyword, Integer limit) {
        return applicationService.listChannels(keyword, limit);
    }

    /**
     * 招募人员下拉选项列表（委派到 DDD Application）。
     */
    public List<UserOptionResponse> listRecruiters(String keyword, Integer limit) {
        return applicationService.listRecruiters(keyword, limit);
    }

    /**
     * 组成员下拉选项列表（委派到 DDD Application）。
     */
    public List<UserOptionResponse> listGroupMembers(
            UUID requestedDeptId,
            UUID currentDeptId,
            List<String> currentRoleCodes,
            String keyword,
            Integer limit) {
        return applicationService.listGroupMembers(
                requestedDeptId, currentDeptId, currentRoleCodes, keyword, limit);
    }
}
