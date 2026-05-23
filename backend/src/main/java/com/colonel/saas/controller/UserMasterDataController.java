package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.debug.DebugSessionLog;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.service.UserMasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "用户域主数据", description = "供商品、寄样、订单、达人等业务域复用的人员下拉接口。")
@RestController
@RequestMapping("/users/master-data")
public class UserMasterDataController extends BaseController {

    private final UserMasterDataService userMasterDataService;

    public UserMasterDataController(UserMasterDataService userMasterDataService) {
        this.userMasterDataService = userMasterDataService;
    }

    @Operation(summary = "渠道人员下拉", description = "返回渠道组长与渠道专员候选。")
    @GetMapping("/channels")
    public ApiResult<List<UserOptionResponse>> channels(
            @Parameter(description = "关键字，匹配用户名或姓名。") @RequestParam(required = false) String keyword,
            @Parameter(description = "最多返回条数，默认50，最大100。") @RequestParam(defaultValue = "50") Integer limit) {
        return ok(userMasterDataService.listChannels(keyword, limit));
    }

    @Operation(summary = "招商人员下拉", description = "返回招商组长、招商专员与招商组长兼容角色候选。")
    @GetMapping("/recruiters")
    public ApiResult<List<UserOptionResponse>> recruiters(
            @Parameter(description = "关键字，匹配用户名或姓名。") @RequestParam(required = false) String keyword,
            @Parameter(description = "最多返回条数，默认50，最大100。") @RequestParam(defaultValue = "50") Integer limit) {
        // #region agent log
        Map<String, Object> debugData = new LinkedHashMap<>();
        debugData.put("keyword", keyword);
        debugData.put("limit", limit);
        DebugSessionLog.write("H3", "UserMasterDataController.recruiters", "recruiters endpoint entered", debugData);
        // #endregion
        List<UserOptionResponse> options = userMasterDataService.listRecruiters(keyword, limit);
        // #region agent log
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("count", options == null ? 0 : options.size());
        DebugSessionLog.write("H3", "UserMasterDataController.recruiters", "recruiters endpoint success", resultData);
        // #endregion
        return ok(options);
    }

    @Operation(summary = "本组成员下拉", description = "组长默认查看本组成员；管理员可传 deptId 查看指定部门成员。")
    @GetMapping("/group-members")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER, RoleCodes.COLONEL_LEADER})
    public ApiResult<List<UserOptionResponse>> groupMembers(
            @Parameter(description = "管理员可指定部门ID。") @RequestParam(required = false) UUID deptId,
            @Parameter(description = "关键字，匹配用户名或姓名。") @RequestParam(required = false) String keyword,
            @Parameter(description = "最多返回条数，默认50，最大100。") @RequestParam(defaultValue = "50") Integer limit,
            @RequestAttribute(value = "deptId", required = false) UUID currentDeptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(userMasterDataService.listGroupMembers(
                deptId,
                currentDeptId,
                roleCodes == null ? Collections.emptyList() : roleCodes,
                keyword,
                limit
        ));
    }
}
