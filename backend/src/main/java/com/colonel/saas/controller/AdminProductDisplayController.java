package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.dto.display.ForceDisplayCancelRequest;
import com.colonel.saas.dto.display.ForceDisplaySwitchRequest;
import com.colonel.saas.entity.ProductDisplayAuditLog;
import com.colonel.saas.service.display.AdminProductDisplayService;
import com.colonel.saas.service.display.ProductDisplayAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 商品展示规则管理控制器，供管理员和招商组长管理商品的强制展示与取消强制展示。
 *
 * <ul>
 *   <li>管理员强制切换商品展示记录</li>
 *   <li>取消管理员强制展示，恢复商品自然展示状态</li>
 *   <li>查询展示规则的审计日志，追踪展示变更历史</li>
 * </ul>
 *
 * <p>所属业务领域：商品域 / 展示规则管理
 * <p>API 路径前缀：{@code /api/admin/products/display}
 * <p>访问权限：管理员和招商组长/专员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}、{@link com.colonel.saas.constant.RoleCodes#BIZ_LEADER}、{@link com.colonel.saas.constant.RoleCodes#BIZ_STAFF}）
 *
 * @see com.colonel.saas.service.display.AdminProductDisplayService
 * @see com.colonel.saas.service.display.ProductDisplayAuditService
 */
@RestController
@RequestMapping("/api/admin/products/display")
public class AdminProductDisplayController {

    /** 商品展示管理服务，负责强制切换和取消强制展示等管理操作 */
    private final AdminProductDisplayService adminProductDisplayService;

    /** 商品展示审计服务，负责记录和查询展示规则变更审计日志 */
    private final ProductDisplayAuditService productDisplayAuditService;

    /**
     * 构造注入商品展示管理服务和审计服务。
     *
     * @param adminProductDisplayService  商品展示管理服务实例
     * @param productDisplayAuditService  商品展示审计服务实例
     */
    public AdminProductDisplayController(
            AdminProductDisplayService adminProductDisplayService,
            ProductDisplayAuditService productDisplayAuditService) {
        this.adminProductDisplayService = adminProductDisplayService;
        this.productDisplayAuditService = productDisplayAuditService;
    }

    /**
     * 管理员强制切换商品展示记录。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验强制切换请求参数的合法性</li>
     *   <li>调用展示管理服务执行强制切换操作</li>
     *   <li>记录强制切换操作的审计日志</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /api/admin/products/display/force-switch}
     *
     * @param request 强制切换请求，包含关系 ID、切换原因和有效期
     * @param userId  当前操作人 ID（从 JWT 解析）
     * @return 空响应，表示操作成功
     * @throws com.colonel.saas.common.exception.BusinessException 展示记录不存在或参数校验失败
     */
    @Operation(summary = "管理员强制切换展示记录")
    @RequirePermission("admin-product-display:force-switch")
    @PostMapping("/force-switch")
    public ApiResult<Void> forceSwitch(
            @Valid @RequestBody ForceDisplaySwitchRequest request,
            @RequestAttribute("userId") UUID userId) {
        // 第一步：执行强制切换
        adminProductDisplayService.forceSwitch(
                request.relationId(),
                userId,
                request.reason(),
                request.until());
        return ApiResult.ok(null);
    }

    /**
     * 取消管理员强制展示，恢复商品自然展示状态。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验取消请求参数的合法性</li>
     *   <li>调用展示管理服务取消强制展示</li>
     *   <li>记录取消操作的审计日志</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /api/admin/products/display/cancel-force}
     *
     * @param request 取消强制请求，包含关系 ID
     * @param userId  当前操作人 ID（从 JWT 解析）
     * @return 空响应，表示操作成功
     * @throws com.colonel.saas.common.exception.BusinessException 展示记录不存在
     */
    @Operation(summary = "取消管理员强制展示")
    @RequirePermission("admin-product-display:cancel-force")
    @PostMapping("/cancel-force")
    public ApiResult<Void> cancelForce(
            @Valid @RequestBody ForceDisplayCancelRequest request,
            @RequestAttribute("userId") UUID userId) {
        // 第一步：取消强制展示
        adminProductDisplayService.cancelForce(request.relationId(), userId);
        return ApiResult.ok(null);
    }

    /**
     * 查询展示规则审计日志。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的商品 ID 筛选条件和分页参数</li>
     *   <li>查询展示规则变更的审计日志，按时间倒序返回</li>
     *   <li>返回分页后的审计日志列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /api/admin/products/display/audit-logs}
     *
     * @param productId 商品 ID，可为空表示查询全部
     * @param page      当前页码，默认为 1
     * @param size      每页记录数，默认为 20
     * @return 分页后的展示规则审计日志列表
     */
    @Operation(summary = "展示规则审计日志")
    @RequirePermission("admin-product-display:audit-logs")
    @GetMapping("/audit-logs")
    public ApiResult<PageResult<ProductDisplayAuditLog>> auditLogs(
            @Parameter(description = "商品 ID") @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        // 第一步：查询审计日志
        Page<ProductDisplayAuditLog> result = productDisplayAuditService.pageAuditLogs(productId, page, size);
        return ApiResult.ok(PageResult.of(result));
    }
}
