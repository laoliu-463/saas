package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.CommissionRule;
import com.colonel.saas.service.CommissionRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 提成规则控制器，供管理员管理 V2 差异化提成规则。
 *
 * <ul>
 *   <li>分页查询提成规则列表，支持按维度类型和提成类型筛选</li>
 *   <li>创建新的提成规则</li>
 *   <li>更新已有的提成规则</li>
 *   <li>删除提成规则</li>
 * </ul>
 *
 * <p>所属业务领域：业绩域 / 提成规则
 * <p>API 路径前缀：{@code /commission-rules}
 * <p>访问权限：仅限管理员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
 *
 * @see com.colonel.saas.service.CommissionRuleService
 * @see com.colonel.saas.entity.CommissionRule
 */
@Tag(name = "提成规则", description = "V2 差异化提成规则管理。")
@RestController
@RequestMapping("/commission-rules")
@RequireRoles({RoleCodes.ADMIN})
public class CommissionRuleController extends BaseController {

    /** 提成规则服务，负责提成规则的 CRUD 操作 */
    private final CommissionRuleService commissionRuleService;

    /**
     * 构造注入提成规则服务。
     *
     * @param commissionRuleService 提成规则服务实例
     */
    public CommissionRuleController(CommissionRuleService commissionRuleService) {
        this.commissionRuleService = commissionRuleService;
    }

    /**
     * 分页查询提成规则列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的维度类型、提成类型、状态、查询生效区间等筛选条件</li>
     *   <li>构建分页查询，按更新时间倒序返回</li>
     *   <li>返回分页结果，包含规则列表和总记录数</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /commission-rules}
     *
     * @param dimensionType  维度类型筛选（如商品、达人等），可为空
     * @param commissionType 提成类型筛选（如固定比例、阶梯等），可为空
     * @param status         启用状态筛选（1=启用，0=禁用），可为空表示不筛选
     * @param effectiveStart 查询生效区间起点（{@code yyyy-MM-dd HH:mm:ss}），可为空表示不限起点
     * @param effectiveEnd   查询生效区间终点（{@code yyyy-MM-dd HH:mm:ss}），可为空表示不限终点；
     *                       规则的有效期与该查询区间存在重叠时即被命中
     * @param page           当前页码，默认为 1
     * @param size           每页记录数，默认为 20
     * @return 分页后的提成规则列表
     */
    @Operation(summary = "分页查询提成规则")
    @GetMapping
    public ApiResult<PageResult<CommissionRule>> page(
            @RequestParam(name = "dimensionType", required = false) String dimensionType,
            @RequestParam(name = "commissionType", required = false) String commissionType,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "effectiveStart", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveStart,
            @RequestParam(name = "effectiveEnd", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveEnd,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        IPage<CommissionRule> result = commissionRuleService.findPage(
                dimensionType,
                commissionType,
                status,
                effectiveStart,
                effectiveEnd,
                page,
                size);
        return okPage(result);
    }

    /**
     * 创建提成规则。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收提成规则实体，包含维度类型、提成类型、比例/金额等配置</li>
     *   <li>校验规则参数的完整性和合法性</li>
     *   <li>持久化到数据库并返回创建后的完整规则对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /commission-rules}
     *
     * @param rule 提成规则实体，包含维度类型、提成类型和计算参数
     * @return 创建成功的提成规则（含生成的 ID 和时间戳）
     * @throws com.colonel.saas.common.exception.ValidateException 规则参数校验失败
     */
    @Operation(summary = "创建提成规则")
    @PostMapping
    public ApiResult<CommissionRule> create(@RequestBody CommissionRule rule) {
        return ok(commissionRuleService.create(rule));
    }

    /**
     * 更新提成规则。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查找已有提成规则</li>
     *   <li>更新规则的各项配置参数</li>
     *   <li>持久化变更并返回更新后的规则对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /commission-rules/{id}}
     *
     * @param id   需要更新的提成规则 ID
     * @param rule 更新后的提成规则数据
     * @return 更新后的提成规则对象
     * @throws com.colonel.saas.common.exception.BusinessException 规则不存在或参数校验失败
     */
    @Operation(summary = "更新提成规则")
    @PutMapping("/{id}")
    public ApiResult<CommissionRule> update(@PathVariable("id") UUID id, @RequestBody CommissionRule rule) {
        return ok(commissionRuleService.update(id, rule));
    }

    /**
     * 删除提成规则。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查找目标提成规则</li>
     *   <li>执行删除操作（逻辑删除或物理删除，取决于服务实现）</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code DELETE /commission-rules/{id}}
     *
     * @param id 需要删除的提成规则 ID
     * @return 空响应，表示删除成功
     * @throws com.colonel.saas.common.exception.BusinessException 规则不存在
     */
    @Operation(summary = "删除提成规则")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable("id") UUID id) {
        commissionRuleService.delete(id);
        return ok(null);
    }
}
