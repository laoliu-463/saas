package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.domain.talent.policy.TalentAddressPolicy;
import com.colonel.saas.domain.talent.policy.TalentClaimPolicy;
import com.colonel.saas.domain.talent.policy.TalentTagPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
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
import java.util.Locale;
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
 *   <li>{@link ColonelsettlementOrderMapper} — 订单查询（用于独家评估和活跃度判断）</li>
 *   <li>{@link SampleRequestMapper} — 寄样查询（用于独家评估）</li>
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
    /** 结算订单 Mapper（用于独家评估和活跃度判断） */
    private final ColonelsettlementOrderMapper orderMapper;
    /** 寄样请求 Mapper（用于独家评估月寄样次数） */
    private final SampleRequestMapper sampleRequestMapper;
    /** Redis 模板（用于认领并发分布式锁） */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 爬虫达人信息服务（用于公开页面数据抓取） */
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    /** 是否启用公开页面爬虫 */
    private final boolean publicPageCrawlEnabled;
    /** 业务规则配置服务（保护期、独家阈值、预设标签等） */
    private final com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    private final BusinessRuleConfigService businessRuleConfigService;
    /** 操作日志服务（用于认领/释放/归属覆盖等操作审计） */
    private final OperationLogService operationLogService;
    /** 系统用户 Mapper（用于归属覆盖时校验目标负责人） */
    private final UserDomainFacade userDomainFacade;

    /**
     * 构造函数，通过依赖注入初始化所有必需的服务和仓储。
     *
     * @param talentMapper             达人实体 Mapper
     * @param talentClaimMapper        达人认领记录 Mapper
     * @param talentEnrichTaskMapper   达人信息补全任务 Mapper
     * @param talentEnrichOrchestrator 信息补全编排器
     * @param orderMapper              结算订单 Mapper
     * @param sampleRequestMapper      寄样请求 Mapper
     * @param redisTemplate            Redis 模板（分布式锁）
     * @param crawlerTalentInfoService 爬虫达人信息服务
     * @param publicPageCrawlEnabled   是否启用公开页面爬虫（配置项 {@code talent.data.public-page-crawl-enabled}）
     * @param configDomainFacade         配置域门面（DDD-CONFIG-002）
     * @param businessRuleConfigService 业务规则配置服务（预设标签等非门面项）
     * @param operationLogService      操作日志服务
     * @param sysUserMapper            系统用户 Mapper
     */
    public TalentService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            TalentEnrichTaskMapper talentEnrichTaskMapper,
            TalentEnrichOrchestrator talentEnrichOrchestrator,
            ColonelsettlementOrderMapper orderMapper,
            SampleRequestMapper sampleRequestMapper,
            RedisTemplate<String, Object> redisTemplate,
            CrawlerTalentInfoService crawlerTalentInfoService,
            @Value("${talent.data.public-page-crawl-enabled:false}") boolean publicPageCrawlEnabled,
            com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade,
            BusinessRuleConfigService businessRuleConfigService,
            OperationLogService operationLogService,
            UserDomainFacade userDomainFacade) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.talentEnrichTaskMapper = talentEnrichTaskMapper;
        this.talentEnrichOrchestrator = talentEnrichOrchestrator;
        this.orderMapper = orderMapper;
        this.sampleRequestMapper = sampleRequestMapper;
        this.redisTemplate = redisTemplate;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.publicPageCrawlEnabled = publicPageCrawlEnabled;
        this.configDomainFacade = configDomainFacade;
        this.businessRuleConfigService = businessRuleConfigService;
        this.operationLogService = operationLogService;
        this.userDomainFacade = userDomainFacade;
    }

    /**
     * 获取达人认领保护期天数。
     *
     * @return 保护期天数，由业务规则配置决定
     */
    private int getProtectDays() {
        return configDomainFacade.getTalentClaimProtectDays();
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
        Set<UUID> claimedTalentIds = getClaimedTalentIds();
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1)
                        .ne(Talent::getBlacklisted, true)
                        .orderByDesc(Talent::getFans)
                        .last("limit " + PUBLIC_POOL_LIMIT))
                .stream()
                .filter(talent -> !claimedTalentIds.contains(talent.getId()))
                .sorted(Comparator.comparing(Talent::getFans, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
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
        List<TalentClaim> claims = talentClaimMapper.findActiveByUserId(userId);
        if (claims.isEmpty()) {
            return List.of();
        }
        Set<UUID> talentIds = claims.stream().map(TalentClaim::getTalentId).collect(Collectors.toSet());
        return talentMapper.selectBatchIds(talentIds).stream()
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
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
        LambdaQueryWrapper<Talent> wrapper = new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDeleted, 0)
                .orderByDesc(Talent::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Talent::getNickname, keyword)
                    .or().like(Talent::getDouyinUid, keyword)
                    .or().like(Talent::getDouyinNo, keyword)
                    .or().like(Talent::getUid, keyword)
                    .or().like(Talent::getSecUid, keyword));
        }
        if (StringUtils.hasText(region)) {
            wrapper.like(Talent::getIpLocation, region);
        }
        if (minFans != null) {
            wrapper.ge(Talent::getFans, minFans);
        }
        if (maxFans != null) {
            wrapper.le(Talent::getFans, maxFans);
        }

        if (dataScope == DataScope.PERSONAL && userId != null) {
            List<TalentClaim> claims = talentClaimMapper.findActiveByUserId(userId);
            Set<UUID> ids = claims.stream().map(TalentClaim::getTalentId).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0L);
            }
            wrapper.in(Talent::getId, ids);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            List<TalentClaim> claims = talentClaimMapper.findActiveByDeptId(deptId);
            Set<UUID> ids = claims.stream().map(TalentClaim::getTalentId).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0L);
            }
            wrapper.in(Talent::getId, ids);
        }
        return talentMapper.selectPage(new Page<>(page, size), wrapper);
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
        if (!StringUtils.hasText(request.getDouyinUid())) {
            String fallbackInput = StringUtils.hasText(request.getProfileUrl())
                    ? request.getProfileUrl()
                    : (StringUtils.hasText(request.getDouyinNo()) ? request.getDouyinNo()
                    : (StringUtils.hasText(request.getUid()) ? request.getUid()
                    : request.getSecUid()));
            if (!StringUtils.hasText(fallbackInput)) {
                throw BusinessException.param("达人抖音号或链接不能为空");
            }
            TalentInputParseResult parsed = TalentInputParser.parse(fallbackInput);
            if (StringUtils.hasText(parsed.getDouyinUid())) {
                request.setDouyinUid(parsed.getDouyinUid());
            }
            if (!StringUtils.hasText(request.getDouyinNo()) && StringUtils.hasText(parsed.getDouyinNo())) {
                request.setDouyinNo(parsed.getDouyinNo());
            }
            if (!StringUtils.hasText(request.getUid()) && StringUtils.hasText(parsed.getUid())) {
                request.setUid(parsed.getUid());
            }
            if (!StringUtils.hasText(request.getSecUid()) && StringUtils.hasText(parsed.getSecUid())) {
                request.setSecUid(parsed.getSecUid());
            }
            if (!StringUtils.hasText(request.getProfileUrl()) && StringUtils.hasText(parsed.getProfileUrl())) {
                request.setProfileUrl(parsed.getProfileUrl());
            }
        }
        if (!StringUtils.hasText(request.getDouyinUid())) {
            throw BusinessException.param("douyinUid 不能为空");
        }
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, request.getDouyinUid())
                .last("limit 1"));
        if (existing != null) {
            throw BusinessException.duplicate("达人 douyinUid 已存在");
        }
        request.setStatus(1);
        if (StringUtils.hasText(request.getNickname())) {
            request.setNickname(request.getNickname().trim());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            request.setContactPhone(request.getContactPhone().trim());
        }
        if (StringUtils.hasText(request.getContactWechat())) {
            request.setContactWechat(request.getContactWechat().trim());
        }
        if (StringUtils.hasText(request.getIntro())) {
            request.setIntro(request.getIntro().trim());
        }
        if (!StringUtils.hasText(request.getDouyinAccount()) && StringUtils.hasText(request.getDouyinNo())) {
            request.setDouyinAccount(request.getDouyinNo().trim());
        }
        if (!StringUtils.hasText(request.getTalentUid()) && StringUtils.hasText(request.getUid())) {
            request.setTalentUid(request.getUid().trim());
        }
        if (request.getUnsupportedFields() == null || request.getUnsupportedFields().isEmpty()) {
            request.setUnsupportedFields(List.of("talentLevel", "sales30d"));
        }
        request.setId(UUID.randomUUID());
        talentMapper.insert(request);
        boolean profilePrefilled = StringUtils.hasText(request.getDataSource()) && StringUtils.hasText(request.getSyncStatus());
        if (profilePrefilled) {
            request.setLastSyncTime(LocalDateTime.now());
            persistTalent(request);
            return request;
        }
        TalentEnrichTask task = createEnrichTask(request, ENRICH_TASK_STATUS_PENDING, null);
        markEnrichTask(task, ENRICH_TASK_STATUS_RUNNING, null);
        try {
            TalentEnrichOrchestrator.OrchestrateResult orchestrateResult = talentEnrichOrchestrator.enrich(request, false);
            enrichTalentInfo(request, false);
            persistTalent(request);
            if (orchestrateResult.updated()) {
                markEnrichTask(task, ENRICH_TASK_STATUS_SUCCESS, null);
            } else {
                request.setEnrichStatus(ENRICH_TASK_STATUS_WAIT_MANUAL);
                request.setLastEnrichTime(LocalDateTime.now());
                persistTalent(request);
                markEnrichTask(task, ENRICH_TASK_STATUS_WAIT_MANUAL, orchestrateResult.message());
            }
        } catch (RuntimeException ex) {
            request.setEnrichStatus(ENRICH_TASK_STATUS_FAILED);
            request.setLastEnrichTime(LocalDateTime.now());
            persistTalent(request);
            markEnrichTask(task, ENRICH_TASK_STATUS_FAILED, ex.getMessage());
        }
        return request;
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
        Talent talent = getById(id);
        if (StringUtils.hasText(request.getNickname())) {
            talent.setNickname(request.getNickname());
        }
        if (request.getFans() != null) {
            talent.setFans(request.getFans());
        }
        if (StringUtils.hasText(request.getLevel())) {
            talent.setLevel(request.getLevel());
        }
        if (request.getStatus() != null) {
            talent.setStatus(request.getStatus());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            talent.setContactPhone(request.getContactPhone().trim());
        }
        if (StringUtils.hasText(request.getContactWechat())) {
            talent.setContactWechat(request.getContactWechat().trim());
        }
        if (StringUtils.hasText(request.getIntro())) {
            talent.setIntro(request.getIntro().trim());
        }
        persistTalent(talent);
        return talent;
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
        Talent talent = getById(id);
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                recipientName, recipientPhone, recipientAddress);
        talent.setShippingRecipientName(addr.recipientName());
        talent.setShippingRecipientPhone(addr.recipientPhone());
        talent.setShippingRecipientAddress(addr.recipientAddress());
        persistTalent(talent);
        return talent;
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
        if (userId == null) {
            return updateShippingAddress(id, recipientName, recipientPhone, recipientAddress);
        }
        Talent talent = getById(id);
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(id, userId);
        if (claim == null) {
            throw new ForbiddenException("仅当前认领人可以维护达人收货地址");
        }
        // T-04 fix: 地址仅存于 claim 层，不写入 talent 主表，避免非认领人通过达人详情查见
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                recipientName, recipientPhone, recipientAddress);
        claim.setRecipientName(addr.recipientName());
        claim.setRecipientPhone(addr.recipientPhone());
        claim.setRecipientAddress(addr.recipientAddress());
        persistTalentClaim(claim);
        talent.setShippingRecipientName(addr.recipientName());
        talent.setShippingRecipientPhone(addr.recipientPhone());
        talent.setShippingRecipientAddress(addr.recipientAddress());
        return talent;
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
        Talent talent = getById(id);
        if (userId == null) {
            return talent;
        }
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(id, userId);
        if (claim == null) {
            // T-04 fix: 无认领时返回空地址，不再泄露 talent 主表旧数据
            talent.setShippingRecipientName(null);
            talent.setShippingRecipientPhone(null);
            talent.setShippingRecipientAddress(null);
            return talent;
        }
        // T-04 fix: 仅返回答领人地址，不使用 talent 主表兜底
        talent.setShippingRecipientName(claim.getRecipientName());
        talent.setShippingRecipientPhone(claim.getRecipientPhone());
        talent.setShippingRecipientAddress(claim.getRecipientAddress());
        return talent;
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
        if (accounts == null || accounts.isEmpty()) {
            return new TalentBatchImportResult(0, 0, 0, 0, List.of());
        }
        List<TalentBatchImportResult.TalentBatchImportItemResult> items = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (String rawAccount : accounts) {
            String account = rawAccount == null ? null : rawAccount.trim();
            if (!StringUtils.hasText(account)) {
                failed++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        rawAccount, "FAILED", null, "账号为空"));
                continue;
            }
            try {
                TalentInputParseResult parsed = TalentInputParser.parse(account);
                if (!StringUtils.hasText(parsed.getDouyinUid())) {
                    failed++;
                    items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                            account, "FAILED", null, "无法解析达人账号"));
                    continue;
                }
                Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDouyinUid, parsed.getDouyinUid())
                        .last("limit 1"));
                if (existing != null) {
                    skipped++;
                    items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                            account, "SKIPPED", existing.getId(), "达人已存在"));
                    continue;
                }
                Talent request = new Talent();
                request.setDouyinUid(parsed.getDouyinUid());
                request.setDouyinNo(parsed.getDouyinNo());
                request.setUid(parsed.getUid());
                request.setSecUid(parsed.getSecUid());
                request.setProfileUrl(parsed.getProfileUrl());
                Talent saved = create(request);
                created++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        account, "CREATED", saved.getId(), null));
                operationLogService.recordSystemAction(
                        operatorId,
                        "达人批量导入",
                        "创建达人",
                        "POST",
                        "talent",
                        saved.getId() == null ? account : saved.getId().toString(),
                        saved.getNickname(),
                        "batch_import_talents");
            } catch (RuntimeException ex) {
                failed++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        account, "FAILED", null, ex.getMessage()));
            }
        }
        return new TalentBatchImportResult(accounts.size(), created, skipped, failed, items);
    }

    /**
     * 获取预设标签列表。
     *
     * @return 系统预设的达人标签列表
     */
    public List<String> listPresetTags() {
        return businessRuleConfigService.getPresetTalentTags();
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
     * 将字符串 trim，空白字符串转为 null。
     *
     * @param value 原始字符串
     * @return trim 后的字符串，空白时返回 null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 返回第一个非空白字符串，否则返回兜底值。
     *
     * @param first    优先值
     * @param fallback 兜底值
     * @return first 非空白时返回 first，否则返回 fallback
     */
    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    /**
     * 逻辑删除达人。
     *
     * @param id 达人 ID
     * @throws BusinessException 达人不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        getById(id);
        talentMapper.deleteById(id);
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
        TalentClaimPolicy.requireClaimUser(userId);
        String lockKey = "talent:claim:lock:" + talentId;
        String lockValue = userId.toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Objects.requireNonNull(lockKey),
                Objects.requireNonNull(lockValue),
                10,
                TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw BusinessException.conflict("达人认领处理中，请稍后重试");
        }
        try {
            Talent talent = getById(talentId);
            int protectDays = getProtectDays();

            TalentClaimPolicy.assertNotDuplicateActiveClaim(
                    talentClaimMapper.findActiveByTalentAndUser(talentId, userId));

            LocalDateTime now = LocalDateTime.now();
            TalentClaim claim = findLatestClaimByTalentAndUser(talentId, userId);
            boolean newClaim = claim == null;
            if (newClaim) {
                claim = new TalentClaim();
                claim.setId(UUID.randomUUID());
                claim.setTalentId(talentId);
                claim.setTalentUid(talent.getDouyinUid());
                claim.setUserId(userId);
            }
            claim.setDeptId(deptId);
            claim.setClaimType(CLAIM_TYPE_MANUAL);
            claim.setClaimedAt(now);
            claim.setProtectedUntil(TalentClaimPolicy.protectedUntil(now, protectDays));
            claim.setStatus(CLAIM_STATUS_ACTIVE);
            if (newClaim) {
                talentClaimMapper.insert(claim);
            } else {
                persistTalentClaim(claim);
            }

            talent.setOwnerId(userId);
            talent.setClaimedAt(now);
            persistTalent(talent);
            operationLogService.recordSystemAction(
                    userId,
                    "达人管理",
                    "认领达人",
                    "POST",
                    "talent",
                    talentId.toString(),
                    talent.getNickname(),
                    String.format("认领达人: 负责人=%s", userId));
            return talent;
        } finally {
            redisTemplate.delete(lockKey);
        }
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
        TalentClaimPolicy.requireClaimUser(userId);
        getById(talentId);

        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        boolean isAdmin = hasRole(roleCodes, "admin");
        TalentClaim releaseTarget = TalentClaimPolicy.selectReleaseTarget(activeClaims, userId, isAdmin);

        releaseTarget.setStatus(CLAIM_STATUS_RELEASED);
        releaseTarget.setProtectedUntil(LocalDateTime.now());
        persistTalentClaim(releaseTarget);

        Talent talent = getById(talentId);
        List<TalentClaim> remainingActiveClaims = talentClaimMapper.findActiveByTalentId(talentId);
        applyReleaseOwnerSnapshot(talent, remainingActiveClaims);
        persistTalent(talent);
        operationLogService.recordSystemAction(
                userId,
                "达人管理",
                "释放达人",
                "POST",
                "talent",
                talentId.toString(),
                talent.getNickname(),
                String.format("释放达人: 操作人=%s, 释放认领=%s", userId, releaseTarget.getId()));
        return talent;
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
        if (newUserId == null) {
            throw BusinessException.param("新负责人ID不能为空");
        }
        UserOwnershipReference targetUser =
                userDomainFacade.loadUserOwnershipReferencesByIds(List.of(newUserId)).get(newUserId);
        if (targetUser == null) {
            throw BusinessException.notFound("目标负责人不存在");
        }
        Talent talent = getById(talentId);

        // Expire all active claims for this talent
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        LocalDateTime now = LocalDateTime.now();
        for (TalentClaim claim : activeClaims) {
            claim.setStatus(CLAIM_STATUS_EXPIRED);
            claim.setProtectedUntil(now);
            persistTalentClaim(claim);
        }

        // Create a new manual claim for the new user
        TalentClaim newClaim = new TalentClaim();
        newClaim.setId(UUID.randomUUID());
        newClaim.setTalentId(talentId);
        newClaim.setTalentUid(talent.getDouyinUid());
        newClaim.setUserId(newUserId);
        newClaim.setDeptId(null);
        newClaim.setClaimType(CLAIM_TYPE_MANUAL);
        newClaim.setClaimedAt(now);
        newClaim.setProtectedUntil(now.plusDays(getProtectDays()));
        newClaim.setStatus(CLAIM_STATUS_ACTIVE);
        talentClaimMapper.insert(newClaim);

        talent.setOwnerId(newUserId);
        talent.setClaimedAt(now);
        persistTalent(talent);

        operationLogService.recordSystemAction(
                currentUserId,
                "达人管理",
                "归属覆盖",
                "POST",
                "talent",
                talentId.toString(),
                talent.getNickname(),
                String.format("归属覆盖: 新负责人=%s, 原因=%s", newUserId, reason));

        return talent;
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
        return blacklist(talentId, reason, null, null, DataScope.ALL);
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
        Talent talent = getById(talentId);
        assertCanOperateBlacklist(talentId, userId, deptId, dataScope);
        talent.setBlacklisted(true);
        talent.setBlacklistReason(StringUtils.hasText(reason) ? reason.trim() : "手动拉黑");
        persistTalent(talent);
        return talent;
    }

    /**
     * 取消拉黑达人（无数据范围限制）。
     *
     * @param talentId 达人 ID
     * @return 取消拉黑后的达人实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent unblacklist(UUID talentId) {
        return unblacklist(talentId, null, null, DataScope.ALL);
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
        Talent talent = getById(talentId);
        assertCanOperateBlacklist(talentId, userId, deptId, dataScope);
        talent.setBlacklisted(false);
        talent.setBlacklistReason(null);
        persistTalent(talent);
        return talent;
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
        Talent talent = getById(talentId);
        TalentEnrichTask task = createEnrichTask(talent, ENRICH_TASK_STATUS_RUNNING, null);
        try {
            TalentEnrichOrchestrator.OrchestrateResult orchestrateResult = talentEnrichOrchestrator.enrich(talent, true);
            if (publicPageCrawlEnabled) {
                enrichTalentInfo(talent, true);
            }
            persistTalent(talent);
            if (orchestrateResult.updated()) {
                markEnrichTask(task, ENRICH_TASK_STATUS_SUCCESS, null);
            } else {
                talent.setEnrichStatus(ENRICH_TASK_STATUS_WAIT_MANUAL);
                talent.setLastEnrichTime(LocalDateTime.now());
                persistTalent(talent);
                markEnrichTask(task, ENRICH_TASK_STATUS_WAIT_MANUAL, orchestrateResult.message());
            }
            return talent;
        } catch (RuntimeException ex) {
            talent.setEnrichStatus(ENRICH_TASK_STATUS_FAILED);
            talent.setLastEnrichTime(LocalDateTime.now());
            persistTalent(talent);
            markEnrichTask(task, ENRICH_TASK_STATUS_FAILED, ex.getMessage());
            return talent;
        }
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
        Talent talent = getById(talentId);
        if (StringUtils.hasText(request.getNickname())) {
            talent.setNickname(request.getNickname().trim());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            talent.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getFans() != null) {
            talent.setFans(request.getFans());
        }
        if (request.getLikesCount() != null) {
            talent.setLikesCount(request.getLikesCount());
        }
        if (request.getFollowingCount() != null) {
            talent.setFollowingCount(request.getFollowingCount());
        }
        if (request.getWorksCount() != null) {
            talent.setWorksCount(request.getWorksCount());
        }
        if (StringUtils.hasText(request.getIpLocation())) {
            talent.setIpLocation(request.getIpLocation().trim());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            talent.setContactPhone(request.getContactPhone().trim());
        }
        if (StringUtils.hasText(request.getContactWechat())) {
            talent.setContactWechat(request.getContactWechat().trim());
        }
        if (StringUtils.hasText(request.getIntro())) {
            talent.setIntro(request.getIntro().trim());
        }
        talent.setDataSource("MANUAL");
        talent.setEnrichStatus(ENRICH_TASK_STATUS_SUCCESS);
        talent.setLastEnrichTime(LocalDateTime.now());
        persistTalent(talent);
        return talent;
    }

    /**
     * 获取达人最新的信息补全任务。
     *
     * @param talentId 达人 ID
     * @return 最新的补全任务记录，无记录时返回 null
     */
    public TalentEnrichTask getLatestEnrichTask(UUID talentId) {
        return talentEnrichTaskMapper.findLatestByTalentId(talentId);
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
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1))
                .stream()
                .map(Talent::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 释放过期的认领记录。
     * <p>
     * 由定时任务调用，处理流程：
     * <ol>
     *   <li>查询所有保护期已过（protectedUntil &lt; now）且状态为 ACTIVE 的认领记录</li>
     *   <li>批量加载关联达人数据</li>
     *   <li>对每条过期认领，检查认领后是否有订单产出（{@link #hasOutputSinceClaim}）</li>
     *   <li>有订单产出的认领自动续期，无产出的标记为 EXPIRED</li>
     * </ol>
     * </p>
     *
     * @param now 当前时间（用于判断保护期是否过期），为 null 时直接返回
     */
    public void releaseExpiredClaims(LocalDateTime now) {
        if (now == null) {
            return;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.selectList(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getStatus, CLAIM_STATUS_ACTIVE)
                .eq(TalentClaim::getDeleted, 0)
                .lt(TalentClaim::getProtectedUntil, now));
        if (activeClaims.isEmpty()) {
            return;
        }
        Set<UUID> talentIds = activeClaims.stream()
                .map(TalentClaim::getTalentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Talent> talentMap = talentMapper.selectBatchIds(talentIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Talent::getId, talent -> talent, (left, right) -> left));
        for (TalentClaim claim : activeClaims) {
            Talent talent = talentMap.get(claim.getTalentId());
            if (talent != null && hasOutputSinceClaim(talent, claim)) {
                continue;
            }
            claim.setStatus(CLAIM_STATUS_EXPIRED);
            persistTalentClaim(claim);
        }
    }

    /**
     * 判断达人自认领以来是否有订单产出。
     * <p>
     * 通过查询认领时间之后的结算订单，检查 extraData 中的 author_id 或 talent_uid
     * 是否匹配达人抖音号。用于过期认领续期判断——有订单产出的认领不自动过期。
     * </p>
     *
     * @param talent 达人实体
     * @param claim  认领记录
     * @return 有订单产出返回 true，否则返回 false
     */
    private boolean hasOutputSinceClaim(Talent talent, TalentClaim claim) {
        if (talent == null || claim == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return false;
        }
        LocalDateTime since = claim.getClaimedAt() == null ? LocalDateTime.now().minusDays(getProtectDays()) : claim.getClaimedAt();
        LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper =
                new LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder>()
                        .ge(com.colonel.saas.entity.ColonelsettlementOrder::getCreateTime, since);
        return loadOrdersInBatches(wrapper).stream()
                .anyMatch(order -> matchesTalent(order, talent.getDouyinUid()));
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
        Talent talent = getById(talentId);
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper =
                new LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder>()
                        .ge(com.colonel.saas.entity.ColonelsettlementOrder::getSettleTime, start);
        if (dataScope == DataScope.PERSONAL && userId != null) {
            wrapper.eq(com.colonel.saas.entity.ColonelsettlementOrder::getUserId, userId);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            wrapper.eq(com.colonel.saas.entity.ColonelsettlementOrder::getDeptId, deptId);
        }
        List<com.colonel.saas.entity.ColonelsettlementOrder> monthOrders = loadOrdersInBatches(wrapper);

        long totalServiceFee = 0L;
        long talentServiceFee = 0L;
        for (com.colonel.saas.entity.ColonelsettlementOrder order : monthOrders) {
            long serviceFee = order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission();
            totalServiceFee += serviceFee;
            if (matchesTalent(order, talent.getDouyinUid())) {
                talentServiceFee += serviceFee;
            }
        }
        long serviceRatio = totalServiceFee == 0 ? 0 : (talentServiceFee * 100 / totalServiceFee);
        Long sampleCount = sampleRequestMapper.selectCount(new LambdaQueryWrapper<com.colonel.saas.entity.SampleRequest>()
                .eq(com.colonel.saas.entity.SampleRequest::getTalentId, talentId)
                .ge(com.colonel.saas.entity.SampleRequest::getCreateTime, start));
        long monthlySamples = sampleCount == null ? 0L : sampleCount;

        boolean eligible = serviceRatio >= configDomainFacade.getExclusiveTalentFeeRatio().longValue()
                && monthlySamples >= configDomainFacade.getExclusiveTalentMonthlySamples();
        return new ExclusiveCheckResult(eligible, serviceRatio, monthlySamples);
    }

    /**
     * 分批加载结算订单数据。
     * <p>
     * 避免大量订单一次性加载导致内存溢出，每批 {@link #ORDER_BATCH_SIZE} 条，
     * 自动翻页直到所有记录加载完毕。
     * </p>
     *
     * @param wrapper 查询条件包装器
     * @return 全部匹配的订单列表
     */
    private List<com.colonel.saas.entity.ColonelsettlementOrder> loadOrdersInBatches(
            LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper) {
        List<com.colonel.saas.entity.ColonelsettlementOrder> result = new java.util.ArrayList<>();
        long current = 1L;
        while (true) {
            Page<com.colonel.saas.entity.ColonelsettlementOrder> page = new Page<>(current, ORDER_BATCH_SIZE);
            IPage<com.colonel.saas.entity.ColonelsettlementOrder> batch = orderMapper.selectPage(page, wrapper);
            List<com.colonel.saas.entity.ColonelsettlementOrder> records = batch.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            result.addAll(records);
            if (current >= batch.getPages()) {
                break;
            }
            current++;
        }
        return result;
    }

    /**
     * 判断订单是否匹配指定达人。
     * <p>
     * 通过订单 extraData 中的 author_id 或 talent_uid 字段与达人抖音号比对。
     * </p>
     *
     * @param order     结算订单
     * @param douyinUid 达人抖音号
     * @return 匹配返回 true，不匹配或数据缺失返回 false
     */
    private boolean matchesTalent(com.colonel.saas.entity.ColonelsettlementOrder order, String douyinUid) {
        if (!StringUtils.hasText(douyinUid)) {
            return false;
        }
        if (order.getExtraData() == null) {
            return false;
        }
        Object authorId = order.getExtraData().get("author_id");
        if (authorId != null && douyinUid.equals(String.valueOf(authorId))) {
            return true;
        }
        Object talentUid = order.getExtraData().get("talent_uid");
        return talentUid != null && douyinUid.equals(String.valueOf(talentUid));
    }

    /**
     * 查找指定达人和用户的最新认领记录（含已过期/已释放）。
     *
     * @param talentId 达人 ID
     * @param userId   用户 ID
     * @return 最新认领记录（按 claimedAt 降序取第一条），无记录时返回 null
     */
    private TalentClaim findLatestClaimByTalentAndUser(UUID talentId, UUID userId) {
        return talentClaimMapper.selectOne(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getTalentId, talentId)
                .eq(TalentClaim::getUserId, userId)
                .eq(TalentClaim::getDeleted, 0)
                .orderByDesc(TalentClaim::getClaimedAt)
                .last("limit 1"));
    }

    /**
     * 获取所有已认领达人的 ID 集合。
     * <p>
     * 查询状态为 ACTIVE 且未逻辑删除的认领记录，提取达人 ID 用于公开池过滤。
     * </p>
     *
     * @return 已认领达人 ID 集合，无认领记录时返回空集合
     */
    private Set<UUID> getClaimedTalentIds() {
        List<TalentClaim> claims = talentClaimMapper.selectList(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getStatus, CLAIM_STATUS_ACTIVE)
                .eq(TalentClaim::getDeleted, 0));
        if (claims.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<>();
        for (TalentClaim claim : claims) {
            if (claim.getTalentId() != null) {
                ids.add(claim.getTalentId());
            }
        }
        return ids;
    }

    /**
     * 通过爬虫数据补全达人资料。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>若 forceCrawl 为 true 且启用了公开页面爬虫，执行 {@link CrawlerTalentInfoService#crawlAndSave}</li>
     *   <li>从爬虫数据库查询达人信息（{@link CrawlerTalentInfoService#findByTalentId}）</li>
     *   <li>将爬到的昵称、粉丝数、头像、地区等字段回填到达人实体</li>
     *   <li>更新爬取状态和时间</li>
     * </ol>
     * </p>
     *
     * @param talent      达人实体（会被直接修改）
     * @param forceCrawl  是否强制执行爬虫抓取
     */
    private void enrichTalentInfo(Talent talent, boolean forceCrawl) {
        if (talent == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return;
        }
        String talentUid = talent.getDouyinUid().trim();

        if (forceCrawl && publicPageCrawlEnabled) {
            int success = crawlerTalentInfoService.crawlAndSave(List.of(talentUid));
            if (success <= 0) {
                talent.setCrawlStatus(2);
                talent.setCrawlMessage("crawl failed");
            }
        }

        CrawlerTalentInfo info = crawlerTalentInfoService.findByTalentId(talentUid);
        if (info == null) {
            return;
        }

        if (StringUtils.hasText(info.getNickname())) {
            talent.setNickname(info.getNickname());
        }
        if (info.getFansCount() != null) {
            talent.setFans(info.getFansCount());
        }
        if (StringUtils.hasText(info.getAvatarUrl())) {
            talent.setAvatarUrl(info.getAvatarUrl());
        }
        if (StringUtils.hasText(info.getRegion())) {
            talent.setIpLocation(info.getRegion());
        }
        talent.setLastCrawlAt(info.getLastCrawlTime() == null ? LocalDateTime.now() : info.getLastCrawlTime());
        talent.setCrawlStatus(1);
        talent.setCrawlMessage(null);
    }

    /**
     * 判断当前用户是否有权释放指定认领记录。
     *
     * @param claim    认领记录
     * @param userId   当前用户 ID
     * @param deptId   当前部门 ID
     * @param isAdmin  是否管理员
     * @return 有权返回 true
     */
    /**
     * 释放认领后重新计算达人所属人快照。
     * <p>
     * 从剩余生效认领记录中选择最新认领者作为新的 ownerId，
     * 同时更新 claimedAt、protectedUntil 和 activeClaimCount。
     * 若无剩余认领记录，清空所有归属信息。
     * </p>
     *
     * @param talent       达人实体（会被直接修改）
     * @param activeClaims 剩余的有效认领记录
     */
    private void applyReleaseOwnerSnapshot(Talent talent, List<TalentClaim> activeClaims) {
        List<TalentClaim> remainingClaims = activeClaims == null
                ? List.of()
                : activeClaims.stream()
                        .filter(claim -> claim.getStatus() != null && claim.getStatus() == CLAIM_STATUS_ACTIVE)
                        .sorted(Comparator.<TalentClaim, LocalDateTime>comparing(
                                TalentClaim::getClaimedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
        talent.setActiveClaimCount(remainingClaims.size());
        if (remainingClaims.isEmpty()) {
            talent.setOwnerId(null);
            talent.setClaimedAt(null);
            talent.setProtectedUntil(null);
            return;
        }
        TalentClaim nextOwnerClaim = remainingClaims.get(0);
        talent.setOwnerId(nextOwnerClaim.getUserId());
        talent.setClaimedAt(nextOwnerClaim.getClaimedAt());
        talent.setProtectedUntil(remainingClaims.stream()
                .map(TalentClaim::getProtectedUntil)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(nextOwnerClaim.getProtectedUntil()));
    }

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
        if (dataScope == DataScope.PERSONAL) {
            boolean ownedByCurrentUser = userId != null && activeClaims.stream()
                    .anyMatch(claim -> userId.equals(claim.getUserId()));
            if (!ownedByCurrentUser) {
                throw new ForbiddenException("无权操作该达人");
            }
            return;
        }
        boolean ownedByCurrentDept = deptId != null && activeClaims.stream()
                .anyMatch(claim -> deptId.equals(claim.getDeptId()));
        if (!ownedByCurrentDept) {
            throw new ForbiddenException("无权操作该达人");
        }
    }

    /**
     * 判断角色编码集合中是否包含指定角色。
     * <p>
     * 比较时忽略大小写。
     * </p>
     *
     * @param roleCodes 角色编码集合（可为 null）
     * @param role      目标角色编码
     * @return 包含返回 true
     */
    private boolean hasRole(Collection<?> roleCodes, String role) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        String target = role.toLowerCase(Locale.ROOT);
        return roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(code -> code.toLowerCase(Locale.ROOT))
                .anyMatch(target::equals);
    }

    /**
     * 创建达人信息补全任务记录。
     *
     * @param talent   达人实体（为 null 或 ID 为 null 时返回 null）
     * @param status   任务初始状态
     * @param errorMsg 错误信息（可为 null）
     * @return 创建的补全任务记录，达人无效时返回 null
     */
    private TalentEnrichTask createEnrichTask(Talent talent, String status, String errorMsg) {
        if (talent == null || talent.getId() == null) {
            return null;
        }
        TalentEnrichTask task = new TalentEnrichTask();
        task.setTalentId(talent.getId());
        task.setInputValue(resolveInputValue(talent));
        task.setInputType(resolveInputType(talent));
        task.setSourceType(ENRICH_SOURCE_SYSTEM);
        task.setTaskStatus(status);
        task.setRetryCount(0);
        task.setErrorMsg(errorMsg);
        task.setId(UUID.randomUUID());
        talentEnrichTaskMapper.insert(task);
        return task;
    }

    /**
     * 更新补全任务状态。
     *
     * @param task     补全任务记录（为 null 或 ID 为 null 时跳过）
     * @param status   新状态
     * @param errorMsg 错误信息（可为 null）
     */
    private void markEnrichTask(TalentEnrichTask task, String status, String errorMsg) {
        if (task == null || task.getId() == null) {
            return;
        }
        TalentEnrichTask update = new TalentEnrichTask();
        update.setId(task.getId());
        update.setTaskStatus(status);
        update.setErrorMsg(errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        talentEnrichTaskMapper.updateById(update);
    }

    /**
     * 解析达人信息补全的输入值。
     * <p>
     * 按优先级选择：profileUrl &gt; douyinNo &gt; uid &gt; secUid &gt; douyinUid。
     * </p>
     *
     * @param talent 达人实体
     * @return 解析出的输入值（已 trim），全部为空时返回 null
     */
    private String resolveInputValue(Talent talent) {
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return talent.getProfileUrl().trim();
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return talent.getDouyinNo().trim();
        }
        if (StringUtils.hasText(talent.getUid())) {
            return talent.getUid().trim();
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return talent.getSecUid().trim();
        }
        if (StringUtils.hasText(talent.getDouyinUid())) {
            return talent.getDouyinUid().trim();
        }
        return null;
    }

    /**
     * 解析达人信息补全的输入类型。
     * <p>
     * 与 {@link #resolveInputValue(Talent)} 对应，返回输入值的类型标识：
     * PROFILE_URL / DOUYIN_NO / UID / SEC_UID / DOUYIN_UID / UNKNOWN。
     * </p>
     *
     * @param talent 达人实体
     * @return 输入类型标识
     */
    private String resolveInputType(Talent talent) {
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return "PROFILE_URL";
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return "DOUYIN_NO";
        }
        if (StringUtils.hasText(talent.getUid())) {
            return "UID";
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return "SEC_UID";
        }
        if (StringUtils.hasText(talent.getDouyinUid())) {
            return "DOUYIN_UID";
        }
        return "UNKNOWN";
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
     * 持久化认领记录（乐观锁更新）。
     *
     * @param claim 认领记录
     */
    private void persistTalentClaim(TalentClaim claim) {
        OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
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
