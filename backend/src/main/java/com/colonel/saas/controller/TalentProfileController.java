package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.ResolveTalentProfileRequest;
import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.service.TalentQueryService;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 达人真实资料控制器.
 *
 * <p>负责达人基础资料的解析与同步，属于达人域。
 * 支持通过抖音号、主页链接或分享链接拉取达人真实基础资料，
 * 也支持对已有达人重新执行资料同步。</p>
 *
 * <p>API 路径前缀：{@code /talents}</p>
 *
 * <p>访问权限：渠道负责人、渠道人员。</p>
 *
 * @see TalentProfileSyncService
 * @see TalentQueryService
 */
@Validated
@Tag(name = "达人真实资料", description = "真实达人基础资料解析、同步与人工补充。")
@RestController
@RequestMapping("/talents")
@RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class TalentProfileController extends BaseController {

    /** 达人资料同步服务 */
    private final TalentProfileSyncService talentProfileSyncService;
    /** 达人查询服务，用于权限校验 */
    private final TalentQueryService talentQueryService;

    /**
     * 构造注入.
     *
     * @param talentProfileSyncService 达人资料同步服务
     * @param talentQueryService       达人查询服务
     */
    public TalentProfileController(
            TalentProfileSyncService talentProfileSyncService,
            TalentQueryService talentQueryService) {
        this.talentProfileSyncService = talentProfileSyncService;
        this.talentQueryService = talentQueryService;
    }

    /**
     * 解析达人真实资料.
     *
     * <p>输入抖音号、主页链接或分享链接，按 provider 链拉取真实基础资料。
     * 支持强制刷新（forceRefresh）和人工补充模式（manualFill）。</p>
     *
     * @param request 解析请求，包含输入值和可选的强制刷新/人工补充标志
     * @return 解析后的达人资料
     * @throws com.colonel.saas.common.exception.BusinessException 输入无效或上游解析失败时抛出
     */
    @Operation(summary = "解析达人真实资料", description = "输入抖音号/主页链接/分享链接，按 provider 链拉取真实基础资料。")
    @PostMapping("/resolve-profile")
    public ApiResult<ResolveTalentProfileResponse> resolveProfile(
            @Valid @RequestBody ResolveTalentProfileRequest request) {
        // 解析请求中的布尔标志
        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        boolean manualFill = Boolean.TRUE.equals(request.getManualFill());
        return ok(talentProfileSyncService.resolveProfile(
                request.getInput(),
                forceRefresh,
                manualFill,
                request.getManualPayload()));
    }

    /**
     * 刷新已有达人真实资料.
     *
     * <p>对已有达人重新执行真实资料同步。同步失败时不覆盖旧的成功数据，
     * 确保数据稳定性。操作前会校验当前用户对该达人的操作权限。</p>
     *
     * @param id           达人主键 ID
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @param userId       当前登录用户 ID
     * @param deptId       当前用户所属部门 ID（可选）
     * @param roleCodes    当前用户角色编码列表（可选）
     * @return 同步后的达人资料
     * @throws com.colonel.saas.common.exception.BusinessException 无操作权限或达人不存在时抛出
     */
    @Operation(summary = "刷新达人真实资料", description = "对已有达人重新执行真实资料同步；失败不覆盖旧成功数据。")
    @PostMapping("/{id}/sync-profile")
    public ApiResult<ResolveTalentProfileResponse> syncProfile(
            @Parameter(description = "达人主键 ID") @PathVariable("id") UUID id,
            @RequestParam(name = "forceRefresh", defaultValue = "false") boolean forceRefresh,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 校验当前用户是否有权限操作该达人
        talentQueryService.assertCanOperate(id, userId, deptId, roleCodes);
        return ok(talentProfileSyncService.syncExistingProfile(id, forceRefresh));
    }
}
