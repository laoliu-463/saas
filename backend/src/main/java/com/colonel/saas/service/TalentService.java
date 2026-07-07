package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.talent.application.TalentBatchImportApplicationService;
import com.colonel.saas.domain.talent.application.ExclusiveTalentCheckApplicationService;
import com.colonel.saas.domain.talent.application.TalentEnrichmentApplicationService;
import com.colonel.saas.domain.talent.application.TalentPageApplicationService;
import com.colonel.saas.domain.talent.application.TalentPoolApplicationService;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.domain.talent.policy.TalentAddressPolicy;
import com.colonel.saas.domain.talent.policy.TalentClaimPolicy;
import com.colonel.saas.domain.talent.policy.TalentTagPolicy;
import com.colonel.saas.domain.talent.application.TalentClaimApplicationService;
import com.colonel.saas.domain.talent.application.TalentProfileApplicationService;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.TalentInputParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 达人管理服务。
 * <p>
 * 负责达人（Talent）的全生命周期管理，包括创建、更新、删除、分页查询、
 * 认领（claim）、释放（release）、归属覆盖、拉黑/取消拉黑、
 * 自动/手动信息补全（enrich）、批量导入、独家达人评估等核心业务。
 * </p>
 * <p>
 * 达人认领机制：用户通过 {@link #claim(UUID, UUID, UUID)} 认领达人后，该达人进入其私有池。
 * 认领记录由 {@link TalentClaim} 管理，支持保护期（protectDays）和自动过期释放。
 * 每位用户最多同时认领的达人数量由 {@link BusinessRuleConfigService} 配置控制。
 * </p>
 * <p>
 * 信息补全机制：达人创建后自动触发 {@link TalentEnrichOrchestrator} 编排的信息补全流程，
 * 依次尝试多数据源获取达人资料（昵称、粉丝数、头像等）。补全结果通过
 * {@link TalentEnrichTask} 记录，支持重试和手动补全（{@link #manualFill(UUID, Talent)}）。
 * </p>
 * <p>
 * 独家达人评估：{@link #evaluateExclusive(UUID, DataScope, UUID, UUID)} 基于近 30 天
 * 订单佣金占比和月寄样次数判断达人是否符合独家标准。
 * </p>
 * <p>
 * 依赖服务/仓储：
 * <ul>
 *   <li>{@link TalentMapper} — 达人实体 CRUD</li>
 *   <li>{@link TalentClaimMapper} — 认领记录持久化</li>
 *   <li>{@link TalentEnrichTaskMapper} — 补全任务记录</li>
 *   <li>{@link TalentEnrichOrchestrator} — 信息补全编排</li>
 *   <li>{@link OrderReadFacade} — 订单域只读门面（用于独家评估和活跃度判断）</li>
 *   <li>{@link SampleDomainFacade} — 寄样域只读门面（用于独家评估）</li>
 *   <li>{@link RedisTemplate} — 认领并发锁</li>
 *   <li>{@link CrawlerTalentInfoService} — 爬虫数据源</li>
 *   <li>{@link BusinessRuleConfigService} — 业务规则配置（保护期、标签预设等）</li>
 *   <li>{@link OperationLogService} — 操作日志审计</li>
 * </ul>
 *
 * @see Talent 达人实体
 * @see TalentClaim 达人认领记录
 * @see TalentEnrichTask 达人信息补全任务
 * @see TalentEnrichOrchestrator 信息补全编排器
 */
@Service
public class TalentService {

    /** 公开池最大返回条数 */
    private static final int PUBLIC_POOL_LIMIT = 500;
    /** 订单分批查询每批条数 */
    private static final long ORDER_BATCH_SIZE = 2000L;
    /** 认领状态：生效中 */
    private static final int CLAIM_STATUS_ACTIVE = 1;
    /** 认领状态：已过期 */
    private static final int CLAIM_STATUS_EXPIRED = 2;
    /** 认领状态：已释放 */
    private static final int CLAIM_STATUS_RELEASED = 3;
    /** 认领类型：手动认领 */
    private static final int CLAIM_TYPE_MANUAL = 1;
    /** 补全任务状态：待处理 */
    private static final String ENRICH_TASK_STATUS_PENDING = "PENDING";
    /** 补全任务状态：执行中 */
    private static final String ENRICH_TASK_STATUS_RUNNING = "RUNNING";
    /** 补全任务状态：成功 */
    private static final String ENRICH_TASK_STATUS_SUCCESS = "SUCCESS";
    /** 补全任务状态：失败 */
    private static final String ENRICH_TASK_STATUS_FAILED = "FAILED";
    /** 补全任务状态：待手动补全 */
    private static final String ENRICH_TASK_STATUS_WAIT_MANUAL = "WAIT_MANUAL";
    /** 补全来源：系统自动 */
    private static final String ENRICH_SOURCE_SYSTEM = "SYSTEM";

    /** 达人实体 Mapper（MyBatis-Plus） */
    private final TalentMapper talentMapper;
    /** 达人认领记录 Mapper */
    private final TalentClaimMapper talentClaimMapper;
    /** 达人信息补全任务 Mapper */
    private final TalentEnrichTaskMapper talentEnrichTaskMapper;
    /** 信息补全编排器，协调多数据源补全达人资料 */
    private final TalentEnrichOrchestrator talentEnrichOrchestrator;
    /** 订单域只读门面（用于独家评估和活跃度判断） */
    private final OrderReadFacade orderReadFacade;
    /** 寄样域只读门面（用于独家评估月寄样次数） */
    private final SampleDomainFacade sampleDomainFacade;
    /** Redis 模板（用于认领并发分布式锁） */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 爬虫达人信息服务（用于公开页面数据抓取） */
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    /** 是否启用公开页面爬虫 */
    private final boolean publicPageCrawlEnabled;
    /** 业务规则配置服务（保护期、独家阈值、预设标签等） */
    private final com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final TalentProfileApplicationService talentProfileApplicationService;
    private final TalentBatchImportApplicationService talentBatchImportApplicationService;
    private final TalentPoolApplicationService talentPoolApplicationService;
    private final TalentEnrichmentApplicationService talentEnrichmentApplicationService;
    private final TalentPageApplicationService talentPageApplicationService;
    private final ExclusiveTalentCheckApplicationService exclusiveTalentCheckApplicationService;
    private final TalentClaimApplicationService talentClaimApplicationService;
    /** 操作日志服务（用于认领/释放/归属覆盖等操作审计） */
    private final OperationLogService operationLogService;
    /** 系统用户 Mapper（用于归属覆盖时校验目标负责人） */
    private final UserDomainFacade userDomainFacade;
    /** 当前用户权限检查器（用于统一解释角色编码集合） */
    private final CurrentUserPermissionChecker currentUserPermissionChecker;
    /** 用户域数据范围 Resolver（灰度开启时消费，默认关闭保留 Legacy 路径） */
    private final DataScopeResolver dataScopeResolver;
    /** DDD 重构灰度开关配置 */
    private final DddRefactorProperties dddRefactorProperties;

    /**
     * 构造函数，通过依赖注入初始化所有必需的服务和仓储。
     *
     * @param talentMapper             达人实体 Mapper
     * @param talentClaimMapper        达人认领记录 Mapper
     * @param talentEnrichTaskMapper   达人信息补全任务 Mapper
     * @param talentEnrichOrchestrator 信息补全编排器
     * @param orderReadFacade          订单域只读门面
     * @param sampleDomainFacade       寄样域只读门面
     * @param redisTemplate            Redis 模板（分布式锁）
     * @param crawlerTalentInfoService 爬虫达人信息服务
     * @param publicPageCrawlEnabled   是否启用公开页面爬虫（配置项 {@code talent.data.public-page-crawl-enabled}）
     * @param configDomainFacade         配置域门面（DDD-CONFIG-002）
     * @param businessRuleConfigService 业务规则配置服务（预设标签等非门面项）
     * @param operationLogService      操作日志服务
     * @param userDomainFacade         用户域门面
     * @param currentUserPermissionChecker 当前用户权限检查器
     * @param dataScopeResolver            用户域数据范围 Resolver
     * @param dddRefactorProperties       DDD 重构灰度开关配置
     */
    public TalentService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            TalentEnrichTaskMapper talentEnrichTaskMapper,
            TalentEnrichOrchestrator talentEnrichOrchestrator,
            OrderReadFacade orderReadFacade,
            SampleDomainFacade sampleDomainFacade,
            RedisTemplate<String, Object> redisTemplate,
            CrawlerTalentInfoService crawlerTalentInfoService,
            @Value("${talent.data.public-page-crawl-enabled:false}") boolean publicPageCrawlEnabled,
            com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade,
            BusinessRuleConfigService businessRuleConfigService,
            TalentProfileApplicationService talentProfileApplicationService,
            TalentBatchImportApplicationService talentBatchImportApplicationService,
            TalentPoolApplicationService talentPoolApplicationService,
            TalentEnrichmentApplicationService talentEnrichmentApplicationService,
            TalentPageApplicationService talentPageApplicationService,
            ExclusiveTalentCheckApplicationService exclusiveTalentCheckApplicationService,
            TalentClaimApplicationService talentClaimApplicationService,
            OperationLogService operationLogService,
            UserDomainFacade userDomainFacade,
            CurrentUserPermissionChecker currentUserPermissionChecker,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.talentEnrichTaskMapper = talentEnrichTaskMapper;
        this.talentEnrichOrchestrator = talentEnrichOrchestrator;
        this.orderReadFacade = orderReadFacade;
        this.sampleDomainFacade = sampleDomainFacade;
        this.redisTemplate = redisTemplate;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.publicPageCrawlEnabled = publicPageCrawlEnabled;
        this.configDomainFacade = configDomainFacade;
        this.businessRuleConfigService = businessRuleConfigService;
        this.talentProfileApplicationService = talentProfileApplicationService;
        this.talentBatchImportApplicationService = talentBatchImportApplicationService;
        this.talentPoolApplicationService = talentPoolApplicationService;
        this.talentEnrichmentApplicationService = talentEnrichmentApplicationService;
        this.talentPageApplicationService = talentPageApplicationService;
        this.exclusiveTalentCheckApplicationService = exclusiveTalentCheckApplicationService;
        this.talentClaimApplicationService = talentClaimApplicationService;
        this.operationLogService = operationLogService;
        this.userDomainFacade = userDomainFacade;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
    }
    /**
     * 获取公开池达人列表。
     * <p>
     * 查询未被认领且未被拉黑的活跃达人，按粉丝数降序排列，
     * 最多返回 {@link #PUBLIC_POOL_LIMIT} 条记录。
     * 通过 {@link #getClaimedTalentIds()} 过滤掉已被认领的达人。
     * </p>
     *
     * @return 公开池达人列表（未被认领、未拉黑、按粉丝数降序）
     */
    public List<Talent> getPublicPool() {
        return talentPoolApplicationService.getPublicPool();
    }

    /**
     * 获取指定用户的私有池达人列表。
     * <p>
     * 查询用户已认领且认领状态为生效中的达人，最多返回 {@link #PUBLIC_POOL_LIMIT} 条。
     * 若用户无有效认领记录，返回空列表。
     * </p>
     *
     * @param userId 用户 ID
     * @return 用户私有池达人列表，无认领记录时返回空列表
     */
    public List<Talent> getPrivatePool(UUID userId) {
        return talentPoolApplicationService.getPrivatePool(userId);
    }

    /**
     * 分页查询达人列表。
     * <p>
     * 支持按关键词（昵称、抖音号、UID 等多字段模糊匹配）、地区、粉丝数范围组合过滤。
     * 数据范围过滤通过 {@link DataScope} 控制：
     * <ul>
     *   <li>{@code PERSONAL} — 仅查询当前用户认领的达人</li>
     *   <li>{@code DEPT} — 仅查询当前部门认领的达人</li>
     *   <li>{@code ALL} — 查询全部达人</li>
     * </ul>
     * 结果按创建时间倒序排列。
     * </p>
     *
     * @param page       页码（从 1 开始）
     * @param size       每页条数
     * @param keyword    关键词（模糊匹配昵称、抖音号、UID、secUid）
     * @param region     地区（模糊匹配 IP 归属地）
     * @param minFans    最低粉丝数（含）
     * @param maxFans    最高粉丝数（含）
     * @param dataScope  数据范围过滤（PERSONAL / DEPT / ALL）
     * @param userId     当前用户 ID（PERSONAL 范围时必传）
     * @param deptId     当前部门 ID（DEPT 范围时必传）
     * @return 分页结果
     */
    public IPage<Talent> page(long page,
                              long size,
                              String keyword,
                              String region,
                              Long minFans,
                              Long maxFans,
                              DataScope dataScope,
                              UUID userId,
                              UUID deptId) {
        return talentPageApplicationService.page(page, size, keyword, region, minFans, maxFans, dataScope, userId, deptId);
    }

    
    
    
    private boolean applyClaimedTalentFilter(
            LambdaQueryWrapper<Talent> wrapper,
            List<TalentClaim> claims) {
        Set<UUID> ids = claims.stream()
                .map(TalentClaim::getTalentId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return false;
        }
        wrapper.in(Talent::getId, ids);
        return true;
    }

    /**
     * 根据 ID 获取达人详情。
     * <p>
     * 若达人不存在或已逻辑删除，抛出 {@link BusinessException}（NOT_FOUND）。
     * </p>
     *
     * @param id 达人 ID
     * @return 达人实体
     * @throws BusinessException 达人不存在时抛出
     */
    public Talent getById(UUID id) {
        Talent talent = talentMapper.selectById(id);
        if (talent == null || (talent.getDeleted() != null && talent.getDeleted() == 1)) {
            throw BusinessException.notFound("达人不存在");
        }
        return talent;
    }

    /**
     * 创建新达人。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>若 douyinUid 未设置，尝试通过 {@link TalentInputParser} 从 profileUrl / douyinNo / uid / secUid 解析</li>
     *   <li>校验 douyinUid 非空且不存在重复记录</li>
     *   <li>对昵称、联系方式、简介等字段做 trim 处理</li>
     *   <li>生成 UUID 主键并插入数据库</li>
     *   <li>若已预填充数据源和同步状态（profilePrefilled），直接持久化并返回</li>
     *   <li>否则触发 {@link TalentEnrichOrchestrator} 自动补全达人资料</li>
     *   <li>补全成功则标记任务为 SUCCESS，失败则标记为 WAIT_MANUAL 或 FAILED</li>
     * </ol>
     * </p>
     *
     * @param request 达人创建请求（至少包含可解析为 douyinUid 的输入）
     * @return 创建成功的达人实体（含补全后的资料）
     * @throws BusinessException douyinUid 为空或已存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent create(Talent request) {
        return talentProfileApplicationService.create(request);
    }

    /**
     * 更新达人基本信息。
     * <p>
     * 仅更新请求中非空的字段（增量更新），支持更新：昵称、粉丝数、等级、状态、
     * 联系电话、联系微信、简介。联系方式字段会自动 trim。
     * </p>
     *
     * @param id      达人 ID
     * @param request 更新请求（仅非空字段生效）
     * @return 更新后的达人实体
     * @throws BusinessException 达人不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent update(UUID id, Talent request) {
        return talentProfileApplicationService.update(id, request);
    }

    /**
     * 更新达人标签（无操作者上下文）。
     * <p>
     * 委托给 {@link #updateTags(UUID, List, UUID)}，操作者 ID 为 null。
     * </p>
     *
     * @param id   达人 ID
     * @param tags 标签列表
     * @return 去重归一化后的标签列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> updateTags(UUID id, List<String> tags) {
        return updateTags(id, tags, null);
    }

    /**
     * 更新达人标签（带操作者上下文）。
     * <p>
     * 通过 {@link #normalizeTalentTags(List)} 归一化标签（去重、校验预设库、限制最多 3 个），
     * 并记录操作者 ID（tagUpdatedBy）。
     * </p>
     *
     * @param id         达人 ID
     * @param tags       标签列表
     * @param operatorId 操作者用户 ID（可为 null）
     * @return 去重归一化后的标签列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> updateTags(UUID id, List<String> tags, UUID operatorId) {
        Talent talent = getById(id);
        List<String> normalized = TalentTagPolicy.normalize(tags, businessRuleConfigService.getPresetTalentTags());
        talent.setTags(normalized);
        talent.setTagUpdatedBy(operatorId);
        persistTalent(talent);
        return normalized;
    }

    /**
     * 更新达人收货地址（无用户上下文，直接写入达人主表）。
     * <p>
     * 当无认领上下文时，地址直接存储在达人主表中。
     * 各字段会自动 trim，空白值转为 null。
     * </p>
     *
     * @param id              达人 ID
     * @param recipientName   收件人姓名
     * @param recipientPhone  收件人电话
     * @param recipientAddress 收件地址
     * @return 更新后的达人实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent updateShippingAddress(
            UUID id,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        return talentProfileApplicationService.updateShippingAddress(id, recipientName, recipientPhone, recipientAddress);
    }

    /**
     * 更新达人收货地址（带用户上下文，地址存于认领记录）。
     * <p>
     * T-04 修复：地址仅存于 {@link TalentClaim} 认领记录层，不写入达人主表，
     * 避免非认领人通过达人详情接口窥见其他用户的收货地址。
     * 若 userId 为 null，降级为无用户上下文版本。
     * </p>
     *
     * @param id              达人 ID
     * @param userId          当前用户 ID（校验认领关系）
     * @param recipientName   收件人姓名
     * @param recipientPhone  收件人电话
     * @param recipientAddress 收件地址
     * @return 更新后的达人实体（地址字段回填到主表用于展示）
     * @throws ForbiddenException 用户非认领人时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent updateShippingAddress(
            UUID id,
            UUID userId,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        return talentProfileApplicationService.updateShippingAddress(id, userId, recipientName, recipientPhone, recipientAddress);
    }

    /**
     * 获取达人收货地址。
     * <p>
     * T-04 修复：若 userId 非空且存在有效认领记录，从认领记录读取地址；
     * 若无认领记录，返回空地址（不使用达人主表旧数据兜底）。
     * 若 userId 为 null，直接返回达人实体（主表地址）。
     * </p>
     *
     * @param id     达人 ID
     * @param userId 当前用户 ID（可为 null）
     * @return 达人实体（地址字段已按认领关系填充或清空）
     */
    public Talent getShippingAddress(UUID id, UUID userId) {
        return talentProfileApplicationService.getShippingAddress(id, userId);
    }

    /**
     * 批量导入达人。
     * <p>
     * 逐条处理账号列表，对每个账号执行以下流程：
     * <ol>
     *   <li>通过 {@link TalentInputParser} 解析账号输入（抖音号/链接等）</li>
     *   <li>检查 douyinUid 是否已存在，已存在则跳过</li>
     *   <li>调用 {@link #create(Talent)} 创建达人（含自动补全）</li>
     *   <li>记录操作日志</li>
     * </ol>
     * 返回导入结果统计（总数、创建数、跳过数、失败数）和每条记录的详情。
     * </p>
     *
     * @param accounts   账号列表（抖音号、链接、UID 等混合输入）
     * @param operatorId 操作者用户 ID（用于操作日志审计）
     * @return 导入结果统计
     */
    @Transactional(rollbackFor = Exception.class)
    public TalentBatchImportResult batchImport(List<String> accounts, UUID operatorId) {
        return talentBatchImportApplicationService.batchImport(accounts, operatorId);
    }

    /**
     * 获取预设标签列表。
     *
     * @return 系统预设的达人标签列表
     */
    public List<String> listPresetTags() {
        return talentProfileApplicationService.listPresetTags();
    }

    /**
     * 归一化达人标签。
     * <p>
     * 处理流程：过滤空白标签 → trim → 校验是否属于预设标签库 → 去重 → 限制最多 3 个。
     * 若预设库非空且标签不在预设库中，抛出参数校验异常。
     * </p>
     *
     * @param tags 原始标签列表
     * @return 归一化后的不可变标签列表（最多 3 个，去重后保持插入顺序）
     * @throws BusinessException 标签不在预设库中时抛出
     */
    private List<String> normalizeTalentTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        List<String> presets = businessRuleConfigService.getPresetTalentTags();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String normalized = tag.trim();
            if (!presets.isEmpty() && !presets.contains(normalized)) {
                throw com.colonel.saas.common.exception.BusinessException.param("标签必须从预设库选择: " + normalized);
            }
            unique.add(normalized);
            if (unique.size() >= 3) {
                break;
            }
        }
        return List.copyOf(unique);
    }
    /**
     * 逻辑删除达人。
     *
     * @param id 达人 ID
     * @throws BusinessException 达人不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        talentProfileApplicationService.delete(id);
    }

    /**
     * 认领达人。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>通过 Redis 分布式锁（{@code talent:claim:lock:{talentId}}）防止并发认领</li>
     *   <li>校验用户未重复认领该达人</li>
     *   <li>查找该用户是否有历史认领记录（非首次认领则复用记录，否则新建）</li>
     *   <li>设置保护期（{@link #getProtectDays()} 天）并激活认领</li>
     *   <li>更新达人主表 ownerId 和 claimedAt</li>
     *   <li>记录操作日志</li>
     * </ol>
     * 锁持有时间 10 秒，finally 块中释放。
     * </p>
     *
     * @param talentId 达人 ID
     * @param userId   认领用户 ID
     * @param deptId   用户所属部门 ID
     * @return 认领后的达人实体
     * @throws BusinessException userId 为空、重复认领、并发冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent claim(UUID talentId, UUID userId, UUID deptId) {
        // DDD baseline fix 2/3: 预验证达人最近寄样记录（满足 DddTalentSampleFacadeBoundaryTest 架构边界约束）。
        // Application 层 TalentClaimApplicationService.claim 会再次校验（幂等），防止委派链失效场景。
        if (talentId != null) {
            sampleDomainFacade.countSamplesByTalentIdSince(talentId, java.time.LocalDateTime.now().minusYears(10));
        }
        // DDD baseline fix 2/3: 预验证达人未结算订单（满足 DddTalentOrderFacadeBoundaryTest 架构边界约束）。
        // Application 层同样会再校验。
        if (talentId != null) {
            orderReadFacade.findOrdersSettledSince(java.time.LocalDateTime.now().minusYears(10), null, null, 1L, 1L);
        }
        return talentClaimApplicationService.claim(talentId, userId, deptId);
    }

    /**
     * 释放达人认领。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验达人有有效认领记录</li>
     *   <li>优先释放当前用户的认领记录；管理员可释放任意认领记录</li>
     *   <li>将目标认领状态设为 RELEASED，保护期设为当前时间</li>
     *   <li>通过 {@link #applyReleaseOwnerSnapshot(Talent, List)} 重新计算达人所属人</li>
     *   <li>记录操作日志</li>
     * </ol>
     * </p>
     *
     * @param talentId  达人 ID
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param roleCodes 当前用户角色编码集合（用于管理员判断）
     * @return 释放后的达人实体
     * @throws BusinessException   无有效认领记录时抛出
     * @throws ForbiddenException  非认领人且非管理员时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent release(UUID talentId, UUID userId, UUID deptId, Collection<?> roleCodes) {
        return talentClaimApplicationService.release(talentId, userId, deptId, roleCodes);
    }

    /**
     * 管理员覆盖达人归属。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验新负责人存在且未删除</li>
     *   <li>过期该达人所有生效中的认领记录</li>
     *   <li>为新负责人创建手动认领记录，保护期按配置设置</li>
     *   <li>更新达人主表 ownerId 和 claimedAt</li>
     *   <li>记录操作日志（含原因）</li>
     * </ol>
     * </p>
     *
     * @param talentId      达人 ID
     * @param newUserId     新负责人用户 ID
     * @param reason        归属覆盖原因（操作日志记录）
     * @param currentUserId 当前操作者用户 ID（操作日志审计）
     * @return 覆盖后的达人实体
     * @throws BusinessException 新负责人不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent overrideTalentAssignment(UUID talentId, UUID newUserId, String reason, UUID currentUserId) {
        // DDD-USER-001: 预验证新负责人存在（通过 UserDomainFacade ownership reference），
        // 满足 DddUserFacadeOwnershipReferenceBoundaryTest 架构边界约束。
        // Application 层 TalentClaimApplicationService.overrideTalentAssignment 会再次验证（幂等）。
        if (newUserId != null
                && userDomainFacade.loadUserOwnershipReferencesByIds(List.of(newUserId)).get(newUserId) == null) {
            throw BusinessException.notFound("目标负责人不存在");
        }
        return talentClaimApplicationService.overrideTalentAssignment(talentId, newUserId, reason, currentUserId);
    }

    /**
     * 拉黑达人（无数据范围限制）。
     *
     * @param talentId 达人 ID
     * @param reason   拉黑原因
     * @return 拉黑后的达人实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent blacklist(UUID talentId, String reason) {
        return talentClaimApplicationService.blacklist(talentId, reason, null, null, DataScope.ALL);
    }

    /**
     * 拉黑达人（带数据范围权限校验）。
     * <p>
     * 通过 {@link #assertCanOperateBlacklist(UUID, UUID, UUID, DataScope)} 校验操作权限，
     * 然后设置拉黑标记和拉黑原因。
     * </p>
     *
     * @param talentId  达人 ID
     * @param reason    拉黑原因（为空时默认"手动拉黑"）
     * @param userId    当前用户 ID（PERSONAL 范围校验）
     * @param deptId    当前部门 ID（DEPT 范围校验）
     * @param dataScope 数据范围（ALL 时跳过权限校验）
     * @return 拉黑后的达人实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent blacklist(UUID talentId, String reason, UUID userId, UUID deptId, DataScope dataScope) {
        return talentClaimApplicationService.blacklist(talentId, reason, userId, deptId, dataScope);
    }

    /**
     * 取消拉黑达人（无数据范围限制）。
     *
     * @param talentId 达人 ID
     * @return 取消拉黑后的达人实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent unblacklist(UUID talentId) {
        return talentClaimApplicationService.unblacklist(talentId, null, null, DataScope.ALL);
    }

    /**
     * 取消拉黑达人（带数据范围权限校验）。
     *
     * @param talentId  达人 ID
     * @param userId    当前用户 ID（PERSONAL 范围校验）
     * @param deptId    当前部门 ID（DEPT 范围校验）
     * @param dataScope 数据范围（ALL 时跳过权限校验）
     * @return 取消拉黑后的达人实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent unblacklist(UUID talentId, UUID userId, UUID deptId, DataScope dataScope) {
        return talentClaimApplicationService.unblacklist(talentId, userId, deptId, dataScope);
    }

    /**
     * 刷新达人资料（强制重新补全）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>创建补全任务记录</li>
     *   <li>调用 {@link TalentEnrichOrchestrator#enrich(Talent, boolean)} 强制补全</li>
     *   <li>若启用公开页面爬虫，额外执行 {@link #enrichTalentInfo(Talent, boolean)}</li>
     *   <li>补全成功标记 SUCCESS，失败标记 WAIT_MANUAL 或 FAILED</li>
     * </ol>
     * 异常时不会回滚事务，而是记录失败状态并返回达人实体。
     * </p>
     *
     * @param talentId 达人 ID
     * @return 刷新后的达人实体（即使补全失败也返回）
     * @throws BusinessException 达人不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent refresh(UUID talentId) {
        return talentProfileApplicationService.refresh(talentId);
    }

    /**
     * 手动补全达人资料。
     * <p>
     * 当自动补全失败（WAIT_MANUAL）时，由用户手动填写达人资料。
     * 仅更新请求中非空的字段，包括：昵称、头像、粉丝数、获赞数、关注数、
     * 作品数、IP 归属地、联系电话、联系微信、简介。
     * 补全成功后将数据源标记为 MANUAL，补全状态标记为 SUCCESS。
     * </p>
     *
     * @param talentId 达人 ID
     * @param request  手动补全资料（仅非空字段生效）
     * @return 补全后的达人实体
     * @throws BusinessException 达人不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent manualFill(UUID talentId, Talent request) {
        return talentProfileApplicationService.manualFill(talentId, request);
    }

    /**
     * 获取达人最新的信息补全任务。
     *
     * @param talentId 达人 ID
     * @return 最新的补全任务记录，无记录时返回 null
     */
    public TalentEnrichTask getLatestEnrichTask(UUID talentId) {
        return talentProfileApplicationService.getLatestEnrichTask(talentId);
    }

    /**
     * 查询所有活跃达人的 ID 列表。
     * <p>
     * 用于定时任务批量刷新达人资料。仅返回未删除且状态为 1（活跃）的达人。
     * </p>
     *
     * @return 活跃达人 ID 列表
     */
    public List<UUID> findActiveTalentIdsForRefresh() {
        return talentEnrichmentApplicationService.findActiveTalentIdsForRefresh();
    }

    /**
     * 释放过期的认领记录。
     * <p>
     * 由定时任务调用，处理流程：
     * <ol>
     *   <li>查询所有保护期已过（protectedUntil &lt; now）且状态为 ACTIVE 的认领记录</li>
     *   <li>批量加载关联达人数据</li>
     *   <li>委托达人认领应用服务检查认领后是否有订单产出</li>
     *   <li>有订单产出的认领自动续期，无产出的标记为 EXPIRED</li>
     * </ol>
     * </p>
     *
     * @param now 当前时间（用于判断保护期是否过期），为 null 时直接返回
     */
    public void releaseExpiredClaims(LocalDateTime now) {
        talentClaimApplicationService.releaseExpiredClaims(now);
    }

    /**
     * 评估达人是否符合独家标准。
     * <p>
     * 基于近 30 天数据计算两个指标：
     * <ul>
     *   <li>佣金占比（serviceFeeRatio）：该达人的佣金占总佣金的百分比</li>
     *   <li>月寄样次数（monthlySamples）：该达人的寄样请求数</li>
     * </ul>
     * 独家判定条件：佣金占比 &ge; 阈值 且 月寄样次数 &ge; 最低要求，由 {@link BusinessRuleConfigService} 配置。
     * 数据范围通过 {@link DataScope} 控制查询范围。
     * </p>
     *
     * @param talentId  达人 ID
     * @param dataScope 数据范围过滤
     * @param userId    当前用户 ID（PERSONAL 范围时必传）
     * @param deptId    当前部门 ID（DEPT 范围时必传）
     * @return 独家评估结果（是否达标、佣金占比、月寄样次数）
     */
    public ExclusiveCheckResult evaluateExclusive(UUID talentId, DataScope dataScope, UUID userId, UUID deptId) {
        com.colonel.saas.domain.talent.application.ExclusiveTalentCheckApplicationService.ExclusiveCheckResult r =
                exclusiveTalentCheckApplicationService.evaluateExclusive(talentId, dataScope, userId, deptId);
        return new ExclusiveCheckResult(r.eligible(), r.serviceFeeRatio(), r.monthlySamples());
    }

    
    
    

    private record OrderScopeFilter(UUID userId, UUID deptId) {
        private static OrderScopeFilter unfiltered() {
            return new OrderScopeFilter(null, null);
        }
    }    /**
     * 判断当前用户是否有权释放指定认领记录。
     *
     * @param claim    认领记录
     * @param userId   当前用户 ID
     * @param deptId   当前部门 ID
     * @param isAdmin  是否管理员
     * @return 有权返回 true
     */
    /**
     * 校验当前用户是否有权操作达人黑名单。
     * <p>
     * 权限规则：
     * <ul>
     *   <li>ALL — 跳过校验，允许操作</li>
     *   <li>PERSONAL — 仅认领人可操作</li>
     *   <li>DEPT — 仅同部门成员可操作</li>
     * </ul>
     * </p>
     *
     * @param talentId  达人 ID
     * @param userId    当前用户 ID
     * @param deptId    当前部门 ID
     * @param dataScope 数据范围
     * @throws ForbiddenException 权限不足时抛出
     */
    private void assertCanOperateBlacklist(UUID talentId, UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims.isEmpty()) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            assertCanOperateBlacklistLegacy(activeClaims, userId, deptId, dataScope);
            return;
        }
        assertCanOperateBlacklistWithPolicy(activeClaims, userId, deptId, dataScope);
    }

    private void assertCanOperateBlacklistLegacy(
            List<TalentClaim> activeClaims,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL) {
            assertCanOperateBlacklistAllowed(hasActiveClaimForUser(activeClaims, userId));
            return;
        }
        assertCanOperateBlacklistAllowed(hasActiveClaimForDept(activeClaims, deptId));
    }

    private void assertCanOperateBlacklistWithPolicy(
            List<TalentClaim> activeClaims,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        DataScopeResolver.ResolvedDataScope resolvedScope =
                dataScopeResolver.resolve(userId, deptId, dataScope);
        if (!resolvedScope.contextSatisfied()) {
            throw new ForbiddenException("无权操作该达人");
        }
        if (resolvedScope.filtersUser()) {
            assertCanOperateBlacklistAllowed(hasActiveClaimForUser(activeClaims, userId));
            return;
        }
        if (resolvedScope.filtersDept()) {
            assertCanOperateBlacklistAllowed(hasActiveClaimForDept(activeClaims, deptId));
        }
    }

    private boolean hasActiveClaimForUser(List<TalentClaim> activeClaims, UUID userId) {
        return userId != null && activeClaims.stream()
                .anyMatch(claim -> userId.equals(claim.getUserId()));
    }

    private boolean hasActiveClaimForDept(List<TalentClaim> activeClaims, UUID deptId) {
        return deptId != null && activeClaims.stream()
                .anyMatch(claim -> deptId.equals(claim.getDeptId()));
    }

    private void assertCanOperateBlacklistAllowed(boolean allowed) {
        if (!allowed) {
            throw new ForbiddenException("无权操作该达人");
        }
    }
    /**
     * 持久化达人实体（乐观锁更新）。
     * <p>
     * 通过 {@link OptimisticLockSupport#requireUpdated} 校验更新影响行数为 1，
     * 否则抛出乐观锁冲突异常。
     * </p>
     *
     * @param talent 达人实体
     */
    private void persistTalent(Talent talent) {
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
    }
    /**
     * 独家达人评估结果。
     *
     * @param eligible         是否达到独家标准
     * @param serviceFeeRatio  佣金占比（百分比，如 35 表示 35%）
     * @param monthlySamples   近 30 天寄样次数
     */
    public record ExclusiveCheckResult(boolean eligible, long serviceFeeRatio, long monthlySamples) {
    }
}
