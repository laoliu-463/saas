package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.colonel.application.ColonelPartnerContactUpdateRouter;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerAdminService;
import com.colonel.saas.service.ColonelPartnerSyncService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 团长主数据管理控制器，供管理员和招商组长维护团长联系方式与触发主数据同步。
 *
 * <ul>
 *   <li>更新指定团长的联系方式（手机号、微信号、联系人姓名）</li>
 *   <li>手动触发团长主数据全量同步</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 团长主数据管理
 * <p>API 路径前缀：{@code /admin/colonel-partners}（应用 context-path 为 {@code /api}）
 * <p>访问权限：管理员和招商组长（{@link com.colonel.saas.constant.RoleCodes#ADMIN}、{@link com.colonel.saas.constant.RoleCodes#BIZ_LEADER}）
 *
 * @see com.colonel.saas.service.ColonelPartnerAdminService
 * @see com.colonel.saas.service.ColonelPartnerSyncService
 */
@RestController
@RequestMapping("/admin/colonel-partners")
public class AdminColonelPartnerController {

    /** 团长主数据管理服务（传统路径，由 ColonelPartnerContactUpdateRouter 灰度切换） */
    private final ColonelPartnerAdminService colonelPartnerAdminService;

    /** 团长主数据同步服务，负责从外部数据源同步团长主数据 */
    private final ColonelPartnerSyncService colonelPartnerSyncService;

    /** 团长合作伙伴联系方式更新路由器（DDD-COLONEL-001） */
    private final ColonelPartnerContactUpdateRouter colonelPartnerContactUpdateRouter;

    /**
     * 构造注入团长主数据管理服务和同步服务。
     *
     * @param colonelPartnerAdminService            团长主数据管理服务实例（传统路径）
     * @param colonelPartnerSyncService             团长主数据同步服务实例
     * @param colonelPartnerContactUpdateRouter     团长联系方式更新路由器（DDD 灰度）
     */
    public AdminColonelPartnerController(
            ColonelPartnerAdminService colonelPartnerAdminService,
            ColonelPartnerSyncService colonelPartnerSyncService,
            ColonelPartnerContactUpdateRouter colonelPartnerContactUpdateRouter) {
        this.colonelPartnerAdminService = colonelPartnerAdminService;
        this.colonelPartnerSyncService = colonelPartnerSyncService;
        this.colonelPartnerContactUpdateRouter = colonelPartnerContactUpdateRouter;
    }

    /**
     * 更新团长联系方式。
     *
     * <p>处理流程（DDD-COLONEL-001 灰度）：
     * <ol>
     *   <li>调用路由器根据特性开关决定走 DDD 还是传统路径</li>
     *   <li>DDD 路径：{@link ColonelPartnerContactUpdateApplicationService}</li>
     *   <li>传统路径：{@link ColonelPartnerAdminService}（兼容兜底）</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /api/admin/colonel-partners/{id}/contact}
     *
     * @param id      团长主数据 ID
     * @param request 联系方式更新请求，包含手机号、微信号和联系人姓名
     * @param userId  当前操作人 ID（从 JWT 解析）
     * @return 更新后的团长主数据对象
     * @throws com.colonel.saas.common.exception.BusinessException 团长不存在或参数校验失败
     */
    @Operation(summary = "更新团长联系方式")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
    @PutMapping("/{id}/contact")
    public ApiResult<ColonelPartner> updateContact(
            @PathVariable UUID id,
            @Valid @RequestBody ColonelPartnerContactUpdateRequest request,
            @RequestAttribute("userId") UUID userId) {
        // 第一步：通过路由器灰度选择 DDD 或传统路径
        return ApiResult.ok(colonelPartnerContactUpdateRouter.updateContactInfo(id, request, userId));
    }

    /**
     * 手动触发团长主数据全量同步。
     *
     * <p>处理流程：
     * <ol>
     *   <li>调用同步服务从外部数据源拉取最新团长主数据</li>
     *   <li>执行全量 upsert 操作，更新本地团长记录</li>
     *   <li>返回本次同步影响的记录数</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /api/admin/colonel-partners/sync}
     *
     * @return 同步结果，包含 upserted（本次同步更新/插入的记录数）
     */
    @Operation(summary = "手动触发团长主数据同步")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
    @PostMapping("/sync")
    public ApiResult<Map<String, Object>> sync() {
        // 第一步：执行全量同步
        int upserted = colonelPartnerSyncService.syncAll();
        // 第二步：返回同步结果
        return ApiResult.ok(Map.of("upserted", upserted));
    }
}
