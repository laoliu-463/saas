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

/**
 * 寄样筛选选项控制器，为寄样台页面提供统一的筛选候选项。
 *
 * <ul>
 *   <li>返回寄样域筛选所需的状态枚举（寄样状态列表）</li>
 *   <li>返回按当前用户数据范围裁剪的动态候选值（达人、商品、操作人等）</li>
 * </ul>
 *
 * <p>所属业务领域：寄样域 / 寄样管理
 * <p>API 路径前缀：{@code /samples}
 * <p>访问权限：登录用户（数据范围由 {@link com.colonel.saas.common.enums.DataScope} 控制）
 *
 * @see com.colonel.saas.service.SampleFilterOptionsService
 */
@Tag(name = "寄样筛选选项", description = "寄样域统一筛选候选项，按当前用户数据范围裁剪。")
@RestController
@RequestMapping("/samples")
public class SampleFilterOptionsController extends BaseController {

    /** 寄样筛选选项服务，负责根据用户权限和数据范围构建筛选候选项 */
    private final SampleFilterOptionsService sampleFilterOptionsService;

    /**
     * 构造注入寄样筛选选项服务。
     *
     * @param sampleFilterOptionsService 筛选选项服务实例
     */
    public SampleFilterOptionsController(SampleFilterOptionsService sampleFilterOptionsService) {
        this.sampleFilterOptionsService = sampleFilterOptionsService;
    }

    /**
     * 获取寄样台筛选选项。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求属性中提取当前用户的 userId、deptId、dataScope 和 roleCodes</li>
     *   <li>调用服务层根据用户权限和数据范围构建筛选候选项</li>
     *   <li>返回包含状态枚举和动态候选值的统一筛选选项对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /samples/filter-options}
     *
     * @param userId    当前登录用户 ID（从 JWT 中解析，自动注入）
     * @param deptId    当前用户所属部门 ID（可为空）
     * @param dataScope 当前用户数据范围枚举（PERSONAL / DEPT / ALL，可为空）
     * @param roleCodes 当前用户角色编码列表（可为空）
     * @return 寄样筛选选项，包含状态枚举列表和按数据范围裁剪的动态候选值
     */
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
