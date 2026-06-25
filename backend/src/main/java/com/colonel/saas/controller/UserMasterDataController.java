package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.debug.DebugSessionLog;
import com.colonel.saas.domain.user.application.UserMasterDataApplicationService;
import com.colonel.saas.dto.user.UserOptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户域主数据控制器，为商品、寄样、订单、达人等业务域提供人员下拉选择接口。
 *
 * <ul>
 *   <li>渠道人员下拉：返回渠道组长与渠道专员候选列表</li>
 *   <li>招商人员下拉：返回招商组长、招商专员与招商组长兼容角色候选列表</li>
 *   <li>本组成员下拉：组长默认查看本组成员，管理员可传入 deptId 查看指定部门成员</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 主数据
 * <p>API 路径前缀：{@code /users/master-data}
 * <p>访问权限：渠道人员和招商人员下拉对登录用户开放；组成员下拉需组长或管理员角色
 *
 * @see UserMasterDataApplicationService
 */
@Tag(name = "用户域主数据", description = "供商品、寄样、订单、达人等业务域复用的人员下拉接口。")
@RestController
@RequestMapping("/users/master-data")
public class UserMasterDataController extends BaseController {

    /** 用户主数据应用服务，负责查询渠道人员、招商人员和组成员下拉数据 */
    private final UserMasterDataApplicationService userMasterDataApplicationService;

    /**
     * 构造注入用户主数据应用服务。
     *
     * @param userMasterDataApplicationService 用户主数据应用服务实例
     */
    public UserMasterDataController(UserMasterDataApplicationService userMasterDataApplicationService) {
        this.userMasterDataApplicationService = userMasterDataApplicationService;
    }

    /**
     * 渠道人员下拉接口，返回渠道组长与渠道专员候选。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的关键字和返回条数限制</li>
     *   <li>查询渠道相关角色的用户列表，按关键字模糊匹配用户名或姓名</li>
     *   <li>返回裁剪后的人员选项列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /users/master-data/channels}
     *
     * @param keyword 关键字，匹配用户名或姓名，可为空
     * @param limit   最多返回条数，默认 50，最大 100
     * @return 渠道人员选项列表 {@link UserOptionResponse}
     */
    @Operation(summary = "渠道人员下拉", description = "返回渠道组长与渠道专员候选。")
    @GetMapping("/channels")
    public ApiResult<List<UserOptionResponse>> channels(
            @Parameter(description = "关键字，匹配用户名或姓名。") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "最多返回条数，默认50，最大100。") @RequestParam(name = "limit", defaultValue = "50") Integer limit) {
        return ok(userMasterDataApplicationService.listChannels(keyword, limit));
    }

    /**
     * 招商人员下拉接口，返回招商相关角色候选。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的关键字和返回条数限制</li>
     *   <li>查询招商组长、招商专员及招商组长兼容角色的用户列表</li>
     *   <li>按关键字模糊匹配用户名或姓名，返回裁剪后的人员选项列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /users/master-data/recruiters}
     *
     * @param keyword 关键字，匹配用户名或姓名，可为空
     * @param limit   最多返回条数，默认 50，最大 100
     * @return 招商人员选项列表 {@link UserOptionResponse}
     */
    @Operation(summary = "招商人员下拉", description = "返回招商组长、招商专员与招商组长兼容角色候选。")
    @GetMapping("/recruiters")
    public ApiResult<List<UserOptionResponse>> recruiters(
            @Parameter(description = "关键字，匹配用户名或姓名。") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "最多返回条数，默认50，最大100。") @RequestParam(name = "limit", defaultValue = "50") Integer limit) {
        // #region agent log
        Map<String, Object> debugData = new LinkedHashMap<>();
        debugData.put("keyword", keyword);
        debugData.put("limit", limit);
        DebugSessionLog.write("H3", "UserMasterDataController.recruiters", "recruiters endpoint entered", debugData);
        // #endregion
        List<UserOptionResponse> options = userMasterDataApplicationService.listRecruiters(keyword, limit);
        // #region agent log
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("count", options == null ? 0 : options.size());
        DebugSessionLog.write("H3", "UserMasterDataController.recruiters", "recruiters endpoint success", resultData);
        // #endregion
        return ok(options);
    }

    /**
     * 本组成员下拉接口，查看当前组或指定部门的成员。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求属性中提取当前用户的部门 ID 和角色编码</li>
     *   <li>管理员可通过 deptId 参数指定查看任意部门的成员</li>
     *   <li>组长默认查看自己所在部门/组的成员</li>
     *   <li>按关键字模糊匹配后返回人员选项列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /users/master-data/group-members}
     *
     * @param deptId        管理员可指定的部门 ID，可为空
     * @param keyword       关键字，匹配用户名或姓名，可为空
     * @param limit         最多返回条数，默认 50，最大 100
     * @param currentDeptId 当前登录用户所属部门 ID（从 JWT 解析，自动注入）
     * @param roleCodes     当前登录用户角色编码列表（从 JWT 解析，自动注入）
     * @return 组成员选项列表 {@link UserOptionResponse}
     */
    @Operation(summary = "本组成员下拉", description = "组长默认查看本组成员；管理员可传 deptId 查看指定部门成员。")
    @GetMapping("/group-members")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public ApiResult<List<UserOptionResponse>> groupMembers(
            @Parameter(description = "管理员可指定部门ID。") @RequestParam(name = "deptId", required = false) UUID deptId,
            @Parameter(description = "关键字，匹配用户名或姓名。") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "最多返回条数，默认50，最大100。") @RequestParam(name = "limit", defaultValue = "50") Integer limit,
            @RequestAttribute(value = "deptId", required = false) UUID currentDeptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(userMasterDataApplicationService.listGroupMembers(
                deptId,
                currentDeptId,
                roleCodes,
                keyword,
                limit
        ));
    }
}
