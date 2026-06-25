package com.colonel.saas.domain.user.application;

import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.service.UserMasterDataService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 用户主数据应用服务。
 *
 * <p>收口 {@code /users/master-data/**} HTTP 出口，现阶段委派既有
 * {@link UserMasterDataService}，保持主数据查询行为不变。</p>
 */
@Service
public class UserMasterDataApplicationService {

    private final UserMasterDataService userMasterDataService;

    public UserMasterDataApplicationService(UserMasterDataService userMasterDataService) {
        this.userMasterDataService = userMasterDataService;
    }

    public List<UserOptionResponse> listChannels(String keyword, Integer limit) {
        return userMasterDataService.listChannels(keyword, limit);
    }

    public List<UserOptionResponse> listRecruiters(String keyword, Integer limit) {
        return userMasterDataService.listRecruiters(keyword, limit);
    }

    public List<UserOptionResponse> listGroupMembers(
            UUID requestedDeptId,
            UUID currentDeptId,
            List<String> roleCodes,
            String keyword,
            Integer limit) {
        return userMasterDataService.listGroupMembers(
                requestedDeptId,
                currentDeptId,
                roleCodes == null ? Collections.emptyList() : roleCodes,
                keyword,
                limit
        );
    }
}
