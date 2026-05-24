package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.service.SampleFilterOptionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "寄样筛选选项", description = "寄样域统一筛选候选项，按当前用户数据范围裁剪。")
@RestController
@RequestMapping("/samples")
public class SampleFilterOptionsController extends BaseController {

    private final SampleFilterOptionsService sampleFilterOptionsService;

    public SampleFilterOptionsController(SampleFilterOptionsService sampleFilterOptionsService) {
        this.sampleFilterOptionsService = sampleFilterOptionsService;
    }

    @Operation(summary = "寄样筛选选项", description = "返回寄样台筛选所需的状态枚举与动态候选值。")
    @GetMapping("/filter-options")
    public ApiResult<SampleFilterOptionsDTO> filterOptions(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) com.colonel.saas.common.enums.DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ok(sampleFilterOptionsService.buildOptions(userId, deptId, dataScope, roleCodes));
    }
}
