package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentBatchImportRequest;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.dto.talent.TalentCreateRequest;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentOperateRequest;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.dto.talent.TalentUpdateRequest;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.job.TalentWeeklyRefreshJob;
import com.colonel.saas.service.TalentQueryService;
import com.colonel.saas.service.TalentService;
import com.colonel.saas.vo.TalentEnrichTaskVO;
import com.colonel.saas.vo.TalentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 达人 CRM 控制器，供渠道人员管理达人池、公海私海、认领释放与达人信息补全。
 *
 * <ul>
 *   <li>分页查询达人列表，支持关键字、地区、粉丝量与池状态筛选</li>
 *   <li>查询达人详情、关联信息与补全结果</li>
 *   <li>新增、编辑和删除达人基础资料</li>
 *   <li>管理达人标签和收货地址</li>
 *   <li>批量导入达人，自动触发信息补全</li>
 *   <li>公海认领和私海释放，含保护期与黑名单机制</li>
 *   <li>管理员归属覆盖与黑名单管理</li>
 *   <li>手动触发达人信息刷新与每周批量刷新</li>
 *   <li>独家达人判断，辅助业务分配决策</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 达人 CRM
 * <p>API 路径前缀：{@code /talents}
 * <p>访问权限：渠道组长和渠道专员（{@link com.colonel.saas.constant.RoleCodes#CHANNEL_LEADER}、{@link com.colonel.saas.constant.RoleCodes#CHANNEL_STAFF}），部分接口覆盖为管理员或仅组长
 *
 * @see com.colonel.saas.service.TalentService
 * @see com.colonel.saas.service.TalentQueryService
 */
@Validated
@Tag(name = "达人CRM", description = "达人池、公海私海、认领释放与达人信息补全相关接口。")
@RestController
@RequestMapping("/talents")
@RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class TalentController extends BaseController {

    /** 达人服务，负责达人增删改查、标签管理、收货地址维护、认领释放与黑名单等操作 */
    private final TalentService talentService;

    /** 达人查询服务，负责达人分页查询、详情查询和操作权限校验 */
    private final TalentQueryService talentQueryService;

    /** 达人每周刷新定时任务，用于手动触发每周批量刷新 */
    private final TalentWeeklyRefreshJob talentWeeklyRefreshJob;

    /**
     * 构造注入达人服务、达人查询服务和每周刷新任务。
     *
     * @param talentService         达人服务实例
     * @param talentQueryService    达人查询服务实例
     * @param talentWeeklyRefreshJob 达人每周刷新定时任务实例
     */
    public TalentController(
            TalentService talentService,
            TalentQueryService talentQueryService,
            TalentWeeklyRefreshJob talentWeeklyRefreshJob) {
        this.talentService = talentService;
        this.talentQueryService = talentQueryService;
        this.talentWeeklyRefreshJob = talentWeeklyRefreshJob;
    }

    /**
     * 分页查询达人列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>注入当前用户的数据范围（userId、deptId、dataScope）到查询参数</li>
     *   <li>按关键字、地区、粉丝量与池状态等条件执行分页查询</li>
     *   <li>将查询结果转换为达人视图对象并返回</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents}
     *
     * @param query      达人分页查询参数，包含关键字、地区、粉丝量和池状态等筛选条件
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @return 分页后的达人列表
     */
    @Operation(summary = "达人分页列表", description = "按关键字、地区、粉丝量与池状态分页查询达人列表，用于达人 CRM 主页面。")
    @GetMapping
    public ApiResult<PageResult<TalentVO>> page(
            @Parameter(description = "达人分页查询参数。") TalentPageQuery query,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        // 第一步：注入当前用户数据范围到查询参数
        query.setUserId(userId);
        query.setDeptId(deptId);
        query.setDataScope(dataScope);
        // 第二步：执行分页查询
        IPage<Talent> result = talentQueryService.page(query);
        // 第三步：转换为视图对象并返回
        return okPage(result.convert(TalentVO::from));
    }

    /**
     * 查询达人详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据达人 ID 和数据范围校验操作权限</li>
     *   <li>查询达人详情，包含关联信息与补全结果</li>
     *   <li>返回达人详情响应对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/{id}}
     *
     * @param id         达人主键 ID，UUID 格式
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @return 达人详情，包含关联信息与补全结果
     * @throws com.colonel.saas.common.exception.BusinessException 达人不存在或无权访问
     */
    @Operation(summary = "达人详情", description = "查询单个达人的详情、关联信息与补全结果，用于达人侧边栏或详情弹窗。")
    @GetMapping("/{id}")
    public ApiResult<TalentDetailResponse> detail(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(talentQueryService.detail(id, userId, deptId, dataScope));
    }

    /**
     * 查询达人认领状态流转矩阵。
     *
     * <p>处理流程：
     * <ol>
     *   <li>构建默认的状态流转矩阵，包含公海、私海、多人认领、保护期和释放等状态</li>
     *   <li>返回各状态的定义和状态间可执行的操作</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/status-transitions}
     *
     * @return 达人认领状态流转矩阵，供产品、前端和后端按同一口径验收
     */
    @Operation(summary = "达人认领状态流转矩阵", description = "返回公海、私海、多人认领、保护期和释放相关状态表，供产品、前端和后端按同一口径验收。")
    @GetMapping("/status-transitions")
    public ApiResult<TalentStatusTransitionMatrix> statusTransitions() {
        return ok(TalentStatusTransitionMatrix.defaultMatrix());
    }

    /**
     * 新增达人。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验达人新增请求参数的合法性</li>
     *   <li>将请求体转换为达人实体</li>
     *   <li>创建达人记录并返回新增的达人视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /talents}
     *
     * @param request 达人新增请求，包含达人昵称、抖音 UID 等基础资料
     * @return 新增的达人视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 参数校验失败或达人已存在
     */
    @Operation(summary = "新增达人", description = "手动新增达人基础资料，用于 CRM 人工补录。")
    @PostMapping
    public ApiResult<TalentVO> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人新增请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"nickname\":\"达人A\",\"douyinUid\":\"test_talent_001\"}"))
            )
            @Valid @RequestBody TalentCreateRequest request) {
        return ok(TalentVO.from(talentService.create(request.toTalent())));
    }

    /**
     * 编辑达人基础资料。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户对该达人的操作权限</li>
     *   <li>校验达人更新请求参数的合法性</li>
     *   <li>更新达人记录并返回更新后的达人视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /talents/{id}}
     *
     * @param id         达人主键 ID，UUID 格式
     * @param request    达人更新请求，包含待更新的字段
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @param roleCodes  当前用户的角色代码列表，可为空
     * @return 更新后的达人视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 达人不存在、无权操作或参数校验失败
     */
    @Operation(summary = "编辑达人", description = "更新达人基础资料。")
    @PutMapping("/{id}")
    public ApiResult<TalentVO> update(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人更新请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"nickname\":\"达人A-更新\"}"))
            )
            @Valid @RequestBody TalentUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：校验操作权限
        talentQueryService.assertCanOperate(id, userId, deptId, roleCodes);
        // 第二步：更新达人资料
        return ok(TalentVO.from(talentService.update(id, request.toUpdateTalent())));
    }

    /**
     * 更新达人标签。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户对该达人的操作权限</li>
     *   <li>从请求体中提取标签列表（最多 3 个，后者覆盖同名）</li>
     *   <li>更新达人标签并返回更新后的标签列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /talents/{id}/tags}
     *
     * @param id         达人主键 ID
     * @param body       请求体，包含 tags 字段（标签列表）
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @param roleCodes  当前用户的角色代码列表，可为空
     * @return 更新后的标签列表
     * @throws com.colonel.saas.common.exception.BusinessException 达人不存在或无权操作
     */
    @Operation(summary = "更新达人标签", description = "最多 3 个标签，后者覆盖同名。")
    @PutMapping("/{id}/tags")
    public ApiResult<List<String>> updateTags(
            @Parameter(description = "达人主键 ID。") @PathVariable("id") UUID id,
            @RequestBody Map<String, List<String>> body,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：校验操作权限
        talentQueryService.assertCanOperate(id, userId, deptId, roleCodes);
        // 第二步：提取标签列表并更新
        List<String> tags = body == null ? List.of() : body.get("tags");
        return ok(talentService.updateTags(id, tags, userId));
    }

    /**
     * 获取达人收货地址。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户对该达人的操作权限</li>
     *   <li>查询达人收货地址信息</li>
     *   <li>返回收货地址请求对象，供寄样域 get_talent_address 调用</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/{id}/shipping-address}
     *
     * @param id         达人主键 ID
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @param roleCodes  当前用户的角色代码列表，可为空
     * @return 达人收货地址，包含收件人姓名、电话和地址
     * @throws com.colonel.saas.common.exception.BusinessException 达人不存在或无权操作
     */
    @Operation(summary = "获取达人收货地址", description = "供寄样域 get_talent_address 调用。")
    @GetMapping("/{id}/shipping-address")
    public ApiResult<ShippingAddressRequest> getShippingAddress(
            @Parameter(description = "达人主键 ID。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：校验操作权限
        talentQueryService.assertCanOperate(id, userId, deptId, roleCodes);
        // 第二步：查询达人收货地址
        Talent talent = talentService.getShippingAddress(id, userId);
        // 第三步：组装收货地址响应
        return ok(new ShippingAddressRequest(
                talent.getShippingRecipientName(),
                talent.getShippingRecipientPhone(),
                talent.getShippingRecipientAddress()));
    }

    /**
     * 维护达人收货地址。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户对该达人的操作权限</li>
     *   <li>提取收货地址信息（收件人姓名、电话、地址）</li>
     *   <li>更新达人收货地址并返回更新后的达人视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /talents/{id}/shipping-address}
     *
     * @param id         达人主键 ID
     * @param request    收货地址请求，包含收件人姓名、电话和地址
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @param roleCodes  当前用户的角色代码列表，可为空
     * @return 更新后的达人视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 达人不存在或无权操作
     */
    @Operation(summary = "维护达人收货地址", description = "认领人维护默认收货信息，供快速寄样带入。")
    @PutMapping("/{id}/shipping-address")
    public ApiResult<TalentVO> updateShippingAddress(
            @Parameter(description = "达人主键 ID。") @PathVariable("id") UUID id,
            @RequestBody ShippingAddressRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：校验操作权限
        talentQueryService.assertCanOperate(id, userId, deptId, roleCodes);
        // 第二步：更新收货地址
        return ok(TalentVO.from(talentService.updateShippingAddress(
                id,
                userId,
                request == null ? null : request.recipientName(),
                request == null ? null : request.recipientPhone(),
                request == null ? null : request.recipientAddress())));
    }

    /** 达人收货地址请求，包含收件人姓名、电话和详细地址 */
    public record ShippingAddressRequest(
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
    }

    /**
     * 批量导入达人。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求体中提取达人账号/链接列表</li>
     *   <li>按账号批量导入达人并自动触发信息补全</li>
     *   <li>返回批量导入结果，包含成功数和失败明细</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /talents/batch-import}
     * <p>访问权限：覆盖为渠道组长和管理员（{@link com.colonel.saas.constant.RoleCodes#CHANNEL_LEADER}、{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
     *
     * @param request 批量导入请求，包含达人账号/链接列表
     * @param userId  当前登录用户 ID（从 JWT 解析）
     * @return 批量导入结果，包含成功数和失败明细
     */
    @Operation(summary = "批量导入达人", description = "按达人账号/链接批量导入并自动补全（batch_import_talents）。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.ADMIN})
    @PostMapping("/batch-import")
    public ApiResult<TalentBatchImportResult> batchImport(
            @RequestBody TalentBatchImportRequest request,
            @RequestAttribute("userId") UUID userId) {
        // 第一步：提取达人账号列表
        List<String> accounts = request == null ? List.of() : request.accounts();
        // 第二步：执行批量导入
        return ok(talentService.batchImport(accounts, userId));
    }

    /**
     * 获取达人预设标签库。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询系统预设的标签列表</li>
     *   <li>返回标签列表，供渠道从列表中选择（最多 3 个）</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/preset-tags}
     *
     * @return 预设标签列表
     */
    @Operation(summary = "获取达人预设标签库", description = "V2 预设标签，供渠道从列表中选择（最多 3 个）。")
    @GetMapping("/preset-tags")
    public ApiResult<List<String>> presetTags() {
        return ok(talentService.listPresetTags());
    }

    /**
     * 删除达人。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户对该达人的操作权限</li>
     *   <li>验证达人未处于关键业务链路中</li>
     *   <li>删除达人记录</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code DELETE /talents/{id}}
     *
     * @param id         达人主键 ID，UUID 格式
     * @param userId     当前登录用户 ID（从 JWT 解析）
     * @param deptId     当前用户所属部门 ID，可为空
     * @param dataScope  数据范围（PERSONAL/DEPT/ALL），可为空
     * @param roleCodes  当前用户的角色代码列表，可为空
     * @return 操作成功（无数据体）
     * @throws com.colonel.saas.common.exception.BusinessException 达人不存在、无权操作或处于关键业务链路
     */
    @Operation(summary = "删除达人", description = "删除达人资料。请确认该达人未处于关键业务链路中。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：校验操作权限
        talentQueryService.assertCanOperate(id, userId, deptId, roleCodes);
        // 第二步：删除达人
        talentService.delete(id);
        return ok();
    }

    /**
     * 查询公海达人列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询所有无有效认领记录或已被释放的达人</li>
     *   <li>将结果转换为达人视图对象列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/pools/public}
     *
     * @return 可被认领的公海达人列表
     */
    @Operation(summary = "公海达人列表", description = "查询当前可被认领的公海达人列表。")
    @GetMapping("/pools/public")
    public ApiResult<List<TalentVO>> publicPool() {
        return ok(talentService.getPublicPool().stream().map(TalentVO::from).toList());
    }

    /**
     * 查询私海达人列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据当前登录用户 ID 查询其已认领的达人</li>
     *   <li>将结果转换为达人视图对象列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/pools/private}
     *
     * @param userId 当前登录用户 ID（从 JWT 解析）
     * @return 当前用户已认领的私海达人列表
     */
    @Operation(summary = "私海达人列表", description = "查询当前登录用户已认领的私海达人列表。")
    @GetMapping("/pools/private")
    public ApiResult<List<TalentVO>> privatePool(@RequestAttribute("userId") UUID userId) {
        return ok(talentService.getPrivatePool(userId).stream().map(TalentVO::from).toList());
    }

    /**
     * 按渠道人员查询私海达人列表（管理员专用）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户为管理员角色</li>
     *   <li>根据指定的渠道人员用户 ID 查询其已认领的达人</li>
     *   <li>将结果转换为达人视图对象列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /talents/pools/by-channel/{channelUserId}}
     *
     * @param channelUserId 渠道人员用户 ID
     * @return 指定渠道人员已认领的私海达人列表
     */
    @Operation(summary = "按渠道查询私海达人", description = "管理员专用：按指定渠道人员 ID 查询其私海达人列表，用于快速寄样时管理员选择渠道达人的场景。")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/pools/by-channel/{channelUserId}")
    public ApiResult<List<TalentVO>> talentsByChannel(
            @Parameter(description = "渠道人员用户 ID。") @PathVariable("channelUserId") UUID channelUserId) {
        return ok(talentService.getPrivatePool(channelUserId).stream().map(TalentVO::from).toList());
    }

    /**
     * 认领达人。
     *
     * <p>处理流程：
     * <ol>
     *   <li>验证达人当前状态是否可认领</li>
     *   <li>生成认领记录，将达人从公海转入当前负责人的私海</li>
     *   <li>按配置的保护天数计算保护期截止时间</li>
     *   <li>返回更新后的达人视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /talents/{id}/claims}
     *
     * @param talentId 达人主键 ID，UUID 格式
     * @param userId   当前登录用户 ID（从 JWT 解析）
     * @param deptId   当前用户所属部门 ID，可为空
     * @return 认领后的达人视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 达人不可认领或已在黑名单中
     */
    @Operation(summary = "认领达人", description = "认领动作，生成认领记录并将达人从公海转入当前负责人的私海。")
    @PostMapping("/{id}/claims")
    public ApiResult<TalentVO> claim(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(TalentVO.from(talentService.claim(talentId, userId, deptId)));
    }

    /**
     * 释放达人。
     *
     * <p>处理流程：
     * <ol>
     *   <li>验证当前用户对该达人的认领记录是否存在</li>
     *   <li>解除达人锁定状态，将认领记录标记为已释放</li>
     *   <li>若仍有其他有效认领，达人归属快照切到剩余认领人</li>
     *   <li>返回更新后的达人视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /talents/{id}/release}
     *
     * @param talentId 达人主键 ID，UUID 格式
     * @param userId   当前登录用户 ID（从 JWT 解析）
     * @param deptId   当前用户所属部门 ID，可为空
     * @param roleCodes 当前用户的角色代码列表，可为空
     * @return 释放后的达人视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 达人未被认领或无权释放
     */
    @Operation(summary = "释放达人", description = "释放动作，解除该达人的锁定状态并将其从当前负责人的私海释放回公共池。当前路径为动作语义，非资源集合。")
    @PostMapping("/{id}/release")
    public ApiResult<TalentVO> release(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(TalentVO.from(talentService.release(talentId, userId, deptId, roleCodes)));
    }

    @Operation(summary = "归属覆盖", description = "组长级别手动覆盖达人的当前归属人，同时记录覆盖原因。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/{id}/override-assignee")
    public ApiResult<TalentVO> overrideAssignee(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestBody @jakarta.validation.Valid OverrideAssigneeRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ok(TalentVO.from(talentService.overrideTalentAssignment(talentId, request.newUserId(), request.reason(), userId)));
    }

    @Operation(summary = "拉黑达人", description = "将达人标记为黑名单，避免继续进入公海与合作流转。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER})
    @PostMapping("/{id}/blacklist")
    public ApiResult<TalentVO> blacklist(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @Valid @RequestBody(required = false) TalentOperateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(TalentVO.from(talentService.blacklist(talentId, request == null ? null : request.getReason(), userId, deptId, dataScope)));
    }

    @Operation(summary = "解除达人黑名单", description = "取消达人黑名单标记，恢复达人正常经营状态。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER})
    @PostMapping("/{id}/unblacklist")
    public ApiResult<TalentVO> unblacklist(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(TalentVO.from(talentService.unblacklist(talentId, userId, deptId, dataScope)));
    }

    @Operation(summary = "刷新达人信息", description = "立即触发单个达人信息刷新，适用于需要同步最新达人资料的场景。")
    @PostMapping("/{id}/refresh")
    public ApiResult<TalentVO> refresh(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        talentQueryService.assertCanOperate(talentId, userId, deptId, roleCodes);
        return ok(TalentVO.from(talentService.refresh(talentId)));
    }

    @Operation(summary = "手动触发每周刷新", description = "手动执行每周批量刷新任务，用于校验达人定时刷新链路。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER})
    @PostMapping("/refresh/weekly")
    public ApiResult<Void> refreshWeekly() {
        talentWeeklyRefreshJob.weeklyRefreshActiveTalents();
        return ok();
    }

    @Operation(summary = "手动补全达人信息", description = "人工补全达人资料，用于修正自动抓取或导入后的缺失字段。")
    @PutMapping("/{id}/manual-fill")
    public ApiResult<TalentVO> manualFill(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人补全请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"nickname\":\"达人A\",\"fans\":20000}"))
            )
            @Valid @RequestBody TalentUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        talentQueryService.assertCanOperate(talentId, userId, deptId, roleCodes);
        return ok(TalentVO.from(talentService.manualFill(talentId, request.toManualFillTalent())));
    }

    @Operation(summary = "获取最新补全任务", description = "查询指定达人最近一次补全任务记录，用于排查补全链路。")
    @GetMapping("/{id}/enrich-task/latest")
    public ApiResult<TalentEnrichTaskVO> latestEnrichTask(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId) {
        return ok(TalentEnrichTaskVO.from(talentService.getLatestEnrichTask(talentId)));
    }

    @Operation(summary = "独家达人判断", description = "判断指定达人是否满足独家条件，用于业务分配与跟进决策。")
    @GetMapping("/{id}/exclusive-status")
    public ApiResult<TalentService.ExclusiveCheckResult> exclusiveCheck(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(talentService.evaluateExclusive(id, dataScope, userId, deptId));
    }

    public record TalentStatusTransitionMatrix(
            String protectionConfigKey,
            boolean allowMultiClaim,
            List<TalentClaimStateVO> states,
            List<TalentClaimTransitionVO> transitions) {

        public static TalentStatusTransitionMatrix defaultMatrix() {
            List<String> channelRoles = List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF);
            List<String> channelAndAdminRoles = List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN);
            return new TalentStatusTransitionMatrix(
                    "talent.protection_days",
                    true,
                    List.of(
                            new TalentClaimStateVO(
                                    "PUBLIC",
                                    "公海",
                                    "无有效认领，或所有认领记录均已释放；渠道可认领。",
                                    true,
                                    false,
                                    channelAndAdminRoles),
                            new TalentClaimStateVO(
                                    "PRIVATE_SELF",
                                    "我的私海",
                                    "当前登录渠道对该达人存在有效认领记录，处于保护期内。",
                                    false,
                                    true,
                                    channelAndAdminRoles),
                            new TalentClaimStateVO(
                                    "PRIVATE_OTHERS",
                                    "他人已认领",
                                    "其他渠道存在有效认领记录；多人认领不互斥，当前渠道仍可认领。",
                                    true,
                                    false,
                                    channelAndAdminRoles),
                            new TalentClaimStateVO(
                                    "RELEASED",
                                    "已释放",
                                    "当前认领记录已释放，可重新认领并重新开始保护期。",
                                    true,
                                    false,
                                    channelAndAdminRoles),
                            new TalentClaimStateVO(
                                    "BLACKLIST",
                                    "黑名单",
                                    "达人被拉黑后不可认领、不可释放，需先解除黑名单。",
                                    false,
                                    false,
                                    List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.ADMIN))
                    ),
                    List.of(
                            new TalentClaimTransitionVO(
                                    "PUBLIC",
                                    "CLAIM",
                                    "认领达人",
                                    "PRIVATE_SELF",
                                    channelRoles,
                                    "当前用户没有该达人的有效认领记录。",
                                    "写入有效认领记录，protectedUntil 按 talent.protection_days 计算。"),
                            new TalentClaimTransitionVO(
                                    "PRIVATE_OTHERS",
                                    "CLAIM",
                                    "继续认领",
                                    "PRIVATE_SELF",
                                    channelRoles,
                                    "其他人已认领不阻塞当前用户认领；同一用户不可重复有效认领。",
                                    "新增或恢复当前用户认领记录，其他认领人的保护期不受影响。"),
                            new TalentClaimTransitionVO(
                                    "PRIVATE_SELF",
                                    "RELEASE",
                                    "释放达人",
                                    "RELEASED",
                                    channelAndAdminRoles,
                                    "认领人可释放自己的认领；管理员可释放可操作范围内的认领。",
                                    "当前认领记录变为已释放；若仍有其他有效认领，达人归属快照切到剩余认领人。"),
                            new TalentClaimTransitionVO(
                                    "PRIVATE_SELF",
                                    "ORDER_RESET_PROTECTION",
                                    "订单重置保护期",
                                    "PRIVATE_SELF",
                                    List.of("system"),
                                    "认领保护期内产生有效订单。",
                                    "按订单时间重新计算 protectedUntil。"),
                            new TalentClaimTransitionVO(
                                    "PRIVATE_SELF",
                                    "AUTO_RELEASE_EXPIRED",
                                    "保护期到期自动释放",
                                    "RELEASED",
                                    List.of("system"),
                                    "保护期到期且未产生可重置保护期的订单。",
                                    "定时任务将该认领记录标记为已释放。")
                    )
            );
        }
    }

    public record TalentClaimStateVO(
            String code,
            String label,
            String description,
            boolean canClaim,
            boolean canRelease,
            List<String> visibleToRoles) {
    }

    public record TalentClaimTransitionVO(
            String fromState,
            String action,
            String actionLabel,
            String toState,
            List<String> actorRoles,
            String condition,
            String effect) {
    }
}
