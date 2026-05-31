package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.ExclusiveMerchantDetailDTO;
import com.colonel.saas.service.ExclusiveMerchantQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 独家商家查询控制器，供管理员和招商人员查询独家商家状态。
 *
 * <ul>
 *   <li>查询当前登录用户关联的独家商家列表</li>
 *   <li>按合作方 ID 查询独家商家详情</li>
 * </ul>
 *
 * <p>所属业务领域：业绩域 / 独家商家
 * <p>API 路径前缀：{@code /exclusive-merchants}
 * <p>访问权限：管理员、招商组长、招商专员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}、{@link com.colonel.saas.constant.RoleCodes#BIZ_LEADER}、{@link com.colonel.saas.constant.RoleCodes#BIZ_STAFF}）
 * <p>注意：V1 保留接口，是否启用由配置控制。
 *
 * @see com.colonel.saas.service.ExclusiveMerchantQueryService
 */
@Tag(name = "独家商家", description = "独家商家查询（V1 保留接口，是否启用由配置控制）。")
@RestController
@RequestMapping("/exclusive-merchants")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
public class ExclusiveMerchantController extends BaseController {

    /** 独家商家查询服务，负责按用户或合作方维度查询独家商家信息 */
    private final ExclusiveMerchantQueryService exclusiveMerchantQueryService;

    /**
     * 构造注入独家商家查询服务。
     *
     * @param exclusiveMerchantQueryService 独家商家查询服务实例
     */
    public ExclusiveMerchantController(ExclusiveMerchantQueryService exclusiveMerchantQueryService) {
        this.exclusiveMerchantQueryService = exclusiveMerchantQueryService;
    }

    /**
     * 查询当前登录用户关联的独家商家列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>提取当前登录用户 ID</li>
     *   <li>查询该用户关联的所有独家商家记录</li>
     *   <li>返回独家商家详情列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /exclusive-merchants/my}
     *
     * @param userId 当前登录用户 ID（从 JWT 解析）
     * @return 当前用户关联的独家商家详情列表
     */
    @Operation(summary = "我的独家商家")
    @GetMapping("/my")
    public ApiResult<List<ExclusiveMerchantDetailDTO>> myExclusiveMerchants(
            @RequestAttribute("userId") UUID userId) {
        return ok(exclusiveMerchantQueryService.listMyExclusiveMerchants(userId));
    }

    /**
     * 按合作方 ID 查询独家商家详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据合作方 ID 查询独家商家状态</li>
     *   <li>返回独家商家详情，包含合作方信息和独家状态</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /exclusive-merchants/{partnerId}}
     *
     * @param partnerId 合作方 ID
     * @return 独家商家详情，包含合作方信息和独家状态
     */
    @Operation(summary = "查询合作方独家状态")
    @GetMapping("/{partnerId}")
    public ApiResult<ExclusiveMerchantDetailDTO> getByPartnerId(@PathVariable String partnerId) {
        return ok(exclusiveMerchantQueryService.getByPartnerId(partnerId));
    }
}
