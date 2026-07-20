package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.dto.rulecenter.RuleCenterBatchUpdateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterChangeLogView;
import com.colonel.saas.dto.rulecenter.RuleCenterEventStatusResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterGroupUpdateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterSchemaResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterUpdateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValuesResponse;
import com.colonel.saas.service.RuleCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 规则中心控制器，供管理员维护业务规则配置。
 *
 * <ul>
 *   <li>返回规则中心页面元数据与字段定义（Schema）</li>
 *   <li>查询全部配置项当前值</li>
 *   <li>保存前校验：校验配置值范围并发出业务告警</li>
 *   <li>按分组或跨分组批量保存规则，记录变更审计</li>
 *   <li>查询配置变更历史日志</li>
 *   <li>查看配置变更事件的消费状态</li>
 * </ul>
 *
 * <p>所属业务领域：配置域 / 规则中心
 * <p>API 路径前缀：{@code /rule-center}
 * <p>访问权限：仅限管理员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
 *
 * @see com.colonel.saas.service.RuleCenterService
 */
@Tag(name = "规则中心", description = "业务规则维护入口，按分组展示与保存系统配置。")
@RestController
@RequestMapping("/rule-center")
@RequirePermission("rule-center:access")
public class RuleCenterController extends BaseController {

    /** 规则中心服务，负责规则的 Schema 定义、校验、保存和变更日志管理 */
    private final RuleCenterService ruleCenterService;

    /**
     * 构造注入规则中心服务。
     *
     * @param ruleCenterService 规则中心服务实例
     */
    public RuleCenterController(RuleCenterService ruleCenterService) {
        this.ruleCenterService = ruleCenterService;
    }

    /**
     * 获取规则中心页面 Schema。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询规则中心的页面元数据定义（分组、字段类型、选项等）</li>
     *   <li>返回前端渲染规则配置页面所需的 Schema 结构</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /rule-center/schema}
     *
     * @return 规则中心 Schema 响应，包含分组和字段定义
     */
    @Operation(summary = "规则中心 Schema", description = "返回页面元数据与字段定义。")
    @GetMapping("/schema")
    public ApiResult<RuleCenterSchemaResponse> schema() {
        return ok(ruleCenterService.schema());
    }

    /**
     * 获取规则中心全部配置项当前值。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从数据库或缓存中读取所有规则配置项的当前生效值</li>
     *   <li>按分组结构返回配置项键值对</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /rule-center}
     *
     * @return 规则中心全部配置项的当前值
     */
    @Operation(summary = "当前规则值", description = "返回规则中心全部配置项当前值。")
    @GetMapping
    public ApiResult<RuleCenterValuesResponse> values() {
        return ok(ruleCenterService.currentValues());
    }

    /**
     * 保存前校验配置值。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收待保存的配置项键值对</li>
     *   <li>校验每项配置值是否在合法范围内</li>
     *   <li>检查是否存在业务告警（如阈值异常、冲突配置等）</li>
     *   <li>返回校验结果，包含错误和告警信息</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /rule-center/validate}
     *
     * @param request 校验请求，包含待校验的配置项键值对
     * @return 校验响应，包含错误和告警列表
     */
    @Operation(summary = "保存前校验", description = "校验配置值范围与业务告警。")
    @PostMapping("/validate")
    public ApiResult<RuleCenterValidateResponse> validate(@RequestBody RuleCenterValidateRequest request) {
        return ok(ruleCenterService.validate(request.values()));
    }

    /**
     * 按分组保存规则。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据分组编码定位目标配置分组</li>
     *   <li>批量更新该分组下的配置项值</li>
     *   <li>记录变更审计日志（变更人、变更原因、变更前后值）</li>
     *   <li>触发配置变更领域事件，通知下游消费方</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /rule-center/groups/{groupCode}}
     *
     * @param groupCode 分组编码，标识目标配置分组
     * @param request   分组更新请求，包含配置项键值对和变更原因
     * @param userId    当前操作人 ID（从 JWT 解析）
     * @param username  当前操作人用户名（从 JWT 解析，可为空）
     * @return 更新结果，包含变更记录数等信息
     * @throws com.colonel.saas.common.exception.BusinessException 分组不存在或配置值校验失败
     */
    @Operation(summary = "保存分组规则", description = "按分组批量保存规则。")
    @PutMapping("/groups/{groupCode}")
    public ApiResult<RuleCenterUpdateResponse> updateGroup(
            @Parameter(description = "分组编码") @PathVariable String groupCode,
            @RequestBody RuleCenterGroupUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "username", required = false) String username) {
        return ok(ruleCenterService.updateGroup(
                groupCode,
                request.values(),
                request.changeReason(),
                userId,
                username));
    }

    /**
     * 跨分组批量保存规则。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收跨多个分组的配置项键值对</li>
     *   <li>批量更新所有指定的配置项值</li>
     *   <li>记录合并后的变更审计日志</li>
     *   <li>触发配置变更领域事件</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /rule-center/batch}
     *
     * @param request 批量更新请求，包含跨分组的配置项键值对和变更原因
     * @param userId  当前操作人 ID（从 JWT 解析）
     * @param username 当前操作人用户名（从 JWT 解析，可为空）
     * @return 更新结果，包含变更记录数等信息
     * @throws com.colonel.saas.common.exception.BusinessException 配置值校验失败
     */
    @Operation(summary = "批量保存规则", description = "跨分组批量保存规则。")
    @PutMapping("/batch")
    public ApiResult<RuleCenterUpdateResponse> batchUpdate(
            @RequestBody RuleCenterBatchUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "username", required = false) String username) {
        return ok(ruleCenterService.batchUpdate(
                request.values(),
                request.changeReason(),
                userId,
                username));
    }

    /**
     * 查询规则配置变更历史日志。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的配置项 key 筛选和分页参数</li>
     *   <li>查询变更历史表，按时间倒序返回</li>
     *   <li>包含变更人、变更原因、变更前后值等详细信息</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /rule-center/change-logs}
     *
     * @param key  按配置项 key 筛选，可为空表示查询全部
     * @param page 当前页码，默认为 1
     * @param size 每页记录数，默认为 20
     * @return 分页后的变更历史日志列表
     */
    @Operation(summary = "变更历史", description = "查询规则中心配置变更日志。")
    @GetMapping("/change-logs")
    public ApiResult<PageResult<RuleCenterChangeLogView>> changeLogs(
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<RuleCenterChangeLogView> result = ruleCenterService.changeLogs(key, page, size);
        return okPage(result);
    }

    /**
     * 查询配置变更事件的分发消费状态。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据事件 ID 查询该配置变更事件的消费记录</li>
     *   <li>返回各消费方的处理状态（成功/失败/待处理）</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /rule-center/events}
     *
     * @param eventId 配置变更事件 ID
     * @return 事件消费状态响应，包含各消费方的处理状态
     */
    @Operation(summary = "事件分发状态", description = "查看配置变更事件消费状态。")
    @GetMapping("/events")
    public ApiResult<RuleCenterEventStatusResponse> eventStatus(
            @RequestParam UUID eventId) {
        return ok(ruleCenterService.eventStatus(eventId));
    }
}
