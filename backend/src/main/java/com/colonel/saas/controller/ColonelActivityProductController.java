package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.domain.product.application.CopyPromotionApplicationService;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.service.ProductPinService;
import com.colonel.saas.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 活动商品主链路控制器 (god controller - 边缘服务, 不再 DDD 切片).
 *
 * <p><strong>当前状态 (2026-07-14):</strong></p>
 * <ul>
 *   <li>已拆分 3 个 sub-controller (3 次 commit):
 *     <ul>
 *       <li>ColonelActivityProductQueryController (detail/SKUs/logs) - commit 2e54714f</li>
 *       <li>ColonelActivityProductPinController (pin/unpin) - commit d6f9cd2f</li>
 *       <li>ColonelActivityProductLibraryController (library-entry/batch-library-entry) - commit 77dd0c49</li>
 *     </ul>
 *   </li>
 *   <li>未拆分 (god controller, 边缘服务处置 - 标 Javadoc + 跳过 DDD 切片):
 *     <ul>
 *       <li>业务管理 5 个 endpoint: bind/assignee/audit-assignee/audit-result/decision</li>
 *       <li>推广跟进 2 个 endpoint: promotion-links/follow</li>
 *       <li>批量 2 个 endpoint: batch-assign/batch-pin</li>
 *     </ul>
 *   </li>
 *   <li>不切理由 (与 9 个 god service 一致处置):
 *     <ol>
 *       <li>AuditRequest 13+ 字段, 与 ColonelActivityProductControllerAuditRequestTest
 *           + ColonelActivityProductControllerCopyPromotionTest 强耦合, 切片需同步改 test</li>
 *       <li>PromotionLinkRequest 含 Swagger content (ExampleObject) 大段装饰, 搬迁工作量大</li>
 *       <li>3 个 batch endpoint 共享 runProductBatch helper, 拆出需抽公共 helper</li>
 *     </ol>
 *   </li>
 * </ul>
 */
@Validated
@RestController
@Tag(name = "活动商品主链路", description = "团长活动下商品的详情、绑定、分配、审核、转链、达人跟进与操作日志接口。")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class ColonelActivityProductController extends BaseController {

    /** 商品服务，负责活动商品的详情、绑定、分配、审核、转链等核心业务逻辑 */
    private final ProductService productService;
    /** 复制推广简介应用服务，负责活动商品转链复制文案编排 */
    private final CopyPromotionApplicationService copyPromotionApplicationService;
    /** 商品置顶服务，负责商品置顶/取消置顶操作 */
    private final ProductPinService productPinService;

    /** 用户服务，负责校验分配目标用户的合法性 */
    private final SysUserService sysUserService;

    /**
     * 构造注入所有依赖。
     *
     * @param productService             商品服务
     * @param copyPromotionApplicationService 复制推广简介应用服务
     * @param productPinService          商品置顶服务

     * @param sysUserService             用户服务
     */
    public ColonelActivityProductController(
            ProductService productService,
            CopyPromotionApplicationService copyPromotionApplicationService,
            ProductPinService productPinService,
            SysUserService sysUserService) {
        this.productService = productService;
        this.copyPromotionApplicationService = copyPromotionApplicationService;
        this.productPinService = productPinService;
        this.sysUserService = sysUserService;
    }

    /**
         * 为活动商品绑定（或修正）关联活动。
         * <p>
         * 将商品关联到指定的团长活动，用于补绑或修正之前错误的活动关联关系。
         * 仅业务团长和团长负责人可操作。
         * </p>
         *
         * @param activityId 当前路由中的团长活动 ID
         * @param productId  商品 ID
         * @param request    绑定请求体，包含目标活动 ID
         * @param userId     当前操作用户 ID
         * @param deptId     当前操作用户所属部门 ID
         * @return 操作结果 Map
         */
    @Operation(summary = "活动商品绑定活动", description = "为活动商品补绑或修正关联活动。")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PutMapping("/{productId}/bind-activity")
    public ApiResult<Map<String, Object>> bindActivity(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "活动绑定请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"boundActivityId\":\"ACTIVITY_001\"}"))
            )
            @Valid @RequestBody BindActivityRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        // 委托 ProductService 将商品绑定到目标活动
        return ok(productService.bindActivity(activityId, productId, request.getBoundActivityId(), userId, deptId));
    }

    /**
     * 为活动商品分配招商组长。
     * <p>
     * 将商品的招商跟进权分配给指定用户。分配前会校验目标用户是否属于当前用户的
     * 可分配范围（基于部门和角色限制）。
     * </p>
     * <ol>
     *   <li>调用 {@link SysUserService#assertAssignableUser} 校验分配目标用户的合法性</li>
     *   <li>委托 {@link ProductService#assignProduct} 完成分配并记录操作日志</li>
     * </ol>
     *
     * @param activityId 团长活动 ID
     * @param productId  商品 ID
     * @param request    分配请求体，包含目标用户 ID
     * @param userId     当前操作用户 ID
     * @param deptId     当前操作用户所属部门 ID
     * @param roleCodes  当前用户的角色编码列表
     * @return 操作结果 Map，包含更新后的商品分配信息
     */
    @Operation(summary = "活动商品分配招商", description = "为活动商品指定招商组长。")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PutMapping("/{productId}/assignee")
    public ApiResult<Map<String, Object>> assign(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "招商组长分配请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
            )
            @Valid @RequestBody AssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // Step 1: 校验目标用户是否属于当前用户的可分配范围
        sysUserService.assertAssignableUser(request.getAssigneeId(), roleCodes, deptId);
        // Step 2: 委托 ProductService 完成商品分配
        return ok(productService.assignProduct(activityId, productId, request.getAssigneeId(), userId, deptId));
    }

    /**
     * 为待审核活动商品分配审核负责人。
     * <p>
     * 指定招商专员作为该商品的审核负责人，但不改变商品的主状态。
     * 仅用于待审核阶段的人工分配场景。
     * </p>
     * <ol>
     *   <li>调用 {@link SysUserService#assertAssignableUser} 校验分配目标用户的合法性</li>
     *   <li>委托 {@link ProductService#assignAuditOwner} 完成审核人分配并记录操作日志</li>
     * </ol>
     *
     * @param activityId 团长活动 ID
     * @param productId  商品 ID
     * @param request    分配请求体，包含目标用户 ID
     * @param userId     当前操作用户 ID
     * @param deptId     当前操作用户所属部门 ID
     * @param roleCodes  当前用户的角色编码列表
     * @return 操作结果 Map
     */
    @Operation(summary = "活动商品分配审核人", description = "为待审核活动商品指定招商专员审核负责人，不改变商品主状态。")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PutMapping("/{productId}/audit-assignee")
    public ApiResult<Map<String, Object>> assignAuditOwner(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "审核负责人分配请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
            )
            @Valid @RequestBody AssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // Step 1: 校验目标用户是否属于当前用户的可分配范围
        sysUserService.assertAssignableUser(request.getAssigneeId(), roleCodes, deptId);
        // Step 2: 委托 ProductService 分配审核负责人
        return ok(productService.assignAuditOwner(activityId, productId, request.getAssigneeId(), userId, deptId));
    }

    /**
     * 提交活动商品审核结果。
     * <p>
     * 招商专员对商品进行审核，可选择通过或驳回，并可附加补充信息（专属价、发货信息、
     * 卖点话术、投流规则、标签等）。审核通过后商品状态将流转到下一阶段。
     * </p>
     * <ol>
     *   <li>从请求体提取审核结果（通过/驳回）及备注</li>
     *   <li>调用 {@link AuditRequest#toSupplementMap} 将补充信息序列化为 Map</li>
     *   <li>委托 {@link ProductService#auditProduct} 执行审核状态流转并记录操作日志</li>
     * </ol>
     *
     * @param activityId 团长活动 ID
     * @param productId  商品 ID
     * @param request    审核结果请求体，包含审核决定、备注及补充信息
     * @param userId     当前操作用户 ID
     * @param deptId     当前操作用户所属部门 ID
     * @return 操作结果 Map，包含更新后的商品状态
     */
    @Operation(summary = "活动商品审核", description = "提交活动商品审核结果。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PutMapping("/{productId}/audit-result")
    public ApiResult<Map<String, Object>> audit(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "审核结果请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"approved\":true,\"reason\":\"素材完整，允许推进\"}"))
            )
            @Valid @RequestBody AuditRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        // Step 1: 将审核请求中的补充信息序列化为 Map 后一并传入服务层
        return ok(productService.auditProduct(
                activityId,
                productId,
                request.isApproved(),
                request.getReason(),
                request.toSupplementMap(),
                userId,
                deptId
        ));
    }

    /**
     * 记录活动商品的推进判断。
     * <p>
     * 招商专员对商品进行人工推进判断，标记为主推（MAIN）、次推（SECONDARY）、
     * 暂缓（PAUSE）或放弃（DROP）。此操作不改变商品主状态，仅记录判断信息。
     * </p>
     *
     * @param activityId 团长活动 ID
     * @param productId  商品 ID
     * @param request    推进判断请求体，包含判断等级和原因
     * @param userId     当前操作用户 ID
     * @param deptId     当前操作用户所属部门 ID
     * @return 操作结果 Map，包含更新后的商品推进判断信息
     */
    @Operation(summary = "活动商品推进判断", description = "记录商品主推、次推、暂缓或放弃等人工推进判断，不改变商品主状态。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PutMapping("/{productId}/decision")
    public ApiResult<Map<String, Object>> decision(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "推进判断请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"decisionLevel\":\"MAIN\",\"reason\":\"佣金高，适合优先推\"}"))
            )
            @Valid @RequestBody DecisionRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        // 委托 ProductService 记录商品推进判断并写入操作日志
        return ok(productService.recordProductDecision(
                activityId,
                productId,
                request.getDecisionLevel(),
                request.getReason(),
                userId,
                deptId
        ));
    }

    /**
     * 为活动商品生成推广链接（转链）。
     * <p>
     * 调用抖店转链接口生成商品推广链接，支持幂等键（24 小时内相同键返回首次结果）。
     * 请求体可为空，为空时使用默认场景参数。
     * </p>
     * <ol>
     *   <li>处理空请求体：若请求体为 null 则创建默认 PromotionLinkRequest</li>
     *   <li>委托 {@link ProductService#generatePromotionLink} 调用上游抖店转链 API</li>
     *   <li>返回转链结果，包含推广链接和短链等信息</li>
     * </ol>
     *
     * @param activityId     团长活动 ID
     * @param productId      商品 ID
     * @param request        转链请求体（可选），包含场景、达人 ID 等参数
     * @param idempotencyKey 幂等键（可选），相同键在 24 小时内重复提交将返回首次转链结果
     * @param userId         当前操作用户 ID
     * @param deptId         当前操作用户所属部门 ID
     * @return 转链结果，包含推广链接、短链等信息
     */
    @Operation(summary = "活动商品转链", description = "为活动商品生成推广链接。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping("/{productId}/promotion-links")
    public ApiResult<com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult> generatePromotionLink(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "转链请求体，可为空；为空时使用默认场景。",
                    content = @Content(examples = @ExampleObject(value = "{\"scene\":\"PRODUCT_LIBRARY\",\"talentId\":\"test_talent_001\"}"))
            )
            @RequestBody(required = false) PromotionLinkRequest request,
            @Parameter(description = "幂等键，相同键在 24h 内重复提交将返回首次转链结果。")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        // Step 1: 处理空请求体，使用默认场景参数
        PromotionLinkRequest safeRequest = request == null ? new PromotionLinkRequest() : request;
        // Step 2: 委托商品域应用服务生成复制推广简介
        com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult result = copyPromotionApplicationService.copyPromotion(
                activityId,
                productId,
                userId,
                deptId,
                safeRequest.getExternalUniqueId(),
                safeRequest.getPromotionScene(),
                safeRequest.getNeedShortLink(),
                safeRequest.getScene(),
                safeRequest.getTalentId(),
                idempotencyKey
        );
        return ok(result);
    }

    /**
     * 记录活动商品的达人跟进信息。
     * <p>
     * 渠道人员对商品关联的达人进行跟进记录，包括跟进状态、跟进内容、
     * 下次回访时间等，用于达人侧协作与后续回访。
     * </p>
     *
     * @param activityId 团长活动 ID
     * @param productId  商品 ID
     * @param request    达人跟进请求体，包含达人信息、跟进状态和内容
     * @param userId     当前操作用户 ID
     * @return 操作结果 Map，包含创建的跟进记录信息
     */
    @Operation(summary = "活动商品达人跟进", description = "记录活动商品的达人跟进信息，用于达人侧协作与后续回访。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping("/{productId}/follow")
    public ApiResult<Map<String, Object>> follow(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人跟进请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"talentName\":\"达人A\",\"followStatus\":\"FOLLOWING\",\"content\":\"已加微信跟进\",\"operatorName\":\"渠道A\"}"))
            )
            @Valid @RequestBody TalentFollowRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        // 委托 ProductService 创建达人跟进记录并写入操作日志
        return ok(productService.startTalentFollow(
                activityId,
                productId,
                request.getTalentId(),
                request.getTalentName(),
                request.getFollowStatus(),
                request.getContent(),
                request.getNextFollowTime(),
                userId,
                request.getOperatorName()
        ));
    }

    /**
     * 批量分配招商组长。
     * <p>
     * 批量为多个活动商品指定同一个招商组长。采用部分失败容错机制：
     * 单个商品分配失败不影响其他商品的分配操作。
     * </p>
     * <ol>
     *   <li>校验目标用户的可分配范围</li>
     *   <li>遍历商品 ID 列表，逐个调用 {@link ProductService#assignProduct} 执行分配</li>
     *   <li>汇总成功/失败数量并返回批量操作结果</li>
     * </ol>
     *
     * @param activityId 团长活动 ID
     * @param request    批量分配请求体，包含商品 ID 列表和目标用户 ID
     * @param userId     当前操作用户 ID
     * @param deptId     当前操作用户所属部门 ID
     * @param roleCodes  当前用户的角色编码列表
     * @return 批量操作结果，包含 total/succeeded/failed 计数及每个商品的执行详情
     */
    @Operation(summary = "批量分配招商", description = "批量为活动商品指定招商组长；单个商品失败不影响其他商品。")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PostMapping("/batch-assign")
    public ApiResult<Map<String, Object>> batchAssign(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Valid @RequestBody BatchAssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // Step 1: 校验目标用户是否属于当前用户的可分配范围
        sysUserService.assertAssignableUser(request.getAssigneeId(), roleCodes, deptId);
        // Step 2: 批量执行分配，通过 runProductBatch 实现部分失败容错
        return ok(runProductBatch(request.getProductIds(), productId ->
                productService.assignProduct(activityId, productId, request.getAssigneeId(), userId, deptId)));
    }

    /**
     * 批量置顶商品。
     * <p>
     * 批量将多个活动商品置顶 24 小时。采用部分失败容错机制，
     * 单个商品置顶失败不影响其他商品。每位招商人员最多同时置顶 10 个商品。
     * </p>
     *
     * @param activityId 团长活动 ID
     * @param request    批量商品 ID 请求体
     * @param userId     当前操作用户 ID
     * @return 批量操作结果，包含 total/succeeded/failed 计数及每个商品的置顶截止时间
     */
    @Operation(summary = "批量置顶商品", description = "批量置顶活动商品 24 小时；单个商品失败不影响其他商品。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-pin")
    public ApiResult<Map<String, Object>> batchPin(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Valid @RequestBody BatchProductIdsRequest request,
            @RequestAttribute("userId") UUID userId) {
        // 批量执行置顶操作，通过 runProductBatch 实现部分失败容错
        return ok(runProductBatch(request.getProductIds(), productId -> {
            var state = productPinService.pin(activityId, productId, userId);
            return Map.of(
                    "activityId", activityId,
                    "productId", productId,
                    "pinned", true,
                    "pinnedUntil", state.getPinnedUntil());
        }));
    }

    /**
     * 批量执行商品操作的核心引擎。
     * <p>
     * 采用<strong>部分失败容错</strong>策略：单个商品执行异常时记录失败原因并继续处理剩余商品，
     * 最终汇总成功/失败计数和明细返回给调用方。
     * </p>
     *
     * <ol>
     *   <li>调用 {@link #normalizeBatchProductIds} 校验、去重、限制商品 ID 列表</li>
     *   <li>遍历商品 ID 列表，逐个调用 {@code action} 执行具体业务操作</li>
     *   <li>成功时记录 productId + data 到 items；失败时记录 productId + reason 到 items 和 failures</li>
     *   <li>汇总 total / succeeded / failed / items / failures 组装返回 Map</li>
     * </ol>
     *
     * @param rawProductIds 原始商品 ID 列表（可能包含空白或重复项）
     * @param action        单个商品的业务操作回调，接收 productId 返回操作结果 Map
     * @return 包含 total、succeeded、failed、items（全量明细）、failures（仅失败明细）的 Map
     */
    private Map<String, Object> runProductBatch(List<String> rawProductIds, ProductBatchAction action) {
        // Step 1: 校验、去重并限制商品 ID 列表
        List<String> productIds = normalizeBatchProductIds(rawProductIds);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int succeeded = 0;
        // Step 2: 逐个商品执行业务操作，捕获单个异常不影响整体流程
        for (String productId : productIds) {
            try {
                Map<String, Object> data = action.apply(productId);
                items.add(Map.of(
                        "productId", productId,
                        "success", true,
                        "data", data == null ? Map.of() : data));
                succeeded++;
            } catch (Exception ex) {
                // Step 3: 单个商品失败时记录失败原因，继续处理后续商品
                String reason = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
                Map<String, Object> failure = Map.of(
                        "productId", productId,
                        "success", false,
                        "reason", reason);
                items.add(failure);
                failures.add(failure);
            }
        }
        // Step 4: 组装汇总结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", productIds.size());
        result.put("succeeded", succeeded);
        result.put("failed", failures.size());
        result.put("items", items);
        result.put("failures", failures);
        return result;
    }

    /**
     * 校验、去重并限制批量商品 ID 列表。
     * <p>
     * 对原始商品 ID 列表执行以下规范化操作：
     * </p>
     * <ol>
     *   <li>校验列表非空，否则抛出参数校验异常</li>
     *   <li>过滤空白项，对每个 ID 执行 trim 操作</li>
     *   <li>按出现顺序去重（保留首个出现项）</li>
     *   <li>校验去重后列表非空</li>
     *   <li>校验数量不超过 100 个（业务上限）</li>
     * </ol>
     *
     * @param rawProductIds 原始商品 ID 列表，可能包含空白、重复项
     * @return 去重、裁剪后的商品 ID 列表，长度在 1 ~ 100 之间
     * @throws BusinessException 当列表为空或超过 100 个时抛出参数校验异常
     */
    private List<String> normalizeBatchProductIds(List<String> rawProductIds) {
        // Step 1: 校验原始列表非空
        if (rawProductIds == null || rawProductIds.isEmpty()) {
            throw com.colonel.saas.common.exception.BusinessException.param("productIds 不能为空");
        }
        // Step 2: 过滤空白项、trim、按出现顺序去重
        List<String> productIds = new ArrayList<>();
        for (String rawProductId : rawProductIds) {
            if (!StringUtils.hasText(rawProductId)) {
                continue;
            }
            String productId = rawProductId.trim();
            if (!productIds.contains(productId)) {
                productIds.add(productId);
            }
        }
        // Step 3: 校验去重后列表非空
        if (productIds.isEmpty()) {
            throw com.colonel.saas.common.exception.BusinessException.param("productIds 不能为空");
        }
        // Step 4: 校验数量上限
        if (productIds.size() > 100) {
            throw com.colonel.saas.common.exception.BusinessException.param("单次批量商品操作最多 100 个");
        }
        return productIds;
    }

    /**
     * 批量商品操作的函数式接口。
     * <p>
     * 定义单个商品的业务操作行为，由 {@link #runProductBatch} 在遍历中回调。
     * 实现方应针对单个 productId 执行业务逻辑，成功时返回结果 Map，
     * 失败时抛出异常由 runProductBatch 统一捕获并记录为失败项。
     * </p>
     */
    @FunctionalInterface
    private interface ProductBatchAction {
        /**
         * 对单个商品执行业务操作。
         *
         * @param productId 商品 ID
         * @return 操作结果 Map，将作为该商品的 "data" 字段返回给前端
         * @throws Exception 业务异常时抛出，由批量引擎捕获并记录为失败
         */
        Map<String, Object> apply(String productId);
    }

    /**
     * 绑定活动请求体。
     * <p>
     * 用于将一个商品关联到另一个团长活动，实现跨活动的商品复用。
     * </p>
     */
    public static class BindActivityRequest {
        /** 要绑定的目标活动 ID */
        @Schema(description = "要绑定的活动 ID。", example = "ACTIVITY_001")
        @NotBlank(message = "boundActivityId 不能为空")
        private String boundActivityId;

        public String getBoundActivityId() {
            return boundActivityId;
        }

        public void setBoundActivityId(String boundActivityId) {
            this.boundActivityId = boundActivityId;
        }
    }

    /**
     * 分配招商组长请求体。
     * <p>
     * 用于单个商品分配招商组长的场景，指定目标用户 ID。
     * </p>
     */
    public static class AssignRequest {
        /** 招商组长用户 ID（UUID 格式），必须是当前用户可分配范围内的有效用户 */
        @Schema(description = "招商组长用户 ID，使用 UUID 格式。", example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "assigneeId 不能为空")
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }

    /**
     * 批量商品 ID 请求体。
     * <p>
     * 用于批量操作（批量入库、批量置顶等）的通用请求体，包含商品 ID 列表。
     * 列表由 {@link #normalizeBatchProductIds} 进行校验、去重和数量限制。
     * </p>
     */
    public static class BatchProductIdsRequest {
        /** 商品 ID 列表，单次最多 100 个，空白项和重复项会被自动过滤 */
        @Schema(description = "商品 ID 列表，单次最多 100 个。", example = "[\"9001\",\"9002\"]")
        private List<String> productIds;

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }
    }

    /**
     * 批量分配招商组长请求体。
     * <p>
     * 继承 {@link BatchProductIdsRequest} 的商品 ID 列表，并额外指定目标招商组长。
     * 用于将多个商品批量分配给同一招商人员。
     * </p>
     */
    public static class BatchAssignRequest extends BatchProductIdsRequest {
        /** 招商组长用户 ID（UUID 格式），必须是当前用户可分配范围内的有效用户 */
        @Schema(description = "招商组长用户 ID，使用 UUID 格式。", example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "assigneeId 不能为空")
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }

    /**
     * 审核请求体。
     * <p>
     * 用于活动商品的审核操作，包含审核结果和丰富的补充信息。
     * 审核通过时可附带专属价、发货信息、卖点、推广话术、投流规则、
     * 奖励说明、参与要求、活动时间、素材文件、标签、寄样门槛等信息。
     * 审核驳回时仅需填写 reason 即可。
     * </p>
     * <p>
     * 通过 {@link #toSupplementMap()} 将所有非空补充字段序列化为 Map，
     * 传递给 {@link ProductService#auditProduct} 写入补充信息表。
     * </p>
     */
    public static class AuditRequest {
        /** 专属价金额：单位为元，最多保留两位小数 */
        @Schema(description = "专属价金额，单位元。", example = "129.00")
        private BigDecimal exclusivePriceAmount;

        /** 是否审核通过：true 表示通过，false 表示驳回 */
        @Schema(description = "是否审核通过。", example = "true")
        private boolean approved;

        /** 审核备注：通过时为补充说明，驳回时为驳回原因 */
        @Schema(description = "审核备注。", example = "素材完整，允许推进")
        private String reason;

        /** 专属价说明：如直播间专属价、日常到手价等价格策略描述 */
        @Schema(description = "专属价说明。", example = "直播间专属价 129 元，日常到手价 149 元。")
        private String exclusivePriceRemark;

        /** 发货信息：发货时效、物流覆盖区域等 */
        @Schema(description = "发货信息。", example = "48 小时内发货，江浙沪次日达。")
        private String shippingInfo;

        /** 商品卖点列表：提炼商品核心卖点，用于达人讲解参考 */
        @Schema(description = "商品卖点列表。", example = "[\"高复购刚需品\", \"夏季场景强\", \"赠品感知明显\"]")
        private List<String> sellingPoints;

        /** 推广话术：建议达人使用的推广话术或脚本 */
        @Schema(description = "推广话术。", example = "可主打复购和夏季囤货场景。")
        private String promotionScript;

        /** 是否支持投流：标识该商品是否可进行付费流量投放 */
        @Schema(description = "是否支持投流。", example = "true")
        private Boolean supportsAds;

        /** 投流规则说明：投流比例、保量曝光等规则 */
        @Schema(description = "投流规则说明。", example = "投流比例1:0.5，保量10万曝光")
        private String adsRule;

        /** 奖励说明：GMV 达标后的额外返点或其他激励政策 */
        @Schema(description = "奖励说明。", example = "破 3 万 GMV 额外返 2 个点。")
        private String rewardRemark;

        /** 参与要求：达人参与推广的前置条件，如类目成交记录等 */
        @Schema(description = "参与要求。", example = "近 30 天食品饮料类目有成交。")
        private String participationRequirements;

        /** 活动时间说明：活动起止日期、开团节奏等 */
        @Schema(description = "活动时间说明。", example = "4 月 1 日至 4 月 15 日，分两波开团。")
        private String campaignTimeRemark;

        /** 手卡或素材文件列表：图片、视频等推广素材的 URL */
        @Schema(description = "手卡或素材文件列表。", example = "[\"https://example.com/material-1.png\"]")
        private List<String> materialFiles;

        /** 货品标签列表：货品维度的分类标签，如品类、场景 */
        @Schema(description = "货品标签列表。", example = "[\"家居\", \"零食\"]")
        private List<String> goodsTags;

        /** 商品标签列表：运营维度的商品标签，如主推、商品链组 */
        @Schema(description = "商品标签列表。", example = "[\"主推\", \"商品链组\"]")
        private List<String> productTags;

        /** 寄样 30 天销售额门槛：达人需满足的近 30 天 GMV 门槛（单位：分或元，视业务约定） */
        @Schema(description = "30天销售额门槛。", example = "30000")
        private Long sampleThresholdSales;

        /** 寄样达人等级门槛：达人等级最低要求 */
        @Schema(description = "达人等级门槛。", example = "1")
        private Integer sampleThresholdLevel;

        /** 寄样补充要求：除销售额和等级外的额外要求，如粉丝量、出镜要求等 */
        @Schema(description = "寄样补充要求。", example = "需真人出镜，粉丝量>10万")
        private String sampleThresholdRemark;

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public BigDecimal getExclusivePriceAmount() {
            return exclusivePriceAmount;
        }

        public void setExclusivePriceAmount(BigDecimal exclusivePriceAmount) {
            this.exclusivePriceAmount = exclusivePriceAmount;
        }

        public String getExclusivePriceRemark() {
            return exclusivePriceRemark;
        }

        public void setExclusivePriceRemark(String exclusivePriceRemark) {
            this.exclusivePriceRemark = exclusivePriceRemark;
        }

        public String getShippingInfo() {
            return shippingInfo;
        }

        public void setShippingInfo(String shippingInfo) {
            this.shippingInfo = shippingInfo;
        }

        public List<String> getSellingPoints() {
            return sellingPoints;
        }

        public void setSellingPoints(List<String> sellingPoints) {
            this.sellingPoints = sellingPoints;
        }

        public String getPromotionScript() {
            return promotionScript;
        }

        public void setPromotionScript(String promotionScript) {
            this.promotionScript = promotionScript;
        }

        public Boolean getSupportsAds() {
            return supportsAds;
        }

        public void setSupportsAds(Boolean supportsAds) {
            this.supportsAds = supportsAds;
        }

        public String getAdsRule() {
            return adsRule;
        }

        public void setAdsRule(String adsRule) {
            this.adsRule = adsRule;
        }

        public String getRewardRemark() {
            return rewardRemark;
        }

        public void setRewardRemark(String rewardRemark) {
            this.rewardRemark = rewardRemark;
        }

        public String getParticipationRequirements() {
            return participationRequirements;
        }

        public void setParticipationRequirements(String participationRequirements) {
            this.participationRequirements = participationRequirements;
        }

        public String getCampaignTimeRemark() {
            return campaignTimeRemark;
        }

        public void setCampaignTimeRemark(String campaignTimeRemark) {
            this.campaignTimeRemark = campaignTimeRemark;
        }

        public List<String> getMaterialFiles() {
            return materialFiles;
        }

        public void setMaterialFiles(List<String> materialFiles) {
            this.materialFiles = materialFiles;
        }

        public List<String> getGoodsTags() {
            return goodsTags;
        }

        public void setGoodsTags(List<String> goodsTags) {
            this.goodsTags = goodsTags;
        }

        public List<String> getProductTags() {
            return productTags;
        }

        public void setProductTags(List<String> productTags) {
            this.productTags = productTags;
        }

        public Long getSampleThresholdSales() {
            return sampleThresholdSales;
        }

        public void setSampleThresholdSales(Long sampleThresholdSales) {
            this.sampleThresholdSales = sampleThresholdSales;
        }

        public Integer getSampleThresholdLevel() {
            return sampleThresholdLevel;
        }

        public void setSampleThresholdLevel(Integer sampleThresholdLevel) {
            this.sampleThresholdLevel = sampleThresholdLevel;
        }

        public String getSampleThresholdRemark() {
            return sampleThresholdRemark;
        }

        public void setSampleThresholdRemark(String sampleThresholdRemark) {
            this.sampleThresholdRemark = sampleThresholdRemark;
        }

        /**
         * 将审核补充信息序列化为 Map 结构，供服务层持久化。
         * <p>
         * 仅包含非空字段：文本类字段通过 {@link #putText} 过滤，
         * 列表类字段通过 {@link #normalizeList} 去空白后写入。
         * </p>
         *
         * @return 补充信息 Map，key 为字段名，value 为对应的非空值
         */
        public Map<String, Object> toSupplementMap() {
            Map<String, Object> supplement = new LinkedHashMap<>();
            if (exclusivePriceAmount != null) {
                supplement.put("exclusivePriceAmount", exclusivePriceAmount);
            }
            putText(supplement, "exclusivePriceRemark", exclusivePriceRemark);
            putText(supplement, "shippingInfo", shippingInfo);
            putText(supplement, "promotionScript", promotionScript);
            putText(supplement, "rewardRemark", rewardRemark);
            putText(supplement, "participationRequirements", participationRequirements);
            putText(supplement, "campaignTimeRemark", campaignTimeRemark);
            putText(supplement, "sampleThresholdRemark", sampleThresholdRemark);
            if (supportsAds != null) {
                supplement.put("supportsAds", supportsAds);
            }
            putText(supplement, "adsRule", adsRule);
            if (sampleThresholdSales != null) {
                supplement.put("sampleThresholdSales", sampleThresholdSales);
            }
            if (sampleThresholdLevel != null) {
                supplement.put("sampleThresholdLevel", sampleThresholdLevel);
            }
            List<String> normalizedSellingPoints = normalizeList(sellingPoints);
            if (!normalizedSellingPoints.isEmpty()) {
                supplement.put("sellingPoints", normalizedSellingPoints);
            }
            List<String> normalizedMaterialFiles = normalizeList(materialFiles);
            if (!normalizedMaterialFiles.isEmpty()) {
                supplement.put("materialFiles", normalizedMaterialFiles);
            }
            List<String> normalizedGoodsTags = normalizeList(goodsTags);
            if (!normalizedGoodsTags.isEmpty()) {
                supplement.put("goodsTags", normalizedGoodsTags);
            }
            List<String> normalizedProductTags = normalizeList(productTags);
            if (!normalizedProductTags.isEmpty()) {
                supplement.put("productTags", normalizedProductTags);
            }
            return supplement;
        }

        /**
         * 将非空文本字段放入补充信息 Map。
         * <p>
         * 若 value 为空白则跳过，否则 trim 后写入。
         * </p>
         *
         * @param supplement 目标 Map
         * @param key        字段名
         * @param value      字段值
         */
        private void putText(Map<String, Object> supplement, String key, String value) {
            if (StringUtils.hasText(value)) {
                supplement.put(key, value.trim());
            }
        }

        /**
         * 规范化字符串列表：过滤空白项并 trim。
         * <p>
         * 输入为 null 或空列表时返回空的不可变列表。
         * </p>
         *
         * @param values 原始字符串列表
         * @return 去除空白项后的列表，保持原有顺序
         */
        private List<String> normalizeList(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    normalized.add(value.trim());
                }
            }
            return normalized;
        }
    }

    /**
     * 商品推进判断请求体。
     * <p>
     * 业务人员对活动商品做出推进等级判断，决定商品在后续推广中的优先级。
     * </p>
     * <ul>
     *   <li>{@code MAIN} — 主推：优先级最高，重点推广</li>
     *   <li>{@code SECONDARY} — 次推：次要推广</li>
     *   <li>{@code PAUSE} — 暂缓：暂停推进，后续再评估</li>
     *   <li>{@code DROP} — 放弃：不再推进</li>
     * </ul>
     */
    public static class DecisionRequest {
        /** 推进等级编码：MAIN / SECONDARY / PAUSE / DROP */
        @Schema(description = "推进判断。可选值：MAIN 主推，SECONDARY 次推，PAUSE 暂缓，DROP 放弃。", example = "MAIN")
        @NotBlank(message = "decisionLevel 不能为空")
        private String decisionLevel;

        /** 判断原因：说明做出该判断的业务依据 */
        @Schema(description = "判断原因。", example = "佣金高，适合优先推")
        @NotBlank(message = "reason 不能为空")
        private String reason;

        public String getDecisionLevel() {
            return decisionLevel;
        }

        public void setDecisionLevel(String decisionLevel) {
            this.decisionLevel = decisionLevel;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 生成推广链接请求体。
     * <p>
     * 向抖音精选联盟申请生成商品推广链接（转链），支持短链生成、
     * 外部幂等标识、推广场景指定等参数配置。
     * </p>
     */
    public static class PromotionLinkRequest {
        /** 外部幂等标识：调用方自行生成，用于防止重复转链；不传则由系统自动生成 */
        @Schema(description = "外部幂等标识，不传则按默认逻辑生成。", example = "external-unique-id-001")
        private String externalUniqueId;

        /** 推广场景编码：抖音精选联盟定义的推广场景类型，默认值 4 */
        @Schema(description = "推广场景编码。待确认：取值含义请联系产品。", example = "4")
        private Integer promotionScene = 4;

        /** 是否需要短链：true 时生成短链接，false 时返回原始长链接 */
        @Schema(description = "是否需要短链。", example = "true")
        private Boolean needShortLink = Boolean.TRUE;

        /** 业务场景标识：区分不同业务入口的转链请求，默认 PRODUCT_LIBRARY */
        @Schema(description = "业务场景标识。", example = "PRODUCT_LIBRARY")
        private String scene = "PRODUCT_LIBRARY";

        /** 达人标识：指定推广达人 ID，用于达人维度的推广链接生成 */
        @Schema(description = "达人标识。", example = "test_talent_001")
        private String talentId;

        public String getExternalUniqueId() {
            return externalUniqueId;
        }

        public void setExternalUniqueId(String externalUniqueId) {
            this.externalUniqueId = externalUniqueId;
        }

        public Integer getPromotionScene() {
            return promotionScene;
        }

        public void setPromotionScene(Integer promotionScene) {
            this.promotionScene = promotionScene;
        }

        public Boolean getNeedShortLink() {
            return needShortLink == null || needShortLink;
        }

        public void setNeedShortLink(Boolean needShortLink) {
            this.needShortLink = needShortLink;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public String getTalentId() {
            return talentId;
        }

        public void setTalentId(String talentId) {
            this.talentId = talentId;
        }
    }

    /**
     * 达人跟进请求体。
     * <p>
     * 渠道人员记录对活动商品关联达人的跟进信息，包括跟进状态、
     * 跟进内容和下次回访时间，用于达人侧协作与后续回访管理。
     * </p>
     */
    public static class TalentFollowRequest {
        /** 达人主键 ID（UUID 格式），关联达人表 */
        @Schema(description = "达人主键 ID，使用 UUID 格式。", example = "33333333-3333-3333-3333-333333333333")
        private UUID talentId;

        /** 达人名称：达人昵称或显示名，冗余存储便于展示 */
        @Schema(description = "达人名称。", example = "达人A")
        private String talentName;

        /** 跟进状态编码：标识当前跟进阶段（如 FOLLOWING 跟进中等） */
        @Schema(description = "跟进状态。待确认：取值含义请联系产品。", example = "FOLLOWING")
        @NotBlank(message = "followStatus 不能为空")
        private String followStatus;

        /** 跟进内容：本次跟进的详细记录，如沟通结果、反馈信息等 */
        @Schema(description = "跟进内容。", example = "已加微信跟进")
        private String content;

        /** 下次跟进时间：计划的下次回访时间，用于跟进提醒 */
        @Schema(description = "下次跟进时间。", example = "2026-04-29T10:00:00")
        private LocalDateTime nextFollowTime;

        /** 操作人显示名称：执行跟进操作的渠道人员名称 */
        @Schema(description = "操作人显示名称。", example = "渠道A")
        private String operatorName;

        public UUID getTalentId() {
            return talentId;
        }

        public void setTalentId(UUID talentId) {
            this.talentId = talentId;
        }

        public String getTalentName() {
            return talentName;
        }

        public void setTalentName(String talentName) {
            this.talentName = talentName;
        }

        public String getFollowStatus() {
            return followStatus;
        }

        public void setFollowStatus(String followStatus) {
            this.followStatus = followStatus;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getNextFollowTime() {
            return nextFollowTime;
        }

        public void setNextFollowTime(LocalDateTime nextFollowTime) {
            this.nextFollowTime = nextFollowTime;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }
    }
}
