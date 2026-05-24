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

@RestController
@RequestMapping("/api/colonel-partners")
public class ColonelPartnerMasterDataController {

    private final ColonelPartnerMapper colonelPartnerMapper;

    public ColonelPartnerMasterDataController(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    @Operation(summary = "团长主数据列表")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.ADMIN})
    @GetMapping
    public ApiResult<PageResult<ColonelPartner>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean hasContact,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<ColonelPartner> wrapper = new LambdaQueryWrapper<ColonelPartner>()
                .orderByDesc(ColonelPartner::getLastSyncAt)
                .orderByAsc(ColonelPartner::getColonelName);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ColonelPartner::getColonelName, keyword.trim());
        }
        if (StringUtils.hasText(source)) {
            wrapper.eq(ColonelPartner::getSource, source.trim());
        }
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
        Page<ColonelPartner> result = colonelPartnerMapper.selectPage(new Page<>(page, size), wrapper);
        return ApiResult.ok(PageResult.of(result));
    }

    @Operation(summary = "团长主数据详情")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.ADMIN})
    @GetMapping("/{id}")
    public ApiResult<ColonelPartner> detail(@PathVariable UUID id) {
        ColonelPartner partner = colonelPartnerMapper.selectById(id);
        if (partner == null) {
            throw BusinessException.notFound("团长主数据不存在");
        }
        return ApiResult.ok(partner);
    }
}
