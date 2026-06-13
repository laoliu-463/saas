package com.colonel.saas.service.sample;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipItem;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.domain.sample.policy.SampleStateMachine;
import com.colonel.saas.domain.sample.policy.SampleStateMachine;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SampleStatusLog;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.service.SampleLogisticsImportService;
import com.colonel.saas.service.SampleLogisticsSubscriptionService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.service.SampleWriteTransactionService;
import com.colonel.saas.vo.SampleTalentVO;
import com.colonel.saas.vo.sample.LogisticsTraceVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleEligibilityCheckVO;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import com.colonel.saas.vo.sample.SampleProductVO;
import com.colonel.saas.vo.sample.SampleStatusTransitionVO;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.StatusLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 寄样管理控制器，负责寄样申请的全生命周期管理。
 *
 * <ul>
 *   <li>创建寄样申请并初始化状态为"待审核"（{@code PENDING_AUDIT}）</li>
 *   <li>寄样单分页查询、看板视图、状态日志查询</li>
 *   <li>单条/批量审核通过、审核拒绝、录入物流、签收确认等状态流转操作</li>
 *   <li>物流信息同步（手动/批量）、物流单号导入</li>
 *   <li>达人候选搜索（基于爬虫达人库）与商品搜索</li>
 *   <li>寄样数据导出（CSV 格式，分页流式写入）</li>
 *   <li>寄样资格校验（达人 30 天销售额、等级等规则）</li>
 *   <li>达人认领关系校验（渠道专员/渠道组长必须先认领达人）</li>
 *   <li>7 天重复申请限制（管理员和渠道组长豁免）</li>
 * </ul>
 *
 * <h3>寄样状态机</h3>
 * <pre>
 * PENDING_AUDIT → PENDING_SHIP → SHIPPING → DELIVERED → PENDING_HOMEWORK → COMPLETED
 *      ↓              (审核通过)    (录入物流)   (物流签收)    (待交作业)       (作业完成)
 *   REJECTED
 *   (审核拒绝)
 * PENDING_HOMEWORK → CLOSED (超时关闭)
 * </pre>
 *
 * <h3>角色权限说明</h3>
 * <ul>
 *   <li>招商组长（{@code BIZ_LEADER}）/ 招商专员（{@code BIZ_STAFF}）：创建寄样、审核操作</li>
 *   <li>渠道组长（{@code CHANNEL_LEADER}）/ 渠道专员（{@code CHANNEL_STAFF}）：创建寄样（需先认领达人）</li>
 *   <li>运营专员（{@code OPS_STAFF}）：物流录入、签收确认、物流同步（仅可查看待发货及后续状态）</li>
 *   <li>管理员（{@code ADMIN}）：所有操作权限</li>
 * </ul>
 *
 * <h3>架构角色</h3>
 * <p>属于寄样域的核心控制器，依赖以下服务：
 * <ul>
 *   <li>{@link SampleWriteTransactionService} — 寄样写操作事务管理</li>
 *   <li>{@link SampleEligibilityService} — 寄样资格校验</li>
 *   <li>{@link SampleLogisticsSyncService} — 物流信息同步</li>
 *   <li>{@link SampleDomainEventPublisher} — 寄样领域事件发布</li>
 * </ul>
 *
 * @see SampleStatus 寄样状态枚举
 * @see SampleDomainEventPublisher 寄样领域事件发布器
 */
@Slf4j
public class SampleApplicationService extends BaseController {

    /** 寄样申请单号日期格式：yyyyMMdd，用于生成唯一申请单号前缀 */
    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 看板视图每批加载的寄样单数量上限 */
    private static final long BOARD_BATCH_SIZE = 2000L;

    /** 商品关键词搜索每批匹配的商品 ID 数量上限 */
    private static final long PRODUCT_KEYWORD_BATCH_SIZE = 500L;

    /** CSV 导出每批查询的寄样单数量上限，用于流式分页写入避免内存溢出 */
    private static final long EXPORT_BATCH_SIZE = 2000L;

    /** 申请来源常量：手动申请（默认来源） */
    private static final String APPLY_SOURCE_MANUAL = "MANUAL";

    /** 申请来源常量：内部快速寄样（由系统内部流程触发） */
    private static final String APPLY_SOURCE_INTERNAL_QUICK_SAMPLE = "INTERNAL_QUICK_SAMPLE";

    /** 寄样申请单数据访问层，负责寄样申请单的 CRUD 操作及分页查询 */
    private final SampleRequestMapper sampleRequestMapper;

    /** 商品数据访问层，用于查询商品信息（寄样申请关联商品、商品搜索等） */
    private final ProductMapper productMapper;

    /** 商品运营状态数据访问层，用于查询商品的运营状态（如是否已上架等） */
    private final ProductOperationStateMapper productOperationStateMapper;

    /** 商品快照数据访问层，用于在创建寄样时保存商品当前信息快照 */
    private final ProductSnapshotMapper productSnapshotMapper;

    /** 系统用户门面，用于查询用户信息（创建人姓名、归属部门等） */
    private final UserDomainFacade userDomainFacade;

    /** 达人数据访问层，用于查询和关联达人信息 */
    private final TalentMapper talentMapper;

    /** 达人认领关系数据访问层，用于校验渠道人员是否已认领指定达人 */
    private final TalentClaimMapper talentClaimMapper;

    /** 寄样状态日志业务服务，负责状态流转日志的记录和查询 */
    private final SampleStatusLogService sampleStatusLogService;

    /** 寄样状态日志数据访问层，提供状态变更日志的底层查询能力 */
    private final SampleStatusLogMapper sampleStatusLogMapper;

    /** 爬虫达人信息查询服务，用于从爬虫达人库中搜索候选达人（含粉丝数、等级等数据） */
    private final CrawlerTalentInfoService crawlerTalentInfoService;

    /** 配置域门面，读取寄样限制等可配置业务规则（DDD-CONFIG-002） */
    private final ConfigDomainFacade configDomainFacade;

    /** 商品业务服务，提供商品查询、商品关键词搜索等能力 */
    private final ProductService productService;

    /** 寄样资格校验服务，校验达人是否满足寄样条件（30 天销售额、等级等规则） */
    private final SampleEligibilityService sampleEligibilityService;

    /** 物流信息同步服务，负责调用物流平台接口获取最新物流轨迹并更新寄样单 */
    private final SampleLogisticsSyncService sampleLogisticsSyncService;

    /** 物流导入服务，支持通过 Excel 模板批量导入物流单号 */
    private final SampleLogisticsImportService sampleLogisticsImportService;

    /** 物流订阅服务，负责订阅/取消物流轨迹推送通知 */
    private final SampleLogisticsSubscriptionService sampleLogisticsSubscriptionService;

    /** 寄样领域事件发布器，发布寄样状态变更等业务事件到事件总线 */
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    /** 寄样写操作事务服务，封装寄样相关的写操作事务边界，确保数据一致性 */
    private final SampleWriteTransactionService sampleWriteTransactionService;

    /**
     * 构造寄样管理控制器，通过构造器注入所有必需的依赖。
     *
     * <p>Spring 框架通过此构造器自动装配以下 18 个依赖组件：
     * <ol>
     *   <li>数据访问层（Mapper）：寄样申请单、商品、商品运营状态、商品快照、系统用户、达人、达人认领、状态日志</li>
     *   <li>业务服务层（Service）：状态日志、爬虫达人查询、业务规则配置、商品服务、资格校验</li>
     *   <li>物流相关服务：物流同步、物流导入、物流订阅</li>
     *   <li>领域基础设施：领域事件发布器、写操作事务服务</li>
     * </ol>
     *
     * @param sampleRequestMapper                寄样申请单数据访问层
     * @param productMapper                      商品数据访问层
     * @param productOperationStateMapper         商品运营状态数据访问层
     * @param productSnapshotMapper               商品快照数据访问层
     * @param userDomainFacade                      系统用户门面
     * @param talentMapper                        达人数据访问层
     * @param talentClaimMapper                   达人认领关系数据访问层
     * @param sampleStatusLogService              寄样状态日志业务服务
     * @param sampleStatusLogMapper               寄样状态日志数据访问层
     * @param crawlerTalentInfoService            爬虫达人信息查询服务
     * @param configDomainFacade                  配置域门面
     * @param productService                      商品业务服务
     * @param sampleEligibilityService            寄样资格校验服务
     * @param sampleLogisticsSyncService          物流信息同步服务
     * @param sampleLogisticsImportService        物流导入服务
     * @param sampleLogisticsSubscriptionService  物流订阅服务
     * @param sampleDomainEventPublisher          寄样领域事件发布器
     * @param sampleWriteTransactionService       寄样写操作事务服务
     */
    public SampleApplicationService(
            SampleRequestMapper sampleRequestMapper,
            ProductMapper productMapper,
            ProductOperationStateMapper productOperationStateMapper,
            ProductSnapshotMapper productSnapshotMapper,
            UserDomainFacade userDomainFacade,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleStatusLogMapper sampleStatusLogMapper,
            CrawlerTalentInfoService crawlerTalentInfoService,
            ConfigDomainFacade configDomainFacade,
            ProductService productService,
            SampleEligibilityService sampleEligibilityService,
            SampleLogisticsSyncService sampleLogisticsSyncService,
            SampleLogisticsImportService sampleLogisticsImportService,
            SampleLogisticsSubscriptionService sampleLogisticsSubscriptionService,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            SampleWriteTransactionService sampleWriteTransactionService) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.productMapper = productMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.userDomainFacade = userDomainFacade;
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleStatusLogMapper = sampleStatusLogMapper;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.configDomainFacade = configDomainFacade;
        this.productService = productService;
        this.sampleEligibilityService = sampleEligibilityService;
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
        this.sampleLogisticsImportService = sampleLogisticsImportService;
        this.sampleLogisticsSubscriptionService = sampleLogisticsSubscriptionService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
        this.sampleWriteTransactionService = sampleWriteTransactionService;
    }

    /**
     * 创建寄样申请，初始化寄样状态为"待审核"（{@code PENDING_AUDIT}）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具有创建寄样申请的操作权限</li>
     *   <li>查询并校验商品是否存在，且商品已加入商品库（{@code selectedToLibrary = true}）</li>
     *   <li>解析达人信息：通过爬虫达人库查询达人详情，若本地不存在则自动创建达人记录</li>
     *   <li>校验渠道人员是否已认领该达人（渠道专员/渠道组长必须先认领）</li>
     *   <li>校验 7 天内是否有重复申请（同用户、同达人、同商品），管理员和渠道组长豁免</li>
     *   <li>校验达人寄样资格（30 天销售额、等级等规则），若不满足则记录资格原因</li>
     *   <li>组装寄样申请单数据并持久化，设置初始状态为 PENDING_AUDIT</li>
     *   <li>记录状态日志，发布寄样创建领域事件</li>
     * </ol>
     *
     * @param request     寄样申请请求体（含商品 ID、达人 ID、数量、收件信息等）
     * @param userId      当前登录用户 ID（从请求上下文自动注入）
     * @param roleCodes   当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 创建成功的寄样申请详情视图对象
     * @throws BusinessException 商品不存在、商品未加入商品库、达人信息异常时抛出业务异常
     * @see SampleStatus#PENDING_AUDIT 初始状态
     * @see SampleEligibilityService 寄样资格校验逻辑
     */
    @Operation(summary = "创建寄样申请", description = "发起寄样申请并初始化寄样状态，用于达人寄样闭环的起点。")
    @PostMapping
    public ApiResult<SampleVO> createSample(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样申请请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"talentId\":\"test_talent_001\",\"quantity\":1,\"remark\":\"优先安排发货\"}"))
            )
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        ensureSampleApplyPermission(roleCodes);
        Product product = requireProduct(request.getProductId());
        // 寄样前必须先加入商品库
        ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
        if (snapshot != null) {
            ProductOperationState state = productOperationStateMapper.selectOne(
                    new LambdaQueryWrapper<ProductOperationState>()
                            .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                            .eq(ProductOperationState::getProductId, snapshot.getProductId())
                            .last("LIMIT 1"));
            if (state == null || !Boolean.TRUE.equals(state.getSelectedToLibrary())) {
                throw BusinessException.stateInvalid("该商品尚未加入商品库，请先审核并加入商品库后再进行寄样操作");
            }
        }
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(request.getTalentId());
        Talent talent = findOrCreateTalentFromCrawler(talentInfo);
        ensureChannelTalentClaim(userId, talent.getId(), roleCodes);
        checkSevenDaysLimit(userId, talent.getId(), product.getId(), roleCodes);
        SampleEligibilityService.EligibilityResult eligibility = ensureEligibilityReasonIfNeeded(request, talent, talentInfo);

        SampleRequest sample = new SampleRequest();
        UUID currentDeptId = resolveUserDeptId(userId);
        sample.setId(UUID.randomUUID());
        sample.setRequestNo(generateRequestNo());
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talentInfo.getTalentId());
        sample.setTalentNickname(StringUtils.hasText(request.getTalentNickname()) ? request.getTalentNickname() : talentInfo.getNickname());
        sample.setTalentFansCount(request.getTalentFansCount() != null ? request.getTalentFansCount() : talentInfo.getFansCount());
        sample.setTalentCreditScore(request.getTalentCreditScore() != null ? request.getTalentCreditScore() : talentInfo.getCreditScore());
        sample.setTalentMainCategory(StringUtils.hasText(request.getTalentMainCategory()) ? request.getTalentMainCategory() : talentInfo.getMainCategory());
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setDeptId(currentDeptId);
        sample.setChannelUserId(userId);
        sample.setChannelDeptId(currentDeptId);
        sample.setExpectedSampleNum(request.getQuantity());
        sample.setActualSampleNum(0);
        sample.setRecipientName(trimToNull(request.getRecipientName()));
        sample.setRecipientPhone(trimToNull(request.getRecipientPhone()));
        sample.setRecipientAddress(trimToNull(request.getRecipientAddress()));
        sample.setStatus(SampleStatus.PENDING_AUDIT.getCode());
        sample.setRemark(request.getRemark());
        sample.setExtraData(buildSampleExtraData(request, eligibility));
        sampleRequestMapper.insert(sample);
        /* 回写收货地址到认领记录，供下次寄样自动带入 */
        writeBackClaimAddress(userId, talent.getId(), sample);
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(), userId, "create sample request");
        sampleDomainEventPublisher.publishSampleCreated(
                sample,
                product.getName(),
                resolveUserDisplayName(userId),
                resolveColonelUserId(product),
                product.getActivityId() == null ? null : String.valueOf(product.getActivityId()));

        return ok(toVO(sample, product, product.getName(), sample.getTalentNickname()));
        });
    }

    /**
     * 寄样资格预检，在创建寄样申请前检查达人是否满足默认寄样标准。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具有创建寄样申请的操作权限</li>
     *   <li>解析达人信息：从爬虫达人库中查询达人详情</li>
     *   <li>若本地达人记录不存在，自动从爬虫数据创建达人记录</li>
     *   <li>调用资格校验服务评估达人是否满足寄样条件（30 天销售额、等级等）</li>
     *   <li>返回资格校验结果，前端根据结果决定是否需要填写申请原因</li>
     * </ol>
     *
     * @param request     寄样申请请求体（用于提取达人 ID 等信息）
     * @param roleCodes   当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 资格校验结果视图对象（是否满足条件、不满足的原因说明等）
     * @throws BusinessException 达人信息解析失败时抛出业务异常
     * @see SampleEligibilityService#evaluate(Talent, CrawlerTalentInfo) 资格校验逻辑
     */
    @Operation(summary = "寄样资格预检", description = "按当前寄样默认标准检查达人是否满足要求；不满足时前端需提醒并要求填写申请原因。")
    @PostMapping("/eligibility-check")
    public ApiResult<SampleEligibilityCheckVO> checkEligibility(
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleApplyPermission(roleCodes);
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(request.getTalentId());
        Talent talent = findOrCreateTalentFromCrawler(talentInfo);
        return ok(toEligibilityVO(sampleEligibilityService.evaluate(talent, talentInfo)));
    }

    /**
     * 分页查询寄样申请列表，支持多维度筛选和数据权限控制。
     *
     * <p>处理流程：
     * <ol>
     *   <li>运营专员（OPS_STAFF）强制限制可见状态为"待发货"及后续状态，若未指定状态则默认 PENDING_SHIP</li>
     *   <li>招商专员在个人数据范围下，若未指定状态则默认展示"待审核"状态单据</li>
     *   <li>构建查询条件：状态、关键字、渠道负责人、商品/店铺/物流/达人关键词、申请时间、作业时间等多种筛选条件</li>
     *   <li>应用数据权限过滤（self / group / all），确保用户只能看到其权限范围内的寄样单</li>
     *   <li>执行分页查询，按创建时间倒序排列</li>
     *   <li>将查询结果转换为视图对象并返回</li>
     * </ol>
     *
     * @param page              页码（从 1 开始）
     * @param size              每页条数（1-100）
     * @param keyword           关键字（匹配达人昵称、达人 UID、寄样单号或商品名称）
     * @param status            寄样状态筛选（如 PENDING_AUDIT、PENDING_SHIP 等）
     * @param channelUserId     渠道负责人用户 ID 筛选
     * @param recruiterUserId   招商负责人用户 ID 筛选
     * @param productKeyword    商品 ID 或商品名称关键词
     * @param shopKeyword       店铺 ID 或店铺名称关键词
     * @param trackingNo        物流单号筛选
     * @param requestNo         申请编号 / 合作单号筛选
     * @param talentKeyword     达人昵称或达人号关键词
     * @param cooperationType   合作类型筛选
     * @param sampleOwnerType   寄样负责方筛选
     * @param homeworkType      交作业类型筛选
     * @param recipientName     收货人姓名筛选
     * @param recipientPhone    收货人手机号筛选
     * @param applyStartTime    申请开始时间筛选
     * @param applyEndTime      申请结束时间筛选
     * @param homeworkStartTime 交作业/完成开始时间筛选
     * @param homeworkEndTime   交作业/完成结束时间筛选
     * @param logisticsCompany  物流公司筛选
     * @param userId            当前登录用户 ID（从请求上下文自动注入）
     * @param deptId            当前用户所属部门 ID（从请求上下文自动注入）
     * @param dataScope         数据权限范围（self / group / all，从请求上下文自动注入）
     * @param roleCodes         当前用户角色编码列表（从请求上下文自动注入）
     * @return 分页查询结果，包含寄样申请列表和分页元信息
     */
    @Operation(summary = "寄样分页", description = "分页查询寄样申请列表，用于寄样业务页面。")
    @GetMapping
    public ApiResult<PageResult<SampleVO>> getSamplePage(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "关键字，可匹配达人昵称、达人 UID、寄样单号或商品名称。") @RequestParam(required = false) String keyword,
            @Parameter(description = "寄样状态。可用值包括 PENDING_AUDIT、PENDING_SHIP、SHIPPING、DELIVERED、PENDING_HOMEWORK、COMPLETED、REJECTED、CLOSED。") @RequestParam(required = false) String status,
            @Parameter(description = "渠道负责人用户 ID 列表（多选，IN 查询），与数据权限范围叠加。") @RequestParam(required = false) List<UUID> channelUserIds,
            @Parameter(description = "招商负责人用户 ID。") @RequestParam(required = false) UUID recruiterUserId,
            @Parameter(description = "商品 ID 或商品名称。") @RequestParam(required = false) String productKeyword,
            @Parameter(description = "店铺 ID 或店铺名称。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "物流单号（精确匹配）。") @RequestParam(required = false) String trackingNo,
            @Parameter(description = "申请编号 / 合作单号（精确匹配）。") @RequestParam(required = false) String requestNo,
            @Parameter(description = "达人昵称或达人号。") @RequestParam(required = false) String talentKeyword,
            @Parameter(description = "合作类型。") @RequestParam(required = false) String cooperationType,
            @Parameter(description = "寄样负责方。") @RequestParam(required = false) String sampleOwnerType,
            @Parameter(description = "交作业类型。") @RequestParam(required = false) String homeworkType,
            @Parameter(description = "收货人姓名。") @RequestParam(required = false) String recipientName,
            @Parameter(description = "收货人手机号。") @RequestParam(required = false) String recipientPhone,
            @Parameter(description = "申请开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyStartTime,
            @Parameter(description = "申请结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyEndTime,
            @Parameter(description = "交作业 / 完成开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkStartTime,
            @Parameter(description = "交作业 / 完成结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkEndTime,
            @Parameter(description = "物流公司。") @RequestParam(required = false) String logisticsCompany,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) com.colonel.saas.common.enums.DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        Page<SampleRequest> pageReq = new Page<>(page, size);
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();

        if (isOpsStaffOnly(roleCodes)) {
            if (!StringUtils.hasText(status)) {
                status = "PENDING_SHIP";
            }
            ensureOpsVisibleStatus(status);
        }

        // 招商专员在个人范围下，默认展示待审核单据
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) && !hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER) && !StringUtils.hasText(status)) {
            status = "PENDING_AUDIT";
        }

        applySampleQueryFilters(
                wrapper,
                status,
                keyword,
                channelUserIds,
                productKeyword,
                shopKeyword,
                trackingNo,
                requestNo,
                talentKeyword,
                cooperationType,
                sampleOwnerType,
                homeworkType,
                recipientName,
                recipientPhone,
                applyStartTime,
                applyEndTime,
                homeworkStartTime,
                homeworkEndTime,
                logisticsCompany);

        IPage<SampleRequest> samplePage;
        // 招商专员且数据范围为个人：按"我负责的商品"过滤
        if (dataScope == com.colonel.saas.common.enums.DataScope.PERSONAL && hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) && !hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER)) {
            samplePage = recruiterUserId == null
                    ? sampleRequestMapper.findPageForAuditor(pageReq, userId, wrapper)
                    : sampleRequestMapper.findPageForAuditor(pageReq, userId, wrapper, recruiterUserId);
        } else {
            samplePage = recruiterUserId == null
                    ? sampleRequestMapper.findPageWithScope(pageReq, wrapper)
                    : sampleRequestMapper.findPageWithScope(pageReq, wrapper, recruiterUserId);
        }

        Map<UUID, Product> productMap = loadProducts(samplePage.getRecords().stream()
                .map(SampleRequest::getProductId)
                .collect(Collectors.toSet()));

        List<SampleVO> records = samplePage.getRecords().stream()
                .map(item -> toVO(
                        item,
                        productMap.get(item.getProductId()),
                        productMap.get(item.getProductId()) == null ? null : productMap.get(item.getProductId()).getName(),
                        item.getTalentNickname()))
                .toList();

        Page<SampleVO> voPage = new Page<>(samplePage.getCurrent(), samplePage.getSize(), samplePage.getTotal());
        voPage.setRecords(records);
        return okPage(voPage);
    }

    /**
     * 寄样分页查询的简略重载版本，不支持渠道负责人和招商负责人筛选。
     *
     * <p>直接委托给全参版本 {@link #getSamplePage(long, long, String, String, UUID, UUID, UUID, UUID, com.colonel.saas.common.enums.DataScope, Object)}，
     * 将 channelUserId 和 recruiterUserId 传入 null。
     *
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param keyword   关键字（匹配达人昵称、达人 UID、寄样单号或商品名称）
     * @param status    寄样状态筛选
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID
     * @param dataScope 数据权限范围
     * @param roleCodes 当前用户角色编码列表
     * @return 分页查询结果
     */
    public ApiResult<PageResult<SampleVO>> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID userId,
            UUID deptId,
            com.colonel.saas.common.enums.DataScope dataScope,
            Object roleCodes) {
        return getSamplePage(page, size, keyword, status, null, null, userId, deptId, dataScope, roleCodes);
    }

    /**
     * 寄样分页查询的中等重载版本，支持渠道负责人（单选）和招商负责人筛选。
     *
     * <p>直接委托给全参版本，将单值 {@code channelUserId} 包装为单元素 {@code channelUserIds} 列表。
     *
     * @param page            页码（从 1 开始）
     * @param size            每页条数
     * @param keyword         关键字（匹配达人昵称、达人 UID、寄样单号或商品名称）
     * @param status          寄样状态筛选
     * @param channelUserId   渠道负责人用户 ID（单值）；非 null 时包装为单元素列表传给全参版本
     * @param recruiterUserId 招商负责人用户 ID 筛选
     * @param userId          当前登录用户 ID
     * @param deptId          当前用户所属部门 ID
     * @param dataScope       数据权限范围
     * @param roleCodes       当前用户角色编码列表
     * @return 分页查询结果
     */
    public ApiResult<PageResult<SampleVO>> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID channelUserId,
            UUID recruiterUserId,
            UUID userId,
            UUID deptId,
            com.colonel.saas.common.enums.DataScope dataScope,
            Object roleCodes) {
        List<UUID> channelUserIds = channelUserId == null ? null : List.of(channelUserId);
        return getSamplePage(
                page, size, keyword, status, channelUserIds, recruiterUserId,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                userId, deptId, dataScope, roleCodes);
    }

    /**
     * 搜索可用于寄样申请的达人候选列表，数据来源于爬虫达人库。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据关键字（达人昵称/UID）、地区、粉丝数范围、信用分等条件查询爬虫达人库</li>
     *   <li>分页返回匹配的达人候选数据，供前端在创建寄样申请时选择</li>
     * </ol>
     *
     * @param request 达人搜索查询请求（含关键字、地区、粉丝数范围、信用分范围、分页参数）
     * @return 分页的达人候选列表视图对象
     * @see CrawlerTalentInfoService#searchTalents 爬虫达人查询逻辑
     */
    @Operation(summary = "寄样达人搜索", description = "搜索可用于寄样申请的达人候选数据，数据来源于达人抓取结果。")
    @GetMapping("/talent-candidates")
    public ApiResult<PageResult<SampleTalentVO>> searchTalents(@Valid SampleTalentQueryRequest request) {
        IPage<SampleTalentVO> page = crawlerTalentInfoService.searchTalents(
                request.getKeyword(),
                request.getRegion(),
                request.getMinFans(),
                request.getMaxFans(),
                request.getMinScore(),
                request.getPage(),
                request.getSize()
        );
        return okPage(page);
    }

    /**
     * 搜索可用于寄样申请的商品候选列表，仅返回已加入商品库的商品。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具有创建寄样申请的操作权限</li>
     *   <li>从商品库中按关键词（商品名称或商品 ID）分页查询已入库商品</li>
     *   <li>将查询结果转换为寄样商品候选视图对象并返回</li>
     * </ol>
     *
     * @param page      页码（从 1 开始）
     * @param size      每页条数（1-100）
     * @param keyword   商品名称或商品 ID 关键词（可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 分页的商品候选列表视图对象
     * @throws ForbiddenException 用户无寄样申请权限时抛出
     * @see ProductService#getSelectedLibraryPage 商品库分页查询
     */
    @Operation(summary = "寄样商品搜索", description = "搜索可用于寄样申请的商品候选数据，返回可直接用于创建寄样申请的商品主键。")
    @GetMapping("/product-candidates")
    public ApiResult<PageResult<SampleProductVO>> searchProducts(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品名称或商品 ID。") @RequestParam(required = false) String keyword,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleApplyPermission(roleCodes);
        IPage<Product> productPage = productService.getSelectedLibraryPage(page, size, keyword, null);
        Page<SampleProductVO> result = new Page<>(productPage.getCurrent(), productPage.getSize(), productPage.getTotal());
        result.setRecords(productPage.getRecords().stream()
                .map(product -> new SampleProductVO(product.getId(), product.getProductId(), product.getName()))
                .toList());
        return okPage(result);
    }

    /**
     * 寄样商品搜索（简化版），适用于内部调用或单元测试场景。
     *
     * <p>直接委托给带 {@code roleCodes} 参数的完整版本，传入 {@code null} 表示跳过角色权限校验。
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param keyword 商品名称或商品 ID 关键词（可选）
     * @return 分页的商品候选列表视图对象
     */
    public ApiResult<PageResult<SampleProductVO>> searchProducts(long page, long size, String keyword) {
        return searchProducts(page, size, keyword, null);
    }

    /**
     * 寄样看板视图，按状态分组返回当前用户可见的全部寄样申请单。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否为纯运营角色（OPS_STAFF），若是则抛出权限异常（运营角色仅可通过发货台查看）</li>
     *   <li>分页批量加载当前用户数据范围内的全部寄样申请单（{@code BOARD_BATCH_SIZE} 一批）</li>
     *   <li>批量查询寄样关联的商品信息，构建商品 ID 到商品对象的映射</li>
     *   <li>遍历所有状态枚举（{@code SampleStatus.values()}），初始化看板各列容器</li>
     *   <li>遍历全部寄样单，按状态转换为旧版状态键并分组，同时组装看板卡片视图对象</li>
     * </ol>
     *
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选，用于行级权限过滤）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 以状态分组的看板卡片映射（键为旧版状态字符串，值为该状态下卡片列表）
     * @throws ForbiddenException 纯运营角色尝试访问看板时抛出
     * @see SampleStatus 寄样状态枚举
     * @see SampleBoardCard 看板卡片视图对象
     */
    @Operation(summary = "寄样看板", description = "按状态分组返回全量寄样单，用于看板视图。")
    @GetMapping("/board")
    public ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        if (isOpsStaffOnly(roleCodes)) {
            throw new ForbiddenException("运营角色仅可通过寄样发货台查看待发货及物流数据");
        }
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        List<SampleRequest> allSamples = loadBoardSamples(wrapper);

        Map<UUID, Product> productMap = loadProducts(allSamples.stream()
                .map(SampleRequest::getProductId)
                .collect(Collectors.toSet()));

        Map<String, List<SampleBoardCard>> board = new HashMap<>();
        for (SampleStatus s : SampleStatus.values()) {
            String key = toLegacyStatus(s);
            board.putIfAbsent(key, new ArrayList<>());
        }

        for (SampleRequest sample : allSamples) {
            SampleStatus internalStatus = SampleStatus.fromCode(sample.getStatus());
            String legacyStatus = toLegacyStatus(internalStatus);
            Product product = productMap.get(sample.getProductId());
            board.get(legacyStatus).add(toBoardCard(sample, product, internalStatus));
        }

        return ok(board);
    }

    /**
     * 寄样看板视图（简化版），适用于内部调用或单元测试场景。
     *
     * <p>直接委托给带 {@code roleCodes} 参数的完整版本，传入 {@code null} 表示跳过角色权限校验。
     *
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 数据范围注解对象（可选）
     * @return 以状态分组的看板卡片映射
     */
    public ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(UUID userId, UUID deptId, DataScope dataScope) {
        return getSampleBoard(userId, deptId, dataScope, null);
    }

    /**
     * 查询寄样状态流转矩阵，返回全部状态转换规则供前端按钮控制与验收校验使用。
     *
     * <p>该接口为只读查询，无参数，返回值包含：
     * <ul>
     *   <li>动作编码（action）：如 APPROVE、REJECT、SHIP 等</li>
     *   <li>前置状态（fromStatus）：允许执行该动作的当前状态列表</li>
     *   <li>后置状态（toStatus）：执行动作后将转换到的目标状态</li>
     *   <li>角色限制（roleCodes）：允许执行该动作的角色列表</li>
     *   <li>必填字段（requiredFields）：执行该动作时必须提供的字段列表</li>
     *   <li>错误文案（errorMessage）：条件不满足时的前端提示文案</li>
     * </ul>
     *
     * @return 寄样状态流转规则列表
     * @see SampleStatus 寄样状态枚举
     * @see SampleStatusTransitionVO 状态流转视图对象
     */
    @Operation(summary = "寄样状态流转矩阵", description = "返回寄样状态机的动作、前置状态、后置状态、角色、必填字段和错误文案，用于前端按钮与验收核验。")
    @GetMapping("/status-transitions")
    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return ok(buildStatusTransitions());
    }

    /**
     * 分页批量加载看板所需的全部寄样申请单，突破 MyBatis-Plus 单次分页查询的性能瓶颈。
     *
     * <p>每次查询 {@code BOARD_BATCH_SIZE} 条记录，循环追加直到最后一页，
     * 最终返回当前用户数据范围内的全部寄样单列表。
     *
     * @param wrapper 查询条件包装器（可附加额外过滤条件）
     * @return 当前用户可见的全部寄样申请单列表
     */
    private List<SampleRequest> loadBoardSamples(QueryWrapper<SampleRequest> wrapper) {
        List<SampleRequest> result = new ArrayList<>();
        long current = 1L;
        while (true) {
            IPage<SampleRequest> scopedPage = sampleRequestMapper.findPageWithScope(new Page<>(current, BOARD_BATCH_SIZE), wrapper);
            List<SampleRequest> records = scopedPage.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            result.addAll(records);
            if (current >= scopedPage.getPages()) {
                break;
            }
            current++;
        }
        return result;
    }

    /**
     * 查询单个寄样申请的完整详情信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>通过 {@code requireSample} 查询寄样申请单，并校验当前用户的数据范围访问权限</li>
     *   <li>查询关联的商品信息，构建商品名称、商品 ID 等展示字段</li>
     *   <li>查询寄样关联的达人信息，组装达人画像视图</li>
     *   <li>查询寄样状态变更日志列表，组装状态历史时间线</li>
     *   <li>组装完整的寄样详情视图对象（{@code SampleVO}）并返回</li>
     * </ol>
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 寄样申请完整详情视图对象
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @throws ForbiddenException        当前用户无权访问该寄样申请时抛出
     * @see SampleVO 寄样详情视图对象
     */
    @Operation(summary = "寄样详情", description = "查询单个寄样申请详情。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<SampleVO> getSampleById(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    /**
     * 查询寄样详情（简化版），适用于内部调用或单元测试场景。
     *
     * <p>直接委托给带 {@code roleCodes} 参数的完整版本，传入 {@code null} 表示跳过角色权限校验。
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 数据范围注解对象（可选）
     * @return 寄样申请完整详情视图对象
     */
    public ApiResult<SampleVO> getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope) {
        return getSampleById(id, userId, deptId, dataScope, null);
    }

    /**
     * 查询寄样申请的状态变更历史日志。
     *
     * <p>处理流程：
     * <ol>
     *   <li>通过 {@code requireSample} 校验寄样申请存在且当前用户有权限访问</li>
     *   <li>查询该寄样申请关联的全部状态变更日志，按操作时间倒序排列</li>
     *   <li>将每条日志中的前置状态、后置状态从内部状态码转换为旧版状态字符串</li>
     *   <li>查询操作人用户信息，解析操作人显示名称</li>
     *   <li>组装状态日志视图对象列表并返回</li>
     * </ol>
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 状态变更日志列表（按操作时间倒序）
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @throws ForbiddenException        当前用户无权访问该寄样申请时抛出
     * @see SampleStatusLog 寄样状态日志实体
     * @see StatusLogVO 状态日志视图对象
     */
    @Operation(summary = "寄样状态日志", description = "查询寄样申请的状态变更历史记录。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/status-logs")
    public ApiResult<List<StatusLogVO>> getStatusLogs(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        requireSample(id, userId, deptId, dataScope, roleCodes);
        List<SampleStatusLog> logs = sampleStatusLogMapper.selectList(
                new LambdaQueryWrapper<SampleStatusLog>()
                        .eq(SampleStatusLog::getRequestId, id)
                        .orderByDesc(SampleStatusLog::getOperateTime));
        List<StatusLogVO> voList = logs.stream().map(log -> {
            StatusLogVO vo = new StatusLogVO();
            vo.setId(log.getId());
            vo.setFromStatus(log.getFromStatus() == null ? null : toLegacyStatus(SampleStatus.fromCode(log.getFromStatus())));
            vo.setToStatus(log.getToStatus() == null ? null : toLegacyStatus(SampleStatus.fromCode(log.getToStatus())));
            vo.setOperatorName(resolveUserDisplayName(log.getOperatorId()));
            vo.setOperateTime(log.getOperateTime());
            vo.setRemark(log.getRemark());
            return vo;
        }).toList();
        return ok(voList);
    }

    /**
     * 执行寄样申请的状态流转操作，驱动寄样状态机向前推进。
     *
     * <p>处理流程：
     * <ol>
     *   <li>规范化动作名称（兼容旧版动作码到新枚举映射）</li>
     *   <li>校验当前用户角色是否有权限执行该动作</li>
     *   <li>查询并校验寄样申请存在且当前用户有权限访问</li>
     *   <li>根据动作类型执行对应的状态转换逻辑：
     *     <ul>
     *       <li>{@code APPROVE}：PENDING_AUDIT → PENDING_SHIP，记录审核时间</li>
     *       <li>{@code REJECTED}：PENDING_AUDIT → REJECTED，校验拒绝原因必填，记录审核时间</li>
     *       <li>{@code SHIPPING}：PENDING_SHIP → SHIPPING，校验快递单号必填，记录发货时间及物流来源</li>
     *       <li>{@code DELIVERED}：SHIPPING → DELIVERED，记录签收时间</li>
     *       <li>{@code PENDING_HOMEWORK}：SHIPPING/DELIVERED → PENDING_HOMEWORK，记录作业开始时间</li>
     *       <li>{@code COMPLETED}：PENDING_HOMEWORK → COMPLETED，记录完成时间</li>
     *       <li>{@code CLOSED}：PENDING_HOMEWORK → CLOSED，记录关闭时间和关闭原因</li>
     *     </ul>
     *   </li>
     *   <li>持久化寄样申请单更新数据</li>
     *   <li>记录状态变更日志，发布领域事件</li>
     *   <li>若动作为发货（SHIPPING），自动订阅物流轨迹推送通知</li>
     *   <li>查询关联商品信息，组装寄样详情视图对象并返回</li>
     * </ol>
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param request   状态流转请求体（含动作类型、快递单号、物流公司编码、原因等）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 状态流转后的寄样申请详情视图对象
     * @throws BusinessException         动作不支持、缺少必填字段、前置状态不匹配时抛出
     * @throws ForbiddenException        用户角色无权执行该动作时抛出
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @see SampleActionRequest 状态流转请求体
     * @see SampleStatus 寄样状态枚举
     */
    @Operation(summary = "寄样状态流转", description = "推进寄样申请状态机。动作值必须符合当前状态允许的流转规则。")
    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/status")
    public ApiResult<SampleVO> actionSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样状态流转请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"action\":\"SHIPPING\",\"trackingNo\":\"SF1234567890\",\"reason\":\"顺丰发出\"}"))
            )
            @Valid @RequestBody SampleActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        String action = SampleStateMachine.normalizeAction(request.getAction());
        ensureActionRolePermission(action, roleCodes);
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int fromStatus = sample.getStatus();
        SampleStatus current = SampleStatus.fromCode(fromStatus);

        if ("PENDING_SHIP".equals(action)) {
            SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_AUDIT);
            sample.setStatus(SampleStatus.PENDING_SHIP.getCode());
            sample.setAuditTime(now);
        } else if ("REJECTED".equals(action)) {
            SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_AUDIT);
            if (!StringUtils.hasText(request.getReason())) {
                throw BusinessException.param("reason is required when reject sample request");
            }
            sample.setStatus(SampleStatus.REJECTED.getCode());
            sample.setRejectReason(request.getReason());
            sample.setAuditTime(now);
        } else if ("SHIPPING".equals(action)) {
            SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_SHIP);
            if (!StringUtils.hasText(request.getTrackingNo())) {
                throw BusinessException.param("trackingNo is required when shipping");
            }
            sample.setStatus(SampleStatus.SHIPPING.getCode());
            sample.setTrackingNo(request.getTrackingNo());
            sample.setShipperCode(request.getShipperCode());
            putExtraValue(sample, "logisticsSource", "MANUAL");
            sample.setShipTime(now);
        } else if ("DELIVERED".equals(action)) {
            SampleStateMachine.ensureTransition(current, SampleStatus.SHIPPING);
            sample.setStatus(SampleStatus.DELIVERED.getCode());
            sample.setDeliverTime(now);
        } else if ("PENDING_HOMEWORK".equals(action)) {
            if (current == SampleStatus.SHIPPING) {
                sample.setDeliverTime(now);
            } else {
                SampleStateMachine.ensurePendingHomeworkTransition(current);
            }
            putExtraValueIfMissing(sample, "logisticsSource", "MANUAL");
            sample.setStatus(SampleStatus.PENDING_HOMEWORK.getCode());
        } else if ("COMPLETED".equals(action)) {
            SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_HOMEWORK);
            sample.setStatus(SampleStatus.COMPLETED.getCode());
            sample.setCompleteTime(now);
        } else if ("CLOSED".equals(action)) {
            SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_HOMEWORK);
            sample.setStatus(SampleStatus.CLOSED.getCode());
            sample.setCloseTime(now);
            sample.setCloseReason(request.getReason());
        } else {
            throw BusinessException.param("Unsupported action: " + request.getAction());
        }

        persistSample(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getReason());
        publishActionDomainEvent(action, sample, userId, now, request.getReason());
        if ("SHIPPING".equals(action)) {
            sampleLogisticsSubscriptionService.subscribeAfterShipment(sample);
        }
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
        });
    }

    /**
     * 删除寄样申请单，仅允许删除处于待审核或已拒绝状态的单据。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具有删除寄样申请的操作权限</li>
     *   <li>查询并校验寄样申请存在且当前用户有权限访问</li>
     *   <li>校验寄样单当前状态必须为 {@code PENDING_AUDIT} 或 {@code REJECTED}，否则拒绝删除</li>
     *   <li>从数据库中永久删除该寄样申请单记录</li>
     * </ol>
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 无返回数据的成功响应
     * @throws BusinessException         寄样单状态不允许删除时抛出
     * @throws ForbiddenException        用户无删除权限时抛出
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @see SampleStatus#PENDING_AUDIT 允许删除的状态
     * @see SampleStatus#REJECTED 允许删除的状态
     */
    @Operation(summary = "删除寄样", description = "删除寄样申请。仅待审核或已拒绝的寄样单允许删除。")
    @DeleteMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<Void> deleteSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleDeletePermission(roleCodes);
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        SampleStateMachine.ensureDeletable(status);
        sampleRequestMapper.deleteById(id);
        return ok();
    }

    /**
     * 手动刷新物流状态，查询最新物流轨迹并根据签收状态自动推进寄样单流程。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询并校验寄样申请存在且当前用户有权限访问</li>
     *   <li>校验当前用户是否具有物流同步操作权限</li>
     *   <li>调用物流同步服务（{@code SampleLogisticsSyncService.syncOne}）查询最新物流轨迹</li>
     *   <li>重新查询寄样申请单（获取同步后的最新数据）</li>
     *   <li>组装物流详情视图对象并返回（包含最新物流轨迹及签收状态）</li>
     * </ol>
     *
     * <p>权限要求：仅管理员（ADMIN）和运营专员（OPS_STAFF）可调用此接口。
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 物流详情视图对象（含物流轨迹、签收时间等）
     * @throws ForbiddenException        非管理员/运营角色调用时抛出
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @see SampleLogisticsSyncService#syncOne 物流同步核心逻辑
     * @see SampleLogisticsVO 物流详情视图对象
     */
    @Operation(summary = "手动刷新物流状态", description = "手动触发物流状态查询，若已签收则自动推进寄样单状态。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/sync")
    public ApiResult<SampleLogisticsVO> syncLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        ensureLogisticsSyncPermission(roleCodes);
        LogisticsQueryResult result = sampleLogisticsSyncService.syncOne(sample.getId());
        sample = sampleRequestMapper.selectById(id);
        return ok(toLogisticsVO(sample, result));
        });
    }

    /**
     * 手动刷新物流状态（兼容旧版路径），功能与 {@code /logistics/sync} 等价。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询并校验寄样申请存在且当前用户有权限访问</li>
     *   <li>调用物流同步服务查询最新物流轨迹</li>
     *   <li>重新查询寄样申请单及关联商品信息</li>
     *   <li>组装完整的寄样详情视图对象并返回（包含最新物流状态）</li>
     * </ol>
     *
     * <p>注意：此接口返回 {@code SampleVO}（完整寄样详情），而 {@code /logistics/sync} 返回 {@code SampleLogisticsVO}（仅物流详情）。
     *
     * <p>权限要求：仅管理员（ADMIN）和运营专员（OPS_STAFF）可调用此接口。
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 寄样申请完整详情视图对象（含最新物流状态）
     * @throws ForbiddenException        非管理员/运营角色调用时抛出
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @see SampleLogisticsSyncService#syncOne 物流同步核心逻辑
     */
    @Operation(summary = "手动刷新物流状态（兼容路径）", description = "与 /logistics/sync 等价。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/refresh")
    public ApiResult<SampleVO> refreshLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        sampleLogisticsSyncService.syncOne(sample.getId());
        sample = sampleRequestMapper.selectById(id);
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
        });
    }

    /**
     * 查询寄样申请的物流轨迹详情，返回物流状态及轨迹时间线。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询并校验寄样申请存在且当前用户有权限访问</li>
     *   <li>调用物流同步服务获取该寄样单关联的全部物流轨迹记录</li>
     *   <li>组装物流详情视图对象（含当前物流状态、快递单号、轨迹时间线）并返回</li>
     * </ol>
     *
     * @param id        寄样申请 ID（UUID 格式）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 物流详情视图对象（含物流状态、快递单号、轨迹时间线）
     * @throws ResourceNotFoundException 寄样申请不存在时抛出
     * @throws ForbiddenException        当前用户无权访问该寄样申请时抛出
     * @see SampleLogisticsVO 物流详情视图对象
     * @see SampleLogisticsTrace 物流轨迹记录实体
     */
    @Operation(summary = "查看物流轨迹", description = "返回寄样单物流状态与轨迹时间线。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics")
    public ApiResult<SampleLogisticsVO> getSampleLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        List<SampleLogisticsTrace> traces = sampleLogisticsSyncService.listTraces(id);
        return ok(toLogisticsVO(sample, traces));
    }

    /**
     * 批量同步物流状态，将所有快递中的寄样单物流信息一次性刷新。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具有物流同步权限（仅管理员与运营角色允许）</li>
     *   <li>调用物流同步服务，批量拉取处于"快递中"状态的寄样单物流信息（上限 100 条）</li>
     *   <li>汇总同步结果（成功/失败/跳过条数）并返回统计摘要</li>
     * </ol>
     *
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 同步结果统计（total/success/failed/skipped）
     * @throws ForbiddenException 当前用户无物流同步权限时抛出
     * @see SampleLogisticsSyncService#syncPendingInTransit(int)
     */
    @Operation(summary = "批量同步物流", description = "运营/管理员批量同步快递中寄样单物流状态。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/logistics/sync-all")
    public ApiResult<Map<String, Integer>> syncAllLogistics(
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureLogisticsSyncPermission(roleCodes);
        SampleLogisticsSyncService.SyncBatchSummary summary = sampleLogisticsSyncService.syncPendingInTransit(100);
        return ok(Map.of(
                "total", summary.total(),
                "success", summary.success(),
                "failed", summary.failed(),
                "skipped", summary.skipped()));
    }

    /**
     * 下载物流单号批量导入 Excel 模板，供运营人员线下填写后回传。
     *
     * <p>处理流程：
     * <ol>
     *   <li>调用物流导入服务生成包含表头和示例数据的 Excel 模板字节数组</li>
     *   <li>设置响应头为 xlsx 格式并触发浏览器下载</li>
     * </ol>
     *
     * @param response HTTP 响应对象（用于输出文件流）
     * @throws IOException 文件写入响应输出流失败时抛出
     * @see SampleLogisticsImportService#generateTemplate()
     */
    @Operation(summary = "下载物流导入模板", description = "下载 Excel 批量导入物流单号模板。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @GetMapping("/logistics/import-template")
    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        byte[] bytes = sampleLogisticsImportService.generateTemplate();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"sample-logistics-import-template.xlsx\"");
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    /**
     * 从 Excel 文件批量导入物流单号，逐行校验并支持部分成功、部分失败。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收上传的 Excel 文件（{@code .xlsx} 格式）</li>
     *   <li>调用物流导入服务逐行解析：校验寄样单号是否存在、状态是否为"待发货"或"快递中"</li>
     *   <li>根据 {@code allowOverwrite} 参数决定是否覆盖已有物流单号</li>
     *   <li>更新成功的记录状态变更为"快递中"，失败的记录附带错误原因</li>
     *   <li>返回导入结果摘要（成功/失败/跳过条数及逐行明细）</li>
     * </ol>
     *
     * @param file          上传的 Excel 文件（multipart/form-data）
     * @param allowOverwrite 是否允许覆盖已有物流单号（默认 false）
     * @param userId        当前操作用户 ID（从请求上下文自动注入）
     * @param roleCodes     当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 导入结果摘要（含成功/失败/跳过条数及逐行错误明细）
     * @see LogisticsImportResult 导入结果视图对象
     * @see SampleLogisticsImportService#importTrackingNumbers(MultipartFile, UUID, Object, boolean)
     */
    @Operation(summary = "Excel 批量导入物流单号", description = "逐行校验，部分成功部分失败。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping(value = "/logistics/import", consumes = "multipart/form-data")
    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean allowOverwrite,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ok(sampleLogisticsImportService.importTrackingNumbers(file, userId, roleCodes, allowOverwrite));
    }

    /**
     * 批量审批通过寄样申请，将"待审核"状态的寄样单批量转为"待发货"。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具备审批权限（仅管理员与招商角色允许）</li>
     *   <li>遍历申请编号列表，逐条执行以下操作：
     *     <ol type="a">
     *       <li>根据申请编号查询寄样单，并校验当前用户的数据范围访问权限</li>
     *       <li>校验当前状态是否为 {@code PENDING_AUDIT}，非该状态则跳过并计数失败</li>
     *       <li>将状态变更为 {@code PENDING_SHIP}，记录审批时间</li>
     *       <li>持久化变更，写入状态日志，发布"审批通过"领域事件</li>
     *     </ol>
     *   </li>
     *   <li>汇总成功/失败条数并返回（单条失败不影响其他记录处理）</li>
     * </ol>
     *
     * @param request   批量操作请求体（含申请编号列表与备注）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 成功与失败条数统计（success/fail）
     * @see SampleBatchActionRequest 批量操作请求体
     * @see SampleStatus#PENDING_AUDIT 待审核状态
     * @see SampleStatus#PENDING_SHIP 待发货状态
     */
    @Operation(summary = "批量审批通过", description = "批量将 PENDING_AUDIT 的寄样申请审批为待发货。仅招商角色可操作。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-approve")
    public ApiResult<Map<String, Integer>> batchApprove(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        ensureActionRolePermission("PENDING_SHIP", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (String requestNo : request.getRequestNos()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(requestNo, userId, deptId, dataScope, roleCodes);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_AUDIT);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.PENDING_SHIP.getCode());
                sample.setAuditTime(now);
                persistSample(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getRemark());
                sampleDomainEventPublisher.publishSampleApproved(
                        sample, resolveColonelUserId(productMapper.selectById(sample.getProductId())), userId, now);
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch approve failed for requestNo={}: {}", requestNo, e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
        });
    }

    /**
     * 批量驳回寄样申请，将"待审核"状态的寄样单批量变更为"已驳回"，驳回原因为必填。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验驳回原因（{@code remark}）是否已填写，未填写则抛出参数异常</li>
     *   <li>校验当前用户是否具备驳回权限（仅管理员与招商角色允许）</li>
     *   <li>遍历申请编号列表，逐条执行以下操作：
     *     <ol type="a">
     *       <li>根据申请编号查询寄样单，并校验当前用户的数据范围访问权限</li>
     *       <li>校验当前状态是否为 {@code PENDING_AUDIT}，非该状态则跳过并计数失败</li>
     *       <li>将状态变更为 {@code REJECTED}，记录驳回原因与审批时间</li>
     *       <li>持久化变更，写入状态日志，发布"驳回"领域事件</li>
     *     </ol>
     *   </li>
     *   <li>汇总成功/失败条数并返回（单条失败不影响其他记录处理）</li>
     * </ol>
     *
     * @param request   批量操作请求体（含申请编号列表与驳回原因）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 成功与失败条数统计（success/fail）
     * @throws BusinessException {@code remark} 为空时抛出参数异常
     * @see SampleBatchActionRequest 批量操作请求体
     * @see SampleStatus#REJECTED 已驳回状态
     */
    @Operation(summary = "批量驳回", description = "批量将 PENDING_AUDIT 的寄样申请驳回。仅招商角色可操作，驳回原因必填。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-reject")
    public ApiResult<Map<String, Integer>> batchReject(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        if (!StringUtils.hasText(request.getRemark())) {
            throw BusinessException.param("remark is required when batch reject");
        }
        ensureActionRolePermission("REJECTED", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (String requestNo : request.getRequestNos()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(requestNo, userId, deptId, dataScope, roleCodes);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_AUDIT);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.REJECTED.getCode());
                sample.setRejectReason(request.getRemark());
                sample.setAuditTime(now);
                persistSample(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getRemark());
                sampleDomainEventPublisher.publishSampleRejected(sample, userId, request.getRemark(), now);
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch reject failed for requestNo={}: {}", requestNo, e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
        });
    }

    /**
     * 批量发货，将"待发货"状态的寄样单批量标记为"快递中"，同时录入物流单号。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具备发货权限（仅管理员与运营角色允许）</li>
     *   <li>遍历发货明细列表，逐条执行以下操作：
     *     <ol type="a">
     *       <li>根据申请编号查询寄样单，并校验当前用户的数据范围访问权限</li>
     *       <li>校验当前状态是否为 {@code PENDING_SHIP}，非该状态则跳过并计数失败</li>
     *       <li>将状态变更为 {@code SHIPPING}，写入物流单号（{@code trackingNo}）与快递公司编码（{@code shipperCode}）</li>
     *       <li>标记物流来源为"手动录入"（{@code MANUAl}），记录发货时间</li>
     *       <li>持久化变更，写入状态日志，发布"已发货"领域事件</li>
     *       <li>订阅物流轨迹跟踪，发货后自动拉取后续物流状态</li>
     *     </ol>
     *   </li>
     *   <li>汇总成功/失败条数并返回（单条失败不影响其他记录处理）</li>
     * </ol>
     *
     * @param request   批量发货请求体（含发货明细列表，每项含申请编号、物流单号、快递公司编码）
     * @param userId    当前登录用户 ID（从请求上下文自动注入）
     * @param deptId    当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope 数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes 当前用户角色编码列表（从请求上下文自动注入，可选）
     * @return 成功与失败条数统计（success/fail）
     * @see SampleBatchShipRequest 批量发货请求体
     * @see SampleBatchShipItem 批量发货明细项
     * @see SampleStatus#PENDING_SHIP 待发货状态
     * @see SampleStatus#SHIPPING 快递中状态
     */
    @Operation(summary = "批量发货", description = "批量将 PENDING_SHIP 的寄样单标记为发货（SHIPPING），同时录入物流单号。仅运营角色可操作。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/batch-ship")
    public ApiResult<Map<String, Integer>> batchShip(
            @Valid @RequestBody SampleBatchShipRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleWriteTransactionService.execute(() -> {
        ensureActionRolePermission("SHIPPING", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (SampleBatchShipItem item : request.getItems()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(item.getRequestNo(), userId, deptId, dataScope, roleCodes);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                SampleStateMachine.ensureTransition(current, SampleStatus.PENDING_SHIP);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.SHIPPING.getCode());
                sample.setTrackingNo(item.getTrackingNo());
                sample.setShipperCode(item.getShipperCode());
                putExtraValue(sample, "logisticsSource", "MANUAL");
                sample.setShipTime(now);
                persistSample(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, item.getTrackingNo());
                sampleDomainEventPublisher.publishSampleShipped(sample, userId, now);
                sampleLogisticsSubscriptionService.subscribeAfterShipment(sample);
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch ship failed for requestNo={}: {}", item.getRequestNo(), e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
        });
    }

    /**
     * 导出寄样申请列表为 CSV 文件，支持全部筛选条件。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验当前用户是否具备导出权限（管理员/招商主管/招商专员/运营角色允许）</li>
     *   <li>校验传入的状态参数是否合法（提前解析，避免在响应头已发送后才报错）</li>
     *   <li>构建查询条件包装器，应用所有筛选条件（状态、关键词、商品、店铺、物流单号、达人、时间范围等）</li>
     *   <li>设置响应头为 UTF-8 BOM 的 CSV 格式，触发浏览器下载</li>
     *   <li>分页批量查询寄样数据（每批 {@code EXPORT_BATCH_SIZE} 条），循环写入 CSV 行</li>
     *   <li>每批查询关联商品信息并缓存，避免重复查询；解析招商负责人显示名称</li>
     *   <li>全部数据写入完毕后刷新输出流；若中途发生异常，重置响应并返回 500 错误</li>
     * </ol>
     *
     * <p>CSV 字段顺序：寄样单号、达人昵称、商品名称、状态、招商负责人、收件人、收件电话、收件地址、物流单号、驳回原因、备注、创建时间。
     *
     * @param status            寄样状态筛选（可选，如 PENDING_AUDIT）
     * @param keyword           关键词搜索（可选，模糊匹配寄样单号/达人昵称）
     * @param channelUserIds    渠道负责人用户 ID 列表（可选）
     * @param recruiterUserId   招商负责人用户 ID（可选）
     * @param productKeyword    商品 ID 或商品名称（可选）
     * @param shopKeyword       店铺 ID 或店铺名称（可选）
     * @param trackingNo        物流单号（可选）
     * @param requestNo         申请编号/合作单号（可选）
     * @param talentKeyword     达人昵称或达人号（可选）
     * @param cooperationType   合作类型（可选）
     * @param sampleOwnerType   寄样负责方（可选）
     * @param homeworkType      交作业类型（可选）
     * @param recipientName     收货人姓名（可选）
     * @param recipientPhone    收货人手机号（可选）
     * @param applyStartTime    申请开始时间（可选）
     * @param applyEndTime      申请结束时间（可选）
     * @param homeworkStartTime 交作业/完成开始时间（可选）
     * @param homeworkEndTime   交作业/完成结束时间（可选）
     * @param logisticsCompany  物流公司（可选）
     * @param userId            当前登录用户 ID（从请求上下文自动注入）
     * @param deptId            当前用户所属部门 ID（从请求上下文自动注入，可选）
     * @param dataScope         数据范围注解对象（从请求上下文自动注入，可选）
     * @param roleCodes         当前用户角色编码列表（从请求上下文自动注入，可选）
     * @param response          HTTP 响应对象（用于输出 CSV 文件流）
     * @throws IOException CSV 写入响应输出流失败时抛出
     * @throws BusinessException 状态参数不合法时抛出
     */
    @Operation(summary = "寄样导出 CSV", description = "导出寄样申请列表为 CSV 文件，支持状态筛选和关键字搜索。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF})
    @GetMapping("/exports")
    public void exportSamples(
            @Parameter(description = "寄样状态。") @RequestParam(required = false) String status,
            @Parameter(description = "关键字。") @RequestParam(required = false) String keyword,
            @Parameter(description = "渠道负责人用户 ID 列表（多选，IN 查询）。") @RequestParam(required = false) List<UUID> channelUserIds,
            @Parameter(description = "招商负责人用户 ID。") @RequestParam(required = false) UUID recruiterUserId,
            @Parameter(description = "商品 ID 或商品名称。") @RequestParam(required = false) String productKeyword,
            @Parameter(description = "店铺 ID 或店铺名称。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "物流单号。") @RequestParam(required = false) String trackingNo,
            @Parameter(description = "申请编号 / 合作单号。") @RequestParam(required = false) String requestNo,
            @Parameter(description = "达人昵称或达人号。") @RequestParam(required = false) String talentKeyword,
            @Parameter(description = "合作类型。") @RequestParam(required = false) String cooperationType,
            @Parameter(description = "寄样负责方。") @RequestParam(required = false) String sampleOwnerType,
            @Parameter(description = "交作业类型。") @RequestParam(required = false) String homeworkType,
            @Parameter(description = "收货人姓名。") @RequestParam(required = false) String recipientName,
            @Parameter(description = "收货人手机号。") @RequestParam(required = false) String recipientPhone,
            @Parameter(description = "申请开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyStartTime,
            @Parameter(description = "申请结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyEndTime,
            @Parameter(description = "交作业 / 完成开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkStartTime,
            @Parameter(description = "交作业 / 完成结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkEndTime,
            @Parameter(description = "物流公司。") @RequestParam(required = false) String logisticsCompany,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes,
            HttpServletResponse response) throws IOException {
        ensureSampleExportPermission(roleCodes);
        // Validate status early, before committing response headers
        if (StringUtils.hasText(status)) {
            parseStatus(status);
        }

        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        applySampleQueryFilters(
                wrapper,
                status,
                keyword,
                channelUserIds,
                productKeyword,
                shopKeyword,
                trackingNo,
                requestNo,
                talentKeyword,
                cooperationType,
                sampleOwnerType,
                homeworkType,
                recipientName,
                recipientPhone,
                applyStartTime,
                applyEndTime,
                homeworkStartTime,
                homeworkEndTime,
                logisticsCompany);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"samples.csv\"");
        PrintWriter writer = response.getWriter();
        try {
            writer.write('\ufeff');
            writer.println("寄样单号,达人昵称,商品名称,状态,招商负责人,收件人,收件电话,收件地址,物流单号,驳回原因,备注,创建时间");

            Map<UUID, Product> productCache = new HashMap<>();
            long current = 1L;
            while (true) {
                Page<SampleRequest> exportPage = new Page<>(current, EXPORT_BATCH_SIZE);
                IPage<SampleRequest> pageResult = recruiterUserId == null
                        ? sampleRequestMapper.findPageWithScope(exportPage, wrapper)
                        : sampleRequestMapper.findPageWithScope(exportPage, wrapper, recruiterUserId);
                List<SampleRequest> records = pageResult.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                Set<UUID> productIds = records.stream().map(SampleRequest::getProductId).collect(Collectors.toSet());
                productIds.removeAll(productCache.keySet());
                if (!productIds.isEmpty()) {
                    productCache.putAll(loadProducts(productIds));
                }
                for (SampleRequest sample : records) {
                    Product product = productCache.get(sample.getProductId());
                    SampleStatus s = SampleStatus.fromCode(sample.getStatus());
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csvEscape(sample.getRequestNo()),
                            csvEscape(sample.getTalentNickname()),
                            csvEscape(product == null ? null : product.getName()),
                            csvEscape(s.getApiStatus()),
                            csvEscape(resolveUserDisplayName(sample.getChannelUserId())),
                            csvEscape(sample.getRecipientName()),
                            csvEscape(sample.getRecipientPhone()),
                            csvEscape(sample.getRecipientAddress()),
                            csvEscape(sample.getTrackingNo()),
                            csvEscape(sample.getRejectReason()),
                            csvEscape(sample.getRemark()),
                            sample.getCreateTime());
                }
                if (current >= pageResult.getPages()) {
                    break;
                }
                current++;
            }
        } catch (Exception e) {
            log.error("CSV export failed for user={}", userId, e);
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\":\"Export failed\"}");
            return;
        }
        writer.flush();
    }

    /**
     * 导出寄样申请列表的简化版（不含渠道/招商负责人筛选），委托给带完整参数的重载版本。
     *
     * <p>所有渠道负责人与招商负责人参数固定传 {@code null}，其余参数透传至
     * {@link #exportSamples(String, String, List, UUID, UUID, UUID, DataScope, Object, HttpServletResponse)}。
     *
     * @param status     寄样状态筛选（可选）
     * @param keyword    关键词搜索（可选）
     * @param userId     当前登录用户 ID
     * @param deptId     当前用户所属部门 ID（可选）
     * @param dataScope  数据范围注解对象（可选）
     * @param roleCodes  当前用户角色编码列表（可选）
     * @param response   HTTP 响应对象（用于输出 CSV 文件流）
     * @throws IOException CSV 写入响应输出流失败时抛出
     * @see #exportSamples(String, String, List, UUID, UUID, UUID, DataScope, Object, HttpServletResponse)
     */
    public void exportSamples(
            String status,
            String keyword,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            HttpServletResponse response) throws IOException {
        exportSamples(status, keyword, (UUID) null, null, userId, deptId, dataScope, roleCodes, response);
    }

    /**
     * 导出寄样申请列表的中等参数版本（含渠道/招商负责人筛选），委托给全参数版本。
     *
     * <p>将状态筛选、关键词、渠道/招商负责人参数透传至全参数版本，其余高级筛选参数（商品、店铺、物流、
     * 时间范围等）固定传 {@code null}，即导出时不做高级筛选限制。
     *
     * <p>需要以下角色之一方可调用：管理员 / 招商主管 / 招商专员 / 运营。
     *
     * @param status           寄样状态筛选（可选）
     * @param keyword          关键词搜索（可选）
     * @param channelUserId    渠道负责人用户 ID（可选）
     * @param recruiterUserId  招商负责人用户 ID（可选）
     * @param userId           当前登录用户 ID
     * @param deptId           当前用户所属部门 ID（可选）
     * @param dataScope        数据范围注解对象（可选）
     * @param roleCodes        当前用户角色编码列表（可选）
     * @param response         HTTP 响应对象（用于输出 CSV 文件流）
     * @throws IOException CSV 写入响应输出流失败时抛出
     * @see #exportSamples(String, String, List, UUID, UUID, UUID, DataScope, Object, HttpServletResponse)
     */
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF})
    public void exportSamples(
            String status,
            String keyword,
            UUID channelUserId,
            UUID recruiterUserId,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            HttpServletResponse response) throws IOException {
        exportSamples(
                status, keyword, channelUserId == null ? null : List.of(channelUserId), recruiterUserId,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                userId, deptId, dataScope, roleCodes, response);
    }

    /**
     * 根据寄样申请编号查询寄样单，同时校验当前用户的数据范围访问权限。
     *
     * <ol>
     *   <li>通过 requestNo 精确查询寄样单，未找到则抛出 NOT_FOUND 异常</li>
     *   <li>调用 {@link #assertCanAccessSample} 校验数据范围（ALL/PERSONAL/DEPT）</li>
     * </ol>
     *
     * @param requestNo      寄样申请编号（唯一业务标识）
     * @param currentUserId  当前登录用户 ID
     * @param currentDeptId  当前用户所属部门 ID
     * @param dataScope      数据范围枚举（ALL / PERSONAL / DEPT）
     * @param roleCodes      当前用户角色集合，用于角色级权限校验
     * @return 寄样单实体
     * @throws BusinessException 未找到对应寄样单时抛出
     * @throws ForbiddenException 当前用户无权访问该寄样单时抛出
     * @see #assertCanAccessSample
     */
    private SampleRequest requireSampleByRequestNo(String requestNo, UUID currentUserId, UUID currentDeptId, DataScope dataScope, Object roleCodes) {
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("LIMIT 1"));
        if (sample == null) {
            throw BusinessException.notFound("Sample request not found: " + requestNo);
        }
        assertCanAccessSample(sample, currentUserId, currentDeptId, dataScope, roleCodes);
        return sample;
    }

    /**
     * 对 CSV 字段值进行转义处理。
     * <p>
     * 当字段值包含逗号、双引号或换行符时，用双引号包裹并将内部双引号转义为两个双引号；
     * 否则原样返回。null 值返回空字符串。
     *
     * @param value 待转义的原始字符串
     * @return 转义后的 CSV 安全字符串
     */
    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 校验寄样单当前状态是否满足预期状态，不满足则抛出业务异常。
     * <p>
     * 状态机流转前置校验，确保只有处于合法前置状态的寄样单才能执行后续操作。
     *
     * @param current  寄样单当前状态
     * @param expected 操作要求的预期状态
     * @throws BusinessException 当前状态与预期不匹配时抛出
     */

    /**
     * 根据 ID 查询寄样单并校验当前用户的访问权限。
     * <p>
     * 与 {@link #requireSampleByRequestNo} 不同，此方法额外校验角色级可见性
     * （运营角色只能看到待发货及后续状态的寄样单）。
     *
     * @param id              寄样单主键 ID
     * @param currentUserId   当前登录用户 ID
     * @param currentDeptId   当前用户所属部门 ID
     * @param dataScope       数据范围枚举（ALL / PERSONAL / DEPT）
     * @param roleCodes       当前用户角色集合
     * @return 寄样单实体
     * @throws BusinessException 未找到对应寄样单时抛出
     * @throws ForbiddenException 当前用户无权访问该寄样单时抛出
     * @see #assertCanAccessSample
     * @see #ensureRoleCanAccessSample
     */
    private SampleRequest requireSample(UUID id, UUID currentUserId, UUID currentDeptId, DataScope dataScope, Object roleCodes) {
        SampleRequest sample = sampleRequestMapper.selectById(id);
        if (sample == null) {
            throw BusinessException.notFound("Sample request not found");
        }
        assertCanAccessSample(sample, currentUserId, currentDeptId, dataScope, roleCodes);
        ensureRoleCanAccessSample(sample, roleCodes);
        return sample;
    }

    /**
     * 校验当前用户是否有权访问指定寄样单（数据范围级别）。
     * <p>
     * 权限校验逻辑按优先级依次执行：
     * <ol>
     *   <li>数据范围为 ALL 或管理员角色 → 直接放行</li>
     *   <li>招商人员且该寄样单关联的商品已分配给当前用户 → 放行</li>
     *   <li>数据范围为 PERSONAL → 仅允许访问自己发起的寄样单</li>
     *   <li>数据范围为 DEPT → 仅允许访问同一部门下的寄样单</li>
     * </ol>
     *
     * @param sample         寄样单实体
     * @param currentUserId  当前登录用户 ID
     * @param currentDeptId  当前用户所属部门 ID
     * @param dataScope      数据范围枚举
     * @param roleCodes      当前用户角色集合
     * @throws ForbiddenException 当前用户无权访问该寄样单时抛出
     * @see #isSampleProductAssignedToUser
     * @see #resolveUserDeptId
     */
    private void assertCanAccessSample(SampleRequest sample, UUID currentUserId, UUID currentDeptId, DataScope dataScope, Object roleCodes) {
        if (sample == null || dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) && isSampleProductAssignedToUser(sample, currentUserId)) {
            return;
        }
        if (dataScope == DataScope.PERSONAL) {
            if (currentUserId == null || !currentUserId.equals(sample.getChannelUserId())) {
                throw new ForbiddenException("无权访问该寄样单");
            }
            return;
        }
        UUID ownerDeptId = sample.getDeptId();
        if (ownerDeptId == null) {
            ownerDeptId = resolveUserDeptId(sample.getChannelUserId());
        }
        if (currentDeptId == null || ownerDeptId == null || !currentDeptId.equals(ownerDeptId)) {
            throw new ForbiddenException("无权访问该寄样单");
        }
    }

    /**
     * 判断寄样单关联的商品是否已分配给指定用户（运营状态维度）。
     * <p>
     * 通过解析寄样单的商品 ID → 源商品 ID → 商品运营状态表中的 assignee 字段，
     * 判断该用户是否为该商品的运营负责人。
     *
     * @param sample  寄样单实体
     * @param userId  待校验的用户 ID
     * @return true 表示该商品已分配给指定用户
     * @see #resolveSampleSourceProductId
     */
    private boolean isSampleProductAssignedToUser(SampleRequest sample, UUID userId) {
        if (sample == null || sample.getProductId() == null || userId == null) {
            return false;
        }
        String sourceProductId = resolveSampleSourceProductId(sample.getProductId());
        if (!StringUtils.hasText(sourceProductId)) {
            return false;
        }
        return productOperationStateMapper.selectCount(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getProductId, sourceProductId)
                .eq(ProductOperationState::getAssigneeId, userId)) > 0;
    }

    /**
     * 根据商品主键解析抖音侧源商品 ID（product_id 字段）。
     * <p>
     * 优先从 Product 表查找，找不到则回退到 ProductSnapshot 表。
     * 用于商品运营状态关联查询。
     *
     * @param productPrimaryId 商品表主键 ID
     * @return 抖音侧源商品 ID，未找到时返回 null
     */
    private String resolveSampleSourceProductId(UUID productPrimaryId) {
        Product product = productMapper.selectById(productPrimaryId);
        if (product != null && StringUtils.hasText(product.getProductId())) {
            return product.getProductId();
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(productPrimaryId);
        if (snapshot != null && StringUtils.hasText(snapshot.getProductId())) {
            return snapshot.getProductId();
        }
        return null;
    }

    /**
     * 解析指定用户所属的部门 ID。
     *
     * @param userId 用户 ID
     * @return 部门 ID，用户不存在时返回 null
     */
    private UUID resolveUserDeptId(UUID userId) {
        if (userId == null) {
            return null;
        }
        UserOptionResponse user = userDomainFacade.getUserById(userId);
        return user == null ? null : user.deptId();
    }

    /**
     * 校验当前用户是否拥有发起寄样申请的权限。
     * <p>
     * 仅渠道角色（CHANNEL_LEADER、CHANNEL_STAFF）和管理员（ADMIN）可发起寄样申请。
     * 其他角色触发此方法将直接抛出 {@link ForbiddenException}。
     *
     * @param roleCodes 当前用户的角色编码集合
     * @throws ForbiddenException 用户不满足角色要求时抛出
     */
    private void ensureSampleApplyPermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            throw new ForbiddenException("仅渠道角色可以发起寄样申请");
        }
    }

    /**
     * 校验当前用户是否拥有删除寄样申请的权限。
     * <p>
     * 仅渠道角色（CHANNEL_LEADER、CHANNEL_STAFF）和管理员（ADMIN）可删除寄样申请。
     * 其他角色触发此方法将直接抛出 {@link ForbiddenException}。
     *
     * @param roleCodes 当前用户的角色编码集合
     * @throws ForbiddenException 用户不满足角色要求时抛出
     */
    private void ensureSampleDeletePermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            throw new ForbiddenException("仅渠道角色可以删除寄样申请");
        }
    }

    /**
     * 校验运营角色是否可访问指定寄样单。
     * <p>
     * 纯运营角色（OPS_STAFF，非 ADMIN）仅可访问处于"待发货及后续物流"状态的寄样单。
     * 该方法在查询单个寄样单详情时调用，用于限制运营人员的数据可见范围。
     * <ul>
     *     <li>非纯运营角色或 sample 为 null 时直接放行</li>
     *     <li>纯运营角色访问非可展示状态的寄样单时抛出异常</li>
     * </ul>
     *
     * @param sample    寄样单实体
     * @param roleCodes 当前用户的角色编码集合
     * @throws ForbiddenException 运营角色访问不可见状态的寄样单时抛出
     * @see #isOpsStaffOnly(Object)
     * @see #isOpsVisibleStatusCode(Integer)
     */
    private void ensureRoleCanAccessSample(SampleRequest sample, Object roleCodes) {
        if (sample == null || !isOpsStaffOnly(roleCodes)) {
            return;
        }
        if (!isOpsVisibleStatusCode(sample.getStatus())) {
            throw new ForbiddenException("运营仅可查看待发货及后续物流寄样单");
        }
    }

    /**
     * 校验当前角色是否可执行指定寄样操作。
     * <p>
     * 根据操作类型分组校验角色权限：
     * <ul>
     *     <li>审核操作（APPROVED/REJECTED）：仅招商角色（ADMIN、BIZ_STAFF）可执行</li>
     *     <li>物流操作（SHIPPING/DELIVERED/PENDING_HOMEWORK）：仅运营角色（ADMIN、OPS_STAFF）可执行</li>
     *     <li>完成/关闭操作（COMPLETED/CLOSED）：仅允许系统自动推进，手动操作直接抛出异常</li>
     *     <li>其他未知操作：不校验，直接放行</li>
     * </ul>
     *
     * @param action    操作编码（如 PENDING_SHIP、SHIPPING 等）
     * @param roleCodes 当前用户的角色编码集合
     * @throws ForbiddenException 角色不具备执行该操作的权限时抛出
     */
    private void ensureActionRolePermission(String action, Object roleCodes) {
        switch (action) {
            case "PENDING_SHIP", "REJECTED" -> {
                if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_STAFF)) {
                    throw new ForbiddenException("仅招商角色可以审核寄样");
                }
            }
            case "SHIPPING", "DELIVERED", "PENDING_HOMEWORK" -> {
                if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
                    throw new ForbiddenException("仅运营角色可以推进物流状态");
                }
            }
            case "COMPLETED", "CLOSED" -> throw new ForbiddenException("完成与关闭状态仅允许系统自动推进");
            default -> {
            }
        }
    }

    /**
     * 构建寄样单完整状态流转规则表。
     * <p>
     * 返回所有合法的状态转移定义，前端据此渲染操作按钮，后端据此校验流转合法性。每个转移条目包含：
     * <ul>
     *     <li>目标状态编码与中文名称</li>
     *     <li>对应的后端持久化状态与前端展示状态映射</li>
     *     <li>允许执行的角色列表及触发方式（USER / SYSTEM）</li>
     *     <li>必填参数校验规则与前置状态校验</li>
     *     <li>用户操作端点与批量操作端点</li>
     *     <li>操作备注说明</li>
     * </ul>
     * <p>
     * 状态机转移路径：
     * <ol>
     *     <li>PENDING_AUDIT → PENDING_SHIP（审核通过，招商角色）</li>
     *     <li>PENDING_AUDIT → REJECTED（审核拒绝，招商角色）</li>
     *     <li>PENDING_SHIP → SHIPPING（录入物流，运营角色）</li>
     *     <li>SHIPPED → DELIVERED（物流签收，运营角色）</li>
     *     <li>DELIVERED → PENDING_HOMEWORK（待交作业，运营角色）</li>
     *     <li>PENDING_TASK → FINISHED（作业完成，系统自动）</li>
     *     <li>PENDING_TASK → CLOSED（超时关闭，系统自动）</li>
     * </ol>
     *
     * @return 寄样单状态流转定义列表，每条记录描述一个合法转移
     */
    private List<SampleStatusTransitionVO> buildStatusTransitions() {
        return List.of(
                new SampleStatusTransitionVO(
                        "PENDING_SHIP",
                        "审核通过",
                        List.of("APPROVED"),
                        List.of("PENDING_AUDIT"),
                        "PENDING_SHIP",
                        "PENDING_SHIP",
                        List.of(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF),
                        "USER",
                        true,
                        List.of(),
                        null,
                        "expected PENDING_AUDIT but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        "POST /samples/batch-approve",
                        "招商审核通过"),
                new SampleStatusTransitionVO(
                        "REJECTED",
                        "审核拒绝",
                        List.of(),
                        List.of("PENDING_AUDIT"),
                        "REJECTED",
                        "REJECTED",
                        List.of(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF),
                        "USER",
                        true,
                        List.of("reason"),
                        "reason is required when reject sample request",
                        "expected PENDING_AUDIT but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        "POST /samples/batch-reject",
                        "招商审核拒绝"),
                new SampleStatusTransitionVO(
                        "SHIPPING",
                        "录入物流",
                        List.of("SHIPPED"),
                        List.of("PENDING_SHIP"),
                        "SHIPPED",
                        "SHIPPING",
                        List.of(RoleCodes.ADMIN, RoleCodes.OPS_STAFF),
                        "USER",
                        true,
                        List.of("trackingNo"),
                        "trackingNo is required when shipping",
                        "expected PENDING_SHIP but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        "POST /samples/batch-ship",
                        "运营录入物流单号"),
                new SampleStatusTransitionVO(
                        "DELIVERED",
                        "物流签收",
                        List.of(),
                        List.of("SHIPPED"),
                        "SHIPPED",
                        "DELIVERED",
                        List.of(RoleCodes.ADMIN, RoleCodes.OPS_STAFF),
                        "USER",
                        true,
                        List.of(),
                        null,
                        "expected SHIPPING but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        null,
                        "物流签收回调或运营确认签收"),
                new SampleStatusTransitionVO(
                        "PENDING_HOMEWORK",
                        "待交作业",
                        List.of("SIGNED", "PENDING_TASK"),
                        List.of("SHIPPED", "DELIVERED"),
                        "PENDING_TASK",
                        "PENDING_HOMEWORK",
                        List.of(RoleCodes.ADMIN, RoleCodes.OPS_STAFF),
                        "USER",
                        true,
                        List.of(),
                        null,
                        "expected DELIVERED but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        null,
                        "签收后进入待交作业"),
                new SampleStatusTransitionVO(
                        "COMPLETED",
                        "作业完成",
                        List.of("FINISHED"),
                        List.of("PENDING_TASK"),
                        "FINISHED",
                        "COMPLETED",
                        List.of(),
                        "SYSTEM",
                        false,
                        List.of(),
                        null,
                        "expected PENDING_HOMEWORK but was {actual}",
                        "完成与关闭状态仅允许系统自动推进",
                        null,
                        null,
                        "订单同步自动完成"),
                new SampleStatusTransitionVO(
                        "CLOSED",
                        "超时关闭",
                        List.of(),
                        List.of("PENDING_TASK"),
                        "CLOSED",
                        "CLOSED",
                        List.of(),
                        "SYSTEM",
                        false,
                        List.of("reason"),
                        null,
                        "expected PENDING_HOMEWORK but was {actual}",
                        "完成与关闭状态仅允许系统自动推进",
                        null,
                        null,
                        "待交作业超时自动关闭"));
    }

    /**
     * 校验当前用户是否有权触发物流信息同步。
     * <p>
     * 仅运营角色（ADMIN、OPS_STAFF）可手动触发物流查询与轨迹同步。
     * 该权限用于"手动刷新物流"等入口，防止非运营人员频繁调用外部物流接口。
     *
     * @param roleCodes 当前用户的角色编码集合（支持 Collection 或逗号分隔字符串）
     * @throws ForbiddenException 角色不在允许列表时抛出
     */
    private void ensureLogisticsSyncPermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅运营或管理员可触发物流同步");
        }
    }

    /**
     * 将寄样单与物流查询结果转换为物流信息 VO。
     * <p>
     * 先调用基础转换方法组装物流轨迹等字段，再将本次实时查询结果的
     * 查询状态、错误码、错误信息、物流供应商等字段合并到 VO 中。
     * <p>
     * 该方法用于"手动刷新物流"场景，用户触发实时查询后返回最新物流状态。
     *
     * @param sample      寄样单实体
     * @param queryResult 物流查询结果（可为 null，此时仅返回历史轨迹）
     * @return 物流信息 VO，包含轨迹列表与本次查询结果
     * @see #toLogisticsVO(SampleRequest, List) 基础转换方法
     */
    private SampleLogisticsVO toLogisticsVO(SampleRequest sample, LogisticsQueryResult queryResult) {
        SampleLogisticsVO vo = toLogisticsVO(sample, sampleLogisticsSyncService.listTraces(sample.getId()));
        if (queryResult != null) {
            vo.setQuerySuccess(queryResult.isSuccess());
            vo.setQueryErrorCode(queryResult.getErrorCode());
            vo.setQueryErrorMessage(queryResult.getErrorMessage());
            vo.setProvider(queryResult.getProvider());
        }
        return vo;
    }

    /**
     * 将寄样单实体与物流轨迹列表转换为物流信息 VO。
     * <p>
     * 从寄样单中提取物流相关字段（物流单号、物流公司、签收时间等），
     * 并将历史轨迹记录映射为 {@link LogisticsTraceVO} 列表一并填入。
     * <p>
     * 该方法是物流 VO 的基础转换，被带查询结果的重载方法调用。
     *
     * @param sample 寄样单实体
     * @param traces 物流轨迹列表（可为 null）
     * @return 物流信息 VO，包含物流单号、签收状态及轨迹时间线
     */
    private SampleLogisticsVO toLogisticsVO(SampleRequest sample, List<SampleLogisticsTrace> traces) {
        SampleLogisticsVO vo = new SampleLogisticsVO();
        vo.setSampleRequestId(sample.getId());
        vo.setRequestNo(sample.getRequestNo());
        vo.setTrackingNo(sample.getTrackingNo());
        vo.setLogisticsCompany(sample.getShipperCode());
        vo.setLogisticsStatus(sample.getLogisticsStatus());
        vo.setLogisticsStatusName(sample.getLogisticsStatusName());
        vo.setLogisticsLastQueryAt(sample.getLogisticsLastQueryAt());
        vo.setLogisticsLastError(sample.getLogisticsLastError());
        vo.setSignedAt(sample.getSignedAt());
        vo.setStatus(toLegacyStatus(SampleStatus.fromCode(sample.getStatus())));
        if (traces != null) {
            vo.setTraces(traces.stream().map(trace -> {
                LogisticsTraceVO item = new LogisticsTraceVO();
                item.setTraceTime(trace.getTraceTime());
                item.setTraceContent(trace.getTraceContent());
                item.setStatusCode(trace.getStatusCode());
                item.setStatusName(trace.getStatusName());
                return item;
            }).toList());
        }
        return vo;
    }

    /**
     * 校验当前用户是否有权导出寄样数据。
     * <p>
     * 允许的角色范围较宽，包括：
     * <ul>
     *     <li>ADMIN（管理员）—— 全局权限</li>
     *     <li>BIZ_LEADER（招商主管）—— 管理团队寄样数据</li>
     *     <li>BIZ_STAFF（招商专员）—— 导出本人经手的寄样数据</li>
     *     <li>OPS_STAFF（运营专员）—— 导出运营相关寄样物流数据</li>
     *     <li>CHANNEL_LEADER（渠道组长）—— 导出本组数据范围内寄样数据</li>
     * </ul>
     * 渠道专员（CHANNEL_STAFF）无导出权限。
     *
     * @param roleCodes 当前用户的角色编码集合
     * @throws ForbiddenException 角色不在允许列表时抛出
     */
    private void ensureSampleExportPermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF, RoleCodes.CHANNEL_LEADER)) {
            throw new ForbiddenException("仅管理员、招商、运营或渠道组长可导出寄样数据");
        }
    }

    /**
     * 根据商品 ID 查找商品实体，必要时从快照中物化。
     * <p>
     * 查找优先级：
     * <ol>
     *     <li>先通过商品 ID 直接查询 Product 表</li>
     *     <li>若未找到，查询 ProductSnapshot 快照表</li>
     *     <li>若快照存在且包含 productId，根据 productId 再查 Product 表</li>
     *     <li>若仍无 Product 记录，调用 {@link #materializeProductFromSnapshot} 从快照物化一条并落库</li>
     *     <li>若所有路径均未找到，抛出 {@link ValidateException}</li>
     * </ol>
     * <p>
     * 该机制保证快照中保存的商品信息在主商品表中也有对应记录，
     * 避免因上游商品被删除或同步延迟导致寄样单无法关联商品。
     *
     * @param productId 商品 ID（可为快照 ID 或商品业务 ID）
     * @return 商品实体，保证非 null
     * @throws ValidateException 商品不存在且无法从快照物化时抛出
     * @see #materializeProductFromSnapshot(ProductSnapshot)
     */
    private Product requireProduct(UUID productId) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            return product;
        }

        ProductSnapshot snapshot = productSnapshotMapper.selectById(productId);
        if (snapshot != null && StringUtils.hasText(snapshot.getProductId())) {
            product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getProductId, snapshot.getProductId())
                    .last("LIMIT 1"));
            if (product == null) {
                product = materializeProductFromSnapshot(snapshot);
                productMapper.insert(product);
            }
        }
        if (product == null) {
            throw new ValidateException("Selected product does not exist");
        }
        return product;
    }

    /**
     * 从商品快照物化出一条 Product 记录。
     * <p>
     * 将快照中保存的商品基本信息（ID、业务ID、标题、价格、封面、详情链接等）
     * 映射为正式的 Product 实体。标题优先使用快照的 title 字段，
     * 若为空则退化为 productId。商品状态默认为 1（上架），审核状态默认为 2（已通过）。
     *
     * @param snapshot 商品快照实体，来源于爬虫或上游同步
     * @return 新创建的 Product 实体（未落库，由调用方决定是否 insert）
     */
    private Product materializeProductFromSnapshot(ProductSnapshot snapshot) {
        Product product = new Product();
        product.setId(snapshot.getId());
        product.setProductId(snapshot.getProductId());
        product.setName(StringUtils.hasText(snapshot.getTitle()) ? snapshot.getTitle() : snapshot.getProductId());
        product.setPrice(snapshot.getPrice());
        product.setCover(snapshot.getCover());
        product.setDetailUrl(snapshot.getDetailUrl());
        product.setStatus(snapshot.getStatus() == null ? 1 : snapshot.getStatus());
        product.setCheckStatus(2);
        return product;
    }

    /**
     * 根据达人 ID 查找爬虫达人信息，不存在时抛出异常。
     * <p>
     * 该方法用于申请寄样时校验达人是否存在，只在爬虫达人表中查找。
     * 若达人 ID 无效或未被爬虫采集到，抛出 {@link ValidateException}。
     *
     * @param talentId 达人抖音 UID
     * @return 爬虫达人信息实体
     * @throws ValidateException 达人不存在时抛出
     * @see #resolveSampleTalentInfo(String) 解析寄样单关联达人的宽松版本
     */
    private CrawlerTalentInfo requireCrawlerTalent(String talentId) {
        CrawlerTalentInfo talentInfo = crawlerTalentInfoService.findByTalentId(talentId);
        if (talentInfo == null) {
            throw new ValidateException("Selected talent does not exist");
        }
        return talentInfo;
    }

    /**
     * 解析寄样单关联的达人信息，支持爬虫达人表与手动录入达人表双路径查找。
     * <p>
     * 查找优先级：
     * <ol>
     *     <li>先查 CrawlerTalentInfo 表（爬虫采集的达人数据）</li>
     *     <li>若未找到，再查 Talent 表（手动录入的达人数据），通过 douyinUid 匹配</li>
     *     <li>若 Talent 表中找到，调用 {@link #buildCrawlerSnapshotFromTalent} 将其转换为
     *         CrawlerTalentInfo 格式以统一后续处理</li>
     *     <li>若两者均未找到，抛出 {@link ValidateException}</li>
     * </ol>
     * <p>
     * 该双路径设计兼容两种达人数据来源：爬虫自动采集与渠道人员手动录入。
     *
     * @param talentId 达人抖音 UID
     * @return 爬虫达人信息实体（可能由手动达人数据转换而来）
     * @throws ValidateException 达人在两个表中均不存在时抛出
     * @see #requireCrawlerTalent(String) 仅查爬虫达人表的严格版本
     */
    private CrawlerTalentInfo resolveSampleTalentInfo(String talentId) {
        CrawlerTalentInfo talentInfo = crawlerTalentInfoService.findByTalentId(talentId);
        if (talentInfo != null) {
            return talentInfo;
        }
        Talent manualTalent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, talentId)
                .last("limit 1"));
        if (manualTalent == null) {
            throw new ValidateException("Selected talent does not exist");
        }
        return buildCrawlerSnapshotFromTalent(manualTalent, talentId);
    }

    /**
     * 将手动录入的 Talent 实体转换为 CrawlerTalentInfo 格式。
     * <p>
     * 主要用于 {@link #resolveSampleTalentInfo(String)} 的双路径查找：
     * 当爬虫达人表中无记录、但手动达人表中有记录时，将手动达人数据
     * 映射为统一的 CrawlerTalentInfo 格式以便后续处理。
     * <p>
     * 字段映射规则：
     * <ul>
     *     <li>talentId：优先使用 douyinUid，为空则使用 selectedTalentId 参数</li>
     *     <li>nickname / avatarUrl：直接映射</li>
     *     <li>fansCount：对应 Talent.fans</li>
     *     <li>mainCategory：优先使用 mainCategory，为空则退化为 categories</li>
     *     <li>region：对应 Talent.ipLocation</li>
     * </ul>
     *
     * @param talent          手动录入的达人实体
     * @param selectedTalentId 前端传入的达人 ID（兜底值）
     * @return 转换后的爬虫达人信息实体
     */
    private CrawlerTalentInfo buildCrawlerSnapshotFromTalent(Talent talent, String selectedTalentId) {
        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId(StringUtils.hasText(talent.getDouyinUid()) ? talent.getDouyinUid() : selectedTalentId);
        info.setNickname(talent.getNickname());
        info.setAvatarUrl(talent.getAvatarUrl());
        info.setFansCount(talent.getFans());
        info.setMainCategory(StringUtils.hasText(talent.getMainCategory()) ? talent.getMainCategory() : talent.getCategories());
        info.setRegion(talent.getIpLocation());
        return info;
    }

    /**
     * 根据爬虫达人信息查找或创建手动录入的 Talent 记录。
     * <p>
     * 先通过抖音 UID 在 Talent 表中查找已有记录，找到则直接返回；
     * 未找到则根据爬虫达人信息创建一条新的 Talent 记录并落库。
     * 新创建的达人状态默认为 1（正常），仅映射 UID、昵称、粉丝数三个核心字段。
     * <p>
     * 该方法用于寄样申请时，将爬虫采集的达人信息同步到业务达人主表，
     * 保证达人管理模块可统一查看所有达人的寄样历史。
     *
     * @param info 爬虫达人信息实体
     * @return Talent 实体（已有记录或新创建的记录）
     */
    private Talent findOrCreateTalentFromCrawler(CrawlerTalentInfo info) {
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, info.getTalentId())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        Talent talent = new Talent();
        talent.setDouyinUid(info.getTalentId());
        talent.setNickname(info.getNickname());
        talent.setFans(info.getFansCount());
        talent.setStatus(1);
        talentMapper.insert(talent);
        return talent;
    }

    /**
     * 校验渠道人员对指定达人的私海认领关系。
     * <p>
     * 仅针对渠道角色（CHANNEL_STAFF、CHANNEL_LEADER）生效，ADMIN 角色跳过此校验。
     * 渠道人员申请寄样前，必须已在达人私海中认领该达人，否则抛出异常。
     * <p>
     * 校验逻辑：
     * <ol>
     *     <li>非渠道角色或 ADMIN 角色直接跳过</li>
     *     <li>userId 或 talentId 为 null 时抛出"达人信息不完整"异常</li>
     *     <li>查询 TalentClaim 表确认该用户是否已认领该达人，未认领则抛出"请先认领"异常</li>
     * </ol>
     *
     * @param userId    当前用户 ID
     * @param talentId  达人 ID
     * @param roleCodes 当前用户的角色编码集合
     * @throws ValidateException 达人信息不完整时抛出
     * @throws ForbiddenException 达人不在当前用户私海中时抛出
     */
    private void ensureChannelTalentClaim(UUID userId, UUID talentId, Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER)
                || hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (userId == null || talentId == null) {
            throw new ValidateException("该达人信息不完整，请重新选择");
        }
        if (talentClaimMapper.findActiveByTalentAndUser(talentId, userId) == null) {
            throw new ForbiddenException("该达人未在你的私海中，请先认领后再申请寄样");
        }
    }

    /**
     * 校验寄样申请的七天重复限制（反薅机制）。
     * <p>
     * 防止同一渠道人员在短时间内对同一达人同一商品重复申请寄样。校验逻辑：
     * <ol>
     *     <li>先检查当前用户是否享有豁免权（{@link #isExemptFromSevenDaysLimit}）</li>
     *     <li>检查业务配置是否启用重复限制开关（{@code sampleRestrictEnabled}）</li>
     *     <li>查询最近 N 天内（配置项 {@code sampleRestrictDays}，通常 7 天）是否存在
     *         同一用户、同一达人、同一商品的非拒绝寄样记录</li>
     *     <li>若存在则抛出重复申请异常</li>
     * </ol>
     *
     * @param userId     渠道用户 ID
     * @param talentId   达人 ID
     * @param productId  商品 ID
     * @param roleCodes  当前用户的角色编码集合（用于判断是否豁免）
     * @throws BusinessException 存在重复申请时抛出
     * @see #isExemptFromSevenDaysLimit(Object)
     */
    private void checkSevenDaysLimit(UUID userId, UUID talentId, UUID productId, Object roleCodes) {
        if (isExemptFromSevenDaysLimit(roleCodes)) {
            return;
        }
        if (!configDomainFacade.isSampleLimitEnabled()) {
            return;
        }
        int restrictDays = configDomainFacade.getSampleLimitDays();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(restrictDays);
        Long count = sampleRequestMapper.selectCount(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getChannelUserId, userId)
                .eq(SampleRequest::getTalentId, talentId)
                .eq(SampleRequest::getProductId, productId)
                .ne(SampleRequest::getStatus, SampleStatus.REJECTED.getCode())
                .ge(SampleRequest::getCreateTime, sevenDaysAgo));
        if (count != null && count > 0) {
            throw BusinessException.duplicate("Duplicate sample request is blocked within " + restrictDays + " days");
        }
    }

    /**
     * 判断当前用户是否豁免七天重复寄样限制。
     * <p>
     * 豁免角色包括：
     * <ul>
     *     <li>ADMIN（管理员）—— 全局豁免</li>
     *     <li>CHANNEL_LEADER（渠道主管）—— 管理人员豁免</li>
     * </ul>
     * <p>
     * 支持多种 roleCodes 参数格式：Collection、逗号分隔字符串、带方括号的字符串。
     * 内部统一做大小写不敏感比较。
     *
     * @param roleCodes 当前用户的角色编码集合
     * @return true 表示豁免七天限制，false 表示需要校验
     * @see #isExemptRoleCode(String)
     */
    private boolean isExemptFromSevenDaysLimit(Object roleCodes) {
        if (roleCodes == null) {
            return false;
        }
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(this::isExemptRoleCode);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        for (String role : normalized.split(",")) {
            if (isExemptRoleCode(role.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定角色编码是否属于七天重复限制的豁免角色。
     * <p>
     * 豁免角色：ADMIN（管理员）、CHANNEL_LEADER（渠道主管）。
     * 角色编码比较不区分大小写。
     *
     * @param roleCode 角色编码（如 "admin"、"channel_leader"）
     * @return true 表示该角色豁免限制
     * @see RoleCodes#ADMIN
     * @see RoleCodes#CHANNEL_LEADER
     */
    private boolean isExemptRoleCode(String roleCode) {
        return RoleCodes.ADMIN.equals(roleCode)
                || RoleCodes.CHANNEL_LEADER.equals(roleCode);
    }

    /**
     * 判断当前用户是否拥有期望角色中的任意一个。
     * <p>
     * 该方法是寄样权限校验的核心工具方法，支持多种 roleCodes 参数格式：
     * <ul>
     *     <li>{@code Collection} 类型——直接遍历比较</li>
     *     <li>{@code String} 类型——按逗号拆分，支持带方括号的格式（如 "[admin,ops_staff]"）</li>
     * </ul>
     * <p>
     * 所有角色比较均不区分大小写，忽略首尾空白。
     *
     * @param roleCodes     当前用户的角色编码，支持 Collection 或逗号分隔字符串
     * @param expectedRoles 期望匹配的角色编码数组（至少匹配一个即返回 true）
     * @return true 表示用户拥有期望角色中的至少一个
     */
    private boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        if (roleCodes == null || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        Set<String> expected = java.util.Arrays.stream(expectedRoles)
                .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(expected::contains);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        for (String role : normalized.split(",")) {
            if (expected.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验达人资质并确保申请原因已填写（当资质不达标时）。
     * <p>
     * 调用 {@link SampleEligibilityService#evaluate} 评估达人是否满足寄样标准，
     * 若不达标则要求申请人在 remark 字段中填写原因，否则抛出异常。
     * <p>
     * 该机制实现"不达标但可申请"的业务流程：运营可自行判断是否为未达标达人寄样，
     * 但必须记录申请原因作为审核依据。
     *
     * @param request    寄样申请请求
     * @param talent     手动录入的达人实体
     * @param talentInfo 爬虫达人信息（可能为 null）
     * @return 资质评估结果，包含是否达标及未达标原因列表
     * @throws BusinessException 达人不达标且未填写申请原因时抛出
     */
    private SampleEligibilityService.EligibilityResult ensureEligibilityReasonIfNeeded(
            SampleApplyRequest request,
            Talent talent,
            CrawlerTalentInfo talentInfo) {
        SampleEligibilityService.EligibilityResult result = sampleEligibilityService.evaluate(talent, talentInfo);
        if (result.eligible()) {
            return result;
        }
        if (!StringUtils.hasText(request.getRemark())) {
            throw BusinessException.stateInvalid("达人未满足默认寄样标准，请先填写申请原因后再提交");
        }
        return result;
    }

    /**
     * 构建寄样单附加数据（extra 字段）。
     * <p>
     * 将申请过程中的上下文信息序列化为 Map 结构存入寄样单的 extra JSON 字段，
     * 供后续审核、查询、导出时使用。包含以下信息：
     * <ul>
     *     <li>{@code eligibilityCheck}——资质评估结果（是否达标、失败规则、具体原因）</li>
     *     <li>{@code applyReason}——申请原因（申请人备注，不达标时必填）</li>
     *     <li>{@code requirementSnapshot}——申请时的资质标准快照（便于审核时对比当时标准）</li>
     *     <li>{@code addressSource}——收货地址来源（默认 "manual" 手动填写）</li>
     *     <li>{@code applySource}——申请来源（手动录入 / 快速寄样）</li>
     *     <li>{@code externalApply}——是否外部申请（默认 false）</li>
     *     <li>{@code applyChannel}——申请渠道（默认 "INTERNAL_SAMPLE_REQUEST"）</li>
     * </ul>
     *
     * @param request     寄样申请请求
     * @param eligibility 资质评估结果
     * @return 可序列化为 JSON 的附加数据 Map
     */
    private Map<String, Object> buildSampleExtraData(
            SampleApplyRequest request,
            SampleEligibilityService.EligibilityResult eligibility) {
        Map<String, Object> extra = new LinkedHashMap<>();
        Map<String, Object> eligibilityCheck = new LinkedHashMap<>();
        eligibilityCheck.put("passed", eligibility.eligible());
        eligibilityCheck.put("failedRules", sampleEligibilityService.classifyFailureRules(eligibility.reasons()));
        eligibilityCheck.put("reasons", eligibility.reasons());
        String applySource = normalizeApplySource(request.getApplySource());
        extra.put("eligibilityCheck", eligibilityCheck);
        extra.put("applyReason", StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        extra.put("requirementSnapshot", buildRequirementSnapshot(eligibility));
        extra.put("addressSource", "manual");
        extra.put("applySource", applySource);
        extra.put("externalApply", false);
        extra.put("applyChannel", "INTERNAL_SAMPLE_REQUEST");
        return extra;
    }

    /**
     * 标准化寄样申请来源。
     * <p>
     * 将前端传入的申请来源值归一化为两种合法值之一：
     * <ul>
     *     <li>{@code INTERNAL_QUICK_SAMPLE}——快速寄样入口发起</li>
     *     <li>{@code MANUAL}（默认值）——手动创建寄样申请</li>
     * </ul>
     * 输入为空或无法识别的值时统一返回 MANUAL。
     *
     * @param applySource 前端传入的申请来源标识
     * @return 标准化后的申请来源编码
     */
    private String normalizeApplySource(String applySource) {
        if (!StringUtils.hasText(applySource)) {
            return APPLY_SOURCE_MANUAL;
        }
        String normalized = applySource.trim().toUpperCase(Locale.ROOT);
        return APPLY_SOURCE_INTERNAL_QUICK_SAMPLE.equals(normalized)
                ? APPLY_SOURCE_INTERNAL_QUICK_SAMPLE
                : APPLY_SOURCE_MANUAL;
    }

    /**
     * 构建寄样申请时的资质标准快照。
     * <p>
     * 记录申请时刻的寄样标准与达人实际数据，便于审核时回溯对比：
     * <ul>
     *     <li>{@code min30DaySales}——标准要求的最低 30 天销售额</li>
     *     <li>{@code minLevel}——标准要求的最低达人等级</li>
     *     <li>{@code actual30DaySales}——达人实际 30 天销售额</li>
     *     <li>{@code actualLevel}——达人实际等级</li>
     *     <li>{@code rawStandard}——原始标准配置（非空时才记录）</li>
     * </ul>
     * <p>
     * 快照机制保证审核时看到的是申请时刻的标准，而非当前已变更的标准。
     *
     * @param eligibility 资质评估结果
     * @return 资质标准快照 Map
     */
    private Map<String, Object> buildRequirementSnapshot(SampleEligibilityService.EligibilityResult eligibility) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("min30DaySales", eligibility.standard().min30DaySales());
        snapshot.put("minLevel", eligibility.standard().minLevel());
        snapshot.put("actual30DaySales", eligibility.actual().monthlySales());
        snapshot.put("actualLevel", eligibility.actual().level());
        if (eligibility.standard().raw() != null && !eligibility.standard().raw().isEmpty()) {
            snapshot.put("rawStandard", eligibility.standard().raw());
        }
        return snapshot;
    }

    /**
     * 将资质评估结果转换为前端展示 VO。
     * <p>
     * 映射关系：
     * <ul>
     *     <li>{@code eligible}←评估是否通过</li>
     *     <li>{@code needReason}←未通过时为 true（前端据此展示申请原因输入框）</li>
     *     <li>{@code reasons}←不达标原因列表</li>
     *     <li>{@code min30DaySales / minLevel}←标准要求值</li>
     *     <li>{@code current30DaySales / currentLevel}←达人实际值</li>
     * </ul>
     *
     * @param result 资质评估结果对象
     * @return 前端展示用的资质评估 VO
     */
    private SampleEligibilityCheckVO toEligibilityVO(SampleEligibilityService.EligibilityResult result) {
        SampleEligibilityCheckVO vo = new SampleEligibilityCheckVO();
        vo.setEligible(result.eligible());
        vo.setNeedReason(!result.eligible());
        vo.setReasons(result.reasons());
        vo.setMin30DaySales(result.standard().min30DaySales());
        vo.setMinLevel(result.standard().minLevel());
        vo.setCurrent30DaySales(result.actual().monthlySales());
        vo.setCurrentLevel(result.actual().level());
        return vo;
    }

    /**
     * 生成寄样单号。
     * <p>
     * 单号格式为 {@code SM + 日期(yyyyMMdd) + 8位大写UUID片段}，例如 {@code SM20260527A1B2C3D4}。
     * 日期前缀保证按时间排序，UUID 片段保证同日单号不重复。
     *
     * @return 唯一寄样单号
     */
    private String generateRequestNo() {
        String date = LocalDateTime.now().format(REQUEST_NO_DATE);
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "SM" + date + unique;
    }

    /**
     * 批量加载商品实体并返回 ID→商品 映射表。
     * <p>
     * 用于列表查询场景中一次性加载所有涉及的商品，避免 N+1 查询问题。
     * 传入空集合时直接返回空 Map，不触发数据库查询。
     *
     * @param ids 商品 ID 集合
     * @return 商品 ID 到商品实体的映射表，ID 为 null 的商品会被过滤
     */
    private Map<UUID, Product> loadProducts(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productMapper.selectBatchIds(ids);
        Map<UUID, Product> map = new HashMap<>();
        for (Product product : products) {
            map.put(product.getId(), product);
        }
        return map;
    }

    /**
     * 根据关键词模糊匹配商品，返回匹配的商品 ID 集合。
     * <p>
     * 匹配逻辑：商品名称（name）或外部商品 ID（product_id）包含关键词即命中。
     * 结果集上限为 {@link #PRODUCT_KEYWORD_BATCH_SIZE}，防止关键词过宽导致大量匹配。
     * 关键词为空时直接返回空集合。
     *
     * @param keyword 搜索关键词
     * @return 匹配商品的 ID 集合，空集合表示无匹配或关键词为空
     */
    private Set<UUID> loadMatchedProductIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Set.of();
        }
        QueryWrapper<Product> wrapper = new QueryWrapper<Product>()
                .select("id")
                .and(query -> query.like("name", keyword).or().like("product_id", keyword))
                .last("LIMIT " + PRODUCT_KEYWORD_BATCH_SIZE);
        return productMapper.selectList(wrapper).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 将前端传入的多维度查询条件应用到 MyBatis-Plus 的 QueryWrapper 上。
     * <p>
     * 支持以下过滤维度，各维度之间为 AND 关系：
     * <ol>
     *     <li>寄样状态（status）——精确匹配</li>
     *     <li>通用关键词（keyword）——模糊匹配达人昵称/UID/寄样单号，或精确匹配关联商品 ID</li>
     *     <li>渠道人员（channelUserIds）——多选 IN 匹配</li>
     *     <li>商品关键词（productKeyword）——先模糊匹配商品，再过滤寄样单</li>
     *     <li>店铺关键词（shopKeyword）——先按店铺名/ID 匹配快照，再过滤寄样单</li>
     *     <li>快递单号（trackingNo）——精确匹配</li>
     *     <li>寄样单号（requestNo）——精确匹配</li>
     *     <li>达人关键词（talentKeyword）——模糊匹配达人昵称/UID</li>
     *     <li>合作类型（cooperationType）——JSON 字段精确匹配</li>
     *     <li>寄样归属类型（sampleOwnerType）——JSON 字段精确匹配</li>
     *     <li>作业类型（homeworkType）——特殊逻辑：有订单→COMPLETED 状态，无订单→PENDING_HOMEWORK 状态</li>
     *     <li>收件人姓名/手机号（recipientName/recipientPhone）——模糊匹配</li>
     *     <li>申请时间范围（applyStartTime/applyEndTime）——create_time 区间</li>
     *     <li>作业时间范围（homeworkStartTime/homeworkEndTime）——complete_time 或 signed_at 区间</li>
     *     <li>物流公司（logisticsCompany）——精确匹配快递公司编码</li>
     * </ol>
     *
     * @param wrapper            MyBatis-Plus 查询包装器（会被直接修改）
     * @param status             寄样状态
     * @param keyword            通用关键词
     * @param channelUserIds     渠道人员用户 ID 列表（多选）；null/空时不过滤
     * @param productKeyword     商品名称/ID 关键词
     * @param shopKeyword        店铺名称/ID 关键词
     * @param trackingNo         快递单号（精确匹配）
     * @param requestNo          寄样单号（精确匹配）
     * @param talentKeyword      达人昵称/UID 关键词
     * @param cooperationType    合作类型
     * @param sampleOwnerType    寄样归属类型
     * @param homeworkType       作业类型（HAS_ORDER / NO_ORDER / 自定义值）
     * @param recipientName      收件人姓名
     * @param recipientPhone     收件人手机号
     * @param applyStartTime     申请起始时间
     * @param applyEndTime       申请截止时间
     * @param homeworkStartTime  作业完成起始时间
     * @param homeworkEndTime    作业完成截止时间
     * @param logisticsCompany   物流公司编码
     */
    private void applySampleQueryFilters(
            QueryWrapper<SampleRequest> wrapper,
            String status,
            String keyword,
            List<UUID> channelUserIds,
            String productKeyword,
            String shopKeyword,
            String trackingNo,
            String requestNo,
            String talentKeyword,
            String cooperationType,
            String sampleOwnerType,
            String homeworkType,
            String recipientName,
            String recipientPhone,
            LocalDateTime applyStartTime,
            LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime,
            LocalDateTime homeworkEndTime,
            String logisticsCompany) {
        if (StringUtils.hasText(status)) {
            wrapper.eq("sr.status", parseStatus(status).getCode());
        }
        if (StringUtils.hasText(keyword)) {
            Set<UUID> matchedProductIds = loadMatchedProductIds(keyword.trim());
            wrapper.and(query -> {
                query.like("sr.talent_nickname", keyword.trim())
                        .or()
                        .like("sr.talent_uid", keyword.trim())
                        .or()
                        .like("sr.request_no", keyword.trim());
                if (!matchedProductIds.isEmpty()) {
                    query.or().in("sr.product_id", matchedProductIds);
                }
            });
        }
        if (channelUserIds != null && !channelUserIds.isEmpty()) {
            // 多选：去重后使用 IN 过滤；与数据权限范围 (channel_user_id = userId) AND 叠加
            List<UUID> distinct = channelUserIds.stream().filter(Objects::nonNull).distinct().toList();
            if (distinct.size() == 1) {
                wrapper.eq("sr.channel_user_id", distinct.get(0));
            } else if (!distinct.isEmpty()) {
                wrapper.in("sr.channel_user_id", distinct);
            }
        }
        if (StringUtils.hasText(productKeyword)) {
            applyProductIdsFilter(wrapper, loadMatchedProductIds(productKeyword.trim()));
        }
        if (StringUtils.hasText(shopKeyword)) {
            applyProductIdsFilter(wrapper, loadMatchedProductIdsByShop(shopKeyword.trim()));
        }
        if (StringUtils.hasText(trackingNo)) {
            // 物流单号必须精确匹配（用户按运单号精准查找）
            wrapper.eq("sr.tracking_no", trackingNo.trim());
        }
        if (StringUtils.hasText(requestNo)) {
            // 寄样单号必须精确匹配（用户按单号精准查找）
            wrapper.eq("sr.request_no", requestNo.trim());
        }
        if (StringUtils.hasText(talentKeyword)) {
            String trimmed = talentKeyword.trim();
            wrapper.and(query -> query.like("sr.talent_nickname", trimmed).or().like("sr.talent_uid", trimmed));
        }
        if (StringUtils.hasText(cooperationType)) {
            wrapper.apply("sr.extra_data ->> 'cooperationType' = {0}", cooperationType.trim());
        }
        if (StringUtils.hasText(sampleOwnerType)) {
            wrapper.apply("sr.extra_data ->> 'sampleOwnerType' = {0}", sampleOwnerType.trim());
        }
        if (StringUtils.hasText(homeworkType)) {
            String normalized = homeworkType.trim().toUpperCase(Locale.ROOT);
            if ("HAS_ORDER".equals(normalized)) {
                wrapper.eq("sr.status", SampleStatus.COMPLETED.getCode());
            } else if ("NO_ORDER".equals(normalized)) {
                wrapper.eq("sr.status", SampleStatus.PENDING_HOMEWORK.getCode());
            } else {
                wrapper.apply("sr.extra_data ->> 'homeworkType' = {0}", homeworkType.trim());
            }
        }
        if (StringUtils.hasText(recipientName)) {
            wrapper.like("sr.recipient_name", recipientName.trim());
        }
        if (StringUtils.hasText(recipientPhone)) {
            wrapper.like("sr.recipient_phone", recipientPhone.trim());
        }
        if (applyStartTime != null) {
            wrapper.ge("sr.create_time", applyStartTime);
        }
        if (applyEndTime != null) {
            wrapper.le("sr.create_time", applyEndTime);
        }
        if (homeworkStartTime != null) {
            wrapper.and(query -> query.ge("sr.complete_time", homeworkStartTime).or().ge("sr.signed_at", homeworkStartTime));
        }
        if (homeworkEndTime != null) {
            wrapper.and(query -> query.le("sr.complete_time", homeworkEndTime).or().le("sr.signed_at", homeworkEndTime));
        }
        if (StringUtils.hasText(logisticsCompany)) {
            wrapper.eq("sr.shipper_code", logisticsCompany.trim());
        }
    }

    /**
     * 将商品 ID 集合作为过滤条件应用到查询包装器。
     * <p>
     * 采用"空集短路"策略：当商品 ID 为空集时，直接应用 {@code 1=0} 条件使查询结果为空，
     * 避免执行无意义的 IN 查询。非空时使用 {@code sr.product_id IN (...)} 过滤。
     *
     * @param wrapper    MyBatis-Plus 查询包装器（会被直接修改）
     * @param productIds 商品 ID 集合（允许为空集）
     */
    private void applyProductIdsFilter(QueryWrapper<SampleRequest> wrapper, Set<UUID> productIds) {
        if (productIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in("sr.product_id", productIds);
    }

    /**
     * 根据店铺关键词从商品快照表匹配关联商品，返回商品快照 ID 集合。
     * <p>
     * 匹配逻辑为 OR 关系：
     * <ul>
     *     <li>店铺名称（shop_name）包含关键词（模糊匹配）</li>
     *     <li>关键词可解析为数字时，同时匹配店铺 ID（shop_id 精确匹配）</li>
     * </ul>
     * 结果集上限为 {@link #PRODUCT_KEYWORD_BATCH_SIZE}。返回的 ID 集合用于后续关联寄样单的 product_id 过滤。
     *
     * @param keyword 店铺名称或店铺 ID 关键词
     * @return 匹配到的商品快照 ID 集合
     */
    private Set<UUID> loadMatchedProductIdsByShop(String keyword) {
        QueryWrapper<ProductSnapshot> wrapper = new QueryWrapper<ProductSnapshot>()
                .select("id")
                .and(query -> {
                    query.like("shop_name", keyword);
                    Long shopId = parseLongOrNull(keyword);
                    if (shopId != null) {
                        query.or().eq("shop_id", shopId);
                    }
                })
                .last("LIMIT " + PRODUCT_KEYWORD_BATCH_SIZE);
        return productSnapshotMapper.selectList(wrapper).stream()
                .map(ProductSnapshot::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 安全地将字符串解析为 Long 类型。
     * <p>
     * 输入为空白、null 或无法解析为数字时返回 null，不抛异常。
     * 用于店铺 ID 等字段的可选数值解析场景。
     *
     * @param value 待解析的字符串
     * @return 解析后的 Long 值，解析失败返回 null
     */
    private Long parseLongOrNull(String value) {
        try {
            return StringUtils.hasText(value) ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 将寄样单实体转换为前端展示 VO。
     * <p>
     * 这是寄样单转 VO 的主方法，执行以下组装逻辑：
     * <ol>
     *     <li>若商品实体未传入但存在 productId，从数据库补查商品</li>
     *     <li>加载商品快照（ProductSnapshot）用于补充外部 ID、封面、价格等信息</li>
     *     <li>解析团长用户 ID（通过商品运营状态表关联）</li>
     *     <li>映射基础字段：寄样单号、达人信息、商品信息、收件地址等</li>
     *     <li>映射物流字段：快递单号、物流公司、物流状态、签收时间等</li>
     *     <li>从 extra JSON 中读取并映射扩展字段：申请原因、申请来源、合作类型、归属类型、作业类型、资质校验等</li>
     *     <li>将内部状态（含 DELIVERED 等新状态）转换为旧版前端状态码</li>
     * </ol>
     *
     * @param sample     寄样单实体
     * @param product    商品实体（可为 null，会自动补查）
     * @param productName 商品名称（可为 null，从实体读取）
     * @param talentName 达人名称（可为 null，回退到寄样单的 talentNickname）
     * @return 前端展示用的 SampleVO
     */
    private SampleVO toVO(SampleRequest sample, Product product, String productName, String talentName) {
        Product resolvedProduct = product;
        if (resolvedProduct == null && sample.getProductId() != null) {
            resolvedProduct = productMapper.selectById(sample.getProductId());
        }
        ProductSnapshot snapshot = resolvedProduct == null || resolvedProduct.getId() == null
                ? null
                : productSnapshotMapper.selectById(resolvedProduct.getId());
        UUID colonelUserId = resolveColonelUserId(resolvedProduct);
        SampleVO vo = new SampleVO();
        vo.setId(sample.getId());
        vo.setRequestNo(sample.getRequestNo());
        vo.setTalentId(sample.getTalentId());
        vo.setTalentUid(sample.getTalentUid());
        vo.setTalentFansCount(sample.getTalentFansCount());
        vo.setTalentCreditScore(sample.getTalentCreditScore() == null ? null : sample.getTalentCreditScore().toPlainString());
        vo.setTalentMainCategory(sample.getTalentMainCategory());
        vo.setTalentName(StringUtils.hasText(talentName) ? talentName : sample.getTalentNickname());
        vo.setProductId(sample.getProductId());
        vo.setProductExternalId(resolveProductExternalId(resolvedProduct, snapshot));
        vo.setProductName(productName);
        vo.setProductCover(resolveProductCover(resolvedProduct, snapshot));
        vo.setProductPriceText(resolveProductPriceText(resolvedProduct, snapshot));
        vo.setShopId(snapshot == null || snapshot.getShopId() == null ? null : String.valueOf(snapshot.getShopId()));
        vo.setShopName(snapshot == null ? null : snapshot.getShopName());
        vo.setQuantity(sample.getExpectedSampleNum() == null ? 1 : sample.getExpectedSampleNum());
        vo.setApplicantUserId(sample.getUserId());
        vo.setApplicantName(resolveUserDisplayName(sample.getUserId()));
        vo.setChannelUserId(sample.getChannelUserId());
        vo.setChannelUserName(resolveUserDisplayName(sample.getChannelUserId()));
        vo.setColonelUserId(colonelUserId);
        vo.setColonelUserName(resolveUserDisplayName(colonelUserId));
        vo.setTrackingNo(sample.getTrackingNo());
        vo.setShipperCode(sample.getShipperCode());
        vo.setLogisticsCompany(sample.getShipperCode());
        vo.setRecipientName(sample.getRecipientName());
        vo.setRecipientPhone(sample.getRecipientPhone());
        vo.setRecipientAddress(sample.getRecipientAddress());
        vo.setLogisticsSource(readExtraText(sample.getExtraData(), "logisticsSource"));
        vo.setLogisticsStatus(sample.getLogisticsStatus());
        vo.setLogisticsStatusName(sample.getLogisticsStatusName());
        vo.setLogisticsLastQueryAt(sample.getLogisticsLastQueryAt());
        vo.setLogisticsLastError(sample.getLogisticsLastError());
        vo.setSignedAt(sample.getSignedAt());
        vo.setRejectReason(sample.getRejectReason());
        vo.setCloseReason(sample.getCloseReason());
        vo.setRemark(sample.getRemark());
        vo.setApplyReason(readExtraText(sample.getExtraData(), "applyReason"));
        vo.setApplySource(readExtraText(sample.getExtraData(), "applySource"));
        vo.setApplySourceLabel(resolveApplySourceLabel(vo.getApplySource()));
        vo.setCooperationType(readExtraText(sample.getExtraData(), "cooperationType"));
        vo.setCooperationTypeLabel(resolveOptionLabel(vo.getCooperationType(), "免费寄样"));
        vo.setSampleOwnerType(readExtraText(sample.getExtraData(), "sampleOwnerType"));
        vo.setSampleOwnerTypeLabel(resolveOptionLabel(vo.getSampleOwnerType(), "商家"));
        vo.setHomeworkType(readExtraText(sample.getExtraData(), "homeworkType"));
        vo.setHomeworkTypeLabel(resolveHomeworkTypeLabel(vo.getHomeworkType(), sample));
        vo.setEligibilityCheck(readExtraMap(sample.getExtraData(), "eligibilityCheck"));
        vo.setRequirementSnapshot(readExtraMap(sample.getExtraData(), "requirementSnapshot"));
        vo.setCreateTime(sample.getCreateTime());
        vo.setUpdateTime(sample.getUpdateTime());
        vo.setShipTime(sample.getShipTime());
        vo.setDeliverTime(sample.getDeliverTime());
        vo.setCompleteTime(sample.getCompleteTime());
        vo.setStatus(toLegacyStatus(SampleStatus.fromCode(sample.getStatus())));
        return vo;
    }

    /**
     * 解析商品外部 ID（抖店商品 ID）。
     * <p>
     * 优先从商品实体（Product）的 productId 字段读取；
     * 若商品实体无此值，回退到商品快照（ProductSnapshot）的 productId。
     * 两者均无时返回 null。
     *
     * @param product  商品实体（可为 null）
     * @param snapshot 商品快照（可为 null）
     * @return 商品外部 ID，无则返回 null
     */
    private String resolveProductExternalId(Product product, ProductSnapshot snapshot) {
        if (product != null && StringUtils.hasText(product.getProductId())) {
            return product.getProductId();
        }
        return snapshot == null ? null : snapshot.getProductId();
    }

    /**
     * 解析商品封面图 URL。
     * <p>
     * 优先从商品实体读取；无则回退到商品快照。两者均无时返回 null。
     * 商品快照保留了申请时刻的封面，即使后续商品图片变更也能展示当时的封面。
     *
     * @param product  商品实体（可为 null）
     * @param snapshot 商品快照（可为 null）
     * @return 商品封面图 URL，无则返回 null
     */
    private String resolveProductCover(Product product, ProductSnapshot snapshot) {
        if (product != null && StringUtils.hasText(product.getCover())) {
            return product.getCover();
        }
        return snapshot == null ? null : snapshot.getCover();
    }

    /**
     * 解析商品价格展示文本。
     * <p>
     * 优先读取快照中的 priceText（如"¥99.00"）；
     * 若快照无价格文本，从商品实体的 price 字段（单位：分）转换为带"¥"前缀的元价格。
     * 两者均无时返回 null。
     *
     * @param product  商品实体（可为 null）
     * @param snapshot 商品快照（可为 null）
     * @return 价格展示文本（如"¥99.00"），无则返回 null
     */
    private String resolveProductPriceText(Product product, ProductSnapshot snapshot) {
        if (snapshot != null && StringUtils.hasText(snapshot.getPriceText())) {
            return snapshot.getPriceText();
        }
        Long price = product == null ? null : product.getPrice();
        return price == null ? null : "¥" + (price / 100.0);
    }

    /**
     * 将申请来源编码转换为前端展示标签。
     * <p>
     * 映射关系：
     * <ul>
     *     <li>{@code INTERNAL_QUICK_SAMPLE} → "内部寄样"</li>
     *     <li>其他 → "手动申请"（默认）</li>
     * </ul>
     *
     * @param applySource 申请来源编码
     * @return 中文展示标签
     */
    private String resolveApplySourceLabel(String applySource) {
        if (APPLY_SOURCE_INTERNAL_QUICK_SAMPLE.equals(applySource)) {
            return "内部寄样";
        }
        return "手动申请";
    }

    /**
     * 通用选项标签解析器，将枚举编码转换为中文展示文本。
     * <p>
     * 支持以下编码：
     * <ul>
     *     <li>合作类型：{@code FREE_SAMPLE}→"免费寄样"、{@code PAID_SAMPLE}→"付费寄样"、{@code EXCHANGE_SAMPLE}→"置换寄样"</li>
     *     <li>寄样归属：{@code MERCHANT}→"商家"、{@code COLONEL}→"团长"、{@code OTHER}→"其他"</li>
     * </ul>
     * 输入为空时返回 fallback 默认值；无法识别的编码原样返回。
     *
     * @param value    选项编码（可为 null 或空白）
     * @param fallback 默认标签（当 value 为空时使用）
     * @return 中文展示标签
     */
    private String resolveOptionLabel(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return switch (value) {
            case "FREE_SAMPLE" -> "免费寄样";
            case "PAID_SAMPLE" -> "付费寄样";
            case "EXCHANGE_SAMPLE" -> "置换寄样";
            case "MERCHANT" -> "商家";
            case "COLONEL" -> "团长";
            case "OTHER" -> "其他";
            default -> value;
        };
    }

    /**
     * 解析作业类型展示标签。
     * <p>
     * 映射逻辑：
     * <ol>
     *     <li>若 extra 中有明确的作业类型编码，按编码映射：
     *         {@code HAS_ORDER}→"有订单"、{@code NO_ORDER}→"无订单"、{@code PARTIAL}→"部分完成"</li>
     *     <li>若 extra 中无作业类型，根据寄样单状态推断：
     *         COMPLETED→"有订单"、PENDING_HOMEWORK→"待交作业"</li>
     *     <li>其他状态返回 null</li>
     * </ol>
     *
     * @param homeworkType 作业类型编码（来自 extra JSON，可为 null）
     * @param sample       寄样单实体（用于状态推断）
     * @return 中文作业类型标签，无法确定时返回 null
     */
    private String resolveHomeworkTypeLabel(String homeworkType, SampleRequest sample) {
        if (StringUtils.hasText(homeworkType)) {
            return switch (homeworkType) {
                case "HAS_ORDER" -> "有订单";
                case "NO_ORDER" -> "无订单";
                case "PARTIAL" -> "部分完成";
                default -> homeworkType;
            };
        }
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        if (status == SampleStatus.COMPLETED) {
            return "有订单";
        }
        if (status == SampleStatus.PENDING_HOMEWORK) {
            return "待交作业";
        }
        return null;
    }

    /**
     * 从 extra JSON 数据中读取指定 key 的嵌套 Map 值。
     * <p>
     * 使用 {@code instanceof} 模式匹配进行类型安全转换。若 key 不存在或值不是 Map 类型，
     * 返回空 Map（而非 null），调用方可安全地进行链式访问。
     *
     * @param extraData 寄样单的 extra JSON Map（可为 null）
     * @param key       要读取的键名
     * @return 嵌套 Map 值，不存在或类型不匹配时返回空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readExtraMap(Map<String, Object> extraData, String key) {
        if (extraData == null || extraData.isEmpty()) {
            return Map.of();
        }
        Object value = extraData.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * 从 extra JSON 数据中读取指定 key 的文本值。
     * <p>
     * 将任意类型通过 {@code String.valueOf()} 转为字符串返回。
     * extraData 为 null/空 或 key 不存在时返回 null。
     *
     * @param extraData 寄样单的 extra JSON Map（可为 null）
     * @param key       要读取的键名
     * @return 文本值，不存在时返回 null
     */
    private String readExtraText(Map<String, Object> extraData, String key) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        Object value = extraData.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 向寄样单的 extra JSON 中写入一个键值对（覆盖写入）。
     * <p>
     * 创建 LinkedHashMap 副本以保证不可变性原则——不直接修改原 Map，
     * 而是创建新 Map 再设置回实体。若 extra 原为 null，则新建空 Map。
     *
     * @param sample 寄样单实体（会被修改 extraData 字段）
     * @param key    要写入的键名
     * @param value  要写入的值
     */
    private void putExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    /**
     * 向寄样单的 extra JSON 中写入一个键值对（仅在 key 不存在时写入）。
     * <p>
     * 与 {@link #putExtraValue} 的区别在于使用 {@code putIfAbsent} 策略，
     * 已有值不会被覆盖。同样创建 LinkedHashMap 副本以保证不可变性。
     *
     * @param sample 寄样单实体（会被修改 extraData 字段）
     * @param key    要写入的键名
     * @param value  要写入的值（仅当 key 不存在时生效）
     */
    private void putExtraValueIfMissing(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.putIfAbsent(key, value);
        sample.setExtraData(extra);
    }

    /**
     * 根据操作类型发布对应的寄样领域事件，驱动下游异步处理（通知、业绩、物流同步等）。
     * <p>
     * 处理流程：
     * <ol>
     *     <li>通过商品 ID 查询商品实体，解析团长（recruiter）用户 ID</li>
     *     <li>根据 action 参数匹配事件类型并发布：
     *         <ul>
     *             <li>{@code PENDING_SHIP} → 审批通过事件（含团长 ID）</li>
     *             <li>{@code REJECTED} → 拒绝事件（含拒绝原因）</li>
     *             <li>{@code SHIPPING} → 发货事件</li>
     *             <li>{@code PENDING_HOMEWORK} → 签收事件（优先用签收时间，否则用当前时间）</li>
     *             <li>{@code COMPLETED} → 完成事件</li>
     *             <li>{@code CLOSED} → 关闭事件（含关闭原因）</li>
     *         </ul>
     *     </li>
     *     <li>未知操作类型静默忽略（不抛异常）</li>
     * </ol>
     *
     * @param action  操作编码（对应 SampleStatus 的 code）
     * @param sample  寄样单实体
     * @param userId  执行操作的用户 ID
     * @param now     操作时间
     * @param reason  拒绝/关闭原因（仅 REJECTED/CLOSED 操作有值）
     */
    private void publishActionDomainEvent(
            String action,
            SampleRequest sample,
            UUID userId,
            LocalDateTime now,
            String reason) {
        Product product = productMapper.selectById(sample.getProductId());
        UUID recruiterId = resolveColonelUserId(product);
        switch (action) {
            case "PENDING_SHIP" -> sampleDomainEventPublisher.publishSampleApproved(sample, recruiterId, userId, now);
            case "REJECTED" -> sampleDomainEventPublisher.publishSampleRejected(sample, userId, reason, now);
            case "SHIPPING" -> sampleDomainEventPublisher.publishSampleShipped(sample, userId, now);
            case "PENDING_HOMEWORK" -> sampleDomainEventPublisher.publishSampleSigned(
                    sample, sample.getSignedAt() != null ? sample.getSignedAt() : now);
            case "COMPLETED" -> sampleDomainEventPublisher.publishSampleCompleted(sample, null, now);
            case "CLOSED" -> sampleDomainEventPublisher.publishSampleClosed(sample, reason, now);
            default -> {
            }
        }
    }

    /**
     * 通过商品运营状态表解析该商品对应的团长用户 ID。
     * <p>
     * 处理流程：
     * <ol>
     *     <li>校验商品非空且 productId 和 activityId 均有值，缺任一返回 null</li>
     *     <li>查询 product_operation_state 表，条件为 activityId + productId，取第一条记录</li>
     *     <li>返回记录中的 assigneeId 字段（即负责该商品的团长用户 ID）</li>
     * </ol>
     *
     * @param product 商品实体（可为 null）
     * @return 团长用户 ID，无法解析时返回 null
     */
    private UUID resolveColonelUserId(Product product) {
        if (product == null || !StringUtils.hasText(product.getProductId()) || product.getActivityId() == null) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, String.valueOf(product.getActivityId()))
                .eq(ProductOperationState::getProductId, product.getProductId())
                .last("limit 1"));
        return state == null ? null : state.getAssigneeId();
    }

    /**
     * 解析用户显示名称，优先展示"真实姓名 (登录名)"格式。
     * <p>
     * 显示优先级：
     * <ol>
     *     <li>realName 和 username 均有值 → "realName (username)"</li>
     *     <li>仅有 realName → realName</li>
     *     <li>仅有 username → username</li>
     *     <li>均无 → null</li>
     * </ol>
     * userId 为 null 或用户不存在时直接返回 null。
     *
     * @param userId 用户 UUID（可为 null）
     * @return 用户显示名，无法解析时返回 null
     */
    private String resolveUserDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        UserOptionResponse user = userDomainFacade.getUserById(userId);
        if (user == null) {
            return null;
        }
        String realName = normalizeDisplayText(user.realName());
        String username = normalizeDisplayText(user.username());
        if (StringUtils.hasText(realName) && StringUtils.hasText(username)) {
            return realName + " (" + username + ")";
        }
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return null;
    }

    /**
     * 对显示文本进行标准化处理（去除首尾空白字符）。
     * <p>
     * 输入为 null 时直接返回 null，不做额外处理。
     *
     * @param value 原始文本（可为 null）
     * @return 去除首尾空白后的文本，原为 null 则返回 null
     */
    private String normalizeDisplayText(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 安全地将字符串去空白并转换，空白字符串统一归一为 null。
     * <p>
     * 与 {@link #normalizeDisplayText} 的区别：此方法额外将空白字符串（如 ""、"  "）
     * 也返回 null，适用于需要将"无有效内容"统一为 null 的场景。
     *
     * @param value 原始字符串（可为 null 或空白）
     * @return 去空白后的有效文本，null 或空白输入返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 将前端传入的状态字符串解析为内部 {@link SampleStatus} 枚举。
     * <p>
     * 处理流程：
     * <ol>
     *     <li>去除首尾空白并转为大写（兼容大小写混写）</li>
     *     <li>通过 {@code SampleStatus.fromApiStatus()} 映射为枚举</li>
     *     <li>映射失败时抛出参数异常，提示无效状态值</li>
     * </ol>
     *
     * @param status 前端状态字符串（如 "PENDING_AUDIT"、"pending_ship"）
     * @return 对应的 SampleStatus 枚举值
     * @throws BusinessException 参数异常，当状态值无法识别时
     */
    private SampleStatus parseStatus(String status) {
        try {
            return SampleStatus.fromApiStatus(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw BusinessException.param("Invalid status: " + status);
        }
    }

    /**
     * 标准化操作名称，将前端传入的别名映射为内部统一操作码。
     * <p>
     * 映射关系：
     * <ul>
     *     <li>{@code APPROVED} → {@code PENDING_SHIP}（审批通过）</li>
     *     <li>{@code SHIPPED} → {@code SHIPPING}（已发货）</li>
     *     <li>{@code SIGNED} → {@code PENDING_HOMEWORK}（已签收）</li>
     *     <li>{@code PENDING_TASK} → {@code PENDING_HOMEWORK}（待交作业，兼容旧版）</li>
     *     <li>{@code FINISHED} → {@code COMPLETED}（已完成，兼容旧版）</li>
     *     <li>其他 → 原样返回（已是标准码）</li>
     * </ul>
     * 输入会先 trim 并转大写，大小写混写可兼容。
     *
     * @param action 前端操作名（如 "approved"、"SHIPPED"、"APPROVED"）
     * @return 标准化后的内部操作码
     */

    /**
     * 校验指定状态是否对运营角色可见。
     * <p>
     * 运营角色（OPS_STAFF）只能查看待发货及后续物流状态的寄样单；
     * 审核中（PENDING_AUDIT）和已拒绝（REJECTED）状态对运营不可见。
     * 校验失败时抛出 {@link ForbiddenException}。
     *
     * @param status 状态字符串（前端传入）
     * @throws ForbiddenException 状态对运营角色不可见时抛出
     */
    private void ensureOpsVisibleStatus(String status) {
        SampleStatus sampleStatus = parseStatus(status);
        if (!isOpsVisibleStatusCode(sampleStatus.getCode())) {
            throw new ForbiddenException("运营仅可查看待发货及后续物流寄样单");
        }
    }

    /**
     * 判断指定状态码是否属于运营角色可见范围。
     * <p>
     * 运营可见状态包括：PENDING_SHIP（待发货）、SHIPPING（运输中）、
     * DELIVERED（已签收）、PENDING_HOMEWORK（待交作业）、COMPLETED（已完成）、CLOSED（已关闭）。
     * PENDING_AUDIT（待审核）和 REJECTED（已拒绝）不在可见范围内。
     *
     * @param statusCode 状态码（可为 null）
     * @return true 表示运营可见，false 表示不可见或 statusCode 为 null
     */
    private boolean isOpsVisibleStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return false;
        }
        return statusCode.equals(SampleStatus.PENDING_SHIP.getCode())
                || statusCode.equals(SampleStatus.SHIPPING.getCode())
                || statusCode.equals(SampleStatus.DELIVERED.getCode())
                || statusCode.equals(SampleStatus.PENDING_HOMEWORK.getCode())
                || statusCode.equals(SampleStatus.COMPLETED.getCode())
                || statusCode.equals(SampleStatus.CLOSED.getCode());
    }

    /**
     * 判断用户是否为纯运营角色（仅有 OPS_STAFF，不含 ADMIN）。
     * <p>
     * ADMIN 拥有全局权限不受运营限制，因此此方法用于区分"纯运营"和"运营+管理员"的场景。
     * 纯运营角色在查询寄样单时需要应用状态可见性过滤。
     *
     * @param roleCodes 角色编码集合（通过 {@link #hasAnyRole} 解析）
     * @return true 表示纯运营角色，false 表示有 ADMIN 权限或无运营角色
     */
    private boolean isOpsStaffOnly(Object roleCodes) {
        return hasAnyRole(roleCodes, RoleCodes.OPS_STAFF) && !hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    /**
     * 将内部 SampleStatus 枚举转换为前端兼容的旧版状态字符串。
     * <p>
     * 映射关系（部分状态在前端合并展示）：
     * <ul>
     *     <li>SHIPPING / DELIVERED → "SHIPPED"（前端统称"已发货"）</li>
     *     <li>PENDING_HOMEWORK → "PENDING_TASK"（前端旧名"待交作业"）</li>
     *     <li>COMPLETED → "FINISHED"（前端旧名"已完成"）</li>
     *     <li>其他 → 直接使用 {@code status.getApiStatus()}</li>
     * </ul>
     *
     * @param status 内部 SampleStatus 枚举值
     * @return 前端兼容的状态字符串
     */
    private String toLegacyStatus(SampleStatus status) {
        return switch (status) {
            case SHIPPING, DELIVERED -> "SHIPPED";
            case PENDING_HOMEWORK -> "PENDING_TASK";
            case COMPLETED -> "FINISHED";
            default -> status.getApiStatus();
        };
    }

    /**
     * 解析寄样单进入指定状态的时间点，用于看板卡片展示状态停留时长。
     * <p>
     * 各状态对应的时间字段：
     * <ul>
     *     <li>PENDING_AUDIT → 创建时间（createTime）</li>
     *     <li>PENDING_SHIP → 审核时间（auditTime）</li>
     *     <li>SHIPPING → 发货时间（shipTime）</li>
     *     <li>DELIVERED / PENDING_HOMEWORK → 签收时间（deliverTime）</li>
     *     <li>COMPLETED → 完成时间（completeTime）</li>
     *     <li>REJECTED → 审核时间（auditTime）</li>
     *     <li>CLOSED → 关闭时间（closeTime）</li>
     * </ul>
     *
     * @param sample 寄样单实体
     * @param status 当前内部状态
     * @return 进入该状态的时间点
     */
    private LocalDateTime resolveStateEnterTime(SampleRequest sample, SampleStatus status) {
        return switch (status) {
            case PENDING_AUDIT -> sample.getCreateTime();
            case PENDING_SHIP -> sample.getAuditTime();
            case SHIPPING -> sample.getShipTime();
            case DELIVERED -> sample.getDeliverTime();
            case PENDING_HOMEWORK -> sample.getDeliverTime();
            case COMPLETED -> sample.getCompleteTime();
            case REJECTED -> sample.getAuditTime();
            case CLOSED -> sample.getCloseTime();
        };
    }

    /**
     * 将寄样单实体转换为看板卡片视图对象（SampleBoardCard）。
     * <p>
     * 组装字段包括：
     * <ol>
     *     <li>基础信息：id、requestNo、talentName、quantity</li>
     *     <li>商品信息：productId、productName</li>
     *     <li>渠道人员：channelUserName（通过 {@link #resolveUserDisplayName} 解析）</li>
     *     <li>物流信息：trackingNo</li>
     *     <li>状态信息：status（旧版兼容格式）、createTime、stateEnterTime（状态进入时间）</li>
     *     <li>备注信息：rejectReason、remark</li>
     * </ol>
     *
     * @param sample         寄样单实体
     * @param product        商品实体（可为 null）
     * @param internalStatus 内部状态枚举
     * @return 看板卡片视图对象
     */
    private SampleBoardCard toBoardCard(SampleRequest sample, Product product, SampleStatus internalStatus) {
        SampleBoardCard card = new SampleBoardCard();
        card.setId(sample.getId());
        card.setRequestNo(sample.getRequestNo());
        card.setTalentName(sample.getTalentNickname());
        card.setProductId(sample.getProductId());
        card.setProductName(product == null ? null : product.getName());
        card.setQuantity(sample.getExpectedSampleNum() == null ? 1 : sample.getExpectedSampleNum());
        card.setChannelUserName(resolveUserDisplayName(sample.getChannelUserId()));
        card.setTrackingNo(sample.getTrackingNo());
        card.setRejectReason(sample.getRejectReason());
        card.setRemark(sample.getRemark());
        card.setStatus(toLegacyStatus(internalStatus));
        card.setCreateTime(sample.getCreateTime());
        card.setStateEnterTime(resolveStateEnterTime(sample, internalStatus));
        return card;
    }

    /**
     * 持久化寄样单变更到数据库（乐观锁更新）。
     * <p>
     * 使用 {@link OptimisticLockSupport#requireUpdated} 校验更新影响行数为 1，
     * 若乐观锁冲突（版本号不匹配或记录不存在）则抛出异常，
     * 防止并发操作导致数据覆盖。
     *
     * @param sample 寄样单实体（必须已设置 id 和 version）
     * @throws OptimisticLockException 乐观锁冲突或记录不存在时抛出
     */
    private void persistSample(SampleRequest sample) {
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
    }

    /**
     * 寄样创建成功后，将本次填写的收货地址回写到认领记录。
     * <p>仅当地址非空时才回写；认领关系不存在时静默跳过。</p>
     *
     * @param channelUserId 渠道归属用户 ID
     * @param talentId      达人 ID
     * @param sample        本次创建的寄样申请
     */
    private void writeBackClaimAddress(UUID channelUserId, UUID talentId, SampleRequest sample) {
        if (channelUserId == null || talentId == null) {
            return;
        }
        String name = sample.getRecipientName();
        String phone = sample.getRecipientPhone();
        String address = sample.getRecipientAddress();
        if (!StringUtils.hasText(name) && !StringUtils.hasText(phone) && !StringUtils.hasText(address)) {
            return;
        }
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(talentId, channelUserId);
        if (claim != null) {
            claim.setRecipientName(name);
            claim.setRecipientPhone(phone);
            claim.setRecipientAddress(address);
            talentClaimMapper.updateById(claim);
            log.debug("T-ADDR: writeback claim address for talent={}, channel={}", talentId, channelUserId);
        }
    }
}
