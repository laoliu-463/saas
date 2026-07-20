package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品域合作方控制器，供招商人员和管理员查询合作方列表、详情和商品。
 *
 * <ul>
 *   <li>分页查询合作方列表，支持关键字和类型筛选（商家型/团长型）</li>
 *   <li>查询指定合作方详情，包含基础信息和聚合统计</li>
 *   <li>分页查询合作方关联的活动商品快照</li>
 * </ul>
 *
 * <p>所属业务领域：商品域 / 合作方
 * <p>API 路径前缀：{@code /colonel/partners}
 * <p>访问权限：招商组长、招商专员和管理员（{@link com.colonel.saas.constant.RoleCodes#BIZ_LEADER}、{@link com.colonel.saas.constant.RoleCodes#BIZ_STAFF}、{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
 * <p>契约别名：list_partners / get_partner_detail / get_partner_products
 *
 * @see com.colonel.saas.service.MerchantService
 */
@Validated
@RestController
@Tag(name = "商品域合作方", description = "商品域 list_partners 等接口；商家型来自商品快照/商家表，团长型来自订单/活动/归因映射聚合。")
@RequestMapping("/colonel/partners")
@RequirePermission("colonel-partner:access")
public class ColonelPartnerController extends BaseController {

    /** 商家服务，负责合作方列表、详情和商品查询等操作 */
    private final MerchantService merchantService;

    /**
     * 构造注入商家服务。
     *
     * @param merchantService 商家服务实例
     */
    public ColonelPartnerController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    /**
     * 分页查询合作方列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的关键字和合作方类型筛选条件</li>
     *   <li>从活动商品快照和商家沉淀数据中聚合查询合作方</li>
     *   <li>返回分页后的合作方列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /colonel/partners}
     *
     * @param keyword     关键字筛选，可为空
     * @param partnerType 合作方类型（merchant/colonel），可为空
     * @param page        当前页码，默认为 1
     * @param size        每页记录数，默认为 10
     * @return 分页后的合作方列表
     */
    @Operation(summary = "合作方列表", description = "商品域 list_partners。")
    @GetMapping
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
     *   <li>根据合作方 ID 和类型查找对应的合作方记录</li>
     *   <li>聚合查询合作方的基础信息和商品统计</li>
     *   <li>返回合作方详情视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /colonel/partners/{id}}
     *
     * @param partnerId   合作方 ID
     * @param partnerType 合作方类型，可为空
     * @return 合作方详情，包含基础信息和商品聚合统计
     * @throws com.colonel.saas.common.exception.BusinessException 合作方不存在
     */
    @Operation(summary = "合作方详情", description = "商品域 get_partner_detail。")
    @GetMapping("/{id}")
    public ApiResult<PartnerDetailVO> getPartnerDetail(
            @Parameter(description = "合作方 ID。") @PathVariable("id") String partnerId,
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
     * <p>HTTP 方法与路径：{@code GET /colonel/partners/{id}/products}
     *
     * @param partnerId   合作方 ID
     * @param partnerType 合作方类型，可为空
     * @param page        当前页码，默认为 1
     * @param size        每页记录数，默认为 10
     * @return 分页后的合作方商品列表
     */
    @Operation(summary = "合作方商品", description = "商品域 get_partner_products。")
    @GetMapping("/{id}/products")
    public ApiResult<PageResult<PartnerProductVO>> listPartnerProducts(
            @Parameter(description = "合作方 ID。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartnerProducts(partnerId, partnerType, page, size));
    }
}
