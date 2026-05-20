package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.ResolveTalentProfileRequest;
import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.service.talent.profile.TalentProfileSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@Tag(name = "达人真实资料", description = "真实达人基础资料解析、同步与人工补充。")
@RestController
@RequestMapping("/talents")
@RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class TalentProfileController extends BaseController {

    private final TalentProfileSyncService talentProfileSyncService;

    public TalentProfileController(TalentProfileSyncService talentProfileSyncService) {
        this.talentProfileSyncService = talentProfileSyncService;
    }

    @Operation(summary = "解析达人真实资料", description = "输入抖音号/主页链接/分享链接，按 provider 链拉取真实基础资料。")
    @PostMapping("/resolve-profile")
    public ApiResult<ResolveTalentProfileResponse> resolveProfile(
            @Valid @RequestBody ResolveTalentProfileRequest request) {
        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        boolean manualFill = Boolean.TRUE.equals(request.getManualFill());
        return ok(talentProfileSyncService.resolveProfile(
                request.getInput(),
                forceRefresh,
                manualFill,
                request.getManualPayload()));
    }

    @Operation(summary = "刷新达人真实资料", description = "对已有达人重新执行真实资料同步；失败不覆盖旧成功数据。")
    @PostMapping("/{id}/sync-profile")
    public ApiResult<ResolveTalentProfileResponse> syncProfile(
            @Parameter(description = "达人主键 ID") @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return ok(talentProfileSyncService.syncExistingProfile(id, forceRefresh));
    }
}
