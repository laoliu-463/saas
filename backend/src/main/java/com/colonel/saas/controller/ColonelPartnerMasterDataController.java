package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerMasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 团长主数据查询控制器，供招商人员和管理员查询团长主数据列表和详情。
 *
 * <ul>
 *   <li>分页查询团长主数据列表，支持关键字、来源和联系方式筛选</li>
 *   <li>按 ID 查询团长主数据详情</li>
 *   <li>查询团长来源下拉数据</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 团长主数据
 * <p>API 路径前缀：{@code /api/colonel-partners}
 * <p>访问权限：招商组长、招商专员和管理员（{@link com.colonel.saas.constant.RoleCodes#BIZ_LEADER}、{@link com.colonel.saas.constant.RoleCodes#BIZ_STAFF}、{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
 *
 * <p><b>不变量：</b>本控制器只读，不写业务规则；不参与业绩归属、提成、独家覆盖。</p>
 *
 * @see com.colonel.saas.service.ColonelPartnerMasterDataService
 */
@RestController
@RequestMapping("/api/colonel-partners")
public class ColonelPartnerMasterDataController {

    /** 团长主数据查询服务，负责团长数据的分页查询、详情查询和来源枚举 */
    private final ColonelPartnerMasterDataService colonelPartnerMasterDataService;

    /**
     * 构造注入团长主数据查询服务。
     *
     * @param colonelPartnerMasterDataService 团长主数据查询服务实例
     */
    public ColonelPartnerMasterDataController(ColonelPartnerMasterDataService colonelPartnerMasterDataService) {
        this.colonelPartnerMasterDataService = colonelPartnerMasterDataService;
    }

    /**
     * 分页查询团长主数据列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的关键字、来源和联系方式筛选条件</li>
     *   <li>委托服务层构建 LambdaQueryWrapper 动态拼接查询条件</li>
     *   <li>按最后同步时间倒序、团长名称正序排列</li>
     *   <li>执行分页查询并返回结果</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /api/colonel-partners}
     *
     * @param keyword    关键字筛选，模糊匹配团长名称，可为空
     * @param source     数据来源筛选，可为空
     * @param hasContact 是否有联系方式，true=有/false=无/null=不限
     * @param page       当前页码，默认为 1
     * @param size       每页记录数，默认为 20
     * @return 分页后的团长主数据列表
     */
    @Operation(summary = "团长主数据列表")
    @RequirePermission("colonel-partner-master-data:list")
    @GetMapping
    public ApiResult<PageResult<ColonelPartner>> list(
            @Parameter(description = "团长名称关键字。") @RequestParam(required = false) String keyword,
            @Parameter(description = "数据来源（如 BUYIN/MANUAL）。") @RequestParam(required = false) String source,
            @Parameter(description = "是否有联系方式，true=有 / false=无 / 不传=不限。") @RequestParam(required = false) Boolean hasContact,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(colonelPartnerMasterDataService.list(keyword, source, hasContact, page, size));
    }

    /**
     * 查询团长主数据详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查询团长主数据记录</li>
     *   <li>验证记录是否存在，不存在则抛出业务异常</li>
     *   <li>返回团长主数据详情</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /api/colonel-partners/{id}}
     *
     * @param id 团长主数据 ID
     * @return 团长主数据详情
     * @throws BusinessException 团长主数据不存在
     */
    @Operation(summary = "团长主数据详情")
    @RequirePermission("colonel-partner-master-data:detail")
    @GetMapping("/{id}")
    public ApiResult<ColonelPartner> detail(@PathVariable UUID id) {
        return ApiResult.ok(colonelPartnerMasterDataService.detail(id));
    }

    /**
     * 列出全部团长来源（去重），用于来源下拉候选。
     *
     * <p>HTTP 方法与路径：{@code GET /api/colonel-partners/sources}
     *
     * @return 来源字符串列表（按字典序排序）
     */
    @Operation(summary = "团长来源下拉")
    @RequirePermission("colonel-partner-master-data:sources")
    @GetMapping("/sources")
    public ApiResult<List<String>> sources() {
        return ApiResult.ok(colonelPartnerMasterDataService.listSources());
    }
}
