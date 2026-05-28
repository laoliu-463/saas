package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 团长主数据查询控制器，供招商人员和管理员查询团长主数据列表和详情。
 *
 * <ul>
 *   <li>分页查询团长主数据列表，支持关键字、来源和联系方式筛选</li>
 *   <li>按 ID 查询团长主数据详情</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 团长主数据
 * <p>API 路径前缀：{@code /api/colonel-partners}
 * <p>访问权限：招商组长、招商专员和管理员（{@link com.colonel.saas.constant.RoleCodes#BIZ_LEADER}、{@link com.colonel.saas.constant.RoleCodes#BIZ_STAFF}、{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
 *
 * @see com.colonel.saas.mapper.ColonelPartnerMapper
 */
@RestController
@RequestMapping("/api/colonel-partners")
public class ColonelPartnerMasterDataController {

    /** 团长主数据 Mapper，直接负责团长数据的分页查询和详情查询 */
    private final ColonelPartnerMapper colonelPartnerMapper;

    /**
     * 构造注入团长主数据 Mapper。
     *
     * @param colonelPartnerMapper 团长主数据 Mapper 实例
     */
    public ColonelPartnerMasterDataController(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    /**
     * 分页查询团长主数据列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的关键字、来源和联系方式筛选条件</li>
     *   <li>构建 LambdaQueryWrapper 动态拼接查询条件</li>
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
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.ADMIN})
    @GetMapping
    public ApiResult<PageResult<ColonelPartner>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean hasContact,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        // 第一步：构建查询条件
        LambdaQueryWrapper<ColonelPartner> wrapper = new LambdaQueryWrapper<ColonelPartner>()
                .orderByDesc(ColonelPartner::getLastSyncAt)
                .orderByAsc(ColonelPartner::getColonelName);
        // 第二步：按关键字模糊匹配团长名称
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ColonelPartner::getColonelName, keyword.trim());
        }
        // 第三步：按来源精确匹配
        if (StringUtils.hasText(source)) {
            wrapper.eq(ColonelPartner::getSource, source.trim());
        }
        // 第四步：按联系方式存在与否筛选
        if (Boolean.TRUE.equals(hasContact)) {
            wrapper.and(w -> w.isNotNull(ColonelPartner::getContactPhone)
                    .or()
                    .isNotNull(ColonelPartner::getContactWechat)
                    .or()
                    .isNotNull(ColonelPartner::getContactName));
        } else if (Boolean.FALSE.equals(hasContact)) {
            wrapper.isNull(ColonelPartner::getContactPhone)
                    .isNull(ColonelPartner::getContactWechat)
                    .isNull(ColonelPartner::getContactName);
        }
        // 第五步：执行分页查询
        Page<ColonelPartner> result = colonelPartnerMapper.selectPage(new Page<>(page, size), wrapper);
        return ApiResult.ok(PageResult.of(result));
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
     * @throws com.colonel.saas.common.exception.BusinessException 团长主数据不存在
     */
    @Operation(summary = "团长主数据详情")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.ADMIN})
    @GetMapping("/{id}")
    public ApiResult<ColonelPartner> detail(@PathVariable UUID id) {
        // 第一步：查询团长主数据
        ColonelPartner partner = colonelPartnerMapper.selectById(id);
        // 第二步：验证记录存在性
        if (partner == null) {
            throw BusinessException.notFound("团长主数据不存在");
        }
        return ApiResult.ok(partner);
    }
}
