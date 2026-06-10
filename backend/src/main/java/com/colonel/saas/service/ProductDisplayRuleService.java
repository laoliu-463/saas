package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.service.display.DisplayRuleOperatorContext;
import com.colonel.saas.service.display.ProductDisplayAuditService;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicyResult;
import com.colonel.saas.domain.product.policy.ProductDisplayRelationInput;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 商品库展示去重规则引擎服务。
 * <p>
 * 核心规则：同一 {@code product_id} 下的所有运营状态记录中，最多只能有一条处于
 * {@link ProductDisplayStatus#DISPLAYING}（展示中）状态，其余合格记录自动降级为
 * {@code HIDDEN}（隐藏）。这确保了商品在前端展示中不会出现重复。
 * </p>
 * <h3>选择优先级（由高到低）</h3>
 * <ol>
 *   <li><strong>强制展示</strong>（{@code forceDisplay=true}）— 管理员手动指定，优先级最高</li>
 *   <li><strong>投流支持</strong>（{@code supportsAds=true}）— 支持广告投放的候选优先</li>
 *   <li><strong>佣金率高</strong>（{@code commissionRatio} 降序）— 佣金比例越高越优先</li>
 *   <li><strong>服务费率低</strong>（{@code serviceFeeRatio} 升序）— 服务费率越低越优先</li>
 *   <li><strong>上架时间早</strong>（{@code shelfTime} 升序）— 上架越早越优先</li>
 * </ol>
 * <h3>保护期机制</h3>
 * <p>
 * 默认保护期为 {@value DEFAULT_PROTECTION_MONTHS} 个月（可按活动配置）。保护期内，
 * 当前展示中的候选不会被普通优先级替换，只有"优势条件"（更高佣金率或更低服务费率或投流支持）
 * 才能覆盖保护期内的候选。保护期结束后，按正常优先级规则重新选择。
 * </p>
 * <h3>触发场景</h3>
 * <ul>
 *   <li>按商品 ID 触发（{@link #applyForProductId}）— 单个商品的展示决策</li>
 *   <li>按活动 ID 触发（{@link #applyForActivityId}）— 活动维度批量触发</li>
 *   <li>全量对账（{@link #reconcileAll}）— 定时任务定期对账所有已入商品库的商品</li>
 * </ul>
 * <h3>审计与事件</h3>
 * <p>
 * 当展示决策发生切换（DISPLAYING 的记录发生变化）时，会同时写入审计日志
 * （{@link ProductDisplayAuditService}）并发布领域事件
 * （{@link ProductDomainEventPublisher}），用于下游订阅和合规追溯。
 * </p>
 *
 * @see ProductDisplayStatus
 * @see DisplayCandidate
 * @see ProductDisplayAuditService
 * @see ProductBizStatusService
 */
@Slf4j
@Service
public class ProductDisplayRuleService {

    /** 展示规则引擎版本号，每次规则变更时递增，用于审计和事件追踪 */
    public static final int DISPLAY_RULE_VERSION = 3;
    /** 默认保护期月数：当前展示候选在保护期内不会被普通优先级替换 */
    public static final int DEFAULT_PROTECTION_MONTHS = 3;

    /** 隐藏原因：被更高优先级的候选替换 */
    public static final String HIDDEN_REASON_REPLACED = "REPLACED_BY_HIGHER_PRIORITY";
    /** 隐藏原因：被具备优势条件的候选覆盖（保护期内仍可被优势条件替换） */
    public static final String HIDDEN_REASON_REPLACED_BY_ADVANTAGE = "REPLACED_BY_ADVANTAGE";
    /** 隐藏原因：不满足展示资格（未通过审核、商品未推广、已手动禁用等） */
    public static final String HIDDEN_REASON_NOT_ELIGIBLE = "NOT_ELIGIBLE";
    /** 隐藏原因：本地审核拒绝 */
    public static final String HIDDEN_REASON_LOCAL_REJECTED = "LOCAL_REJECTED";
    /** 隐藏原因：上游商品不是推广中 */
    public static final String HIDDEN_REASON_UPSTREAM_NOT_PROMOTING = "UPSTREAM_NOT_PROMOTING";
    /** 隐藏原因：本地发布已暂停 */
    public static final String HIDDEN_REASON_LOCAL_PAUSED = "LOCAL_PAUSED";
    /** 隐藏原因：本地发布已暂停（历史常量名，值已统一为 LOCAL_PAUSED） */
    public static final String HIDDEN_REASON_PUBLISH_PAUSED = HIDDEN_REASON_LOCAL_PAUSED;
    /** 隐藏原因：活动已过期（推广结束时间早于当前时间） */
    public static final String HIDDEN_REASON_ACTIVITY_EXPIRED = "ACTIVITY_EXPIRED";
    /** 隐藏原因：被管理员强制替换 */
    public static final String HIDDEN_REASON_ADMIN_FORCE = "ADMIN_FORCE_REPLACED";
    /** 展示原因：管理员强制展示 */
    public static final String DISPLAY_REASON_FORCE = "ADMIN_FORCE";
    /** 展示原因：优势条件覆盖（保护期内被更高佣金/更低费率/投流支持的候选替换） */
    public static final String DISPLAY_REASON_ADVANTAGE = "ADVANTAGE_OVERRIDE";
    /** 展示原因：规则引擎正常选择（基于优先级比较器选出最优候选） */
    public static final String DISPLAY_REASON_RULE = "RULE_ENGINE";
    /** 修复原因：上游推广中自动入库 */
    public static final String REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY = "UPSTREAM_PROMOTING_AUTO_LIBRARY";
    /** 修复原因：上游不是推广中 */
    public static final String REPAIR_REASON_UPSTREAM_NOT_PROMOTING = "UPSTREAM_NOT_PROMOTING";
    /** 修复原因：本地审核拒绝 */
    public static final String REPAIR_REASON_LOCAL_REJECTED = "LOCAL_REJECTED";
    /** 修复原因：本地暂停 */
    public static final String REPAIR_REASON_LOCAL_PAUSED = "LOCAL_PAUSED";
    /** 修复原因：推广期已结束 */
    public static final String REPAIR_REASON_EXPIRED = "EXPIRED";

    /** 商品推广中状态码（snapshot.status == 1 表示商品正在推广） */
    private static final int PROMOTING_STATUS = 1;
    private static final int DEFAULT_REPAIR_LIMIT = 1000;
    private static final int MAX_REPAIR_LIMIT = 10000;
    private static final String AUTO_LIBRARY_REPAIR_REMARK = "上游状态为推广中，系统自动入库展示";

    /** JSON 序列化/反序列化工具，用于解析审核附加数据 payload */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** 多格式时间解析器列表，按优先级尝试解析各种日期时间格式 */
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    );

    /** 商品运营状态 Mapper（查询/更新展示状态、保护期等字段） */
    private final ProductOperationStateMapper operationStateMapper;
    /** 商品快照 Mapper（获取佣金率、推广状态、活动信息等快照数据） */
    private final ProductSnapshotMapper snapshotMapper;
    /** 商品业务状态服务（读取审核状态，判断是否 APPROVED） */
    private final ProductBizStatusService productBizStatusService;
    /** 团长结算活动 Mapper（查询活动级别的保护期配置） */
    private final ColonelsettlementActivityMapper colonelActivityMapper;
    /** 商品领域事件发布器（发布展示/隐藏/规则应用等事件） */
    private final ProductDomainEventPublisher productDomainEventPublisher;
    /** 展示审计日志服务（记录展示切换事件，用于合规追溯） */
    private final ProductDisplayAuditService productDisplayAuditService;
    /** 商品展示去重纯策略（DDD-PRODUCT-002） */
    private final ProductDisplayPolicy productDisplayPolicy;

    /**
     * 构造注入所有依赖。
     *
     * @param operationStateMapper      商品运营状态 Mapper
     * @param snapshotMapper            商品快照 Mapper
     * @param productBizStatusService   商品业务状态服务
     * @param colonelActivityMapper     团长结算活动 Mapper
     * @param productDomainEventPublisher 商品领域事件发布器
     * @param productDisplayAuditService  展示审计日志服务
     * @param productDisplayPolicy        商品展示去重策略
     */
    public ProductDisplayRuleService(
            ProductOperationStateMapper operationStateMapper,
            ProductSnapshotMapper snapshotMapper,
            ProductBizStatusService productBizStatusService,
            ColonelsettlementActivityMapper colonelActivityMapper,
            ProductDomainEventPublisher productDomainEventPublisher,
            ProductDisplayAuditService productDisplayAuditService,
            ProductDisplayPolicy productDisplayPolicy) {
        this.operationStateMapper = operationStateMapper;
        this.snapshotMapper = snapshotMapper;
        this.productBizStatusService = productBizStatusService;
        this.colonelActivityMapper = colonelActivityMapper;
        this.productDomainEventPublisher = productDomainEventPublisher;
        this.productDisplayAuditService = productDisplayAuditService;
        this.productDisplayPolicy = productDisplayPolicy;
    }

    /**
     * 按商品 ID 触发展示去重规则（使用系统默认操作者上下文）。
     *
     * @param productId 商品 ID
     * @see #applyForProductId(String, DisplayRuleOperatorContext)
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyForProductId(String productId) {
        applyForProductId(productId, DisplayRuleOperatorContext.system());
    }

    /**
     * 按商品 ID 触发展示去重规则。
     * <p>
     * 查询该商品下所有运营状态记录（按创建时间升序），然后执行核心去重逻辑。
     * 当商品 ID 为空或不存在任何运营状态时直接返回。
     * </p>
     *
     * @param productId 商品 ID
     * @param operator  操作者上下文（用于审计日志记录谁触发了规则）
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyForProductId(String productId, DisplayRuleOperatorContext operator) {
        /* 空值防御：商品 ID 为空时直接跳过 */
        if (!StringUtils.hasText(productId)) {
            return;
        }
        /* 查询该商品下所有运营状态，按创建时间排序（先创建的优先考虑） */
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getProductId, productId.trim())
                        .orderByAsc(ProductOperationState::getCreateTime));
        if (states.isEmpty()) {
            return;
        }
        /* 进入核心去重决策流程 */
        applyForStates(productId.trim(), states, operator);
    }

    /**
     * 按活动 ID 批量触发展示去重规则（使用系统默认操作者上下文）。
     *
     * @param activityId 活动 ID
     * @see #applyForActivityId(String, DisplayRuleOperatorContext)
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyForActivityId(String activityId) {
        applyForActivityId(activityId, DisplayRuleOperatorContext.system());
    }

    /**
     * 按活动 ID 批量触发展示去重规则。
     * <p>
     * 查询该活动下所有已入商品库（{@code selectedToLibrary=true}）的运营状态，
     * 提取去重后的商品 ID 集合，然后逐个商品调用 {@link #applyForProductId} 执行去重。
     * </p>
     *
     * @param activityId 活动 ID
     * @param operator   操作者上下文
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyForActivityId(String activityId, DisplayRuleOperatorContext operator) {
        if (!StringUtils.hasText(activityId)) {
            return;
        }
        long totalStartedAt = System.nanoTime();
        String normalizedActivityId = activityId.trim();
        long queryStartedAt = System.nanoTime();
        /* 查询该活动下已入商品库的运营状态（仅选取 productId 字段以减少 IO） */
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, normalizedActivityId)
                        .eq(ProductOperationState::getSelectedToLibrary, true));
        long queryCostMs = elapsedMs(queryStartedAt);
        long calcStartedAt = System.nanoTime();
        /* 提取去重后的商品 ID 集合（LinkedHashSet 保持插入顺序） */
        Set<String> productIds = states.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long calcCostMs = elapsedMs(calcStartedAt);
        long updateStartedAt = System.nanoTime();
        /* 逐个商品执行展示去重规则 */
        for (String productId : productIds) {
            applyForProductId(productId, operator);
        }
        long updateCostMs = elapsedMs(updateStartedAt);
        DisplayRuleActivityStats stats = countActivityDisplayStats(normalizedActivityId);
        long totalCostMs = elapsedMs(totalStartedAt);
        log.info("[ProductDisplayRule] activityId={}, relations={}, productIds={}, displaying={}, hidden={}, totalCostMs={}, queryCostMs={}, calcCostMs={}, updateCostMs={}",
                normalizedActivityId,
                states.size(),
                productIds.size(),
                stats.displaying(),
                stats.hidden(),
                totalCostMs,
                queryCostMs,
                calcCostMs,
                updateCostMs);
    }

    /**
     * 全量对账：重新执行所有已入商品库的商品的展示去重规则（使用定时任务操作者上下文）。
     * <p>
     * 通常由定时任务调用，确保即使存在数据不一致也能在对账周期内自动修正。
     * </p>
     *
     * @return 处理的商品数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int reconcileAll() {
        return reconcileAll(DisplayRuleOperatorContext.job());
    }

    /**
     * 全量对账：重新执行所有已入商品库的商品的展示去重规则。
     * <p>
     * 查询所有 {@code selectedToLibrary=true} 的运营状态，提取去重后的商品 ID 集合，
     * 逐个商品调用 {@link #applyForProductId} 执行去重决策。
     * </p>
     *
     * @param operator 操作者上下文
     * @return 处理的商品数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int reconcileAll(DisplayRuleOperatorContext operator) {
        /* 查询所有已入商品库的运营状态（仅选取 productId 字段） */
        List<ProductOperationState> libraryStates = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getSelectedToLibrary, true));
        /* 提取去重后的商品 ID 集合 */
        Set<String> productIds = libraryStates.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int processed = 0;
        /* 逐个商品执行展示去重规则 */
        for (String productId : productIds) {
            applyForProductId(productId, operator);
            processed++;
        }
        log.info("Product display rule reconcile completed, productIds={}", processed);
        return processed;
    }

    @Transactional(rollbackFor = Exception.class)
    public LibraryRepairResult repairLibraryStateForActivity(String activityId, boolean dryRun, int limit) {
        if (!StringUtils.hasText(activityId)) {
            return LibraryRepairResult.empty(null, dryRun);
        }
        String normalizedActivityId = activityId.trim();
        int normalizedLimit = normalizeRepairLimit(limit);
        List<ProductSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, normalizedActivityId)
                .orderByAsc(ProductSnapshot::getProductId)
                .last("LIMIT " + normalizedLimit));
        return repairSnapshots(normalizedActivityId, snapshots, dryRun);
    }

    @Transactional(rollbackFor = Exception.class)
    public LibraryRepairResult repairLibraryStateForAllPromoting(boolean dryRun, int limit) {
        int normalizedLimit = normalizeRepairLimit(limit);
        List<ProductSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getStatus, PROMOTING_STATUS)
                .orderByAsc(ProductSnapshot::getActivityId)
                .orderByAsc(ProductSnapshot::getProductId)
                .last("LIMIT " + normalizedLimit));
        return repairSnapshots(null, snapshots, dryRun);
    }

    @Transactional(rollbackFor = Exception.class)
    public LibraryRepairResult repairLibraryStateForProducts(List<String> productIds, boolean dryRun, int limit) {
        if (productIds == null || productIds.isEmpty()) {
            return LibraryRepairResult.empty(null, dryRun);
        }
        List<String> normalizedProductIds = productIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(normalizeRepairLimit(limit))
                .toList();
        if (normalizedProductIds.isEmpty()) {
            return LibraryRepairResult.empty(null, dryRun);
        }
        List<ProductSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                .in(ProductSnapshot::getProductId, normalizedProductIds)
                .orderByAsc(ProductSnapshot::getActivityId)
                .orderByAsc(ProductSnapshot::getProductId));
        return repairSnapshots(null, snapshots, dryRun);
    }

    @Transactional(readOnly = true)
    public LibraryHealthResult inspectLibraryHealth() {
        List<ProductSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                .orderByAsc(ProductSnapshot::getActivityId)
                .orderByAsc(ProductSnapshot::getProductId));
        Map<String, ProductOperationState> stateMap = loadOperationStateMap(snapshots);
        long snapshotTotal = snapshots.size();
        long promotingTotal = 0;
        long promotingNotSelected = 0;
        long promotingNotDisplaying = 0;
        long displayingWithHiddenReason = 0;
        long selectedButNotPromoting = 0;
        long upstreamNotPromoting = 0;
        long localRejected = 0;
        long localPaused = 0;
        LocalDateTime lastSyncTime = null;

        for (ProductSnapshot snapshot : snapshots) {
            ProductOperationState state = stateMap.get(snapshotKey(snapshot.getActivityId(), snapshot.getProductId()));
            boolean promoting = isLocallyDisplayableSnapshotStatus(snapshot);
            if (promoting) {
                promotingTotal++;
            } else {
                upstreamNotPromoting++;
            }
            if (state != null) {
                if (promoting && !Boolean.TRUE.equals(state.getSelectedToLibrary())) {
                    promotingNotSelected++;
                }
                if (promoting && !ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
                    promotingNotDisplaying++;
                }
                if (ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())
                        && StringUtils.hasText(state.getHiddenReason())) {
                    displayingWithHiddenReason++;
                }
                if (Boolean.TRUE.equals(state.getSelectedToLibrary()) && !promoting) {
                    selectedButNotPromoting++;
                }
                if (isLocalRejected(state)) {
                    localRejected++;
                }
                if (isLocalPaused(state)) {
                    localPaused++;
                }
            } else if (promoting) {
                promotingNotSelected++;
                promotingNotDisplaying++;
            }
            if (snapshot.getSyncTime() != null && (lastSyncTime == null || snapshot.getSyncTime().isAfter(lastSyncTime))) {
                lastSyncTime = snapshot.getSyncTime();
            }
        }
        return new LibraryHealthResult(
                snapshotTotal,
                promotingTotal,
                promotingNotSelected,
                promotingNotDisplaying,
                displayingWithHiddenReason,
                selectedButNotPromoting,
                upstreamNotPromoting,
                localRejected,
                localPaused,
                lastSyncTime,
                null);
    }

    private LibraryRepairResult repairSnapshots(String activityId, List<ProductSnapshot> snapshots, boolean dryRun) {
        if (snapshots == null || snapshots.isEmpty()) {
            return LibraryRepairResult.empty(activityId, dryRun);
        }
        Map<String, ProductOperationState> stateMap = loadOperationStateMap(snapshots);
        LocalDateTime now = LocalDateTime.now();
        LibraryRepairAccumulator accumulator = new LibraryRepairAccumulator(activityId, dryRun);

        for (ProductSnapshot snapshot : snapshots) {
            ProductOperationState existingState = stateMap.get(snapshotKey(snapshot.getActivityId(), snapshot.getProductId()));
            ProductOperationState state = existingState;
            if (state == null) {
                state = new ProductOperationState();
                state.setActivityId(snapshot.getActivityId());
                state.setProductId(snapshot.getProductId());
                state.setSelectedToLibrary(false);
                state.setAuditStatus(1);
                state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                state.setDisplayStatus(ProductDisplayStatus.PENDING.name());
            }
            LibraryRepairDecision decision = buildRepairDecision(snapshot, state, now);
            accumulator.accept(snapshot, state, decision);
            if (!dryRun && decision.changed()) {
                ProductOperationState target = existingState == null
                        ? productBizStatusService.initStateIfAbsent(null, snapshot.getActivityId(), snapshot.getProductId(), null, null, "商品库展示状态修复")
                        : existingState;
                applyRepairDecision(target, decision, now);
                OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(target));
            }
        }

        LibraryRepairResult result = accumulator.toResult();
        log.info("Product library repair completed, activityId={}, dryRun={}, scanned={}, willSelectToLibrary={}, willDisplay={}, unchanged={}",
                activityId,
                dryRun,
                result.scanned(),
                result.willSelectToLibrary(),
                result.willDisplay(),
                result.unchanged());
        return result;
    }

    private Map<String, ProductOperationState> loadOperationStateMap(List<ProductSnapshot> snapshots) {
        Set<String> activityIds = snapshots.stream()
                .map(ProductSnapshot::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> productIds = snapshots.stream()
                .map(ProductSnapshot::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (activityIds.isEmpty() || productIds.isEmpty()) {
            return Map.of();
        }
        return operationStateMapper.selectList(new LambdaQueryWrapper<ProductOperationState>()
                        .in(ProductOperationState::getActivityId, activityIds)
                        .in(ProductOperationState::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(
                        state -> snapshotKey(state.getActivityId(), state.getProductId()),
                        state -> state,
                        (left, right) -> left));
    }

    private LibraryRepairDecision buildRepairDecision(ProductSnapshot snapshot, ProductOperationState state, LocalDateTime now) {
        boolean oldSelected = Boolean.TRUE.equals(state.getSelectedToLibrary());
        String oldDisplayStatus = ProductDisplayStatus.fromCode(state.getDisplayStatus()).name();
        String oldHiddenReason = state.getHiddenReason();
        Integer oldAuditStatus = state.getAuditStatus();
        String oldBizStatus = state.getBizStatus();

        boolean newSelected = oldSelected;
        String newDisplayStatus = oldDisplayStatus;
        String newHiddenReason = oldHiddenReason;
        Integer newAuditStatus = oldAuditStatus;
        String newBizStatus = oldBizStatus;
        String reason = null;
        boolean willDisplay = false;

        if (shouldAutoEnterLibrary(snapshot, state, now)) {
            newSelected = true;
            newDisplayStatus = ProductDisplayStatus.DISPLAYING.name().equals(oldDisplayStatus)
                    ? ProductDisplayStatus.DISPLAYING.name()
                    : ProductDisplayStatus.PENDING.name();
            newHiddenReason = null;
            newAuditStatus = 2;
            ProductBizStatus currentBizStatus = safeBizStatus(newBizStatus);
            if (currentBizStatus == null
                    || currentBizStatus == ProductBizStatus.PENDING_AUDIT
                    || currentBizStatus == ProductBizStatus.REJECTED) {
                newBizStatus = ProductBizStatus.APPROVED.name();
            }
            reason = REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY;
            willDisplay = true;
        } else if (isLocalPaused(state)
                && isLocallyDisplayableSnapshotStatus(snapshot)
                && !isPromotionExpired(snapshot, now)) {
            newSelected = true;
            newDisplayStatus = ProductDisplayStatus.HIDDEN.name();
            newHiddenReason = HIDDEN_REASON_LOCAL_PAUSED;
            newAuditStatus = 2;
            ProductBizStatus currentBizStatus = safeBizStatus(newBizStatus);
            if (currentBizStatus == null
                    || currentBizStatus == ProductBizStatus.PENDING_AUDIT
                    || currentBizStatus == ProductBizStatus.REJECTED) {
                newBizStatus = ProductBizStatus.APPROVED.name();
            }
            reason = REPAIR_REASON_LOCAL_PAUSED;
        } else {
            newDisplayStatus = ProductDisplayStatus.HIDDEN.name();
            newHiddenReason = resolveLibraryHiddenReason(snapshot, state, now);
            reason = HIDDEN_REASON_ACTIVITY_EXPIRED.equals(newHiddenReason)
                    ? REPAIR_REASON_EXPIRED
                    : REPAIR_REASON_UPSTREAM_NOT_PROMOTING;
        }

        boolean changed = oldSelected != newSelected
                || !Objects.equals(oldDisplayStatus, newDisplayStatus)
                || !Objects.equals(oldHiddenReason, newHiddenReason)
                || !Objects.equals(oldAuditStatus, newAuditStatus)
                || !Objects.equals(oldBizStatus, newBizStatus);
        return new LibraryRepairDecision(
                oldSelected,
                newSelected,
                oldDisplayStatus,
                newDisplayStatus,
                oldHiddenReason,
                newHiddenReason,
                oldAuditStatus,
                newAuditStatus,
                oldBizStatus,
                newBizStatus,
                reason,
                changed,
                willDisplay);
    }

    private void applyRepairDecision(ProductOperationState state, LibraryRepairDecision decision, LocalDateTime now) {
        state.setSelectedToLibrary(decision.newSelectedToLibrary());
        if (decision.newSelectedToLibrary() && state.getSelectedAt() == null) {
            state.setSelectedAt(now);
        }
        state.setDisplayStatus(decision.newDisplayStatus());
        state.setHiddenReason(decision.newHiddenReason());
        state.setAuditStatus(decision.newAuditStatus());
        state.setBizStatus(decision.newBizStatus());
        state.setDisplayRuleVersion(DISPLAY_RULE_VERSION);
        if (REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY.equals(decision.reason())
                && !StringUtils.hasText(state.getAuditRemark())) {
            state.setAuditRemark(AUTO_LIBRARY_REPAIR_REMARK);
        }
        state.setLastOperationAt(now);
    }

    public boolean shouldAutoEnterLibrary(ProductSnapshot snapshot, ProductOperationState state) {
        return shouldAutoEnterLibrary(snapshot, state, LocalDateTime.now());
    }

    boolean shouldAutoEnterLibrary(ProductSnapshot snapshot, ProductOperationState state, LocalDateTime now) {
        if (snapshot == null) {
            return false;
        }
        if (!isLocallyDisplayableSnapshotStatus(snapshot)) {
            return false;
        }
        if (isLocalPaused(state)) {
            return false;
        }
        return !isPromotionExpired(snapshot, now);
    }

    private String resolveLibraryHiddenReason(ProductSnapshot snapshot, ProductOperationState state, LocalDateTime now) {
        if (snapshot != null && !isLocallyDisplayableSnapshotStatus(snapshot)) {
            return HIDDEN_REASON_UPSTREAM_NOT_PROMOTING;
        }
        if (isLocalPaused(state)) {
            return HIDDEN_REASON_LOCAL_PAUSED;
        }
        if (snapshot != null && isPromotionExpired(snapshot, now)) {
            return HIDDEN_REASON_ACTIVITY_EXPIRED;
        }
        if (isLocalRejected(state)) {
            return HIDDEN_REASON_LOCAL_REJECTED;
        }
        return HIDDEN_REASON_NOT_ELIGIBLE;
    }

    private ProductBizStatus safeBizStatus(String raw) {
        try {
            return ProductBizStatus.fromCode(raw);
        } catch (IllegalArgumentException ex) {
            return ProductBizStatus.PENDING_AUDIT;
        }
    }

    private int normalizeRepairLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_REPAIR_LIMIT;
        }
        return Math.min(limit, MAX_REPAIR_LIMIT);
    }

    /**
     * 判断当前时间是否在保护期内。
     * <p>
     * 保护期 = 首次展示时间 + 配置的保护月数。当 {@code firstDisplayedAt} 为 null 时，
     * 认为不在保护期内（商品从未被展示过，不存在保护需求）。
     * </p>
     *
     * @param firstDisplayedAt   商品首次展示时间（可为 null）
     * @param monthsOfProtection 配置的保护月数（null 或 <=0 时使用默认值 {@value DEFAULT_PROTECTION_MONTHS}）
     * @param now                当前时间
     * @return 在保护期内返回 true，否则返回 false
     */
    boolean isInProtectionPeriod(LocalDateTime firstDisplayedAt, Integer monthsOfProtection, LocalDateTime now) {
        return productDisplayPolicy.isInProtectionPeriod(firstDisplayedAt, monthsOfProtection, now);
    }

    /**
     * 解析商品的服务费率。
     * <p>
     * 优先使用 {@code adServiceRatio}（广告服务费率，字符串百分比格式，如 "5.5%"），
     * 解析失败或为零时回退到 {@code activityAdCosRatio}（活动广告成本比例，Long 数值格式）。
     * </p>
     *
     * @param snapshot 商品快照（可为 null）
     * @return 服务费率（BigDecimal），null 快照时返回 {@link BigDecimal#ZERO}
     */
    BigDecimal resolveServiceFeeRatio(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return BigDecimal.ZERO;
        }
        /* 优先解析广告服务费率字符串（支持百分比符号和中文全角百分号） */
        BigDecimal rate = parsePercentValue(snapshot.getAdServiceRatio());
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            return rate;
        }
        /* 回退到活动广告成本比例数值 */
        return normalizeRatioNumber(snapshot.getActivityAdCosRatio());
    }

    boolean hasAdvantageOver(DisplayCandidate challenger, DisplayCandidate incumbent) {
        return productDisplayPolicy.hasAdvantageOver(
                toRelationInputFromCandidate(challenger),
                toRelationInputFromCandidate(incumbent));
    }

    private void applyForStates(String productId, List<ProductOperationState> states, DisplayRuleOperatorContext operator) {
        Map<String, ProductSnapshot> snapshotMap = loadSnapshots(states);
        hydrateProtectionMonths(snapshotMap);
        LocalDateTime now = LocalDateTime.now();
        UUID oldDisplayRelationId = states.stream()
                .filter(state -> ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))
                .map(ProductOperationState::getId)
                .findFirst()
                .orElse(null);

        applyNormalDisplayDedup(productId, states, snapshotMap, operator, now, oldDisplayRelationId);
    }

    private void applyNormalDisplayDedup(
            String productId,
            List<ProductOperationState> states,
            Map<String, ProductSnapshot> snapshotMap,
            DisplayRuleOperatorContext operator,
            LocalDateTime now,
            UUID oldDisplayRelationId) {
        List<ProductDisplayRelationInput> relationInputs = new ArrayList<>(states.size());
        Map<UUID, ProductOperationState> stateById = new java.util.HashMap<>(states.size());
        for (ProductOperationState state : states) {
            ProductSnapshot snapshot = snapshotMap.get(snapshotKey(state.getActivityId(), state.getProductId()));
            relationInputs.add(toRelationInput(state, snapshot));
            stateById.put(state.getId(), state);
        }

        ProductDisplayPolicyResult policyResult = productDisplayPolicy.decide(relationInputs, now);
        DisplayCandidate currentDisplaying = findCurrentDisplaying(states, snapshotMap, now);
        LocalDateTime productFirstDisplayedAt = resolveProductFirstDisplayedAt(states, currentDisplaying);
        String selectedReason = policyResult.selectedRelationId() == null
                ? null
                : policyResult.displayReasons().get(policyResult.selectedRelationId());

        List<DisplayDecision> decisions = new ArrayList<>();
        for (ProductDisplayPolicyResult.RelationDisplayOutcome outcome : policyResult.relationOutcomes()) {
            ProductOperationState state = stateById.get(outcome.relationId());
            ProductDisplayStatus currentStatus = ProductDisplayStatus.fromCode(state.getDisplayStatus());
            ProductDisplayStatus nextStatus = ProductDisplayStatus.valueOf(outcome.nextDisplayStatus());
            decisions.add(new DisplayDecision(
                    state,
                    currentStatus,
                    nextStatus,
                    outcome.hiddenReason(),
                    outcome.displayReason()));
        }

        for (DisplayDecision decision : decisions) {
            if (decision.demotesDisplaying()) {
                persistDisplayDecision(decision.state(), decision.nextStatus(),
                        decision.hiddenReason(), decision.displayReason(), productFirstDisplayedAt, now);
            }
        }
        for (DisplayDecision decision : decisions) {
            if (!decision.demotesDisplaying() && decision.nextStatus() != ProductDisplayStatus.DISPLAYING) {
                persistDisplayDecision(decision.state(), decision.nextStatus(),
                        decision.hiddenReason(), decision.displayReason(), productFirstDisplayedAt, now);
            }
        }
        for (DisplayDecision decision : decisions) {
            if (decision.nextStatus() == ProductDisplayStatus.DISPLAYING) {
                persistDisplayDecision(decision.state(), decision.nextStatus(),
                        decision.hiddenReason(), decision.displayReason(), productFirstDisplayedAt, now);
            }
        }

        if (policyResult.whetherNeedEvent()) {
            List<UUID> candidateIds = policyResult.eventCandidates();
            productDisplayAuditService.writeAudit(
                    productId,
                    policyResult.previousDisplayRelationId(),
                    policyResult.selectedRelationId(),
                    candidateIds,
                    "DISPLAY_SWITCH",
                    selectedReason,
                    null,
                    DISPLAY_RULE_VERSION,
                    operator,
                    Map.of("candidateCount", candidateIds.size()));
            productDomainEventPublisher.publishDisplayRuleApplied(
                    productId,
                    policyResult.previousDisplayRelationId(),
                    policyResult.selectedRelationId(),
                    DISPLAY_RULE_VERSION,
                    operator.operatorType(),
                    operator.operatorId(),
                    selectedReason == null ? Map.of() : Map.of("selectedReason", selectedReason));
        }
    }

    private ProductDisplayRelationInput toRelationInput(ProductOperationState state, ProductSnapshot snapshot) {
        DisplayCandidate candidate = toCandidate(state, snapshot);
        return new ProductDisplayRelationInput(
                state.getId(),
                state.getProductId(),
                state.getActivityId(),
                state.getBizStatus(),
                snapshot == null ? null : snapshot.getStatus(),
                snapshot == null ? null : parseDateTime(snapshot.getPromotionStartTime()),
                snapshot == null ? null : parseDateTime(snapshot.getPromotionEndTime()),
                state.getDisplayStatus(),
                state.getSelectedAt(),
                state.getFirstDisplayedAt(),
                state.getLastDisplayedAt(),
                candidate.commissionRatio(),
                candidate.serviceFeeRatio(),
                candidate.supportsAds(),
                state.getPinnedAt() != null,
                state.getPinnedUntil(),
                null,
                null,
                Boolean.TRUE.equals(state.getSelectedToLibrary()),
                isLocalPaused(state),
                isLocalRejected(state),
                Boolean.TRUE.equals(state.getForceDisplay()),
                state.getForceDisplayUntil(),
                candidate.shelfTime(),
                snapshot == null ? null : snapshot.getMonthsOfProtection());
    }

    private ProductDisplayRelationInput toRelationInputFromCandidate(DisplayCandidate candidate) {
        ProductOperationState state = candidate.state();
        return new ProductDisplayRelationInput(
                state.getId(),
                state.getProductId(),
                state.getActivityId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                candidate.commissionRatio(),
                candidate.serviceFeeRatio(),
                candidate.supportsAds(),
                false,
                null,
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                candidate.shelfTime(),
                null);
    }

    private DisplayCandidate findCurrentDisplaying(
            List<ProductOperationState> states,
            Map<String, ProductSnapshot> snapshotMap,
            LocalDateTime now) {
        for (ProductOperationState state : states) {
            if (!ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
                continue;
            }
            ProductSnapshot snapshot = snapshotMap.get(snapshotKey(state.getActivityId(), state.getProductId()));
            return toCandidate(state, snapshot);
        }
        return null;
    }

    private LocalDateTime resolveProductFirstDisplayedAt(
            List<ProductOperationState> states,
            DisplayCandidate currentDisplaying) {
        if (currentDisplaying != null && currentDisplaying.state().getFirstDisplayedAt() != null) {
            return currentDisplaying.state().getFirstDisplayedAt();
        }
        return states.stream()
                .map(ProductOperationState::getFirstDisplayedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private void persistDisplayDecision(
            ProductOperationState state,
            ProductDisplayStatus nextStatus,
            String hiddenReason,
            String displayReason,
            LocalDateTime productFirstDisplayedAt,
            LocalDateTime now) {
        ProductDisplayStatus current = ProductDisplayStatus.fromCode(state.getDisplayStatus());
        boolean statusChanged = current != nextStatus;
        boolean reasonChanged = !Objects.equals(state.getHiddenReason(), hiddenReason);

        if (!statusChanged && !reasonChanged
                && (nextStatus != ProductDisplayStatus.DISPLAYING || state.getFirstDisplayedAt() != null)) {
            return;
        }

        state.setDisplayStatus(nextStatus.name());
        state.setDisplayRuleVersion(DISPLAY_RULE_VERSION);
        state.setHiddenReason(hiddenReason);
        state.setDisplayReason(displayReason);
        if (nextStatus == ProductDisplayStatus.DISPLAYING) {
            if (productFirstDisplayedAt != null) {
                state.setFirstDisplayedAt(productFirstDisplayedAt);
            } else if (state.getFirstDisplayedAt() == null) {
                state.setFirstDisplayedAt(now);
            }
            state.setLastDisplayedAt(now);
        }
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));

        if (statusChanged) {
            if (nextStatus == ProductDisplayStatus.DISPLAYING) {
                productDomainEventPublisher.publishProductListed(
                        state.getActivityId(),
                        state.getProductId(),
                        state.getId(),
                        state.getSelectedBy(),
                        DISPLAY_RULE_VERSION,
                        displayReason);
            } else if (current == ProductDisplayStatus.DISPLAYING) {
                productDomainEventPublisher.publishProductHidden(
                        state.getActivityId(),
                        state.getProductId(),
                        state.getId(),
                        hiddenReason,
                        DISPLAY_RULE_VERSION);
            }
        }
    }

    boolean isEligibleForDisplay(ProductOperationState state, ProductSnapshot snapshot, LocalDateTime now) {
        if (state == null || snapshot == null) {
            return false;
        }
        if (isLocalPaused(state)) {
            return false;
        }
        if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            return false;
        }
        if (!isLocallyDisplayableSnapshotStatus(snapshot)) {
            return false;
        }
        return !isPromotionExpired(snapshot, now);
    }

    private boolean isLocallyDisplayableSnapshotStatus(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        Integer status = snapshot.getStatus();
        return Integer.valueOf(PROMOTING_STATUS).equals(status);
    }

    private boolean isLocalRejected(ProductOperationState state) {
        if (state == null) {
            return false;
        }
        if (Integer.valueOf(3).equals(state.getAuditStatus())) {
            return true;
        }
        return productBizStatusService.readBizStatus(state) == ProductBizStatus.REJECTED;
    }

    private boolean isLocalPaused(ProductOperationState state) {
        return state != null && Boolean.TRUE.equals(state.getManualDisabled());
    }

    private boolean isPromotionExpired(ProductSnapshot snapshot, LocalDateTime now) {
        LocalDateTime endTime = parseDateTime(snapshot.getPromotionEndTime());
        return endTime != null && endTime.isBefore(now);
    }

    private DisplayCandidate toCandidate(ProductOperationState state, ProductSnapshot snapshot) {
        return new DisplayCandidate(
                state,
                supportsAds(state, snapshot),
                resolveCommissionRatio(snapshot),
                resolveServiceFeeRatio(snapshot),
                resolveShelfTime(state, snapshot));
    }

    private boolean supportsAds(ProductOperationState state, ProductSnapshot snapshot) {
        Map<String, Object> supplement = parseAuditPayload(state.getAuditPayload());
        if (Boolean.TRUE.equals(readBoolean(supplement, "supportsAds"))) {
            return true;
        }
        if (StringUtils.hasText(state.getPromoteLink()) || StringUtils.hasText(state.getShortLink())) {
            return true;
        }
        return snapshot != null && snapshot.getActivityAdCosRatio() != null && snapshot.getActivityAdCosRatio() > 0;
    }

    private BigDecimal resolveCommissionRatio(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return BigDecimal.ZERO;
        }
        if (snapshot.getActivityCosRatio() != null) {
            return BigDecimal.valueOf(snapshot.getActivityCosRatio());
        }
        String text = snapshot.getActivityCosRatioText();
        if (!StringUtils.hasText(text)) {
            return BigDecimal.ZERO;
        }
        String normalized = text.replace("%", "").trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime resolveShelfTime(ProductOperationState state, ProductSnapshot snapshot) {
        if (state.getSelectedAt() != null) {
            return state.getSelectedAt();
        }
        if (snapshot != null && snapshot.getSyncTime() != null) {
            return snapshot.getSyncTime();
        }
        return state.getCreateTime();
    }

    private Map<String, ProductSnapshot> loadSnapshots(List<ProductOperationState> states) {
        Set<String> activityIds = states.stream()
                .map(ProductOperationState::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> productIds = states.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (activityIds.isEmpty() || productIds.isEmpty()) {
            return Map.of();
        }
        return snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                        .in(ProductSnapshot::getActivityId, activityIds)
                        .in(ProductSnapshot::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshotKey(snapshot.getActivityId(), snapshot.getProductId()),
                        snapshot -> snapshot,
                        (left, right) -> left));
    }

    private void hydrateProtectionMonths(Map<String, ProductSnapshot> snapshotMap) {
        Set<String> activityIds = snapshotMap.values().stream()
                .map(ProductSnapshot::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (activityIds.isEmpty()) {
            return;
        }
        Map<String, Integer> protectionMap = new java.util.LinkedHashMap<>();
        for (String activityId : activityIds) {
            ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
            if (activity != null && activity.getMonthsOfProtection() != null) {
                protectionMap.put(activityId, activity.getMonthsOfProtection());
            }
        }
        snapshotMap.values().forEach(snapshot -> {
            Integer protection = protectionMap.get(snapshot.getActivityId());
            if (protection != null) {
                snapshot.setMonthsOfProtection(protection);
            }
        });
    }

    private Map<String, Object> parseAuditPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Boolean readBoolean(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal normalizeRatioNumber(Long raw) {
        if (raw == null || raw <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = BigDecimal.valueOf(raw);
        if (raw >= 1000) {
            return value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercentValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }
        String normalized = raw.trim()
                .replace("%", "")
                .replace("％", "")
                .replace(",", "")
                .replace(" ", "");
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String snapshotKey(String activityId, String productId) {
        return activityId + "::" + productId;
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private DisplayRuleActivityStats countActivityDisplayStats(String activityId) {
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, activityId)
                        .eq(ProductOperationState::getSelectedToLibrary, true));
        long displaying = states.stream()
                .filter(state -> ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))
                .count();
        long hidden = states.stream()
                .filter(state -> ProductDisplayStatus.HIDDEN.name().equals(state.getDisplayStatus()))
                .count();
        return new DisplayRuleActivityStats(displaying, hidden);
    }

    public record LibraryRepairResult(
            String activityId,
            boolean dryRun,
            int scanned,
            int promoting,
            int willSelectToLibrary,
            int willDisplay,
            int willHideByUpstream,
            int willHideByLocalRejected,
            int willHideByLocalPaused,
            int unchanged,
            List<LibraryRepairItem> items) {

        static LibraryRepairResult empty(String activityId, boolean dryRun) {
            return new LibraryRepairResult(activityId, dryRun, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
    }

    public record LibraryRepairItem(
            String activityId,
            String productId,
            Boolean oldSelectedToLibrary,
            Boolean newSelectedToLibrary,
            String oldDisplayStatus,
            String newDisplayStatus,
            String oldHiddenReason,
            String newHiddenReason,
            Integer oldAuditStatus,
            Integer newAuditStatus,
            String oldBizStatus,
            String newBizStatus,
            String reason) {
    }

    public record LibraryHealthResult(
            long snapshotTotal,
            long promotingTotal,
            long promotingNotSelected,
            long promotingNotDisplaying,
            long displayingWithHiddenReason,
            long selectedButNotPromoting,
            long upstreamNotPromoting,
            long localRejected,
            long localPaused,
            LocalDateTime lastSyncTime,
            String lastSyncError) {
    }

    private record LibraryRepairDecision(
            Boolean oldSelectedToLibrary,
            Boolean newSelectedToLibrary,
            String oldDisplayStatus,
            String newDisplayStatus,
            String oldHiddenReason,
            String newHiddenReason,
            Integer oldAuditStatus,
            Integer newAuditStatus,
            String oldBizStatus,
            String newBizStatus,
            String reason,
            boolean changed,
            boolean willDisplay) {
    }

    private record DisplayDecision(
            ProductOperationState state,
            ProductDisplayStatus currentStatus,
            ProductDisplayStatus nextStatus,
            String hiddenReason,
            String displayReason) {

        private boolean demotesDisplaying() {
            return currentStatus == ProductDisplayStatus.DISPLAYING
                    && nextStatus != ProductDisplayStatus.DISPLAYING;
        }
    }

    private static final class LibraryRepairAccumulator {
        private final String activityId;
        private final boolean dryRun;
        private final List<LibraryRepairItem> items = new ArrayList<>();
        private int scanned;
        private int promoting;
        private int willSelectToLibrary;
        private int willDisplay;
        private int willHideByUpstream;
        private int willHideByLocalRejected;
        private int willHideByLocalPaused;
        private int unchanged;

        private LibraryRepairAccumulator(String activityId, boolean dryRun) {
            this.activityId = activityId;
            this.dryRun = dryRun;
        }

        private void accept(ProductSnapshot snapshot, ProductOperationState state, LibraryRepairDecision decision) {
            scanned++;
            if (snapshot != null && Integer.valueOf(PROMOTING_STATUS).equals(snapshot.getStatus())) {
                promoting++;
            }
            if (!Boolean.TRUE.equals(decision.oldSelectedToLibrary())
                    && Boolean.TRUE.equals(decision.newSelectedToLibrary())) {
                willSelectToLibrary++;
            }
            if (decision.willDisplay()) {
                willDisplay++;
            }
            if (HIDDEN_REASON_UPSTREAM_NOT_PROMOTING.equals(decision.newHiddenReason())
                    || HIDDEN_REASON_ACTIVITY_EXPIRED.equals(decision.newHiddenReason())) {
                willHideByUpstream++;
            }
            if (HIDDEN_REASON_LOCAL_REJECTED.equals(decision.newHiddenReason())) {
                willHideByLocalRejected++;
            }
            if (HIDDEN_REASON_LOCAL_PAUSED.equals(decision.newHiddenReason())) {
                willHideByLocalPaused++;
            }
            if (!decision.changed()) {
                unchanged++;
                return;
            }
            items.add(new LibraryRepairItem(
                    snapshot == null ? null : snapshot.getActivityId(),
                    snapshot == null ? null : snapshot.getProductId(),
                    decision.oldSelectedToLibrary(),
                    decision.newSelectedToLibrary(),
                    decision.oldDisplayStatus(),
                    decision.newDisplayStatus(),
                    decision.oldHiddenReason(),
                    decision.newHiddenReason(),
                    decision.oldAuditStatus(),
                    decision.newAuditStatus(),
                    decision.oldBizStatus(),
                    decision.newBizStatus(),
                    decision.reason()));
        }

        private LibraryRepairResult toResult() {
            return new LibraryRepairResult(
                    activityId,
                    dryRun,
                    scanned,
                    promoting,
                    willSelectToLibrary,
                    willDisplay,
                    willHideByUpstream,
                    willHideByLocalRejected,
                    willHideByLocalPaused,
                    unchanged,
                    List.copyOf(items));
        }
    }

    private record DisplayRuleActivityStats(long displaying, long hidden) {
    }

    record DisplayCandidate(
            ProductOperationState state,
            boolean supportsAds,
            BigDecimal commissionRatio,
            BigDecimal serviceFeeRatio,
            LocalDateTime shelfTime) {
    }
}
