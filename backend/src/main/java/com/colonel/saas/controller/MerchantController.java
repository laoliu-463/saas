package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.service.MerchantService;
import com.colonel.saas.vo.PartnerDetailVO;
import com.colonel.saas.vo.PartnerProductVO;
import com.colonel.saas.vo.PartnerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 商家管理控制器，供管理员和招商人员管理商家归属与基本信息。
 *
 * <ul>
 *   <li>分页查询合作方列表，支持关键字和类型筛选</li>
 *   <li>查询合作方详情，包含基础信息和商品聚合统计</li>
 *   <li>分页查询合作方关联的活动商品快照</li>
 *   <li>管理员手动覆盖商家的当前归属人</li>
 * </ul>
 *
 * <p>所属业务领域：商品域 / 商家管理
 * <p>API 路径前缀：{@code /merchants}
 * <p>访问权限：类级别仅限管理员；部分接口开放给招商组长和招商专员
 *
 * @see com.colonel.saas.service.MerchantService
 */
@Validated
@Tag(name = "商家管理", description = "商家归属与基本信息管理。")
@RestController
@RequestMapping("/merchants")
@RequireRoles({RoleCodes.ADMIN})
public class MerchantController extends BaseController {

    /** 商家服务，负责合作方列表、详情、商品查询和归属覆盖等操作 */
    private final MerchantService merchantService;

    /**
     * 构造注入商家服务。
     *
     * @param merchantService 商家服务实例
     */
    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    /**
     * 分页查询合作方列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的关键字和合作方类型筛选条件</li>
     *   <li>从活动商品快照和商家沉淀数据中聚合查询商家型合作方</li>
     *   <li>返回分页后的合作方列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /merchants}
     *
     * @param keyword     关键字筛选，可为空
     * @param partnerType 合作方类型，可为空
     * @param page        当前页码，默认为 1
     * @param size        每页记录数，默认为 10
     * @return 分页后的合作方列表
     */
    @Operation(summary = "查询合作方列表", description = "从活动商品快照和商家沉淀数据中查询商家型合作方，用于商品域 list_partners。")
    @GetMapping
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    public ApiResult<PageResult<PartnerVO>> listPartners(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartners(keyword, partnerType, page, size));
    }

    /**
     * 查询合作方详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据合作方 ID 查找对应的合作方记录</li>
     *   <li>聚合查询合作方的基础信息和商品统计</li>
     *   <li>返回合作方详情视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /merchants/{id}}
     *
     * @param partnerId   合作方 ID，商家型通常为 shop_id 或 merchant_id
     * @param partnerType 合作方类型，可为空
     * @return 合作方详情，包含基础信息和商品聚合统计
     * @throws com.colonel.saas.common.exception.BusinessException 合作方不存在
     */
    @Operation(summary = "查询合作方详情", description = "查询商家型合作方的基础信息和商品聚合统计，用于商品域 get_partner_detail。")
    @GetMapping("/{id}")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    public ApiResult<PartnerDetailVO> getPartnerDetail(
            @Parameter(description = "合作方 ID，商家型通常为 shop_id 或 merchant_id。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType) {
        return ok(merchantService.getPartnerDetail(partnerId, partnerType));
    }

    /**
     * 分页查询合作方关联的活动商品。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据合作方 ID 和类型查找对应的合作方记录</li>
     *   <li>查询该合作方关联的活动商品快照列表</li>
     *   <li>返回分页后的商品列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /merchants/{id}/products}
     *
     * @param partnerId   合作方 ID；商家型为 shop_id/merchant_id，团长型为 colonel_buyin_id
     * @param partnerType 合作方类型，可为空
     * @param page        当前页码，默认为 1
     * @param size        每页记录数，默认为 10
     * @return 分页后的合作方商品列表
     */
    @Operation(summary = "查询合作方商品", description = "分页查询商家型合作方关联的活动商品快照，用于商品域 get_partner_products。")
    @GetMapping("/{id}/products")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    public ApiResult<PageResult<PartnerProductVO>> listPartnerProducts(
            @Parameter(description = "合作方 ID；商家型为 shop_id/merchant_id，团长型为 colonel_buyin_id。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartnerProducts(partnerId, partnerType, page, size));
    }

    /**
     * 管理员手动覆盖商家的当前归属人。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验归属覆盖请求参数的合法性</li>
     *   <li>更新商家的当前归属人</li>
     *   <li>记录归属覆盖原因的审计日志</li>
     *   <li>返回更新后的商家对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /merchants/{id}/override-assignee}
     *
     * @param merchantId 商家 merchant_id，字符串格式
     * @param request    归属覆盖请求，包含新归属人 ID 和覆盖原因
     * @param userId     当前操作人 ID（从 JWT 解析）
     * @return 更新后的商家对象
     * @throws com.colonel.saas.common.exception.BusinessException 商家不存在或参数校验失败
     */
    @Operation(summary = "归属覆盖", description = "组长级别手动覆盖商家的当前归属人，同时记录覆盖原因。")
    @PostMapping("/{id}/override-assignee")
    public ApiResult<Merchant> overrideAssignee(
            @Parameter(description = "商家 merchant_id，字符串格式。") @PathVariable("id") String merchantId,
            @RequestBody @jakarta.validation.Valid OverrideAssigneeRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ok(merchantService.overrideMerchantAssignment(merchantId, request.newUserId(), request.reason(), userId));
    }
}
