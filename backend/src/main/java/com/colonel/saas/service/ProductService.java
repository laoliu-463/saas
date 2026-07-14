package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.domain.product.application.dto.ActivityProductRefreshRequest;
import com.colonel.saas.domain.product.application.ProductLibraryApplicationService;
import com.colonel.saas.domain.product.application.port.CopyPromotionSupportPort;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.product.policy.ProductPinPolicy;
import com.colonel.saas.dto.product.ProductFilterOptionItem;
import com.colonel.saas.dto.product.ProductFilterOptionsDTO;
import com.colonel.saas.common.enums.TalentFollowStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.order.facade.PromotionLinkRecordFacade;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 商品核心业务服务（商品域，DDD-PRODUCT-001 评估中）。
 *
 * <ul>
 *   <li>商品快照同步：从抖音活动 API 拉取并落库商品快照，支持全量刷新和增量更新</li>
 *   <li>精选库管理：商品入库、分类查询、条件筛选、状态标记（展示/隐藏/待审核）</li>
 *   <li>推广链路：生成推广链接（含幂等保障）、构建推广素材包、关联订单汇总</li>
 *   <li>审核流程：待审 -> 通过/驳回，支持分配审核人、记录审核决策、补充审核信息</li>
 *   <li>达人跟品：创建跟品记录，跟踪达人履约进度</li>
 *   <li>活动商品视图：构建活动商品列表视图，包含 SKU、佣金、服务费、标签等聚合信息</li>
 *   <li>数据范围过滤：通过 {@link ProductOperationState} 与 {@link ProductBizStatusService} 实现业务状态驱动的查询</li>
 * </ul>
 *
 * <p>架构角色：商品域聚合根服务，协调快照、运营状态、推广链接、订单、达人等多个子领域。
 * 依赖 MyBatis-Plus 进行持久化，通过 {@link DouyinActivityGateway} 与抖店开放平台交互。</p>
 *
 * <p><b>DDD 切片状态（DDD-PRODUCT-001 Slice 3 收尾）：</b>
 * 本服务是商品域的"god service"——6370 行 / 60 public method，内部 helper 高度耦合
 * （{@code toLegacyProduct} 调 10+ private helper；{@code collectSelectedLibraryProducts} /
 * {@code sortSelectedLibraryProducts} / {@code tryGetSelectedLibraryDbPage} 共享多状态
 * 过滤逻辑）。</p>
 *
 * <p><b>已切出的方法（DDD-PRODUCT-001 Slice 1+2）：</b>
 * <ul>
 *   <li>{@link #listLibraryCategories} → {@code ProductLibraryApplicationService}（Slice 1）</li>
 *   <li>{@link #getAdminCounts} → {@code ProductLibraryApplicationService}（Slice 2）</li>
 *   <li>{@link #getSelectedLibraryPage} / {@link #getSelectedLibraryCursorPage} →
 *       {@code ProductLibraryApplicationService} → {@code ProductLibraryQueryPort}；本类暂作为 Legacy 查询适配实现</li>
 * </ul>
 *
 * <p><b>不再逐方法切片的理由：</b>
 * <ol>
 *   <li>剩余 58 个方法中，多数依赖 {@code toLegacyProduct} / {@code collectSelectedLibraryProducts} /
 *       {@code sortSelectedLibraryProducts} / {@code tryGetSelectedLibraryDbPage} 等内部
 *       高度耦合 helper，搬到 Application 会破坏封装（helper 也得搬，导致"搬一个 method 搬半个 class"）</li>
 *   <li>{@code ProductController} 的商品库分页已经通过 {@code ProductLibraryPageQueryService}
 *       进入应用层；当前查询端口仍由 Legacy 适配器提供实现，可在不改 HTTP 契约的情况下继续替换</li>
 *   <li>{@code ProductDomainFacade} / {@code LegacyProductDomainFacade} 已存在但
 *       只被 OrderService / ProductQuickSampleService 跨域调用，未接管 Controller 主路径</li>
 * </ol>
 *
 * <p><b>推荐后续路径：</b>
 * 继续按查询端口、视图物化和复杂筛选边界逐步替换 Legacy 适配器；当前阶段对其余
 * 商品命令和活动同步方法维持现状。</p>
 *
 * @see ProductBizStatusService 商品业务状态计算
 * @see ProductDisplayRuleService 商品展示规则
 * @see PickSourceMappingService 货源映射（pick_source）
 * @see TalentFollowService 达人跟品服务
 * @see PromotionLinkIdempotencyService 推广链接幂等
 */
@Slf4j
@Service
public class ProductService implements CopyPromotionSupportPort {

    /** Jackson 全局序列化/反序列化器，用于审核补充信息等 JSON 字段处理 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** 精选库批量查询每次拉取的上限条数 */
    private static final long SELECTED_LIBRARY_BATCH_SIZE = 200L;
    /** 商品库无限下拉单批最大返回条数。 */
    private static final long SELECTED_LIBRARY_CURSOR_MAX_LIMIT = 500L;
    /** 商品库 cursor 中用于模拟 NULLS LAST 的最小时间。 */
    private static final LocalDateTime SELECTED_LIBRARY_CURSOR_MIN_TIME = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final int SELECTED_LIBRARY_CURSOR_VERSION = 1;
    /** 上游商品状态：推广中。 */
    private static final int UPSTREAM_PRODUCT_STATUS_PROMOTING = 1;
    /** 审核入库去重窗口：近 3 个月内同商品 ID 不允许重复进入商品库。 */
    private static final int AUDIT_LIBRARY_DUPLICATE_WINDOW_MONTHS = 3;
    private static final Set<String> EDITABLE_AUDIT_SUPPLEMENT_KEYS = Set.of(
            "exclusivePriceAmount",
            "exclusivePriceRemark",
            "supportsAds",
            "rewardRemark",
            "participationRequirements");
    /** 上游推广中商品自动进入商品库时写入的审核备注。 */
    private static final String AUTO_APPROVE_PROMOTING_REMARK = "上游状态为推广中，系统自动入库展示";
    /** 抖店团长 buyin 通常为 17–20 位；捕获组上限 30 位，并在数字后截断避免粘连字段。 */
    private static final Pattern BUYIN_ID_PATTERN = Pattern.compile(
            "(?:(?:origin_colonel_buyin_id|originColonelBuyinId|colonel_buyin_id|colonelBuyinId)\\s*[=:]\\s*['\\\"]?"
                    + "|[?&](?:origin_colonel_buyin_id|originColonelBuyinId|colonel_buyin_id|colonelBuyinId)=)"
                    + "([0-9]{10,30})(?![0-9])",
            Pattern.CASE_INSENSITIVE);
    public static final String FALLBACK_REASON_REAL_PROMOTION_WRITE_DISABLED = "REAL_PROMOTION_WRITE_DISABLED";

    /** 商品域转链端口，隔离 legacy 抖音推广网关（DDD-PRODUCT-004） */
    private final DouyinConvertPort douyinConvertPort;
    /** 抖音商品网关，用于查询商品详情、SKU 信息 */
    private final DouyinProductGateway douyinProductGateway;
    /** 商品快照持久层，存储从抖音同步的商品基础数据 */
    private final ProductSnapshotMapper snapshotMapper;
    /** 商品运营状态持久层，记录选库、展示、分配等运营状态 */
    private final ProductOperationStateMapper operationStateMapper;
    /** 商品操作日志持久层，记录审核、分配、决策等操作审计 */
    private final ProductOperationLogMapper operationLogMapper;
    /** 推广链接事实门面，存储和读取生成的推广链接及短链 */
    private final PromotionLinkRecordFacade promotionLinkRecordFacade;
    /** 订单域只读门面，用于活动商品订单摘要与团长商品范围。 */
    private final OrderReadFacade orderReadFacade;
    /** 商户持久层，用于查询商家名称等信息 */
    private final MerchantMapper merchantMapper;
    /** 系统用户门面，用于查询操作人姓名（分配人、审核人等） */
    private final UserDomainFacade userDomainFacade;
    /** 货源映射服务，管理 pick_source 与推广链接的映射关系 */
    private final PickSourceMappingService pickSourceMappingService;
    /** 商品业务状态计算服务，根据多维度状态计算商品当前业务阶段 */
    private final ProductBizStatusService productBizStatusService;
    /** 团长结算活动持久层，查询活动元数据（佣金、有效期等） */
    private final ColonelsettlementActivityMapper colonelActivityMapper;
    /** 达人跟品服务，管理达人对商品的跟品记录 */
    private final TalentFollowService talentFollowService;
    /** 抖音活动网关，查询活动详情、SKU 列表 */
    private final DouyinActivityGateway douyinActivityGateway;
    /** 推广链接幂等服务，防止重复生成推广链接 */
    private final PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    /** 配置域门面，读取推广模板与 pick_extra 规则（DDD-CONFIG-003） */
    private final com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    /** 商品展示规则服务，管理商品的展示/隐藏规则和置顶逻辑 */
    private final ProductDisplayRuleService productDisplayRuleService;
    /** 团长合作伙伴同步服务，同步团长合作关系数据 */
    private final ColonelPartnerSyncService colonelPartnerSyncService;
    /** 商品领域事件发布器，发布商品状态变更等事件 */
    private final ProductDomainEventPublisher productDomainEventPublisher;
    /** 商品库展示优先级策略（DDD-PRODUCT-002，不含置顶） */
    private final ProductDisplayPolicy productDisplayPolicy;
    private final ProductLibraryApplicationService productLibraryApplicationService;
    /** 活动商品列表 Redis 短 TTL 缓存；单元测试未注入时自动回退 DB 直查。 */
    private ActivityProductRedisCacheService activityProductRedisCacheService;
    /** 活动商品同步写入协调器；Spring 运行时启用，手工构造的旧单测保留原调用兼容。 */
    private ProductActivitySyncWriteCoordinator activitySyncWriteCoordinator;
    /** 同一活动的分页写入与展示状态刷新必须串行，避免状态分区并发更新相同商品。 */
    private final Map<String, Object> activityPageRefreshLocks = new ConcurrentHashMap<>();
    @Value("${douyin.real.promotion-write-enabled:false}")
    private boolean realPromotionWriteEnabled;
    @Value("${douyin.real.allow-promotion-write:false}")
    private boolean allowRealPromotionWrite;
    @Value("${ddd.refactor.enabled:false}")
    private boolean dddRefactorEnabled;
    @Value("${ddd.refactor.product-display-policy.enabled:false}")
    private boolean dddProductDisplayPolicyEnabled;
    @Value("${product.activity.sync.page-interval-ms:500}")
    private long productActivitySyncPageIntervalMs;
    @Value("${product.activity.sync.max-retries:3}")
    private int productActivitySyncMaxRetries;
    @Value("${product.sync.activityProduct.pageSize:20}")
    private int productSyncActivityProductPageSize;
    @Value("${product.sync.activityProduct.maxPagesPerActivity:1000}")
    private int productSyncActivityProductMaxPagesPerActivity;
    @Value("${product.sync.activityProduct.maxRowsPerActivity:50000}")
    private int productSyncActivityProductMaxRowsPerActivity;
    /** 上游每页落库后是否立即刷新商品库展示状态。生产默认开启；手工构造的单测保持旧编排。 */
    @Value("${product.sync.activityProduct.page-library-refresh-enabled:true}")
    private boolean pageLibraryRefreshEnabled;

    @Autowired
    public ProductService(
            DouyinConvertPort douyinConvertPort,
            DouyinProductGateway douyinProductGateway,
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper,
            PromotionLinkRecordFacade promotionLinkRecordFacade,
            OrderReadFacade orderReadFacade,
            MerchantMapper merchantMapper,
            UserDomainFacade userDomainFacade,
            PickSourceMappingService pickSourceMappingService,
            ProductBizStatusService productBizStatusService,
            ColonelsettlementActivityMapper colonelActivityMapper,
            TalentFollowService talentFollowService,
            DouyinActivityGateway douyinActivityGateway,
            PromotionLinkIdempotencyService promotionLinkIdempotencyService,
            com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade,
            ProductDisplayRuleService productDisplayRuleService,
            ColonelPartnerSyncService colonelPartnerSyncService,
            ProductDomainEventPublisher productDomainEventPublisher,
            ProductDisplayPolicy productDisplayPolicy,
            @org.springframework.context.annotation.Lazy ProductLibraryApplicationService productLibraryApplicationService) {
        this.douyinConvertPort = douyinConvertPort;
        this.douyinProductGateway = douyinProductGateway;
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
        this.promotionLinkRecordFacade = promotionLinkRecordFacade;
        this.orderReadFacade = orderReadFacade;
        this.merchantMapper = merchantMapper;
        this.userDomainFacade = userDomainFacade;
        this.pickSourceMappingService = pickSourceMappingService;
        this.productBizStatusService = productBizStatusService;
        this.colonelActivityMapper = colonelActivityMapper;
        this.talentFollowService = talentFollowService;
        this.douyinActivityGateway = douyinActivityGateway;
        this.promotionLinkIdempotencyService = promotionLinkIdempotencyService;
        this.configDomainFacade = configDomainFacade;
        this.productDisplayRuleService = productDisplayRuleService;
        this.colonelPartnerSyncService = colonelPartnerSyncService;
        this.productDomainEventPublisher = productDomainEventPublisher;
        this.productDisplayPolicy = productDisplayPolicy;
        this.productLibraryApplicationService = productLibraryApplicationService;
    }

    @Autowired(required = false)
    void setActivityProductRedisCacheService(ActivityProductRedisCacheService activityProductRedisCacheService) {
        this.activityProductRedisCacheService = activityProductRedisCacheService;
    }

    @Autowired(required = false)
    void setActivitySyncWriteCoordinator(ProductActivitySyncWriteCoordinator activitySyncWriteCoordinator) {
        this.activitySyncWriteCoordinator = activitySyncWriteCoordinator;
    }

    public IPage<Product> getPage(long page, long size, Integer status) {
        return getPage(page, size, status, null);
    }

    public IPage<Product> getPage(long page, long size, Integer status, UUID assigneeId) {
        if (assigneeId != null) {
            return getAssignedPickPage(page, size, assigneeId);
        }
        Page<ProductSnapshot> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        if (status != null) {
            wrapper.eq(ProductSnapshot::getStatus, status);
        }
        IPage<ProductSnapshot> snapshotPage = snapshotMapper.selectPage(query, wrapper);
        List<ProductSnapshot> snapshots = snapshotPage.getRecords();
        Map<String, ProductOperationState> stateMap = loadOperationStatesForSnapshots(snapshots);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        Page<Product> result = new Page<>(snapshotPage.getCurrent(), snapshotPage.getSize());
        result.setTotal(snapshotPage.getTotal());
        result.setRecords(snapshots.stream()
                .map(snapshot -> toLegacyProduct(
                        snapshot,
                        stateMap.get(stateBatchKey(snapshot.getActivityId(), snapshot.getProductId())),
                        assigneeNameMap))
                .toList());
        return result;
    }

    private IPage<Product> getAssignedPickPage(long page, long size, UUID assigneeId) {
        Page<ProductOperationState> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<ProductOperationState> statePage = operationStateMapper.selectPage(
                query,
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getAssigneeId, assigneeId)
                        .orderByDesc(ProductOperationState::getUpdateTime)
                        .orderByDesc(ProductOperationState::getCreateTime)
        );
        List<ProductOperationState> states = statePage.getRecords();
        Map<String, ProductSnapshot> snapshotMap = loadSnapshotsForStateBatch(states);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(Set.of(assigneeId));

        Page<Product> result = new Page<>(statePage.getCurrent(), statePage.getSize());
        result.setTotal(statePage.getTotal());
        result.setRecords(states.stream()
                .map(state -> {
                    ProductSnapshot snapshot = snapshotMap.get(stateBatchKey(state.getActivityId(), state.getProductId()));
                    return snapshot == null ? null : toLegacyProduct(snapshot, state, assigneeNameMap);
                })
                .filter(java.util.Objects::nonNull)
                .toList());
        return result;
    }

    public IPage<Product> getSelectedLibraryPage(long page, long size, String keyword, Integer status) {
        return getSelectedLibraryPage(page, size, SelectedLibraryFilter.of(keyword, status));
    }

    public IPage<Product> getSelectedLibraryPage(long page, long size, SelectedLibraryFilter filter) {
        SelectedLibraryFilter rawFilter = filter == null ? SelectedLibraryFilter.empty() : filter;
        SelectedLibraryFilter safeFilter = rawFilter.normalized(productDisplayPolicy);
        long currentPage = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        if (StringUtils.hasText(safeFilter.assigneeId()) && parseAssigneeFilterId(safeFilter.assigneeId()) == null) {
            return emptySelectedLibraryPage(currentPage, pageSize);
        }

        IPage<Product> dbPage = tryGetSelectedLibraryDbPage(currentPage, pageSize, safeFilter);
        if (dbPage != null) {
            return dbPage;
        }

        List<Product> allMatched = collectSelectedLibraryProducts(safeFilter);
        sortSelectedLibraryProducts(allMatched, safeFilter.sortBy());

        long total = allMatched.size();
        int fromIndex = (int) Math.min((currentPage - 1) * pageSize, total);
        int toIndex = (int) Math.min(fromIndex + pageSize, total);
        List<Product> pageRecords = fromIndex < toIndex
                ? new ArrayList<>(allMatched.subList(fromIndex, toIndex))
                : List.of();

        Page<Product> result = new Page<>(currentPage, pageSize, total);
        result.setRecords(pageRecords);
        return result;
    }

    public SelectedLibraryCursorPage getSelectedLibraryCursorPage(String cursor, long limit, SelectedLibraryFilter filter) {
        SelectedLibraryFilter rawFilter = filter == null ? SelectedLibraryFilter.empty() : filter;
        SelectedLibraryFilter safeFilter = rawFilter.normalized(productDisplayPolicy);
        long pageSize = normalizeSelectedLibraryCursorLimit(limit);
        if (StringUtils.hasText(safeFilter.assigneeId()) && parseAssigneeFilterId(safeFilter.assigneeId()) == null) {
            return SelectedLibraryCursorPage.empty(pageSize);
        }
        if (!canUseSelectedLibraryDbPage(safeFilter)) {
            return null;
        }

        SelectedLibraryCursor decodedCursor = decodeSelectedLibraryCursor(cursor);
        LocalDateTime snapshotTime = decodedCursor == null ? LocalDateTime.now() : decodedCursor.snapshotTime();
        UUID assigneeId = parseAssigneeFilterId(safeFilter.assigneeId());
        List<String> categoryTokens = selectedLibraryCategoryTokens(safeFilter);
        List<ProductSnapshot> snapshots = snapshotMapper.selectSelectedLibraryCursorPage(
                safeFilter.keyword(),
                safeFilter.status(),
                safeFilter.shopKeyword(),
                categoryTokens,
                safeFilter.activityId(),
                assigneeId,
                safeFilter.promotionLink(),
                safeFilter.allianceStatus(),
                safeFilter.assignee(),
                safeFilter.partnerId(),
                safeFilter.partnerType(),
                safeFilter.published(),
                safeFilter.cooperationType(),
                safeFilter.recruitActivityId(),
                safeFilter.listed(),
                safeFilter.productId(),
                decodedCursor == null ? null : decodedCursor.pinnedRank(),
                decodedCursor == null ? null : decodedCursor.promotionStartTime(),
                decodedCursor == null ? null : decodedCursor.syncTime(),
                decodedCursor == null ? null : decodedCursor.selectedAt(),
                decodedCursor == null ? null : decodedCursor.activityId(),
                decodedCursor == null ? null : decodedCursor.productId(),
                pageSize + 1,
                snapshotTime);
        if (snapshots == null || snapshots.isEmpty()) {
            return SelectedLibraryCursorPage.empty(pageSize);
        }

        boolean hasMore = snapshots.size() > pageSize;
        List<ProductSnapshot> pageSnapshots = hasMore
                ? new ArrayList<>(snapshots.subList(0, (int) pageSize))
                : new ArrayList<>(snapshots);
        Map<String, ProductOperationState> stateMap = loadOperationStatesForSnapshots(pageSnapshots);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<String, String> activityNameMap = loadActivityNameMap(pageSnapshots.stream()
                .map(ProductSnapshot::getActivityId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<Product> records = pageSnapshots.stream()
                .map(snapshot -> {
                    ProductOperationState state = stateMap.get(stateBatchKey(snapshot.getActivityId(), snapshot.getProductId()));
                    if (state == null) {
                        return null;
                    }
                    Product product = toLegacyProduct(snapshot, state, assigneeNameMap, activityNameMap);
                    return matchesSelectedLibraryFilters(product, snapshot, state, safeFilter) ? product : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        String nextCursor = hasMore && !pageSnapshots.isEmpty()
                ? encodeSelectedLibraryCursor(pageSnapshots.get(pageSnapshots.size() - 1), stateMap, snapshotTime)
                : null;
        return new SelectedLibraryCursorPage(records, pageSize, hasMore, nextCursor);
    }

    private long normalizeSelectedLibraryCursorLimit(long limit) {
        if (limit <= 0) {
            return SELECTED_LIBRARY_CURSOR_MAX_LIMIT;
        }
        return Math.min(limit, SELECTED_LIBRARY_CURSOR_MAX_LIMIT);
    }

    private SelectedLibraryCursor decodeSelectedLibraryCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor.trim());
            Map<String, Object> payload = OBJECT_MAPPER.readValue(decoded, new TypeReference<Map<String, Object>>() {});
            Number version = readCursorNumber(payload, "v");
            if (version == null || version.intValue() != SELECTED_LIBRARY_CURSOR_VERSION) {
                throw BusinessException.param("商品库游标版本无效");
            }
            return new SelectedLibraryCursor(
                    readCursorTime(payload, "snapshotTime"),
                    readCursorNumber(payload, "pinnedRank").intValue(),
                    readCursorTime(payload, "promotionStartTime"),
                    readCursorTime(payload, "syncTime"),
                    readCursorTime(payload, "selectedAt"),
                    readCursorText(payload, "activityId"),
                    readCursorText(payload, "productId"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.param("商品库游标无效", ex);
        }
    }

    private String encodeSelectedLibraryCursor(
            ProductSnapshot snapshot,
            Map<String, ProductOperationState> stateMap,
            LocalDateTime snapshotTime) {
        ProductOperationState state = stateMap.get(stateBatchKey(snapshot.getActivityId(), snapshot.getProductId()));
        if (state == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", SELECTED_LIBRARY_CURSOR_VERSION);
        payload.put("snapshotTime", snapshotTime.toString());
        payload.put("pinnedRank", selectedLibraryPinnedRank(state, snapshotTime));
        payload.put("promotionStartTime", cursorSortTime(parseDateTime(snapshot.getPromotionStartTime())).toString());
        payload.put("syncTime", cursorSortTime(snapshot.getSyncTime()).toString());
        payload.put("selectedAt", cursorSortTime(state.getSelectedAt()).toString());
        payload.put("activityId", snapshot.getActivityId());
        payload.put("productId", snapshot.getProductId());
        try {
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw BusinessException.param("商品库游标生成失败", ex);
        }
    }

    private int selectedLibraryPinnedRank(ProductOperationState state, LocalDateTime snapshotTime) {
        return state != null && state.getPinnedUntil() != null && state.getPinnedUntil().isAfter(snapshotTime) ? 0 : 1;
    }

    private LocalDateTime cursorSortTime(LocalDateTime value) {
        return value == null ? SELECTED_LIBRARY_CURSOR_MIN_TIME : value;
    }

    private Number readCursorNumber(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                throw BusinessException.param("商品库游标字段无效：" + field, ex);
            }
        }
        throw BusinessException.param("商品库游标缺少字段：" + field);
    }

    private LocalDateTime readCursorTime(Map<String, Object> payload, String field) {
        String value = readCursorText(payload, field);
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw BusinessException.param("商品库游标时间字段无效：" + field, ex);
        }
    }

    private String readCursorText(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw BusinessException.param("商品库游标缺少字段：" + field);
    }

    private IPage<Product> tryGetSelectedLibraryDbPage(long currentPage, long pageSize, SelectedLibraryFilter safeFilter) {
        if (!canUseSelectedLibraryDbPage(safeFilter)) {
            return null;
        }
        long offset = Math.max(0L, (currentPage - 1L) * pageSize);
        UUID assigneeId = parseAssigneeFilterId(safeFilter.assigneeId());
        List<String> categoryTokens = selectedLibraryCategoryTokens(safeFilter);
        LocalDateTime now = LocalDateTime.now();
        List<ProductSnapshot> snapshots = snapshotMapper.selectSelectedLibraryPage(
                safeFilter.keyword(),
                safeFilter.status(),
                safeFilter.shopKeyword(),
                categoryTokens,
                safeFilter.activityId(),
                assigneeId,
                safeFilter.promotionLink(),
                safeFilter.allianceStatus(),
                safeFilter.assignee(),
                safeFilter.partnerId(),
                safeFilter.partnerType(),
                safeFilter.published(),
                safeFilter.cooperationType(),
                safeFilter.recruitActivityId(),
                safeFilter.listed(),
                safeFilter.productId(),
                pageSize,
                offset,
                now);
        if (snapshots == null) {
            return null;
        }

        long total = snapshotMapper.countSelectedLibraryPage(
                safeFilter.keyword(),
                safeFilter.status(),
                safeFilter.shopKeyword(),
                categoryTokens,
                safeFilter.activityId(),
                assigneeId,
                safeFilter.promotionLink(),
                safeFilter.allianceStatus(),
                safeFilter.assignee(),
                safeFilter.partnerId(),
                safeFilter.partnerType(),
                safeFilter.published(),
                safeFilter.cooperationType(),
                safeFilter.recruitActivityId(),
                safeFilter.listed(),
                safeFilter.productId());
        if (snapshots.isEmpty() && total == 0L) {
            return emptySelectedLibraryPage(currentPage, pageSize);
        }

        Map<String, ProductOperationState> stateMap = loadOperationStatesForSnapshots(snapshots);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<String, String> activityNameMap = loadActivityNameMap(snapshots.stream()
                .map(ProductSnapshot::getActivityId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<Product> records = snapshots.stream()
                .map(snapshot -> {
                    ProductOperationState state = stateMap.get(stateBatchKey(snapshot.getActivityId(), snapshot.getProductId()));
                    if (state == null) {
                        return null;
                    }
                    Product product = toLegacyProduct(snapshot, state, assigneeNameMap, activityNameMap);
                    return matchesSelectedLibraryFilters(product, snapshot, state, safeFilter) ? product : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        Page<Product> result = new Page<>(currentPage, pageSize, total);
        result.setRecords(records);
        return result;
    }

    private boolean canUseSelectedLibraryDbPage(SelectedLibraryFilter filter) {
        if (filter == null) {
            return true;
        }
        if ("COLONEL".equals(filter.partnerType())) {
            return false;
        }
        return !StringUtils.hasText(filter.serviceFee())
                && !StringUtils.hasText(filter.supportsAds())
                && !StringUtils.hasText(filter.salesRange())
                && !StringUtils.hasText(filter.commission())
                && !StringUtils.hasText(filter.hasSample())
                && !StringUtils.hasText(filter.systemTag())
                && !StringUtils.hasText(filter.decision())
                && !StringUtils.hasText(filter.goodsTags())
                && !StringUtils.hasText(filter.productTags())
                && !StringUtils.hasText(filter.colonelName())
                && !StringUtils.hasText(filter.livePriceMin())
                && !StringUtils.hasText(filter.livePriceMax())
                && !StringUtils.hasText(filter.commissionMin())
                && !StringUtils.hasText(filter.commissionMax())
                && !StringUtils.hasText(filter.sampleSalesMin())
                && !StringUtils.hasText(filter.sampleSalesMax())
                && !StringUtils.hasText(filter.materialDownload())
                && !StringUtils.hasText(filter.exclusivePrice())
                && !StringUtils.hasText(filter.productChain())
                && !StringUtils.hasText(filter.handCard())
                && !StringUtils.hasText(filter.doubleCommission())
                && !StringUtils.hasText(filter.notInLibrary())
                && !StringUtils.hasText(filter.dedup())
                && !StringUtils.hasText(filter.recruitActivityName())
                && !StringUtils.hasText(filter.freeSample());
    }

    private List<String> selectedLibraryCategoryTokens(SelectedLibraryFilter filter) {
        List<String> tokens = parseCsvTokens(filter.categories());
        if (tokens.isEmpty() && StringUtils.hasText(filter.categoryName())) {
            return List.of(filter.categoryName().trim());
        }
        return tokens;
    }

    private List<Product> collectSelectedLibraryProducts(SelectedLibraryFilter safeFilter) {
        List<Product> matched = new ArrayList<>();
        Set<String> colonelPartnerProductIds = resolveColonelPartnerProductScope(safeFilter);
        Set<String> colonelNameProductIds = resolveColonelNameProductScope(safeFilter);

        long statePageNo = 1L;
        boolean hasMore = true;
        while (hasMore) {
            Page<ProductOperationState> requestPage = new Page<>(statePageNo, SELECTED_LIBRARY_BATCH_SIZE);
            LambdaQueryWrapper<ProductOperationState> stateQuery = new LambdaQueryWrapper<ProductOperationState>()
                    .eq(ProductOperationState::getSelectedToLibrary, true)
                    .eq(ProductOperationState::getDisplayStatus, ProductDisplayStatus.DISPLAYING.name())
                    .and(w -> w.isNull(ProductOperationState::getAuditStatus)
                            .or()
                            .ne(ProductOperationState::getAuditStatus, 3))
                    .and(w -> w.isNull(ProductOperationState::getManualDisabled)
                            .or()
                            .eq(ProductOperationState::getManualDisabled, false))
                    .eq(StringUtils.hasText(safeFilter.activityId()),
                            ProductOperationState::getActivityId,
                            safeFilter.activityId())
                    .eq(parseAssigneeFilterId(safeFilter.assigneeId()) != null,
                            ProductOperationState::getAssigneeId,
                            parseAssigneeFilterId(safeFilter.assigneeId()))
                    .orderByDesc(ProductOperationState::getSelectedAt)
                    .orderByDesc(ProductOperationState::getUpdateTime);
            Page<ProductOperationState> statePage = (Page<ProductOperationState>) operationStateMapper.selectPage(
                    requestPage,
                    stateQuery
            );
            List<ProductOperationState> stateBatch = statePage.getRecords();
            if (stateBatch == null || stateBatch.isEmpty()) {
                break;
            }
            Map<String, ProductSnapshot> snapshotMap = loadSnapshotsForStateBatch(stateBatch);
            Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateBatch.stream()
                    .map(ProductOperationState::getAssigneeId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            // [V1 必做] 一次性查活动名映射，避免在循环里逐条查 colonel_activity。
            // 活动 ID 集合是按当前分批 state 收集的，最坏情况 batchSize 条记录，1 次 SQL。
            Map<String, String> activityNameMap = loadActivityNameMap(stateBatch.stream()
                    .map(ProductOperationState::getActivityId)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            for (ProductOperationState state : stateBatch) {
                ProductSnapshot snapshot = snapshotMap.get(stateBatchKey(state.getActivityId(), state.getProductId()));
                if (snapshot == null) {
                    continue;
                }
                if (colonelPartnerProductIds != null && !colonelPartnerProductIds.contains(snapshot.getProductId())) {
                    continue;
                }
                if (colonelNameProductIds != null && !colonelNameProductIds.contains(snapshot.getProductId())) {
                    continue;
                }
                Product product = toLegacyProduct(snapshot, state, assigneeNameMap, activityNameMap);
                if (!matchesSelectedLibraryFilters(product, snapshot, state, safeFilter)) {
                    continue;
                }
                matched.add(product);
            }
            hasMore = statePage.getTotal() > statePageNo * SELECTED_LIBRARY_BATCH_SIZE;
            statePageNo++;
        }
        return matched;
    }

    private void sortSelectedLibraryProducts(List<Product> products, String sortBy) {
        if (products == null || products.size() <= 1) {
            return;
        }
        products.sort(this::compareLibraryProducts);
    }

    /**
     * 商品库默认展示优先级规则（优先级递减）：
     * 1. 置顶商品优先（24h内）
     * 2. 上游合作开始时间（promotionStartTime）更晚优先
     * 3. 同步时间更晚优先
     * 4. 入库时间更晚优先
     */
    private int compareLibraryProducts(Product left, Product right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        boolean leftPinned = isPinnedAndNotExpired(left);
        boolean rightPinned = isPinnedAndNotExpired(right);
        if (leftPinned != rightPinned) {
            return leftPinned ? -1 : 1;
        }

        int cooperationTime = compareDateTimeDesc(
                resolveLibraryCooperationStartTime(left),
                resolveLibraryCooperationStartTime(right));
        if (cooperationTime != 0) {
            return cooperationTime;
        }

        int syncTime = compareDateTimeDesc(left.getSyncTime(), right.getSyncTime());
        if (syncTime != 0) {
            return syncTime;
        }

        return compareDateTimeDesc(left.getSelectedAt(), right.getSelectedAt());
    }

    private int compareDateTimeDesc(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private LocalDateTime resolveLibraryCooperationStartTime(Product product) {
        if (product == null) {
            return null;
        }
        return parseDateTime(product.getPromotionStartTime());
    }

    private boolean isPinnedAndNotExpired(Product p) {
        return ProductPinPolicy.isPinnedForPresentation(
                Boolean.TRUE.equals(p.getPinned()),
                p.getPinnedUntil(),
                LocalDateTime.now());
    }

    public PageResult<Map<String, Object>> getPromotionLinkHistory(String productId, long page, long size) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setPage(currentPage);
        result.setSize(pageSize);
        if (!StringUtils.hasText(productId) || promotionLinkRecordFacade == null) {
            result.setTotal(0);
            result.setRecords(List.of());
            return result;
        }
        List<PromotionLink> links = promotionLinkRecordFacade.findByProductId(productId);
        if (links == null || links.isEmpty()) {
            result.setTotal(0);
            result.setRecords(List.of());
            return result;
        }
        long fromIndexLong = (currentPage - 1) * pageSize;
        if (fromIndexLong >= links.size()) {
            result.setTotal(links.size());
            result.setRecords(List.of());
            return result;
        }
        int fromIndex = Math.toIntExact(fromIndexLong);
        int toIndex = (int) Math.min(fromIndexLong + pageSize, links.size());
        result.setTotal(links.size());
        result.setRecords(links.subList(fromIndex, toIndex).stream()
                .map(this::toPromotionLinkHistoryItem)
                .toList());
        return result;
    }

    private Map<String, ProductSnapshot> loadSnapshotsForStateBatch(List<ProductOperationState> stateBatch) {
        if (stateBatch == null || stateBatch.isEmpty()) {
            return Map.of();
        }
        List<UUID> snapshotIds = stateBatch.stream()
                .filter(state -> StringUtils.hasText(state.getActivityId()) && StringUtils.hasText(state.getProductId()))
                .map(state -> buildSnapshotId(state.getActivityId(), state.getProductId()))
                .toList();
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ProductSnapshot> result = snapshotMapper.selectBatchIds(snapshotIds).stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getActivityId()) && StringUtils.hasText(snapshot.getProductId()))
                .collect(Collectors.toMap(
                        snapshot -> stateBatchKey(snapshot.getActivityId(), snapshot.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        // 回填 colonel_activity.months_of_protection（非持久化字段）
        Set<String> activityIds = result.values().stream()
                .map(ProductSnapshot::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (!activityIds.isEmpty()) {
            Map<String, Integer> protectionMap = new LinkedHashMap<>();
            for (String activityId : activityIds) {
                ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
                if (activity != null && activity.getMonthsOfProtection() != null) {
                    protectionMap.put(activityId, activity.getMonthsOfProtection());
                }
            }
            result.values().forEach(snapshot -> {
                Integer protection = protectionMap.get(snapshot.getActivityId());
                if (protection != null) {
                    snapshot.setMonthsOfProtection(protection);
                }
            });
        }
        return result;
    }

    private Map<String, ProductOperationState> loadOperationStatesForSnapshots(List<ProductSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }
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
                .filter(state -> StringUtils.hasText(state.getActivityId()) && StringUtils.hasText(state.getProductId()))
                .collect(Collectors.toMap(
                        state -> stateBatchKey(state.getActivityId(), state.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<UUID, String> loadUserDisplayNames(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> displayLabels = userDomainFacade.loadUserDisplayLabelsByIds(userIds);
        return displayLabels == null ? Map.of() : displayLabels;
    }

    /**
     * 批量加载活动 ID → 活动名映射（轻量查询，仅取 activity_id 和 activity_name）。
     * <p>用于商品库视图构造时回填 {@code Product.activityName}，避免对每条商品单独查库。
     * 上游 activityId 集合为空或全 null 时返回空 Map。</p>
     *
     * @param activityIds 抖店活动 ID 集合
     * @return activityId → activityName 映射（去重后按 LinkedHashMap 保序）
     */
    private Map<String, String> loadActivityNameMap(Collection<String> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> dedup = activityIds.stream()
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (dedup.isEmpty()) {
            return Map.of();
        }
        return colonelActivityMapper.selectNamesByActivityIds(new ArrayList<>(dedup)).stream()
                .filter(java.util.Objects::nonNull)
                .filter(activity -> activity.getActivityId() != null)
                .collect(Collectors.toMap(
                        ColonelsettlementActivity::getActivityId,
                        ColonelsettlementActivity::getName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String stateBatchKey(String activityId, String productId) {
        return activityId + "::" + productId;
    }

    private boolean matchesSelectedLibraryFilters(
            Product product,
            ProductSnapshot snapshot,
            ProductOperationState state,
            SelectedLibraryFilter filter) {
        if (product == null) {
            return false;
        }
        if (!productDisplayPolicy.matchesSelectedLibraryCoreVisibility(
                snapshot == null ? null : snapshot.getStatus(),
                state != null,
                state == null ? null : state.getAuditStatus(),
                state == null ? null : state.getBizStatus(),
                state == null ? null : state.getManualDisabled())) {
            return false;
        }
        if (StringUtils.hasText(filter.keyword())) {
            String trimmed = filter.keyword();
            boolean matchedByName = containsIgnoreCase(product.getName(), trimmed);
            boolean matchedByProductId = containsIgnoreCase(product.getProductId(), trimmed);
            boolean matchedByShop = containsIgnoreCase(snapshot.getShopName(), trimmed);
            if (!matchedByName && !matchedByProductId && !matchedByShop) {
                return false;
            }
        }
        if (StringUtils.hasText(filter.productId())
                && !filter.productId().trim().equals(product.getProductId())) {
            return false;
        }
        if (filter.status() != null && !java.util.Objects.equals(product.getStatus(), filter.status())) {
            return false;
        }
        if (StringUtils.hasText(filter.shopKeyword()) && !containsIgnoreCase(snapshot.getShopName(), filter.shopKeyword())) {
            return false;
        }
        if (!matchesCategoriesFilter(snapshot, filter.categories(), filter.categoryName())) {
            return false;
        }
        if (StringUtils.hasText(filter.activityId())
                && !filter.activityId().trim().equals(snapshot.getActivityId())) {
            return false;
        }
        if (StringUtils.hasText(filter.serviceFee()) && !matchesServiceFeeFilter(snapshot, filter.serviceFee())) {
            return false;
        }
        if (StringUtils.hasText(filter.supportsAds()) && !matchesSupportsAdsFilter(state, snapshot, filter.supportsAds())) {
            return false;
        }
        if (StringUtils.hasText(filter.salesRange()) && !matchesSalesRange(snapshot.getSales(), filter.salesRange())) {
            return false;
        }
        if (StringUtils.hasText(filter.promotionLink()) && !productDisplayPolicy.matchesSelectedLibraryPromotionLinkFilter(
                filter.promotionLink(),
                state == null ? null : state.getPromoteLink(),
                state == null ? null : state.getShortLink(),
                state == null ? null : state.getBizStatus())) {
            return false;
        }
        if (StringUtils.hasText(filter.allianceStatus()) && !productDisplayPolicy.matchesSelectedLibraryAllianceStatusFilter(
                filter.allianceStatus(),
                snapshot == null ? null : snapshot.getStatus(),
                snapshot == null ? null : snapshot.getStatusText())) {
            return false;
        }
        if (StringUtils.hasText(filter.commission()) && !matchesCommissionFilter(snapshot, filter.commission())) {
            return false;
        }
        if (StringUtils.hasText(filter.hasSample()) && !matchesHasSampleFilter(snapshot, filter.hasSample())) {
            return false;
        }
        if (StringUtils.hasText(filter.assigneeId()) && !matchesAssigneeIdFilter(state, filter.assigneeId())) {
            return false;
        }
        if (StringUtils.hasText(filter.assignee()) && !matchesAssigneeFilter(state, filter.assignee())) {
            return false;
        }
        if (StringUtils.hasText(filter.systemTag()) && !matchesSystemTagFilter(snapshot, filter.systemTag())) {
            return false;
        }
        if (StringUtils.hasText(filter.partnerId())
                && !"COLONEL".equals(filter.partnerType())
                && !matchesMerchantPartnerFilter(snapshot, filter.partnerId())) {
            return false;
        }
        if (!matchesAuditTagFilters(state, normalizeTagFilter(filter.goodsTags()), normalizeTagFilter(filter.productTags()))) {
            return false;
        }
        if (StringUtils.hasText(filter.colonelName()) && !StringUtils.hasText(snapshot.getProductId())) {
            return false;
        }
        if (StringUtils.hasText(filter.published()) && !productDisplayPolicy.matchesSelectedLibraryPublishedFilter(
                filter.published(),
                state == null ? null : state.getPromoteLink(),
                state == null ? null : state.getShortLink())) {
            return false;
        }
        if (StringUtils.hasText(filter.listed()) && !productDisplayPolicy.matchesSelectedLibraryListedFilter(
                filter.listed(),
                snapshot == null ? null : snapshot.getStatus())) {
            return false;
        }
        if (StringUtils.hasText(filter.freeSample()) && !matchesFreeSampleFilter(state, filter.freeSample())) {
            return false;
        }
        if (StringUtils.hasText(filter.cooperationType()) && !matchesCooperationTypeFilter(snapshot, filter.cooperationType())) {
            return false;
        }
        if (!matchesLivePriceRange(snapshot, filter.livePriceMin(), filter.livePriceMax())) {
            return false;
        }
        if (!matchesCommissionRange(snapshot, filter.commissionMin(), filter.commissionMax())) {
            return false;
        }
        if (!matchesSampleSalesRange(state, snapshot, filter.sampleSalesMin(), filter.sampleSalesMax())) {
            return false;
        }
        if (StringUtils.hasText(filter.materialDownload()) && !matchesMaterialDownloadFilter(state, filter.materialDownload())) {
            return false;
        }
        if (StringUtils.hasText(filter.exclusivePrice()) && !matchesExclusivePriceFilter(state, filter.exclusivePrice())) {
            return false;
        }
        if (StringUtils.hasText(filter.productChain()) && !matchesSupplementBooleanOrProductTag(state, List.of("productChainGroup", "productChain"), "商品链组", filter.productChain())) {
            return false;
        }
        if (StringUtils.hasText(filter.handCard()) && !matchesHandCardFilter(state, filter.handCard())) {
            return false;
        }
        if (StringUtils.hasText(filter.doubleCommission()) && !matchesSupplementBooleanOrProductTag(state, List.of("doubleCommission"), "双佣金", filter.doubleCommission())) {
            return false;
        }
        if (StringUtils.hasText(filter.notInLibrary()) && !matchesNotInLibraryFilter(state, filter.notInLibrary())) {
            return false;
        }
        if (StringUtils.hasText(filter.dedup()) && !matchesAuditCheckboxAny(state, List.of("dedupeSelection", "dedup"), filter.dedup())) {
            return false;
        }
        if (!matchesRecruitActivityFilter(snapshot, filter.recruitActivityId(), filter.recruitActivityName())) {
            return false;
        }
        return !StringUtils.hasText(filter.decision()) || matchesDecisionFilter(snapshot.getActivityId(), snapshot.getProductId(), filter.decision());
    }

    private Set<String> resolveColonelNameProductScope(SelectedLibraryFilter filter) {
        if (!StringUtils.hasText(filter.colonelName())) {
            return null;
        }
        return colonelPartnerSyncService.resolveProductIdsByColonelName(filter.colonelName());
    }

    private Set<String> resolveColonelPartnerProductScope(SelectedLibraryFilter filter) {
        if (!"COLONEL".equals(filter.partnerType()) || !StringUtils.hasText(filter.partnerId())) {
            return null;
        }
        Set<String> productIds = new LinkedHashSet<>(pickSourceMappingService.listProductIdsByColonelBuyinId(filter.partnerId()));
        Long buyinId = parseColonelBuyinId(filter.partnerId());
        if (buyinId == null) {
            return productIds;
        }
        productIds.addAll(orderReadFacade.findProductIdsByColonelBuyinId(buyinId));
        return productIds;
    }

    private Long parseColonelBuyinId(String colonelBuyinId) {
        if (!StringUtils.hasText(colonelBuyinId)) {
            return null;
        }
        try {
            return Long.parseLong(colonelBuyinId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean matchesMerchantPartnerFilter(ProductSnapshot snapshot, String partnerId) {
        if (snapshot.getShopId() != null && partnerId.equals(String.valueOf(snapshot.getShopId()))) {
            return true;
        }
        return StringUtils.hasText(snapshot.getShopName()) && partnerId.equalsIgnoreCase(snapshot.getShopName().trim());
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean isUpstreamPromoting(ProductSnapshot snapshot) {
        return snapshot != null && Integer.valueOf(UPSTREAM_PRODUCT_STATUS_PROMOTING).equals(snapshot.getStatus());
    }

    private boolean isUpstreamPromoting(DouyinProductGateway.ActivityProductItem item) {
        return item != null && Integer.valueOf(UPSTREAM_PRODUCT_STATUS_PROMOTING).equals(item.status());
    }

    private boolean matchesFreeSampleFilter(ProductOperationState state, String freeSample) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        boolean free = "FREE".equalsIgnoreCase(readString(supplement, "sampleType"))
                || Boolean.TRUE.equals(readBoolean(supplement, "sampleFree"))
                || Boolean.TRUE.equals(readBoolean(supplement, "sample_free"))
                || Boolean.TRUE.equals(readBoolean(supplement, "freeSample"));
        return "1".equals(freeSample) ? free : !free;
    }

    private boolean matchesCooperationTypeFilter(ProductSnapshot snapshot, String cooperationType) {
        if (snapshot.getCosType() != null && cooperationType.equals(String.valueOf(snapshot.getCosType()))) {
            return true;
        }
        return StringUtils.hasText(snapshot.getCosTypeText())
                && snapshot.getCosTypeText().toLowerCase(Locale.ROOT).contains(cooperationType.toLowerCase(Locale.ROOT));
    }

    private boolean matchesLivePriceRange(ProductSnapshot snapshot, String minRaw, String maxRaw) {
        BigDecimal min = parseMoneyFilter(minRaw);
        BigDecimal max = parseMoneyFilter(maxRaw);
        if (min == null && max == null) {
            return true;
        }
        BigDecimal price = snapshot.getPrice() == null
                ? parsePriceText(snapshot.getPriceText())
                : BigDecimal.valueOf(snapshot.getPrice()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (min != null && price.compareTo(min) < 0) {
            return false;
        }
        return max == null || price.compareTo(max) <= 0;
    }

    private boolean matchesCommissionRange(ProductSnapshot snapshot, String minRaw, String maxRaw) {
        BigDecimal min = parseRatioFilter(minRaw);
        BigDecimal max = parseRatioFilter(maxRaw);
        if (min == null && max == null) {
            return true;
        }
        BigDecimal rate = resolveCommissionRate(snapshot);
        if (min != null && rate.compareTo(min) < 0) {
            return false;
        }
        return max == null || rate.compareTo(max) <= 0;
    }

    private boolean matchesSampleSalesRange(ProductOperationState state, ProductSnapshot snapshot, String minRaw, String maxRaw) {
        Long min = parseLongFilter(minRaw);
        Long max = parseLongFilter(maxRaw);
        if (min == null && max == null) {
            return true;
        }
        long sales = resolveSampleSalesThreshold(state, snapshot);
        if (min != null && sales < min) {
            return false;
        }
        return max == null || sales <= max;
    }

    private long resolveSampleSalesThreshold(ProductOperationState state, ProductSnapshot snapshot) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        Long fromSupplement = readLong(supplement, "sampleSales", "sampleSales30d", "minSampleSales30d", "sample_sales");
        if (fromSupplement != null) {
            return fromSupplement;
        }
        return snapshot != null && snapshot.getSales() != null ? snapshot.getSales() : 0L;
    }

    private Boolean firstBoolean(Map<String, Object> supplement, String... keys) {
        if (supplement == null || supplement.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Boolean value = readBoolean(supplement, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean matchesAuditCheckbox(ProductOperationState state, String key, String expected) {
        return matchesAuditCheckboxAny(state, List.of(key), expected);
    }

    private boolean matchesAuditCheckboxAny(ProductOperationState state, List<String> keys, String expected) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        boolean enabled = keys.stream().anyMatch(key -> Boolean.TRUE.equals(readBoolean(supplement, key)));
        return "1".equals(expected) ? enabled : !enabled;
    }

    private boolean matchesMaterialDownloadFilter(ProductOperationState state, String expected) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        boolean enabled = Boolean.TRUE.equals(readBoolean(supplement, "materialDownloadAvailable"))
                || Boolean.TRUE.equals(readBoolean(supplement, "materialDownload"))
                || !readStringList(supplement, "materialFiles").isEmpty();
        return "1".equals(expected) ? enabled : !enabled;
    }

    private boolean matchesExclusivePriceFilter(ProductOperationState state, String expected) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        boolean enabled = readDecimal(supplement, "exclusivePriceAmount") != null
                || Boolean.TRUE.equals(readBoolean(supplement, "exclusivePrice"))
                || StringUtils.hasText(readString(supplement, "exclusivePriceRemark"))
                || readProductTags(state).contains("专属价");
        return "1".equals(expected) ? enabled : !enabled;
    }

    private boolean matchesHandCardFilter(ProductOperationState state, String expected) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        boolean enabled = Boolean.TRUE.equals(readBoolean(supplement, "handCardAvailable"))
                || Boolean.TRUE.equals(readBoolean(supplement, "handCard"))
                || !readStringList(supplement, "handCardFiles").isEmpty()
                || readProductTags(state).contains("手卡");
        return "1".equals(expected) ? enabled : !enabled;
    }

    private boolean matchesSupplementBooleanOrProductTag(
            ProductOperationState state,
            List<String> keys,
            String tag,
            String expected) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        boolean enabled = keys.stream().anyMatch(key -> Boolean.TRUE.equals(readBoolean(supplement, key)))
                || readProductTags(state).contains(tag);
        return "1".equals(expected) ? enabled : !enabled;
    }

    private boolean matchesProductTagCheckbox(ProductOperationState state, String tag, String expected) {
        List<String> tags = readProductTags(state);
        boolean matched = tags.contains(tag);
        return "1".equals(expected) ? matched : !matched;
    }

    private boolean matchesNotInLibraryFilter(ProductOperationState state, String expected) {
        if (state == null) {
            return "1".equals(expected); // null state means not in library
        }
        Map<String, Object> supplement = parseAuditPayload(state.getAuditPayload());
        Boolean notInPool = firstBoolean(supplement, "notInProductPool", "notInLibrary");
        if (notInPool != null) {
            return "1".equals(expected) ? notInPool : !notInPool;
        }
        boolean inLibrary = Boolean.TRUE.equals(state.getSelectedToLibrary());
        return "1".equals(expected) ? !inLibrary : inLibrary;
    }

    private boolean matchesRecruitActivityFilter(ProductSnapshot snapshot, String recruitActivityId, String recruitActivityName) {
        // recruitActivityId: exact match on activityId
        if (StringUtils.hasText(recruitActivityId)) {
            if (!StringUtils.hasText(snapshot.getActivityId()) || !snapshot.getActivityId().equals(recruitActivityId.trim())) {
                return false;
            }
        }
        // recruitActivityName: fuzzy match via ColonelSettlementActivity lookup (expensive, only when name is provided)
        if (StringUtils.hasText(recruitActivityName) && StringUtils.hasText(snapshot.getActivityId())) {
            ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(snapshot.getActivityId());
            if (activity == null || activity.getName() == null
                    || !activity.getName().toLowerCase(Locale.ROOT).contains(recruitActivityName.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private List<String> readProductTags(ProductOperationState state) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        Object raw = supplement.get("productTags");
        if (raw == null) {
            raw = supplement.get("product_tags");
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return raw == null ? List.of() : List.of(String.valueOf(raw));
    }

    private BigDecimal parseDecimalFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLongFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseMoneyFilter(String raw) {
        Long cents = parseLongFilter(raw);
        if (cents == null) {
            return null;
        }
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseRatioFilter(String raw) {
        Long value = parseLongFilter(raw);
        if (value == null) {
            return null;
        }
        return normalizeRatioNumber(value);
    }

    private BigDecimal parsePriceText(String priceText) {
        if (!StringUtils.hasText(priceText)) {
            return BigDecimal.ZERO;
        }
        String normalized = priceText.replaceAll("[^\\d.]", "");
        if (!StringUtils.hasText(normalized)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private boolean matchesSalesRange(Long sales, String salesRange) {
        long value = sales == null ? 0L : sales;
        return switch (salesRange) {
            case "lt100" -> value < 100L;
            case "100_999" -> value >= 100L && value < 1_000L;
            case "1k_29k" -> value >= 1_000L && value < 30_000L;
            case "gte30000" -> value >= 30_000L;
            default -> true;
        };
    }

    private void applyActivityProductPromotionStatusFilter(
            LambdaQueryWrapper<ProductSnapshot> wrapper,
            Integer promotionStatus) {
        Integer normalizedPromotionStatus = productDisplayPolicy.normalizeActivityProductFilterStatus(promotionStatus);
        if (isDddProductDisplayPolicyEnabled()) {
            List<Integer> statuses = productDisplayPolicy.activityProductFilterStatuses(normalizedPromotionStatus);
            if (statuses.isEmpty()) {
                if (normalizedPromotionStatus != null) {
                    wrapper.eq(ProductSnapshot::getStatus, Integer.MIN_VALUE);
                }
                return;
            }
            if (statuses.size() == 1) {
                wrapper.eq(ProductSnapshot::getStatus, statuses.get(0));
                return;
            }
            wrapper.in(ProductSnapshot::getStatus, statuses);
            return;
        }
        if (normalizedPromotionStatus == null) {
            wrapper.in(ProductSnapshot::getStatus, 0, 1, 2, 3, 4, 6);
            return;
        }
        if (!productDisplayPolicy.isSupportedActivityProductQueryStatus(normalizedPromotionStatus)) {
            wrapper.eq(ProductSnapshot::getStatus, Integer.MIN_VALUE);
            return;
        }
        if (Integer.valueOf(3).equals(normalizedPromotionStatus)) {
            wrapper.eq(ProductSnapshot::getStatus, 3);
            return;
        }
        wrapper.eq(ProductSnapshot::getStatus, normalizedPromotionStatus);
    }

    private boolean isDddProductDisplayPolicyEnabled() {
        return dddRefactorEnabled && dddProductDisplayPolicyEnabled;
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (containsIgnoreCase(value, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCommissionFilter(ProductSnapshot snapshot, String commission) {
        BigDecimal rate = resolveCommissionRate(snapshot);
        return switch (commission) {
            case "gt20" -> rate.compareTo(BigDecimal.valueOf(20)) >= 0;
            case "10_20" -> rate.compareTo(BigDecimal.TEN) >= 0 && rate.compareTo(BigDecimal.valueOf(20)) < 0;
            case "lt10" -> rate.compareTo(BigDecimal.TEN) < 0;
            default -> true;
        };
    }

    private boolean matchesHasSampleFilter(ProductSnapshot snapshot, String hasSample) {
        boolean hasRule = isSampleRuleAvailable(snapshot);
        return "1".equals(hasSample) ? hasRule : !"0".equals(hasSample) || !hasRule;
    }

    private boolean matchesAssigneeFilter(ProductOperationState state, String assignee) {
        boolean assigned = state != null && state.getAssigneeId() != null;
        return switch (assignee) {
            case "assigned" -> assigned;
            case "unassigned" -> !assigned;
            default -> true;
        };
    }

    private boolean matchesAssigneeIdFilter(ProductOperationState state, String assigneeId) {
        UUID expected = parseAssigneeFilterId(assigneeId);
        if (expected == null) {
            return false;
        }
        return state != null && expected.equals(state.getAssigneeId());
    }

    private UUID parseAssigneeFilterId(String assigneeId) {
        if (!StringUtils.hasText(assigneeId)) {
            return null;
        }
        try {
            return UUID.fromString(assigneeId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean matchesCategoriesFilter(ProductSnapshot snapshot, String categories, String legacyCategoryName) {
        List<String> tokens = parseCsvTokens(categories);
        if (tokens.isEmpty() && StringUtils.hasText(legacyCategoryName)) {
            tokens = List.of(legacyCategoryName.trim());
        }
        if (tokens.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(snapshot.getCategoryName())) {
            return false;
        }
        String categoryName = snapshot.getCategoryName().toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(token -> {
            String normalized = token.toLowerCase(Locale.ROOT);
            return categoryName.equals(normalized) || categoryName.contains(normalized);
        });
    }

    private boolean matchesServiceFeeFilter(ProductSnapshot snapshot, String serviceFee) {
        BigDecimal rate = resolveServiceFeeRate(snapshot);
        if (rate == null) {
            return false;
        }
        return switch (serviceFee) {
            case "gt20" -> rate.compareTo(BigDecimal.valueOf(20)) >= 0;
            case "10_20" -> rate.compareTo(BigDecimal.TEN) >= 0 && rate.compareTo(BigDecimal.valueOf(20)) < 0;
            case "lt10" -> rate.compareTo(BigDecimal.TEN) < 0;
            default -> true;
        };
    }

    private boolean matchesSupportsAdsFilter(ProductOperationState state, ProductSnapshot snapshot, String supportsAds) {
        boolean adsEnabled = supportsAdsEnabled(state, snapshot);
        return "1".equals(supportsAds) ? adsEnabled : !adsEnabled;
    }

    private boolean supportsAdsEnabled(ProductOperationState state, ProductSnapshot snapshot) {
        Map<String, Object> supplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        if (Boolean.TRUE.equals(readBoolean(supplement, "supportsAds"))) {
            return true;
        }
        if (state != null && (StringUtils.hasText(state.getPromoteLink()) || StringUtils.hasText(state.getShortLink()))) {
            return true;
        }
        return snapshot.getActivityAdCosRatio() != null && snapshot.getActivityAdCosRatio() > 0;
    }

    private List<String> parseCsvTokens(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public List<String> listLibraryCategories() {
        return productLibraryApplicationService.listLibraryCategories();
    }

    public AdminProductCounts getAdminCounts() {
        com.colonel.saas.domain.product.application.ProductLibraryApplicationService.AdminProductCounts app =
                productLibraryApplicationService.getAdminCounts();
        return new AdminProductCounts(
                app.snapshotTotal(),
                app.relationTotal(),
                app.distinctProductTotal(),
                app.displayingTotal(),
                app.pendingTotal(),
                app.hiddenTotal(),
                app.activityTotal());
    }

    public record AdminProductCounts(
            long snapshotTotal,
            long relationTotal,
            long distinctProductTotal,
            long displayingTotal,
            long pendingTotal,
            long hiddenTotal,
            long activityTotal) {
    }

    private Page<Product> emptySelectedLibraryPage(long currentPage, long pageSize) {
        Page<Product> result = new Page<>(currentPage, pageSize, 0L);
        result.setRecords(List.of());
        return result;
    }

    private boolean matchesSystemTagFilter(ProductSnapshot snapshot, String systemTag) {
        BigDecimal commissionRate = resolveCommissionRate(snapshot);
        return switch (systemTag) {
            case "high_commission" -> commissionRate.compareTo(BigDecimal.valueOf(20)) >= 0;
            case "traffic" -> Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag());
            case "new" -> (snapshot.getSales() == null ? 0L : snapshot.getSales()) < 100L;
            case "high_price" -> parsePriceYuan(snapshot.getPriceText(), snapshot.getPrice()).compareTo(BigDecimal.valueOf(300)) >= 0;
            default -> true;
        };
    }

    private boolean matchesDecisionFilter(String activityId, String productId, String decision) {
        DecisionSummary summary = findDecisionSummary(activityId, productId);
        if ("NONE".equals(decision)) {
            return summary == null || !StringUtils.hasText(summary.level());
        }
        return summary != null && decision.equals(summary.level());
    }

    private boolean isSampleRuleAvailable(ProductSnapshot snapshot) {
        LocalDateTime promotionEndTime = parseDateTime(snapshot.getPromotionEndTime());
        boolean activityExpired = promotionEndTime != null && promotionEndTime.isBefore(LocalDateTime.now());
        return !activityExpired && StringUtils.hasText(snapshot.getStatusText());
    }

    private BigDecimal parsePriceYuan(String priceText, Long priceCent) {
        if (StringUtils.hasText(priceText)) {
            String normalized = priceText.trim().replaceAll("[^0-9.]", "");
            if (StringUtils.hasText(normalized)) {
                try {
                    return new BigDecimal(normalized);
                } catch (NumberFormatException ignore) {
                    // Fallback to cent price below.
                }
            }
        }
        if (priceCent == null || priceCent <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(priceCent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private List<String> buildLibrarySystemTags(ProductSnapshot snapshot) {
        List<String> tags = new ArrayList<>();
        if (resolveCommissionRate(snapshot).compareTo(BigDecimal.valueOf(20)) >= 0) {
            tags.add("高佣");
        }
        if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
            tags.add("抖音商品池");
        }
        if ((snapshot.getSales() == null ? 0L : snapshot.getSales()) < 100L) {
            tags.add("新品");
        }
        if (parsePriceYuan(snapshot.getPriceText(), snapshot.getPrice()).compareTo(BigDecimal.valueOf(300)) >= 0) {
            tags.add("高客单价");
        }
        return tags;
    }

    private List<String> buildLibraryAlertTags(ProductSnapshot snapshot, ProductOperationState state) {
        List<String> tags = new ArrayList<>();
        if (!StringUtils.hasText(snapshot.getDetailUrl())) {
            tags.add("无商品链接");
        }
        LocalDateTime promotionEndTime = parseDateTime(snapshot.getPromotionEndTime());
        if (promotionEndTime != null && promotionEndTime.isBefore(LocalDateTime.now())) {
            tags.add("活动过期");
        }
        if (state == null || state.getAssigneeId() == null) {
            tags.add("未分配负责人");
        }
        return tags;
    }

    public Product getById(UUID id) {
        ProductSnapshot snapshot = getSnapshotById(id);
        return toLegacyProduct(snapshot);
    }

    /**
     * 更新商品审核补充信息，不改变商品审核、上架或推广状态。
     *
     * <p>编辑接口只允许更新商品侧边栏开放的字段，避免把商品状态机字段
     * 或第三方同步字段混入 audit_payload。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Product updateAuditSupplement(
            UUID id,
            Map<String, Object> supplementPatch,
            UUID operatorId,
            UUID operatorDeptId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        if (supplementPatch == null || supplementPatch.isEmpty()) {
            throw BusinessException.conflict("商品补充信息不能为空");
        }
        for (String key : supplementPatch.keySet()) {
            if (!EDITABLE_AUDIT_SUPPLEMENT_KEYS.contains(key)) {
                throw BusinessException.conflict("不支持编辑商品字段：" + key);
            }
        }

        ProductOperationState state = getOrInitOperationState(snapshot.getActivityId(), snapshot.getProductId());
        Map<String, Object> current = new LinkedHashMap<>(parseAuditPayload(state.getAuditPayload()));
        Map<String, Object> normalizedPatch = normalizeAuditSupplement(supplementPatch);
        if (supplementPatch.containsKey("exclusivePriceAmount")
                && !isBlankPatchValue(supplementPatch.get("exclusivePriceAmount"))
                && !normalizedPatch.containsKey("exclusivePriceAmount")) {
            throw BusinessException.conflict("专属价金额必须是非负数字，最多保留两位小数");
        }

        for (String key : EDITABLE_AUDIT_SUPPLEMENT_KEYS) {
            if (supplementPatch.containsKey(key) && isBlankPatchValue(supplementPatch.get(key))) {
                current.remove(key);
            }
        }
        current.putAll(normalizedPatch);

        state.setAuditPayload(writeAuditPayload(current));
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            persistOperationState(state);
        }

        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        Map<String, Object> logPayload = new LinkedHashMap<>();
        logPayload.put("eventLabel", "编辑商品补充信息");
        logPayload.put("changedFields", new ArrayList<>(supplementPatch.keySet()));
        productBizStatusService.logStatusChange(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                "EDIT_AUDIT_SUPPLEMENT",
                currentStatus,
                currentStatus,
                operatorId,
                operatorDeptId,
                logPayload,
                "编辑商品补充信息",
                true,
                null);
        productDisplayRuleService.applyForProductId(snapshot.getProductId());
        evictActivityProductCache(snapshot.getActivityId());
        return getById(id);
    }

    private boolean isBlankPatchValue(Object value) {
        return value == null || (value instanceof String text && !StringUtils.hasText(text));
    }

    // P8.5 修复: @Transactional 仅保留在真正做事的方法上 (Spring AOP self-invocation 绕过代理)
    public Product bindActivity(UUID id, UUID activityId) {
        return bindActivity(id, activityId, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product bindActivity(UUID id, UUID activityId, UUID operatorId, UUID operatorDeptId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        bindActivity(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                activityId == null ? null : activityId.toString(),
                operatorId,
                operatorDeptId);
        return getById(id);
    }

    // P8.5 修复: @Transactional 仅保留在真正做事的方法上
    public Product assignProduct(UUID id, UUID assigneeId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        assignProduct(snapshot.getActivityId(), snapshot.getProductId(), assigneeId, null, null);
        return getById(id);
    }

    // P8.5 修复: @Transactional 仅保留在真正做事的方法上
    public Product auditProduct(UUID id, boolean approved, String reason) {
        return auditProduct(id, approved, reason, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product auditProduct(UUID id, boolean approved, String reason, Map<String, Object> supplement) {
        ProductSnapshot snapshot = getSnapshotById(id);
        auditProduct(snapshot.getActivityId(), snapshot.getProductId(), approved, reason, supplement, null, null);
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product pausePublish(UUID id, UUID operatorId, UUID operatorDeptId) {
        return updatePublishPaused(id, true, operatorId, operatorDeptId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product resumePublish(UUID id, UUID operatorId, UUID operatorDeptId) {
        return updatePublishPaused(id, false, operatorId, operatorDeptId);
    }

    private Product updatePublishPaused(UUID id, boolean paused, UUID operatorId, UUID operatorDeptId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        ProductOperationState state = getOrInitOperationState(snapshot.getActivityId(), snapshot.getProductId());
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        LocalDateTime now = LocalDateTime.now();

        state.setManualDisabled(paused);
        state.setLastOperationAt(now);
        state.setDisplayReason(null);
        if (paused) {
            state.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());
            state.setHiddenReason(ProductDisplayRuleService.HIDDEN_REASON_PUBLISH_PAUSED);
        } else {
            state.setDisplayStatus(ProductDisplayStatus.PENDING.name());
            state.setHiddenReason(null);
        }

        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            persistOperationState(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventLabel", paused ? "商品暂停发布" : "商品恢复发布");
        payload.put("manualDisabled", paused);
        payload.put("displayStatus", state.getDisplayStatus());
        payload.put("hiddenReason", state.getHiddenReason());
        payload.put("selectedToLibrary", Boolean.TRUE.equals(state.getSelectedToLibrary()));
        payload.put("productTitle", safeText(snapshot.getTitle(), "活动商品"));
        productBizStatusService.logStatusChange(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                paused ? "PUBLISH_PAUSE" : "PUBLISH_RESUME",
                currentStatus,
                currentStatus,
                operatorId,
                operatorDeptId,
                payload,
                paused ? "暂停发布" : "恢复发布",
                true,
                null
        );
        productDisplayRuleService.applyForProductId(snapshot.getProductId());
        evictActivityProductCache(snapshot.getActivityId());
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            UUID id,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink) {
        return generatePromotionLink(id, userId, deptId, externalUniqueId, promotionScene, needShortLink, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            UUID id,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId) {
        return generatePromotionLink(id, userId, deptId, externalUniqueId, promotionScene, needShortLink, scene, talentId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            UUID id,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey) {
        ProductSnapshot snapshot = getSnapshotById(id);
        return generatePromotionLink(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId,
                idempotencyKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertSnapshots(String activityId, List<DouyinProductGateway.ActivityProductItem> items) {
        upsertSnapshotsWithStats(activityId, items);
    }

    /**
     * backfill 专用批写入口：传入 items 必须已按 product_id 升序排序；
     * 调用方负责把 items 拆成 batch，并用 {@code TransactionTemplate} 包成独立小事务。
     *
     * <p>Phase 4-1.5 deadlock 修复关键点：</p>
     * <ul>
     *   <li>batch 内 items 顺序由调用方控制，避免不同事务按不同 product_id 顺序加锁。</li>
     *   <li>本方法不直接开事务（外层不传 {@code Propagation.REQUIRES_NEW}），
     *       由 {@link com.colonel.saas.service.ProductActivityBackfillService} 编程式事务决定边界。</li>
     * </ul>
     */
    public void upsertSnapshotsPreSorted(String activityId, List<DouyinProductGateway.ActivityProductItem> items) {
        upsertSnapshotsWithStats(activityId, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public ActivitySnapshotUpsertStats upsertSnapshotsWithStats(String activityId, List<DouyinProductGateway.ActivityProductItem> items) {
        if (!StringUtils.hasText(activityId) || items == null || items.isEmpty()) {
            return new ActivitySnapshotUpsertStats(0, 0, items == null ? 0 : items.size(), 0, 0);
        }
        // Phase 4-1.5 deadlock 修复：固定 batch 内的 product_id 升序，避免不同事务按不同顺序加锁。
        // backfill 路径在调用方已按 product_id 排过序；这里再排一次保证幂等。
        List<DouyinProductGateway.ActivityProductItem> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparingLong(DouyinProductGateway.ActivityProductItem::productId));
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
        UUID activityRecruiterId = activity == null ? null : activity.getRecruiterUserId();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int libraryEntryCount = 0;
        int unchanged = 0;
        int stateUpdated = 0;
        for (DouyinProductGateway.ActivityProductItem item : sortedItems) {
            String productId = String.valueOf(item.productId());
            if (!StringUtils.hasText(productId)) {
                skipped++;
                continue;
            }
            UUID snapshotId = buildSnapshotId(activityId, productId);
            ProductSnapshot snapshot = snapshotMapper.selectById(snapshotId);
            boolean snapshotExisted = snapshot != null;
            if (!snapshotExisted) {
                snapshot = new ProductSnapshot();
                snapshot.setId(snapshotId);
                snapshot.setActivityId(activityId);
                snapshot.setProductId(productId);
                created++;
            }
            // 备份一份旧 snapshot 用于后续判断是否真的需要写入。
            ProductSnapshot beforeFill = snapshotExisted ? cloneSnapshotForCompare(snapshot) : null;
            fillSnapshot(snapshot, item);
            // Phase 4-1.5 no-op 优化：snapshot 字段未变时跳过 mapper.upsert，避免 ON CONFLICT 走全字段更新造成行锁。
            if (snapshotExisted && beforeFill != null && snapshotFieldsEqual(beforeFill, snapshot)) {
                unchanged++;
            } else {
                snapshotMapper.upsert(snapshot);
                if (snapshotExisted) {
                    updated++;
                }
            }
            ProductOperationState existingState = getOperationState(activityId, productId);
            ProductOperationState state = productBizStatusService.initStateIfAbsent(existingState, activityId, productId, null, null, "活动商品同步");
            boolean stateChanged = false;
            if (activityRecruiterId != null && state.getAssigneeId() == null) {
                state.setAssigneeId(activityRecruiterId);
                stateChanged = true;
            }
            UpstreamProductLibraryDecision libraryDecision = applyUpstreamProductLibraryDecision(state, item);
            stateChanged = stateChanged || libraryDecision.stateChanged();
            if (libraryDecision.libraryEntered()) {
                libraryEntryCount++;
            }
            if (stateChanged) {
                operationStateMapper.updateById(state);
                stateUpdated++;
            }
        }
        if (created > 0 || updated > 0 || libraryEntryCount > 0 || stateUpdated > 0) {
            evictActivityProductCache(activityId);
        }
        return new ActivitySnapshotUpsertStats(created, updated, skipped, libraryEntryCount, unchanged);
    }

    /**
     * 活动同步的本地写入入口。生产运行时由协调器提供稳定排序、独立批事务和 40P01 重试；
     * 手工构造的旧单测没有 Spring 注入时保留原行为，避免改变既有测试夹具的构造契约。
     */
    private ActivitySnapshotUpsertStats upsertActivityProductBatch(
            String activityId,
            List<DouyinProductGateway.ActivityProductItem> items) {
        if (activitySyncWriteCoordinator == null || items == null || items.isEmpty()) {
            return upsertSnapshotsWithStats(activityId, items);
        }
        List<ActivitySnapshotUpsertStats> batchStats = activitySyncWriteCoordinator.executeInBatches(
                activityId,
                items,
                batch -> upsertSnapshotsWithStats(activityId, batch));
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int libraryEntryCount = 0;
        int unchanged = 0;
        for (ActivitySnapshotUpsertStats stats : batchStats) {
            if (stats == null) {
                continue;
            }
            created += stats.createdCount();
            updated += stats.updatedCount();
            skipped += stats.skippedCount();
            libraryEntryCount += stats.libraryEntryCount();
            unchanged += stats.unchangedCount();
        }
        return new ActivitySnapshotUpsertStats(created, updated, skipped, libraryEntryCount, unchanged);
    }

    /**
     * 将单个上游分页结果推进到商品库可见状态。
     *
     * <p>状态分区同步可能由多个线程拉取同一活动；按活动加锁后串行执行
     * 快照写入、展示状态修复和去重规则，避免同一商品在不同 status 分区之间
     * 交叉覆盖。锁粒度只覆盖当前活动，不阻塞其它活动同步。</p>
     */
    private ActivitySnapshotUpsertStats upsertAndRefreshActivityProductPage(
            String activityId,
            List<DouyinProductGateway.ActivityProductItem> items,
            int pageNo) {
        Object lock = activityPageRefreshLocks.computeIfAbsent(
                activityId == null ? "" : activityId.trim(),
                ignored -> new Object());
        synchronized (lock) {
            long startedAt = System.nanoTime();
            ActivitySnapshotUpsertStats stats = upsertActivityProductBatch(activityId, items);
            LinkedHashSet<String> productIds = items == null
                    ? new LinkedHashSet<>()
                    : items.stream()
                            .filter(java.util.Objects::nonNull)
                            .map(item -> String.valueOf(item.productId()))
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            if (productIds.isEmpty()) {
                return stats;
            }

            long repairStartedAt = System.nanoTime();
            productDisplayRuleService.repairLibraryStateForActivityProducts(
                    activityId,
                    productIds,
                    false,
                    productIds.size());
            long repairCostMs = elapsedMs(repairStartedAt);

            long displayStartedAt = System.nanoTime();
            productDisplayRuleService.applyForProductIds(productIds);
            long displayCostMs = elapsedMs(displayStartedAt);
            evictActivityProductCache(activityId);

            log.info("Activity product page library visibility refreshed, activityId={}, page={}, productCount={}, createdCount={}, updatedCount={}, repairCostMs={}, displayCostMs={}, totalCostMs={}",
                    activityId,
                    pageNo,
                    productIds.size(),
                    stats.createdCount(),
                    stats.updatedCount(),
                    repairCostMs,
                    displayCostMs,
                    elapsedMs(startedAt));
            return stats;
        }
    }

    private UpstreamProductLibraryDecision applyUpstreamProductLibraryDecision(
            ProductOperationState state,
            DouyinProductGateway.ActivityProductItem item) {
        if (state == null) {
            return UpstreamProductLibraryDecision.unchanged();
        }
        boolean selectedBefore = Boolean.TRUE.equals(state.getSelectedToLibrary());
        boolean changed = false;
        if (!isUpstreamPromoting(item)) {
            changed = setIfDifferent(state::getSelectedToLibrary, state::setSelectedToLibrary, false) || changed;
            changed = setIfDifferent(state::getDisplayStatus, state::setDisplayStatus, ProductDisplayStatus.HIDDEN.name()) || changed;
            changed = setIfDifferent(state::getHiddenReason, state::setHiddenReason, ProductDisplayRuleService.HIDDEN_REASON_UPSTREAM_NOT_PROMOTING) || changed;
            return new UpstreamProductLibraryDecision(changed, false);
        }

        boolean wasLocalRejected = productDisplayPolicy.isLocalRejectedProductState(
                state.getAuditStatus(),
                state.getBizStatus());
        changed = applyUpstreamPromotingLibraryState(state, wasLocalRejected) || changed;
        if (Boolean.TRUE.equals(state.getManualDisabled())) {
            changed = setIfDifferent(state::getDisplayStatus, state::setDisplayStatus, ProductDisplayStatus.HIDDEN.name()) || changed;
            changed = setIfDifferent(state::getHiddenReason, state::setHiddenReason, ProductDisplayRuleService.HIDDEN_REASON_LOCAL_PAUSED) || changed;
            return new UpstreamProductLibraryDecision(changed, !selectedBefore);
        }
        if (!ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
            changed = setIfDifferent(state::getDisplayStatus, state::setDisplayStatus, ProductDisplayStatus.PENDING.name()) || changed;
        }
        changed = setIfDifferent(state::getHiddenReason, state::setHiddenReason, null) || changed;
        return new UpstreamProductLibraryDecision(changed, !selectedBefore);
    }

    private boolean applyUpstreamPromotingLibraryState(ProductOperationState state, boolean wasLocalRejected) {
        boolean changed = false;
        changed = setIfDifferent(state::getSelectedToLibrary, state::setSelectedToLibrary, true) || changed;
        if (state.getSelectedAt() == null) {
            state.setSelectedAt(LocalDateTime.now());
            changed = true;
        }
        if (!Integer.valueOf(2).equals(state.getAuditStatus())) {
            state.setAuditStatus(2);
            changed = true;
        }
        ProductBizStatus currentStatus = readStateBizStatus(state);
        if (currentStatus == null
                || currentStatus == ProductBizStatus.PENDING_AUDIT
                || currentStatus == ProductBizStatus.REJECTED) {
            state.setBizStatus(ProductBizStatus.APPROVED.name());
            changed = true;
        }
        if (!StringUtils.hasText(state.getAuditRemark()) || wasLocalRejected) {
            state.setAuditRemark(AUTO_APPROVE_PROMOTING_REMARK);
            changed = true;
        }
        return changed;
    }

    private ProductBizStatus readStateBizStatus(ProductOperationState state) {
        try {
            return ProductBizStatus.fromCode(state == null ? null : state.getBizStatus());
        } catch (IllegalArgumentException ex) {
            return ProductBizStatus.PENDING_AUDIT;
        }
    }

    private <T> boolean setIfDifferent(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter, T next) {
        if (java.util.Objects.equals(getter.get(), next)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private record UpstreamProductLibraryDecision(boolean stateChanged, boolean libraryEntered) {
        private static UpstreamProductLibraryDecision unchanged() {
            return new UpstreamProductLibraryDecision(false, false);
        }
    }

    /**
     * 管理员为活动分配或清除招商组长，并级联更新该活动下已有商品的负责人。
     *
     * @param activityId  活动 ID
     * @param assigneeId  招商组长用户 ID，null 表示清除分配
     * @param operatorId 操作人用户 ID
     * @return 分配结果信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assignActivity(String activityId, UUID assigneeId, UUID operatorId) {
        if (!StringUtils.hasText(activityId)) {
            throw BusinessException.param("activityId 不能为空");
        }
        String normalizedActivityId = activityId.trim();
        UUID recruiterUserId = null;
        UUID recruiterDeptId = null;
        String recruiterUserName = null;
        String recruiterDeptName = null;

        // 非清除分配时，校验目标用户
        if (assigneeId != null) {
            UserOwnershipReference assignee = resolveUserOwnershipReference(assigneeId);
            if (assignee == null) {
                throw BusinessException.notFound("目标用户不存在");
            }
            recruiterUserId = assigneeId;
            recruiterDeptId = assignee.deptId();
            recruiterUserName = resolveUserDisplayName(assigneeId);
            recruiterDeptName = resolveDeptName(recruiterDeptId);
        }

        ColonelsettlementActivity existing = colonelActivityMapper.selectByActivityId(normalizedActivityId);
        if (existing == null) {
            colonelActivityMapper.upsertListActivitySummary(
                    UUID.nameUUIDFromBytes(("assign-activity-" + normalizedActivityId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    normalizedActivityId,
                    normalizedActivityId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    LocalDateTime.now());
        }

        LocalDateTime assignedAt = LocalDateTime.now();
        int updated = colonelActivityMapper.updateRecruiterAssignment(
                normalizedActivityId,
                recruiterUserId,
                recruiterDeptId,
                assignedAt,
                operatorId);
        if (updated == 0) {
            throw BusinessException.notFound("活动不存在或已删除");
        }

        // 级联更新该活动下商品的负责人
        operationStateMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ProductOperationState>()
                        .eq("activity_id", normalizedActivityId)
                        .eq("deleted", 0)
                        .set("assignee_id", recruiterUserId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", normalizedActivityId);
        payload.put("recruiterUserId", recruiterUserId);
        payload.put("recruiterUserName", recruiterUserName);
        payload.put("recruiterDeptId", recruiterDeptId);
        payload.put("recruiterDeptName", recruiterDeptName);
        payload.put("assignedAt", assignedAt);
        payload.put("assignedBy", operatorId);
        // 兼容旧字段
        payload.put("assigneeId", recruiterUserId);
        payload.put("activityAssigneeId", recruiterUserId);
        payload.put("assigneeName", recruiterUserName);
        payload.put("activityAssigneeName", recruiterUserName);
        return payload;
    }

    private String resolveDeptName(UUID deptId) {
        if (deptId == null) {
            return null;
        }
        return null; // 简化实现，如有需要可注入 SysDeptMapper 查询部门名称
    }

    /**
     * 活动商品全量刷新结果（供活动列表「获取同步商品」展示同步/入库数量）。
     */
    public record ActivityProductRefreshResult(
            int syncedProductCount,
            int libraryEntryCount,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int pagesFetched,
            int fetchedRows,
            int distinctProductIds,
            int duplicateProductIds,
            String stoppedReason,
            boolean stillHasNextWhenStopped,
            boolean complete) {

        public ActivityProductRefreshResult(
                int syncedProductCount,
                int libraryEntryCount,
                int createdCount,
                int updatedCount,
                int skippedCount) {
            this(syncedProductCount, libraryEntryCount, createdCount, updatedCount, skippedCount,
                    0, syncedProductCount, syncedProductCount, 0,
                    ActivityProductPaginationRunner.StopReason.DONE_NO_MORE.name(), false, true);
        }
    }

    public record ActivityProductRefreshProgress(int pagesFetched) {
    }

    public ActivityProductRefreshResult refreshActivitySnapshots(ActivityProductRefreshRequest request) {
        if (request == null) {
            return refreshActivitySnapshots((DouyinProductGateway.ActivityProductQueryRequest) null);
        }
        return refreshActivitySnapshots(toActivityProductQueryRequest(request));
    }

    public ActivityProductRefreshResult refreshActivitySnapshots(DouyinProductGateway.ActivityProductQueryRequest request) {
        return refreshActivitySnapshots(
                request,
                productSyncActivityProductMaxPagesPerActivity,
                productSyncActivityProductMaxRowsPerActivity);
    }

    public ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request,
            long pageIntervalMs) {
        return refreshActivitySnapshots(
                request,
                productSyncActivityProductMaxPagesPerActivity,
                productSyncActivityProductMaxRowsPerActivity,
                pageIntervalMs);
    }

    public ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int maxPagesPerActivity,
            int maxRowsPerActivity) {
        return refreshActivitySnapshots(
                request,
                maxPagesPerActivity,
                maxRowsPerActivity,
                normalizedProductActivitySyncPageIntervalMs());
    }

    // P8.5 修复: @Transactional 仅保留在真正做事的方法上 (Spring AOP self-invocation 绕过代理)
    public ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            long pageIntervalMs) {
        return refreshActivitySnapshots(request, maxPagesPerActivity, maxRowsPerActivity, pageIntervalMs, null);
    }

    // 事务外编排：每页 upsert 由 upsertSnapshotsWithStats(@Transactional) 独立提交
    public ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            long pageIntervalMs,
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer) {
        if (request == null || !StringUtils.hasText(request.activityId())) {
            return new ActivityProductRefreshResult(0, 0, 0, 0, 0);
        }
        int pageSize = Math.min(Math.max(request.count() == null ? productSyncActivityProductPageSize : request.count(), 1), 20);
        int normalizedMaxPages = Math.max(maxPagesPerActivity <= 0 ? productSyncActivityProductMaxPagesPerActivity : maxPagesPerActivity, 1);
        int normalizedMaxRows = Math.max(maxRowsPerActivity <= 0 ? productSyncActivityProductMaxRowsPerActivity : maxRowsPerActivity, 1);
        long normalizedPageIntervalMs = normalizedProductActivitySyncPageIntervalMs(pageIntervalMs);
        java.util.concurrent.atomic.AtomicInteger pageCounter = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger progressPageCounter = new java.util.concurrent.atomic.AtomicInteger();
        ActivityProductPaginationRunner.Result pageResult = ActivityProductPaginationRunner.run(
                request,
                new ActivityProductPaginationRunner.Options(
                        pageSize,
                        normalizedMaxPages,
                        normalizedMaxRows,
                        true),
                pageRequest -> queryActivityProductsWithRetry(pageRequest, pageCounter.getAndIncrement()),
                page -> {
                    ActivitySnapshotUpsertStats stats = pageLibraryRefreshEnabled
                            ? upsertAndRefreshActivityProductPage(request.activityId(), page.items(), page.pageNo())
                            : upsertActivityProductBatch(request.activityId(), page.items());
                    notifyActivityProductRefreshProgress(progressConsumer, progressPageCounter.incrementAndGet());
                    return new ActivityProductPaginationRunner.PageWriteStats(
                            stats.createdCount(),
                            stats.updatedCount(),
                            stats.skippedCount(),
                            stats.libraryEntryCount());
                        },
                pageNo -> sleepBeforeNextActivityProductPage(request.activityId(), pageNo - 1, normalizedPageIntervalMs));
        return finishActivityProductRefresh(request, pageResult, normalizedPageIntervalMs);
    }

    private DouyinProductGateway.ActivityProductQueryRequest toActivityProductQueryRequest(
            ActivityProductRefreshRequest request) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                request.appId(),
                request.activityId(),
                request.searchType(),
                request.sortType(),
                request.count(),
                request.cooperationInfo(),
                request.cooperationType(),
                request.productInfo(),
                request.status(),
                request.retrieveMode(),
                request.cursor(),
                request.page());
    }

    public ActivityProductRefreshResult refreshActivitySnapshotsByStatusPartitions(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            long pageIntervalMs,
            int parallelism,
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer) {
        return refreshActivitySnapshotsByStatusPartitions(
                request,
                null,
                maxPagesPerActivity,
                maxRowsPerActivity,
                pageIntervalMs,
                parallelism,
                progressConsumer);
    }

    public ActivityProductRefreshResult refreshActivitySnapshotsByStatusPartitions(
            DouyinProductGateway.ActivityProductQueryRequest request,
            List<Integer> requestedStatuses,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            long pageIntervalMs,
            int parallelism,
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer) {
        if (request == null || !StringUtils.hasText(request.activityId())) {
            return new ActivityProductRefreshResult(0, 0, 0, 0, 0);
        }
        int normalizedParallelism = Math.min(Math.max(parallelism, 1), 6);
        boolean constrainedStatuses = requestedStatuses != null && !requestedStatuses.isEmpty();
        if (request.status() != null || normalizedParallelism <= 1) {
            if (constrainedStatuses && request.status() == null) {
                return refreshActivitySnapshotsByStatusesSerial(
                        request,
                        normalizeActivityProductStatusPartitions(requestedStatuses),
                        maxPagesPerActivity,
                        maxRowsPerActivity,
                        normalizedProductActivityPrioritySyncPageIntervalMs(pageIntervalMs),
                        progressConsumer);
            }
            return refreshActivitySnapshots(request, maxPagesPerActivity, maxRowsPerActivity, pageIntervalMs, progressConsumer);
        }
        List<Integer> statuses = constrainedStatuses
                ? normalizeActivityProductStatusPartitions(requestedStatuses)
                : productDisplayPolicy.activityProductFilterStatuses(null);
        if (statuses.isEmpty()) {
            return refreshActivitySnapshots(request, maxPagesPerActivity, maxRowsPerActivity, pageIntervalMs, progressConsumer);
        }
        int pageSize = Math.min(Math.max(request.count() == null ? productSyncActivityProductPageSize : request.count(), 1), 20);
        int normalizedMaxPages = Math.max(maxPagesPerActivity <= 0 ? productSyncActivityProductMaxPagesPerActivity : maxPagesPerActivity, 1);
        int normalizedMaxRows = Math.max(maxRowsPerActivity <= 0 ? productSyncActivityProductMaxRowsPerActivity : maxRowsPerActivity, 1);
        long normalizedPageIntervalMs = constrainedStatuses
                ? normalizedProductActivityPrioritySyncPageIntervalMs(pageIntervalMs)
                : normalizedProductActivitySyncPageIntervalMs(pageIntervalMs);
        StatusPartitionPreflight preflight = statusPartitionPreflight(
                request,
                statuses,
                pageSize,
                normalizedMaxPages,
                normalizedMaxRows,
                Math.min(normalizedParallelism, statuses.size()),
                constrainedStatuses);
        if (!preflight.safe()) {
            log.info("Activity product status-partition sync fallback to serial, activityId={}, statuses={}, pageSize={}, maxPages={}, maxRows={}",
                    request.activityId(), statuses, pageSize, normalizedMaxPages, normalizedMaxRows);
            if (constrainedStatuses) {
                return refreshActivitySnapshotsByStatusesSerial(
                        request,
                        statuses,
                        normalizedMaxPages,
                        normalizedMaxRows,
                        normalizedPageIntervalMs,
                        progressConsumer);
            }
            return refreshActivitySnapshots(request, normalizedMaxPages, normalizedMaxRows, normalizedPageIntervalMs, progressConsumer);
        }

        if (preflight.pageMode()) {
            return refreshActivitySnapshotsByPagePartitions(
                    request,
                    statuses,
                    pageSize,
                    normalizedMaxPages,
                    normalizedMaxRows,
                    normalizedPageIntervalMs,
                    normalizedParallelism,
                    progressConsumer,
                    preflight);
        }

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(Math.min(normalizedParallelism, statuses.size()));
        java.util.concurrent.atomic.AtomicInteger pageCounter = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger progressPageCounter = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger remainingRows =
                new java.util.concurrent.atomic.AtomicInteger(normalizedMaxRows);
        List<ActivityProductStatusPartitionFetch> partitions;
        try {
            List<java.util.concurrent.CompletableFuture<ActivityProductStatusPartitionFetch>> futures = statuses.stream()
                    .map(status -> java.util.concurrent.CompletableFuture.supplyAsync(
                            () -> fetchActivityProductStatusPartition(
                                    request,
                                    status,
                                    pageSize,
                                    normalizedMaxPages,
                                    normalizedMaxRows,
                                    remainingRows,
                                    normalizedPageIntervalMs,
                                    pageCounter,
                                    progressPageCounter,
                                    progressConsumer,
                                    preflight.firstPages().get(status)),
                            executor))
                    .toList();
            partitions = futures.stream()
                    .map(java.util.concurrent.CompletableFuture::join)
                    .toList();
        } catch (java.util.concurrent.CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }

        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int libraryEntryCount = 0;
        for (ActivityProductStatusPartitionFetch partition : partitions) {
            if (!pageLibraryRefreshEnabled) {
                for (List<DouyinProductGateway.ActivityProductItem> pageItems : partition.pages()) {
                    ActivitySnapshotUpsertStats stats = upsertActivityProductBatch(request.activityId(), pageItems);
                    createdCount += stats.createdCount();
                    updatedCount += stats.updatedCount();
                    skippedCount += stats.skippedCount();
                    libraryEntryCount += stats.libraryEntryCount();
                }
            }
            if (partition.result() != null && partition.result().complete()) {
                int staleDeletedCount = reconcileStatusPartitionSnapshots(
                        request.activityId(),
                        partition.status(),
                        partition.result().productIds());
                if (staleDeletedCount > 0) {
                    log.info("Activity product status-partition stale snapshots marked deleted, activityId={}, status={}, staleDeletedCount={}",
                            request.activityId(), partition.status(), staleDeletedCount);
                }
            }
        }
        ActivityProductPaginationRunner.Result pageResult = aggregateStatusPartitionResults(
                partitions,
                createdCount,
                updatedCount,
                skippedCount,
                libraryEntryCount);
        log.info("Activity product status-partition sync merged, activityId={}, statuses={}, parallelism={}, pagesFetched={}, fetchedRows={}, complete={}",
                request.activityId(),
                statuses,
                Math.min(normalizedParallelism, statuses.size()),
                pageResult.pagesFetched(),
                pageResult.fetchedRows(),
                pageResult.complete());
        return finishActivityProductRefresh(request, pageResult, normalizedPageIntervalMs);
    }

    private ActivityProductRefreshResult refreshActivitySnapshotsByStatusesSerial(
            DouyinProductGateway.ActivityProductQueryRequest request,
            List<Integer> statuses,
            int maxPagesPerActivity,
            int maxRowsPerActivity,
            long pageIntervalMs,
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer) {
        int syncedProductCount = 0;
        int libraryEntryCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int pagesFetched = 0;
        int fetchedRows = 0;
        int distinctProductIds = 0;
        int duplicateProductIds = 0;
        String stoppedReason = "STATUS_SCOPE_COMPLETED";
        boolean stillHasNextWhenStopped = false;
        boolean complete = true;
        int remainingRows = Math.max(maxRowsPerActivity <= 0 ? productSyncActivityProductMaxRowsPerActivity : maxRowsPerActivity, 1);
        int pageSize = Math.min(Math.max(request.count() == null ? productSyncActivityProductPageSize : request.count(), 1), 20);

        for (Integer status : statuses) {
            if (remainingRows <= 0) {
                stoppedReason = "REQUESTED_MAX_ROWS_REACHED";
                stillHasNextWhenStopped = true;
                complete = true;
                break;
            }
            ActivityProductRefreshResult result = refreshActivitySnapshots(
                    activityProductRequestWithStatus(request, status, pageSize, null),
                    maxPagesPerActivity,
                    remainingRows,
                    pageIntervalMs,
                    progressConsumer);
            syncedProductCount += result.syncedProductCount();
            libraryEntryCount += result.libraryEntryCount();
            createdCount += result.createdCount();
            updatedCount += result.updatedCount();
            skippedCount += result.skippedCount();
            pagesFetched += result.pagesFetched();
            fetchedRows += result.fetchedRows();
            distinctProductIds += result.distinctProductIds();
            duplicateProductIds += result.duplicateProductIds();
            remainingRows -= Math.max(result.fetchedRows(), 0);
            if (!result.complete()) {
                boolean requestedLimitReached = "MAX_ROWS_REACHED".equals(result.stoppedReason()) || remainingRows <= 0;
                stoppedReason = requestedLimitReached ? "REQUESTED_MAX_ROWS_REACHED" : result.stoppedReason();
                stillHasNextWhenStopped = result.stillHasNextWhenStopped();
                complete = requestedLimitReached;
                break;
            }
        }

        return new ActivityProductRefreshResult(
                syncedProductCount,
                libraryEntryCount,
                createdCount,
                updatedCount,
                skippedCount,
                pagesFetched,
                fetchedRows,
                distinctProductIds,
                duplicateProductIds,
                stoppedReason,
                stillHasNextWhenStopped,
                complete);
    }

    private ActivityProductRefreshResult refreshActivitySnapshotsByPagePartitions(
            DouyinProductGateway.ActivityProductQueryRequest request,
            List<Integer> statuses,
            int pageSize,
            int maxPages,
            int maxRows,
            long pageIntervalMs,
            int parallelism,
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer,
            StatusPartitionPreflight preflight) {
        java.util.concurrent.atomic.AtomicInteger remainingRows =
                new java.util.concurrent.atomic.AtomicInteger(maxRows);
        java.util.concurrent.atomic.AtomicInteger progressPageCounter = new java.util.concurrent.atomic.AtomicInteger();
        int workerCount = Math.min(Math.max(parallelism, 1), statuses.size());
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(workerCount);
        List<ActivityProductPageFetch> fetchedPages = new ArrayList<>();
        try {
            List<java.util.concurrent.CompletableFuture<ActivityProductPageFetch>> futures = new ArrayList<>();
            for (Integer status : statuses) {
                DouyinProductGateway.ActivityProductListResult firstPage = preflight.firstPages().get(status);
                long total = firstPage == null || firstPage.total() == null
                        ? 0L
                        : Math.max(firstPage.total(), 0L);
                int pageCount = Math.min(maxPages, Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                        (total + pageSize - 1L) / pageSize)));
                futures.addAll(java.util.stream.IntStream.rangeClosed(1, pageCount)
                        .mapToObj(pageNo -> java.util.concurrent.CompletableFuture.supplyAsync(
                                () -> fetchActivityProductPage(
                                        request,
                                        status,
                                        pageSize,
                                        pageNo,
                                        remainingRows,
                                        firstPage),
                                executor))
                        .toList());
            }
            for (java.util.concurrent.CompletableFuture<ActivityProductPageFetch> future : futures) {
                fetchedPages.add(future.join());
            }
        } catch (java.util.concurrent.CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof DouyinApiException douyinApiException
                    && isDouyinRateLimited(douyinApiException)) {
                throw douyinApiException;
            }
            log.warn("Activity product page-mode partition failed, fallback to cursor mode, activityId={}, statuses={}, message={}",
                    request.activityId(), statuses, cause.getMessage());
            return refreshActivitySnapshotsByStatusesSerial(
                    request,
                    statuses,
                    maxPages,
                    maxRows,
                    pageIntervalMs,
                    progressConsumer);
        } finally {
            executor.shutdownNow();
        }

        Map<Integer, List<ActivityProductPageFetch>> pagesByStatus = fetchedPages.stream()
                .collect(Collectors.groupingBy(
                        ActivityProductPageFetch::status,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ActivityProductStatusPartitionFetch> partitions = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int libraryEntryCount = 0;
        for (Integer status : statuses) {
            List<ActivityProductPageFetch> statusPages = pagesByStatus.getOrDefault(status, List.of()).stream()
                    .sorted(java.util.Comparator.comparingInt(ActivityProductPageFetch::pageNo))
                    .toList();
            List<List<DouyinProductGateway.ActivityProductItem>> pageItems = pageLibraryRefreshEnabled
                    ? List.of()
                    : new ArrayList<>();
            Set<String> productIds = new LinkedHashSet<>();
            List<ActivityProductPaginationRunner.PageSummary> pageSamples = new ArrayList<>();
            int fetchedRows = 0;
            int duplicateProductIds = 0;
            boolean hasNext = false;
            for (ActivityProductPageFetch pageFetch : statusPages) {
                DouyinProductGateway.ActivityProductListResult page = pageFetch.page();
                List<DouyinProductGateway.ActivityProductItem> items = page.items() == null ? List.of() : page.items();
                int duplicateInPage = 0;
                for (DouyinProductGateway.ActivityProductItem item : items) {
                    String productId = String.valueOf(item.productId());
                    if (StringUtils.hasText(productId) && !productIds.add(productId)) {
                        duplicateProductIds++;
                        duplicateInPage++;
                    }
                }
                fetchedRows += items.size();
                hasNext = pageFetch.pageNo() < pageFetch.pageCount();
                pageSamples.add(new ActivityProductPaginationRunner.PageSummary(
                        pageFetch.pageNo(),
                        null,
                        null,
                        items.size(),
                        hasNext,
                        items.isEmpty() ? null : String.valueOf(items.get(0).productId()),
                        items.isEmpty() ? null : String.valueOf(items.get(items.size() - 1).productId()),
                        duplicateInPage,
                        pageFetch.elapsedMs()));
                if (pageLibraryRefreshEnabled) {
                    ActivitySnapshotUpsertStats stats = upsertAndRefreshActivityProductPage(
                            request.activityId(), items, pageFetch.pageNo());
                    createdCount += stats.createdCount();
                    updatedCount += stats.updatedCount();
                    skippedCount += stats.skippedCount();
                    libraryEntryCount += stats.libraryEntryCount();
                } else {
                    pageItems.add(List.copyOf(items));
                }
                notifyActivityProductRefreshProgress(progressConsumer, progressPageCounter.incrementAndGet());
            }
            if (!pageLibraryRefreshEnabled) {
                for (List<DouyinProductGateway.ActivityProductItem> items : pageItems) {
                    ActivitySnapshotUpsertStats stats = upsertActivityProductBatch(request.activityId(), items);
                    createdCount += stats.createdCount();
                    updatedCount += stats.updatedCount();
                    skippedCount += stats.skippedCount();
                    libraryEntryCount += stats.libraryEntryCount();
                }
            }
            DouyinProductGateway.ActivityProductListResult firstPage = preflight.firstPages().get(status);
            int expectedRows = firstPage == null || firstPage.total() == null
                    ? fetchedRows
                    : Math.max(firstPage.total().intValue(), 0);
            boolean complete = expectedRows <= fetchedRows && !hasNext;
            ActivityProductPaginationRunner.StopReason stopReason = complete
                    ? ActivityProductPaginationRunner.StopReason.DONE_NO_MORE
                    : remainingRows.get() <= 0
                    ? ActivityProductPaginationRunner.StopReason.MAX_ROWS_REACHED
                    : statusPages.size() >= maxPages
                    ? ActivityProductPaginationRunner.StopReason.MAX_PAGES_REACHED
                    : ActivityProductPaginationRunner.StopReason.EMPTY_PAGE_WITH_HAS_NEXT;
            ActivityProductPaginationRunner.Result result = new ActivityProductPaginationRunner.Result(
                    statusPages.size(),
                    fetchedRows,
                    productIds.size(),
                    duplicateProductIds,
                    0,
                    0,
                    0,
                    0,
                    stopReason,
                    !complete,
                    complete,
                    null,
                    List.copyOf(pageSamples),
                    List.of(),
                    Set.copyOf(productIds));
            if (result.complete()) {
                int staleDeletedCount = reconcileStatusPartitionSnapshots(
                        request.activityId(), status, result.productIds());
                if (staleDeletedCount > 0) {
                    log.info("Activity product page-mode stale snapshots marked deleted, activityId={}, status={}, staleDeletedCount={}",
                            request.activityId(), status, staleDeletedCount);
                }
            }
            partitions.add(new ActivityProductStatusPartitionFetch(status, result, pageItems));
        }
        ActivityProductPaginationRunner.Result pageResult = aggregateStatusPartitionResults(
                partitions,
                createdCount,
                updatedCount,
                skippedCount,
                libraryEntryCount);
        log.info("Activity product page-mode status-partition sync merged, activityId={}, statuses={}, parallelism={}, pagesFetched={}, fetchedRows={}, complete={}",
                request.activityId(), statuses, workerCount, pageResult.pagesFetched(), pageResult.fetchedRows(), pageResult.complete());
        return finishActivityProductRefresh(request, pageResult, pageIntervalMs, false);
    }

    private ActivityProductPageFetch fetchActivityProductPage(
            DouyinProductGateway.ActivityProductQueryRequest request,
            Integer status,
            int pageSize,
            int pageNo,
            java.util.concurrent.atomic.AtomicInteger remainingRows,
            DouyinProductGateway.ActivityProductListResult firstPage) {
        if (pageNo > 1 && remainingRows.get() <= 0) {
            return new ActivityProductPageFetch(status, pageNo, 0, new DouyinProductGateway.ActivityProductListResult(
                    firstPage != null && firstPage.test(),
                    firstPage == null ? 0L : firstPage.activityId(),
                    firstPage == null ? null : firstPage.institutionId(),
                    firstPage == null ? 0L : firstPage.total(),
                    null,
                    List.of()), 0L);
        }
        long startedAt = System.nanoTime();
        DouyinProductGateway.ActivityProductListResult page = pageNo == 1
                ? firstPage
                : reserveActivityProductRows(
                        queryActivityProductsWithRetry(
                                activityProductPageRequestWithStatus(request, status, pageSize, pageNo),
                                pageNo - 1),
                        remainingRows);
        return new ActivityProductPageFetch(status, pageNo, pageCountFor(firstPage, pageSize), page, elapsedMs(startedAt));
    }

    private int pageCountFor(DouyinProductGateway.ActivityProductListResult firstPage, int pageSize) {
        long total = firstPage == null || firstPage.total() == null ? 0L : Math.max(firstPage.total(), 0L);
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, (total + pageSize - 1L) / pageSize));
    }

    private List<Integer> normalizeActivityProductStatusPartitions(List<Integer> requestedStatuses) {
        if (requestedStatuses == null || requestedStatuses.isEmpty()) {
            return List.of();
        }
        List<Integer> statuses = new ArrayList<>();
        for (Integer status : requestedStatuses) {
            if (status != null && !statuses.contains(status)) {
                statuses.add(status);
            }
        }
        return List.copyOf(statuses);
    }

    private ActivityProductRefreshResult finishActivityProductRefresh(
            DouyinProductGateway.ActivityProductQueryRequest request,
            ActivityProductPaginationRunner.Result pageResult,
            long normalizedPageIntervalMs) {
        return finishActivityProductRefresh(request, pageResult, normalizedPageIntervalMs, true);
    }

    private ActivityProductRefreshResult finishActivityProductRefresh(
            DouyinProductGateway.ActivityProductQueryRequest request,
            ActivityProductPaginationRunner.Result pageResult,
            long normalizedPageIntervalMs,
            boolean reconcileWholeActivity) {
        int createdCount = pageResult.createdCount();
        int updatedCount = pageResult.updatedCount();
        int skippedCount = pageResult.skippedCount();
        int libraryEntryCount = pageResult.libraryEntryCount();

        int staleDeletedCount = reconcileWholeActivity
                ? reconcileActivitySnapshotsAfterCompleteRefresh(request, pageResult)
                : 0;
        if (staleDeletedCount > 0) {
            log.info("Activity product stale snapshots marked deleted, activityId={}, status={}, staleDeletedCount={}",
                    request.activityId(), request.status(), staleDeletedCount);
        }

        int repairLimit = Math.min(Math.max(10000, pageResult.distinctProductIds()), 50000);
        ProductDisplayRuleService.LibraryRepairResult repairResult = pageResult.complete()
                ? productDisplayRuleService.repairLibraryStateForActivity(request.activityId(), false, repairLimit)
                : productDisplayRuleService.repairLibraryStateForActivityProducts(
                        request.activityId(), pageResult.productIds(), false, repairLimit);
        log.info("Activity product library state repair after refresh, activityId={}, scope={}, scanned={}, promoting={}, willSelectToLibrary={}, willDisplay={}, unchanged={}",
                request.activityId(),
                pageResult.complete() ? "FULL_ACTIVITY" : "FETCHED_PRODUCTS",
                repairResult.scanned(),
                repairResult.promoting(),
                repairResult.willSelectToLibrary(),
                repairResult.willDisplay(),
                repairResult.unchanged());
        if (pageResult.complete()) {
            productDisplayRuleService.applyForActivityId(request.activityId());
        } else {
            productDisplayRuleService.applyForProductIds(pageResult.productIds());
        }
        evictActivityProductCache(request.activityId());
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(request.activityId());
        String syncStatus = pageResult.complete() ? "SUCCESS" : syncStatusForStopReason(pageResult.stopReason());
        productDomainEventPublisher.publishActivitySyncCompleted(
                request.activityId(),
                activity == null ? null : activity.getName(),
                "FULL",
                createdCount,
                updatedCount,
                skippedCount,
                syncStatus,
                null);
        log.info("Activity product sync summary, activityId={}, pagesFetched={}, fetchedRows={}, distinctProductIds={}, duplicateProductIds={}, created={}, updated={}, skipped={}, libraryEntryCount={}, pageIntervalMs={}, stoppedReason={}, stillHasNextWhenStopped={}, complete={}",
                request.activityId(),
                pageResult.pagesFetched(),
                pageResult.fetchedRows(),
                pageResult.distinctProductIds(),
                pageResult.duplicateProductIds(),
                createdCount,
                updatedCount,
                skippedCount,
                libraryEntryCount,
                normalizedPageIntervalMs,
                pageResult.stopReason(),
                pageResult.stillHasNextWhenStopped(),
                pageResult.complete());
        return new ActivityProductRefreshResult(
                pageResult.distinctProductIds(),
                libraryEntryCount,
                createdCount,
                updatedCount,
                skippedCount,
                pageResult.pagesFetched(),
                pageResult.fetchedRows(),
                pageResult.distinctProductIds(),
                pageResult.duplicateProductIds(),
                pageResult.stopReason().name(),
                pageResult.stillHasNextWhenStopped(),
                pageResult.complete());
    }

    private StatusPartitionPreflight statusPartitionPreflight(
            DouyinProductGateway.ActivityProductQueryRequest request,
            List<Integer> statuses,
            int pageSize,
            int maxPages,
            int maxRows,
            int parallelism,
            boolean pageMode) {
        long estimatedRows = 0L;
        long estimatedPages = 0L;
        java.util.concurrent.atomic.AtomicInteger preflightPageCounter = new java.util.concurrent.atomic.AtomicInteger();
        Map<Integer, DouyinProductGateway.ActivityProductListResult> firstPages = new LinkedHashMap<>();
        int preflightParallelism = Math.min(Math.max(parallelism, 1), statuses.size());
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(preflightParallelism);
        List<java.util.concurrent.Future<DouyinProductGateway.ActivityProductListResult>> futures = statuses.stream()
                .map(status -> executor.submit(() -> queryActivityProductsWithRetry(
                        pageMode
                                ? activityProductPageRequestWithStatus(request, status, pageSize, 1L)
                                : activityProductRequestWithStatus(request, status, pageSize, null),
                        preflightPageCounter.getAndIncrement())))
                .toList();
        try {
            for (int index = 0; index < statuses.size(); index++) {
                Integer status = statuses.get(index);
                DouyinProductGateway.ActivityProductListResult firstPage;
                try {
                    firstPage = futures.get(index).get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("Activity product status-partition preflight interrupted, fallback to serial, activityId={}, status={}",
                            request.activityId(), status);
                    return StatusPartitionPreflight.unsafe();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof DouyinApiException douyinApiException
                            && isDouyinRateLimited(douyinApiException)) {
                        throw douyinApiException;
                    }
                    if (cause instanceof DouyinApiException douyinApiException) {
                        log.warn("Activity product status-partition preflight failed, fallback to serial, activityId={}, status={}, errorCode={}, subCode={}, message={}",
                                request.activityId(), status, douyinApiException.getErrorCode(),
                                douyinApiException.getSubCode(), douyinApiException.getErrorMsg());
                    } else {
                        log.warn("Activity product status-partition preflight failed, fallback to serial, activityId={}, status={}, message={}",
                                request.activityId(), status, cause.getMessage());
                    }
                    return StatusPartitionPreflight.unsafe();
                }
                if (firstPage.total() == null) {
                    log.info("Activity product status-partition preflight missing total, activityId={}, status={}",
                            request.activityId(), status);
                    return StatusPartitionPreflight.unsafe();
                }
                if (pageMode && StringUtils.hasText(firstPage.nextCursor())) {
                    log.info("Activity product page-mode preflight received cursor response, fallback to cursor mode, activityId={}, status={}",
                            request.activityId(), status);
                    return new StatusPartitionPreflight(true, Map.copyOf(firstPages), false);
                }
                firstPages.put(status, firstPage);
                long total = Math.max(firstPage.total(), 0L);
                estimatedRows += total;
                estimatedPages += Math.max(1L, (total + pageSize - 1L) / pageSize);
                if (estimatedRows > maxRows || estimatedPages > maxPages) {
                    log.info("Activity product status-partition preflight exceeds safety bound, activityId={}, status={}, estimatedRows={}, estimatedPages={}, maxRows={}, maxPages={}",
                            request.activityId(), status, estimatedRows, estimatedPages, maxRows, maxPages);
                    return StatusPartitionPreflight.unsafe();
                }
            }
            return new StatusPartitionPreflight(true, Map.copyOf(firstPages), pageMode);
        } finally {
            executor.shutdownNow();
        }
    }

    private ActivityProductStatusPartitionFetch fetchActivityProductStatusPartition(
            DouyinProductGateway.ActivityProductQueryRequest request,
            Integer status,
            int pageSize,
            int maxPages,
            int maxRows,
            java.util.concurrent.atomic.AtomicInteger remainingRows,
            long pageIntervalMs,
            java.util.concurrent.atomic.AtomicInteger pageCounter,
            java.util.concurrent.atomic.AtomicInteger progressPageCounter,
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer,
            DouyinProductGateway.ActivityProductListResult preflightFirstPage) {
        List<List<DouyinProductGateway.ActivityProductItem>> pages = pageLibraryRefreshEnabled
                ? List.of()
                : new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean reusePreflightFirstPage =
                new java.util.concurrent.atomic.AtomicBoolean(preflightFirstPage != null);
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                activityProductRequestWithStatus(request, status, pageSize, null),
                new ActivityProductPaginationRunner.Options(pageSize, maxPages, maxRows, true),
                pageRequest -> {
                    if (!StringUtils.hasText(pageRequest.cursor())
                            && reusePreflightFirstPage.compareAndSet(true, false)) {
                        return reserveActivityProductRows(preflightFirstPage, remainingRows);
                    }
                    return reserveActivityProductRows(
                            queryActivityProductsWithRetry(pageRequest, pageCounter.getAndIncrement()),
                            remainingRows);
                },
                page -> {
                    if (pageLibraryRefreshEnabled) {
                        ActivitySnapshotUpsertStats stats = upsertAndRefreshActivityProductPage(
                                request.activityId(), page.items(), page.pageNo());
                        notifyActivityProductRefreshProgress(progressConsumer, progressPageCounter.incrementAndGet());
                        return new ActivityProductPaginationRunner.PageWriteStats(
                                stats.createdCount(),
                                stats.updatedCount(),
                                stats.skippedCount(),
                                stats.libraryEntryCount());
                    }
                    pages.add(List.copyOf(page.items()));
                    notifyActivityProductRefreshProgress(progressConsumer, progressPageCounter.incrementAndGet());
                    return ActivityProductPaginationRunner.PageWriteStats.ZERO;
                },
                pageNo -> sleepBeforeNextActivityProductPage(request.activityId(), pageNo - 1, pageIntervalMs));
        return new ActivityProductStatusPartitionFetch(status, result, pages);
    }

    private DouyinProductGateway.ActivityProductListResult reserveActivityProductRows(
            DouyinProductGateway.ActivityProductListResult page,
            java.util.concurrent.atomic.AtomicInteger remainingRows) {
        if (page == null || page.items() == null || page.items().isEmpty() || remainingRows == null) {
            return page;
        }
        int grantedRows = reserveRows(remainingRows, page.items().size());
        List<DouyinProductGateway.ActivityProductItem> items = grantedRows <= 0
                ? List.of()
                : List.copyOf(page.items().subList(0, grantedRows));
        return new DouyinProductGateway.ActivityProductListResult(
                page.test(),
                page.activityId(),
                page.institutionId(),
                page.total(),
                page.nextCursor(),
                items);
    }

    private int reserveRows(
            java.util.concurrent.atomic.AtomicInteger remainingRows,
            int requestedRows) {
        int normalizedRequestedRows = Math.max(requestedRows, 0);
        while (normalizedRequestedRows > 0) {
            int currentRemainingRows = remainingRows.get();
            if (currentRemainingRows <= 0) {
                return 0;
            }
            int grantedRows = Math.min(currentRemainingRows, normalizedRequestedRows);
            if (remainingRows.compareAndSet(currentRemainingRows, currentRemainingRows - grantedRows)) {
                return grantedRows;
            }
        }
        return 0;
    }

    private record StatusPartitionPreflight(
            boolean safe,
            Map<Integer, DouyinProductGateway.ActivityProductListResult> firstPages,
            boolean pageMode) {

        static StatusPartitionPreflight unsafe() {
            return new StatusPartitionPreflight(false, Map.of(), false);
        }
    }

    private DouyinProductGateway.ActivityProductQueryRequest activityProductRequestWithStatus(
            DouyinProductGateway.ActivityProductQueryRequest request,
            Integer status,
            int pageSize,
            String cursor) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                request.appId(),
                request.activityId(),
                request.searchType(),
                request.sortType(),
                pageSize,
                request.cooperationInfo(),
                request.cooperationType(),
                request.productInfo(),
                status,
                request.retrieveMode(),
                cursor,
                null);
    }

    private DouyinProductGateway.ActivityProductQueryRequest activityProductPageRequestWithStatus(
            DouyinProductGateway.ActivityProductQueryRequest request,
            Integer status,
            int pageSize,
            long page) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                request.appId(),
                request.activityId(),
                request.searchType(),
                request.sortType(),
                pageSize,
                request.cooperationInfo(),
                request.cooperationType(),
                request.productInfo(),
                status,
                0L,
                null,
                page);
    }

    private ActivityProductPaginationRunner.Result aggregateStatusPartitionResults(
            List<ActivityProductStatusPartitionFetch> partitions,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int libraryEntryCount) {
        int pagesFetched = 0;
        int fetchedRows = 0;
        int duplicateProductIds = 0;
        boolean complete = true;
        boolean stillHasNextWhenStopped = false;
        ActivityProductPaginationRunner.StopReason stopReason = ActivityProductPaginationRunner.StopReason.DONE_NO_MORE;
        String lastCursor = null;
        Set<String> productIds = new LinkedHashSet<>();
        List<ActivityProductPaginationRunner.PageSummary> pageSamples = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ActivityProductStatusPartitionFetch partition : partitions) {
            ActivityProductPaginationRunner.Result result = partition.result();
            pagesFetched += result.pagesFetched();
            fetchedRows += result.fetchedRows();
            duplicateProductIds += result.duplicateProductIds();
            for (String productId : result.productIds()) {
                if (!productIds.add(productId)) {
                    duplicateProductIds++;
                }
            }
            pageSamples.addAll(result.pageSamples());
            result.warnings().forEach(warning -> warnings.add("status=" + partition.status() + ": " + warning));
            lastCursor = result.lastCursor();
            if (!result.complete()) {
                if (complete) {
                    stopReason = result.stopReason();
                }
                complete = false;
                stillHasNextWhenStopped = stillHasNextWhenStopped || result.stillHasNextWhenStopped();
            }
        }
        return new ActivityProductPaginationRunner.Result(
                pagesFetched,
                fetchedRows,
                productIds.size(),
                duplicateProductIds,
                createdCount,
                updatedCount,
                skippedCount,
                libraryEntryCount,
                stopReason,
                stillHasNextWhenStopped,
                complete,
                lastCursor,
                List.copyOf(pageSamples),
                List.copyOf(warnings),
                Set.copyOf(productIds));
    }

    private record ActivityProductStatusPartitionFetch(
            int status,
            ActivityProductPaginationRunner.Result result,
            List<List<DouyinProductGateway.ActivityProductItem>> pages) {
    }

    private record ActivityProductPageFetch(
            int status,
            int pageNo,
            int pageCount,
            DouyinProductGateway.ActivityProductListResult page,
            long elapsedMs) {
    }

    private int reconcileActivitySnapshotsAfterCompleteRefresh(
            DouyinProductGateway.ActivityProductQueryRequest request,
            ActivityProductPaginationRunner.Result pageResult) {
        if (request == null
                || pageResult == null
                || !pageResult.complete()
                || !StringUtils.hasText(request.activityId())) {
            return 0;
        }
        Set<String> currentProductIds = pageResult.productIds() == null ? Set.of() : pageResult.productIds();
        UpdateWrapper<ProductSnapshot> wrapper = new UpdateWrapper<>();
        wrapper.eq("activity_id", request.activityId())
                .eq("deleted", 0)
                .set("deleted", 1)
                .set("update_time", LocalDateTime.now());
        Integer status = productDisplayPolicy.normalizeActivityProductFilterStatus(request.status());
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (!currentProductIds.isEmpty()) {
            wrapper.notIn("product_id", currentProductIds);
        }
        return snapshotMapper.update(null, wrapper);
    }

    private int reconcileStatusPartitionSnapshots(
            String activityId,
            int status,
            Set<String> currentProductIds) {
        if (!StringUtils.hasText(activityId)) {
            return 0;
        }
        Set<String> productIds = currentProductIds == null ? Set.of() : currentProductIds;
        UpdateWrapper<ProductSnapshot> wrapper = new UpdateWrapper<>();
        wrapper.eq("activity_id", activityId)
                .eq("deleted", 0)
                .set("deleted", 1)
                .set("update_time", LocalDateTime.now());
        Integer normalizedStatus = productDisplayPolicy.normalizeActivityProductFilterStatus(status);
        if (normalizedStatus != null) {
            wrapper.eq("status", normalizedStatus);
        } else {
            wrapper.eq("status", status);
        }
        if (!productIds.isEmpty()) {
            wrapper.notIn("product_id", productIds);
        }
        return snapshotMapper.update(null, wrapper);
    }

    private String syncStatusForStopReason(ActivityProductPaginationRunner.StopReason stopReason) {
        if (stopReason == ActivityProductPaginationRunner.StopReason.API_ERROR
                || stopReason == ActivityProductPaginationRunner.StopReason.INVALID_RESPONSE) {
            return "FAILED";
        }
        if (stopReason == ActivityProductPaginationRunner.StopReason.MAX_PAGES_REACHED) {
            return "INCOMPLETE_MAX_PAGES";
        }
        if (stopReason == ActivityProductPaginationRunner.StopReason.REPEATED_CURSOR
                || stopReason == ActivityProductPaginationRunner.StopReason.EMPTY_CURSOR_WITH_HAS_NEXT
                || stopReason == ActivityProductPaginationRunner.StopReason.EMPTY_PAGE_WITH_HAS_NEXT) {
            return "INCOMPLETE_CURSOR_ERROR";
        }
        return "PARTIAL";
    }

    private DouyinProductGateway.ActivityProductListResult queryActivityProductsWithRetry(
            DouyinProductGateway.ActivityProductQueryRequest request,
            int pageNo) {
        int maxRetries = Math.max(0, productActivitySyncMaxRetries);
        int attempt = 0;
        while (true) {
            long startedAt = System.nanoTime();
            try {
                DouyinProductGateway.ActivityProductListResult result = douyinProductGateway.queryActivityProducts(request);
                log.info("Activity product page sync succeeded, endpoint=queryActivityProducts, activityId={}, status={}, page={}, size={}, costMs={}",
                        request.activityId(),
                        request.status(),
                        pageNo + 1,
                        request.count(),
                        elapsedMs(startedAt));
                return result;
            } catch (DouyinApiException ex) {
                log.warn("Activity product page sync failed, endpoint=queryActivityProducts, activityId={}, status={}, page={}, size={}, attempt={}, costMs={}, errorCode={}, subCode={}, message={}",
                        request.activityId(),
                        request.status(),
                        pageNo + 1,
                        request.count(),
                        attempt + 1,
                        elapsedMs(startedAt),
                        ex.getErrorCode(),
                        ex.getSubCode(),
                        ex.getErrorMsg());
                if (isDouyinRateLimited(ex) || attempt >= maxRetries) {
                    throw ex;
                }
                sleepBeforeRetry(request.activityId(), pageNo, attempt);
                attempt++;
            } catch (RuntimeException ex) {
                log.warn("Activity product page sync failed, endpoint=queryActivityProducts, activityId={}, status={}, page={}, size={}, attempt={}, costMs={}, message={}",
                        request.activityId(),
                        request.status(),
                        pageNo + 1,
                        request.count(),
                        attempt + 1,
                        elapsedMs(startedAt),
                        ex.getMessage());
                if (attempt >= maxRetries) {
                    throw ex;
                }
                sleepBeforeRetry(request.activityId(), pageNo, attempt);
                attempt++;
            }
        }
    }

    private boolean isDouyinRateLimited(DouyinApiException ex) {
        if (ex == null) {
            return false;
        }
        if (ex.getErrorCode() == 429) {
            return true;
        }
        String text = (ex.getSubCode() + " " + ex.getErrorMsg() + " " + ex.getMessage()).toLowerCase(Locale.ROOT);
        return text.contains("429") || text.contains("rate") || text.contains("limit") || text.contains("限流");
    }

    private void sleepBeforeNextActivityProductPage(String activityId, int pageNo, long pageIntervalMs) {
        sleepQuietly(pageIntervalMs, "page", activityId, pageNo, 0);
    }

    private void notifyActivityProductRefreshProgress(
            java.util.function.Consumer<ActivityProductRefreshProgress> progressConsumer,
            int pagesFetched) {
        if (progressConsumer == null) {
            return;
        }
        try {
            progressConsumer.accept(new ActivityProductRefreshProgress(Math.max(pagesFetched, 0)));
        } catch (Exception ex) {
            log.warn("Activity product refresh progress callback failed, pagesFetched={}, message={}",
                    pagesFetched,
                    ex.getMessage());
        }
    }

    private void sleepBeforeRetry(String activityId, int pageNo, int attempt) {
        long base = normalizedProductActivitySyncPageIntervalMs();
        long backoffMs = Math.min(5000L, base * (1L << Math.min(attempt, 3)));
        sleepQuietly(backoffMs, "retry", activityId, pageNo, attempt);
    }

    private long normalizedProductActivitySyncPageIntervalMs() {
        return Math.min(1000L, Math.max(300L, productActivitySyncPageIntervalMs));
    }

    private long normalizedProductActivitySyncPageIntervalMs(long pageIntervalMs) {
        return Math.min(1000L, Math.max(300L, pageIntervalMs));
    }

    private long normalizedProductActivityPrioritySyncPageIntervalMs(long pageIntervalMs) {
        return Math.min(1000L, Math.max(100L, pageIntervalMs));
    }

    private void sleepQuietly(long millis, String phase, String activityId, int pageNo, int attempt) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Activity product sync interrupted before {}, activityId={}, page={}, attempt={}",
                    phase,
                    activityId,
                    pageNo + 1,
                    attempt + 1);
            throw BusinessException.stateInvalid("活动商品同步被中断");
        }
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    record ActivitySnapshotUpsertStats(
            int createdCount,
            int updatedCount,
            int skippedCount,
            int libraryEntryCount,
            int unchangedCount) {

        ActivitySnapshotUpsertStats(int createdCount, int updatedCount, int skippedCount, int libraryEntryCount) {
            this(createdCount, updatedCount, skippedCount, libraryEntryCount, 0);
        }
    }

    public Map<String, Object> buildActivityProductListView(
            DouyinProductGateway.ActivityProductListResult result) {
        Map<String, Object> data = new LinkedHashMap<>(result.toMap());
        data.remove("test");
        List<DouyinProductGateway.ActivityProductItem> items = result.items();
        if (items == null || items.isEmpty()) {
            data.put("items", List.of());
            return data;
        }

        Set<String> productIds = items.stream()
                .map(item -> String.valueOf(item.productId()))
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, ProductOperationState> stateMap = operationStateMapper.selectList(
                        new LambdaQueryWrapper<ProductOperationState>()
                                .eq(ProductOperationState::getActivityId, String.valueOf(result.activityId()))
                                .in(ProductOperationState::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(ProductOperationState::getProductId, Function.identity(), (left, right) -> left));
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<String, DecisionSummary> decisionSummaryMap = buildDecisionSummaryMap(String.valueOf(result.activityId()), productIds);

        data.put("items", items.stream().map(item -> {
            Map<String, Object> view = new LinkedHashMap<>(item.toMap());
            view.put("relationId", buildSnapshotId(String.valueOf(result.activityId()), String.valueOf(item.productId())));
            ProductOperationState state = stateMap.get(String.valueOf(item.productId()));
            DecisionSummary decisionSummary = decisionSummaryMap.get(String.valueOf(item.productId()));
            if (state != null) {
                ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
                view.put("boundActivityId", state.getBoundActivityId());
                view.put("assigneeId", state.getAssigneeId());
                view.put("assigneeName", resolveUserDisplayName(state.getAssigneeId(), assigneeNameMap));
                view.put("auditStatus", state.getAuditStatus());
                view.put("auditRemark", state.getAuditRemark());
                view.put("shortLink", state.getShortLink());
                view.put("promoteLink", state.getPromoteLink());
                applyActivityProductStatusFields(view, item.status(), state);
            } else {
                ProductBizStatus bizStatus = ProductBizStatus.PENDING_AUDIT;
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
                applyActivityProductStatusFields(view, item.status(), null);
            }
            applyDecisionSummary(view, decisionSummary);
            return view;
        }).toList());
        return data;
    }

    public boolean hasActivitySnapshots(String activityId) {
        Long count = snapshotMapper.selectCount(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId));
        return count != null && count > 0;
    }

    public Map<String, Object> buildActivityProductListViewFromDb(
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus,
            Integer promotionStatus) {
        return buildActivityProductListViewFromDb(
                activityId, count, cursor, productInfo, bizStatus, promotionStatus, "default", null, null);
    }

    public Map<String, Object> buildActivityProductListViewFromDb(
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus,
            Integer promotionStatus,
            String sortBy) {
        return buildActivityProductListViewFromDb(
                activityId, count, cursor, productInfo, bizStatus, promotionStatus, sortBy, null, null);
    }

    public Map<String, Object> buildActivityProductListViewFromDb(
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus,
            Integer promotionStatus,
            String sortBy,
            String goodsTags,
            String productTags) {
        return withActivityProductListCache(
                activityId,
                activityProductListCacheQuery(count, cursor, productInfo, bizStatus, promotionStatus, sortBy, goodsTags, productTags),
                () -> buildActivityProductListViewFromDbUncached(
                        activityId, count, cursor, productInfo, bizStatus, promotionStatus, sortBy, goodsTags, productTags));
    }

    private Map<String, Object> buildActivityProductListViewFromDbUncached(
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus,
            Integer promotionStatus,
            String sortBy,
            String goodsTags,
            String productTags) {
        int pageSize = Math.min(Math.max(count == null ? 20 : count, 1), 20);
        int offset = parseCursor(cursor);
        BizStatusFilter bizStatusFilter = resolveBizStatusFilter(activityId, bizStatus);
        if (bizStatusFilter.isEmptyFilter()) {
            return emptyActivityProductListView(activityId);
        }
        Set<String> auditTagProductScope = resolveActivityAuditTagProductScope(activityId, goodsTags, productTags);
        if (auditTagProductScope != null && auditTagProductScope.isEmpty()) {
            return emptyActivityProductListView(activityId);
        }
        Integer normalizedPromotionStatus = productDisplayPolicy.normalizeActivityProductFilterStatus(promotionStatus);
        if (normalizedPromotionStatus != null
                && !productDisplayPolicy.isSupportedActivityProductQueryStatus(normalizedPromotionStatus)) {
            return emptyActivityProductListView(activityId);
        }

        LambdaQueryWrapper<ProductSnapshot> countWrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .and(StringUtils.hasText(productInfo), w -> w.like(ProductSnapshot::getTitle, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getProductId, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getShopName, productInfo.trim()));
        applyActivityProductPromotionStatusFilter(countWrapper, normalizedPromotionStatus);
        applyBizStatusFilter(countWrapper, bizStatusFilter);
        applyProductIdScope(countWrapper, auditTagProductScope);
        Long total = snapshotMapper.selectCount(countWrapper);

        LambdaQueryWrapper<ProductSnapshot> queryWrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .and(StringUtils.hasText(productInfo), w -> w.like(ProductSnapshot::getTitle, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getProductId, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getShopName, productInfo.trim()))
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        applyActivityProductPromotionStatusFilter(queryWrapper, normalizedPromotionStatus);
        applyBizStatusFilter(queryWrapper, bizStatusFilter);
        applyProductIdScope(queryWrapper, auditTagProductScope);

        String normalizedSortBy = productDisplayPolicy.normalizeActivityProductSortBy(sortBy);
        List<ProductSnapshot> snapshots;
        List<Map<String, Object>> items;
        int nextOffset;
        boolean hasMore;
        if ("latest".equals(normalizedSortBy)) {
            Page<ProductSnapshot> snapshotPage = new Page<>(offset / pageSize + 1, pageSize);
            snapshots = snapshotMapper.selectPage(snapshotPage, queryWrapper).getRecords();
            if (snapshots.isEmpty()) {
                return emptyActivityProductListView(activityId, total == null ? 0 : total);
            }
            items = buildActivityProductItems(activityId, snapshots);
            sortActivityProductItems(items, normalizedSortBy);
            nextOffset = offset + snapshots.size();
            hasMore = total != null && nextOffset < total;
        } else {
            // Use SQL-level sort + pagination instead of loading all records into memory.
            // This avoids O(n) memory + sort for large activity product lists (e.g. 700+ snapshots).
            List<String> includeIds = bizStatusFilter.includeProductIds().isEmpty()
                    ? null : new ArrayList<>(bizStatusFilter.includeProductIds());
            List<String> excludeIds = bizStatusFilter.excludeProductIds().isEmpty()
                    ? null : new ArrayList<>(bizStatusFilter.excludeProductIds());
            List<String> productIdScope = (auditTagProductScope == null || auditTagProductScope.isEmpty())
                    ? null : new ArrayList<>(auditTagProductScope);
            snapshots = snapshotMapper.selectPageSorted(
                    activityId,
                    normalizedPromotionStatus,
                    StringUtils.hasText(productInfo) ? productInfo.trim() : null,
                    bizStatusFilter.mode().name(),
                    includeIds,
                    excludeIds,
                    productIdScope,
                    pageSize,
                    offset,
                    java.time.LocalDateTime.now());
            if (snapshots.isEmpty()) {
                return emptyActivityProductListView(activityId, total == null ? 0 : total);
            }
            items = buildActivityProductItems(activityId, snapshots);
            // SQL already sorted; only in-memory pinned re-sort needed (rare, lightweight).
            sortActivityProductItems(items, normalizedSortBy);
            nextOffset = offset + snapshots.size();
            // If we got a full page, there are more results.
            hasMore = snapshots.size() == pageSize;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityId", activityId);
        result.put("institutionId", 0);
        result.put("total", total == null ? items.size() : total);
        result.put("nextCursor", hasMore ? String.valueOf(nextOffset) : "");
        result.put("hasMore", hasMore);
        result.put("items", items);
        result.put("statusCounts", loadActivityProductStatusCounts(activityId));
        return result;
    }

    private Map<String, Object> loadActivityProductStatusCounts(String activityId) {
        if (activityProductRedisCacheService != null) {
            return activityProductRedisCacheService.getOrLoadActivityProductStatusCounts(
                    activityId,
                    () -> loadActivityProductStatusCountsUncached(activityId));
        }
        return loadActivityProductStatusCountsUncached(activityId);
    }

    private Map<String, Object> loadActivityProductStatusCountsUncached(String activityId) {
        Map<String, Object> raw = snapshotMapper.selectActivityStatusCounts(activityId);
        return productDisplayPolicy.normalizeActivityProductStatusCounts(raw);
    }

    private Map<String, Object> withActivityProductListCache(
            String activityId,
            String queryKey,
            Supplier<Map<String, Object>> loader) {
        if (activityProductRedisCacheService == null) {
            return loader.get();
        }
        return activityProductRedisCacheService.getOrLoadActivityProductList(activityId, queryKey, loader);
    }

    private String activityProductListCacheQuery(
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus,
            Integer promotionStatus,
            String sortBy,
            String goodsTags,
            String productTags) {
        return String.join("|",
                "count=" + (count == null ? "" : count),
                "cursor=" + normalizeCacheToken(cursor),
                "productInfo=" + normalizeCacheToken(productInfo),
                "bizStatus=" + normalizeCacheToken(bizStatus),
                "promotionStatus=" + (promotionStatus == null ? "" : promotionStatus),
                "sortBy=" + normalizeCacheToken(sortBy),
                "goodsTags=" + normalizeCacheToken(goodsTags),
                "productTags=" + normalizeCacheToken(productTags));
    }

    private String normalizeCacheToken(String value) {
        return value == null ? "" : value.trim();
    }

    private void evictActivityProductCache(String activityId) {
        if (activityProductRedisCacheService != null) {
            activityProductRedisCacheService.evictActivity(activityId);
        }
    }

    private List<Map<String, Object>> buildActivityProductItems(String activityId, List<ProductSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        Set<String> productIds = snapshots.stream()
                .map(ProductSnapshot::getProductId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, ProductOperationState> stateMap = operationStateMapper.selectList(
                        new LambdaQueryWrapper<ProductOperationState>()
                                .eq(ProductOperationState::getActivityId, activityId)
                                .in(ProductOperationState::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(ProductOperationState::getProductId, Function.identity(), (left, right) -> left));
        Map<String, DecisionSummary> decisionSummaryMap = buildDecisionSummaryMap(activityId, productIds);
        Map<String, OrderReadFacade.ProductOrderSummary> orderSummaryMap = buildOrderSummaryMap(activityId, productIds);
        Map<String, PromotionSummary> promotionSummaryMap = buildPromotionSummaryMap(activityId, productIds);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, Merchant> merchantMap = buildMerchantMap(snapshots.stream()
                .map(ProductSnapshot::getShopId)
                .collect(Collectors.toCollection(HashSet::new)));
        String activityName = colonelActivityMapper.selectByActivityId(activityId) == null
                ? null : colonelActivityMapper.selectByActivityId(activityId).getName();
        return snapshots.stream()
                .map(snapshot -> toActivityProductView(
                        snapshot,
                        stateMap.get(snapshot.getProductId()),
                        decisionSummaryMap.get(snapshot.getProductId()),
                        orderSummaryMap.get(snapshot.getProductId()),
                        promotionSummaryMap.get(snapshot.getProductId()),
                        lookupMerchant(merchantMap, snapshot.getShopId()),
                        assigneeNameMap,
                        activityName))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static final long ACTIVITY_SNAPSHOT_BATCH_SIZE = 200L;

    private List<ProductSnapshot> loadAllActivitySnapshots(LambdaQueryWrapper<ProductSnapshot> queryWrapper) {
        List<ProductSnapshot> all = new ArrayList<>();
        long pageNo = 1L;
        while (true) {
            Page<ProductSnapshot> page = snapshotMapper.selectPage(
                    new Page<>(pageNo, ACTIVITY_SNAPSHOT_BATCH_SIZE),
                    queryWrapper);
            List<ProductSnapshot> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            all.addAll(records);
            if (page.getTotal() <= pageNo * ACTIVITY_SNAPSHOT_BATCH_SIZE) {
                break;
            }
            pageNo++;
        }
        return all;
    }

    private Map<String, Object> emptyActivityProductListView(String activityId) {
        return emptyActivityProductListView(activityId, 0L);
    }

    private Map<String, Object> emptyActivityProductListView(String activityId, long total) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("activityId", activityId);
        empty.put("institutionId", 0);
        empty.put("total", total);
        empty.put("nextCursor", "");
        empty.put("hasMore", false);
        empty.put("items", List.of());
        empty.put("statusCounts", loadActivityProductStatusCounts(activityId));
        return empty;
    }

    public List<Map<String, Object>> listActivityProductSkus(String productId) {
        if (!StringUtils.hasText(productId)) {
            return List.of();
        }
        try {
            return douyinProductGateway.queryProductSkus(productId.trim()).stream()
                    .map(sku -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("skuId", sku.skuId());
                        row.put("skuName", sku.skuName());
                        row.put("price", sku.price());
                        row.put("priceText", formatSkuPriceText(sku.price()));
                        row.put("stock", sku.stock());
                        row.put("cover", sku.cover());
                        return row;
                    })
                    .toList();
        } catch (Exception ex) {
            log.warn("query product skus failed productId={}", productId, ex);
            return List.of();
        }
    }

    private String formatSkuPriceText(Long priceInFen) {
        if (priceInFen == null || priceInFen <= 0) {
            return "-";
        }
        return "¥" + BigDecimal.valueOf(priceInFen).movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    public Map<String, Object> getActivityProductDetail(String activityId, String productId) {
        ProductSnapshot snapshot = getSnapshot(activityId, productId);
        ProductOperationState state = getOperationState(activityId, productId);
        DecisionSummary decisionSummary = findDecisionSummary(activityId, productId);
        OrderReadFacade.ProductOrderSummary orderSummary = findOrderSummary(activityId, productId);
        PromotionSummary promotionSummary = findPromotionSummary(activityId, productId);
        Merchant merchant = findMerchant(snapshot.getShopId());
        String activityName = colonelActivityMapper.selectByActivityId(activityId) == null
                ? null : colonelActivityMapper.selectByActivityId(activityId).getName();

        Map<String, Object> detail = toActivityProductView(snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant, null, activityName);
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        if (state != null) {
            detail.put("promotionScene", state.getPromotionScene());
            detail.put("externalUniqueId", state.getExternalUniqueId());
            detail.put("lastOperationAt", state.getLastOperationAt());
        }
        detail.put("auditSupplement", auditSupplement);
        detail.put("promotionLinks", promotionSummary == null ? List.of() : promotionSummary.linkRecords());
        detail.put("promotionMaterialPack", buildPromotionMaterialPack(snapshot, state, merchant, auditSupplement));
        detail.put("followRecords", talentFollowService.listByProduct(activityId, productId));
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> putIntoLibrary(
            String activityId,
            String productId,
            UUID operatorId,
            UUID operatorDeptId) {
        ProductSnapshot snapshot = ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        if (Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            Map<String, Object> existingDetail = getActivityProductDetail(activityId, productId);
            existingDetail.put("selectedToLibrary", true);
            existingDetail.put("libraryVisible", true);
            return existingDetail;
        }
        requireUpstreamPromotingForLibraryEntry(snapshot);
        state.setSelectedToLibrary(true);
        state.setSelectedAt(LocalDateTime.now());
        state.setSelectedBy(operatorId);
        state.setLastOperationAt(LocalDateTime.now());
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        state.setAuditStatus(2);
        state.setAuditRemark(AUTO_APPROVE_PROMOTING_REMARK);
        state.setBizStatus(ProductBizStatus.APPROVED.name());

        if (state.getId() == null) {
            operationStateMapper.insert(state);
        } else {
            persistOperationState(state);
        }

        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setBeforeStatus(currentStatus == null ? null : currentStatus.name());
        log.setAfterStatus(state.getBizStatus());
        log.setSuccess(true);
        log.setOperationType("LIBRARY_ENTRY");
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationRemark("上游状态为推广中，已加入商品库");
        log.setOperationPayload("{eventLabel=加入商品库, productTitle=" + safeText(snapshot.getTitle(), "活动商品") + "}");
        operationLogMapper.insert(log);

        Map<String, Object> detail = getActivityProductDetail(activityId, productId);
        detail.put("selectedToLibrary", true);
        detail.put("libraryVisible", true);
        productDisplayRuleService.applyForProductId(productId);
        evictActivityProductCache(activityId);
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindActivity(
            String activityId,
            String productId,
            String boundActivityId,
            UUID operatorId,
            UUID operatorDeptId) {
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        assertCanBindActivityInDept(state, operatorDeptId);
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        state.setBoundActivityId(StringUtils.hasText(boundActivityId) ? boundActivityId.trim() : null);
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            persistOperationState(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boundActivityId", state.getBoundActivityId());
        payload.put("eventLabel", "商品活动绑定已更新");

        ProductOperationLog log = new ProductOperationLog();
        log.setId(UUID.randomUUID());
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType("BIND_ACTIVITY");
        log.setBeforeStatus(currentStatus.name());
        log.setAfterStatus(currentStatus.name());
        log.setSuccess(true);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark("绑定活动成功");
        operationLogMapper.insert(log);
        evictActivityProductCache(activityId);
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assignProduct(
            String activityId,
            String productId,
            UUID assigneeId,
            UUID operatorId,
            UUID operatorDeptId) {
        if (assigneeId == null) {
            throw BusinessException.param("assigneeId 不能为空");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        ensurePostAuditLibraryFlag(state, operatorId);
        requireSelectedToLibrary(state, "分配招商");
        UUID oldAssigneeId = state.getAssigneeId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", resolveUserDisplayName(assigneeId));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", resolveUserDisplayName(operatorId));
        payload.put("eventLabel", "商品已分配给招商组长");
        productBizStatusService.changeStatus(
                state,
                ProductBizStatus.ASSIGNED,
                "ASSIGN",
                operatorId,
                operatorDeptId,
                payload,
                "分配招商成功",
                current -> current.setAssigneeId(assigneeId)
        );
        if (!java.util.Objects.equals(oldAssigneeId, assigneeId)) {
            productDomainEventPublisher.publishProductOwnerChanged(
                    activityId,
                    productId,
                    oldAssigneeId,
                    assigneeId,
                    operatorId);
        }
        evictActivityProductCache(activityId);
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assignAuditOwner(
            String activityId,
            String productId,
            UUID assigneeId,
            UUID operatorId,
            UUID operatorDeptId) {
        if (assigneeId == null) {
            throw BusinessException.param("assigneeId 不能为空");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        if (currentStatus != ProductBizStatus.PENDING_AUDIT) {
            throw BusinessException.stateInvalid("仅待审核商品可分配审核人");
        }
        state.setAssigneeId(assigneeId);
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            persistOperationState(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", resolveUserDisplayName(assigneeId));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", resolveUserDisplayName(operatorId));
        payload.put("eventLabel", "商品已分配给审核负责人");
        productBizStatusService.logStatusChange(
                activityId,
                productId,
                "ASSIGN_AUDIT",
                currentStatus,
                currentStatus,
                operatorId,
                operatorDeptId,
                payload,
                "分配审核人成功",
                true,
                null
        );
        evictActivityProductCache(activityId);
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditProduct(
            String activityId,
            String productId,
            boolean approved,
            String reason,
            UUID operatorId,
            UUID operatorDeptId) {
        return auditProduct(activityId, productId, approved, reason, null, operatorId, operatorDeptId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditProduct(
            String activityId,
            String productId,
            boolean approved,
            String reason,
            Map<String, Object> supplement,
            UUID operatorId,
            UUID operatorDeptId) {
        if (!approved && !StringUtils.hasText(reason)) {
            throw BusinessException.stateInvalid("审核拒绝时必须填写原因");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        if (beforeStatus == null) {
            beforeStatus = ProductBizStatus.PENDING_AUDIT;
        }
        String auditPayload = null;
        if (approved) {
            validateAuditSupplement(supplement);
            auditPayload = writeAuditPayload(supplement);
            assertNoRecentLibraryDuplicateForAudit(activityId, productId);
        }
        final String approvedAuditPayload = auditPayload;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        payload.put("reason", reason);
        state.setLastOperationAt(LocalDateTime.now());
        state.setAuditStatus(approved ? 2 : 3);
        state.setAuditRemark(approved ? null : reason);
        state.setAuditPayload(approvedAuditPayload);
        if (approved) {
            state.setSelectedToLibrary(true);
            state.setSelectedAt(LocalDateTime.now());
            state.setSelectedBy(operatorId);
            payload.put("eventLabel", "审核通过并加入商品库");
            payload.put("selectedToLibrary", true);
            payload.put("libraryVisible", true);
            payload.put("supplement", normalizeAuditSupplement(supplement));
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.APPROVED,
                    "AUDIT",
                    operatorId,
                    operatorDeptId,
                    payload,
                    "审核通过，已加入商品库",
                    current -> {
                        current.setAuditStatus(2);
                        current.setAuditRemark(null);
                        current.setAuditPayload(approvedAuditPayload);
                        current.setSelectedToLibrary(true);
                        current.setSelectedAt(LocalDateTime.now());
                        current.setSelectedBy(operatorId);
                        current.setLastOperationAt(LocalDateTime.now());
                    }
            );
            productDisplayRuleService.applyForProductId(productId);
            evictActivityProductCache(activityId);
            Map<String, Object> detail = getActivityProductDetail(activityId, productId);
            detail.put("selectedToLibrary", true);
            detail.put("libraryVisible", true);
            return detail;
        }

        state.setSelectedToLibrary(false);
        state.setSelectedAt(null);
        state.setSelectedBy(null);
        state.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());
        state.setHiddenReason("审核拒绝");
        payload.put("eventLabel", "审核拒绝");
        productBizStatusService.changeStatus(
                state,
                ProductBizStatus.REJECTED,
                "AUDIT",
                operatorId,
                operatorDeptId,
                payload,
                "审核拒绝",
                current -> {
                    current.setAuditStatus(3);
                    current.setAuditRemark(reason);
                    current.setAuditPayload(null);
                    current.setSelectedToLibrary(false);
                    current.setSelectedAt(null);
                    current.setSelectedBy(null);
                    current.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());
                    current.setHiddenReason("审核拒绝");
                    current.setLastOperationAt(LocalDateTime.now());
                }
        );
        evictActivityProductCache(activityId);
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordProductDecision(
            String activityId,
            String productId,
            String decisionLevel,
            String reason,
            UUID operatorId,
            UUID operatorDeptId) {
        String normalizedLevel = normalizeDecisionLevel(decisionLevel);
        if (!StringUtils.hasText(reason)) {
            throw BusinessException.param("推进判断原因不能为空");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        requireSelectedToLibrary(state, "保存推进判断");
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            operationStateMapper.insert(state);
        } else {
            persistOperationState(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionLevel", normalizedLevel);
        payload.put("decisionLabel", decisionLabel(normalizedLevel));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", resolveUserDisplayName(operatorId));
        payload.put("eventLabel", "商品推进判断已更新");

        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType("DECISION");
        log.setBeforeStatus(currentStatus.name());
        log.setAfterStatus(currentStatus.name());
        log.setSuccess(true);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark(reason.trim());
        operationLogMapper.insert(log);

        evictActivityProductCache(activityId);
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink) {
        return generatePromotionLink(activityId, productId, userId, deptId, externalUniqueId, promotionScene, needShortLink, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId) {
        return generatePromotionLink(
                activityId,
                productId,
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId,
                null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return generatePromotionLinkInternal(
                    activityId,
                    productId,
                    userId,
                    deptId,
                    externalUniqueId,
                    promotionScene,
                    needShortLink,
                    scene,
                    talentId);
        }
        String scopeKey = promotionLinkIdempotencyService.buildScopeKey(userId, activityId, productId, idempotencyKey);
        java.util.Optional<DouyinPromotionGateway.PromotionLinkResult> completed =
                promotionLinkIdempotencyService.findCompleted(scopeKey);
        if (completed.isPresent()) {
            return completed.get();
        }
        if (!promotionLinkIdempotencyService.tryAcquireInFlight(scopeKey)) {
            completed = promotionLinkIdempotencyService.findCompleted(scopeKey);
            if (completed.isPresent()) {
                return completed.get();
            }
            throw BusinessException.idempotencyInProgress("相同 Idempotency-Key 请求正在处理中，请稍后重试");
        }
        try {
            DouyinPromotionGateway.PromotionLinkResult result = generatePromotionLinkInternal(
                    activityId,
                    productId,
                    userId,
                    deptId,
                    externalUniqueId,
                    promotionScene,
                    needShortLink,
                    scene,
                    talentId,
                    idempotencyKey);
            promotionLinkIdempotencyService.markCompleted(scopeKey, result);
            return result;
        } catch (RuntimeException ex) {
            promotionLinkIdempotencyService.releaseInFlight(scopeKey);
            throw ex;
        }
    }

    /**
     * 装配"复制推广简介"上下文（DDD-PRODUCT-004）。
     *
     * <p>负责：
     * <ol>
     *   <li>保证 snapshot 存在（缺失则抛错）</li>
     *   <li>加载或初始化商品操作状态</li>
     *   <li>校验商品已加入商品库（未加入则抛错）</li>
     *   <li>校验业务状态：APPROVED / ASSIGNED / LINKED 三种之一放行</li>
     * </ol>
     *
     * <p>校验失败的异常原样抛出，调用方无需额外处理（与原 {@code generatePromotionLinkCopy} 行为一致）。</p>
     */
    @Override
    public CopyPromotionSupportPort.Context prepareCopyPromotionContext(
            String activityId,
            String productId,
            String actionLabel) {
        ProductSnapshot snapshot = ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        requireSelectedToLibrary(state, actionLabel);
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        if (beforeStatus == null) {
            beforeStatus = ProductBizStatus.fromCode(state.getBizStatus());
        }
        boolean relinkExistingProduct = beforeStatus == ProductBizStatus.LINKED;
        if (beforeStatus != ProductBizStatus.APPROVED
                && beforeStatus != ProductBizStatus.ASSIGNED
                && !relinkExistingProduct) {
            throw BusinessException.stateInvalid("当前状态不允许执行PROMOTION_LINK，当前状态：" + beforeStatus.name());
        }
        return new CopyPromotionSupportPort.Context(snapshot, state);
    }

    @Override
    public CopyPromotionSupportPort.GeneratedPromotionLink generatePromotionLinkForCopy(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey) {
        DouyinPromotionGateway.PromotionLinkResult result = generatePromotionLink(
                activityId,
                productId,
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId,
                idempotencyKey);
        return new CopyPromotionSupportPort.GeneratedPromotionLink(
                result.shortLink(),
                result.promoteLink(),
                result.pickSource());
    }

    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLinkInternal(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId) {
        return generatePromotionLinkInternal(
                activityId,
                productId,
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId,
                null);
    }

    private DouyinPromotionGateway.PromotionLinkResult generatePromotionLinkInternal(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey) {
        ProductSnapshot snapshot = ensureSnapshotExists(activityId, productId);
        NativeColonelBuyinResolution nativeColonelBuyin = resolveColonelBuyinIdForNativeMapping(snapshot.getActivityId(), snapshot.getProductId());
        String finalExternalId = StringUtils.hasText(externalUniqueId) ? externalUniqueId : String.valueOf(userId);
        int finalPromotionScene = promotionScene == null ? 4 : promotionScene;
        String finalScene = normalizePromotionScene(scene);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        requireSelectedToLibrary(state, "生成推广链接");
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        if (beforeStatus == null) {
            beforeStatus = ProductBizStatus.fromCode(state.getBizStatus());
        }
        boolean relinkExistingProduct = beforeStatus == ProductBizStatus.LINKED;
        if (beforeStatus != ProductBizStatus.APPROVED
                && beforeStatus != ProductBizStatus.ASSIGNED
                && !relinkExistingProduct) {
            throw BusinessException.stateInvalid("当前状态不允许执行PROMOTION_LINK，当前状态：" + beforeStatus.name());
        }
        Map<UUID, String> channelCodes = userDomainFacade.loadUserChannelCodesByIds(List.of(userId));
        String userName = userDomainFacade.getUserName(userId);
        String channelUserName = StringUtils.hasText(userName) ? userName : "unknown";
        String operatorName = StringUtils.hasText(userName) ? userName : "system";
        String desiredPickExtra = buildPickExtra(
                userId,
                channelCodes == null ? null : channelCodes.get(userId),
                snapshot.getProductId(),
                snapshot.getActivityId());
        String attemptedPickSource = null;

        try {
            DouyinConvertPort.ConvertResult portResult = douyinConvertPort.convert(
                    new DouyinConvertPort.ConvertCommand(
                            finalExternalId,
                            finalPromotionScene,
                            List.of(snapshot.getProductId()),
                            needShortLink,
                            new DouyinConvertPort.ConvertContext(
                                    userId,
                                    deptId,
                                    snapshot.getProductId(),
                                    snapshot.getActivityId(),
                                    snapshot.getDetailUrl(),
                                    finalScene,
                                    talentId,
                                    desiredPickExtra)));
            DouyinPromotionGateway.PromotionLinkResult result = new DouyinPromotionGateway.PromotionLinkResult(
                    portResult.pickSource(),
                    portResult.pickExtra(),
                    portResult.shortId(),
                    portResult.shortLink(),
                    portResult.promoteLink(),
                    portResult.uuidSeed());
            attemptedPickSource = result.pickSource();

            // 1. 保存 PromotionLink
            PromotionLink link = new PromotionLink();
            link.setId(UUID.randomUUID());
            link.setProductId(snapshot.getProductId());
            link.setActivityId(snapshot.getActivityId());
            link.setTalentId(talentId);
            link.setChannelUserId(userId);
            link.setChannelUserName(channelUserName);
            link.setOriginalProductUrl(snapshot.getDetailUrl());
            link.setPromotionUrl(result.promoteLink());
            link.setShortUrl(result.shortLink());
            link.setPickSource(result.pickSource());
            link.setPickExtra(result.pickExtra());
            link.setLinkStatus("ACTIVE");
            link.setOperatorId(userId);
            link.setOperatorName(operatorName);
            link.setCreatedAt(LocalDateTime.now());
            link.setUpdatedAt(LocalDateTime.now());
            promotionLinkRecordFacade.save(link);

            // 2. 保存 PickSourceMapping (用于订单归因反查)
            if (nativeColonelBuyin.resolved()) {
                log.info("Native mapping resolved for activityId={}, productId={}, colonelBuyinId={}, source={}",
                        snapshot.getActivityId(),
                        snapshot.getProductId(),
                        nativeColonelBuyin.colonelBuyinId(),
                        nativeColonelBuyin.source());
                pickSourceMappingService.saveOrUpdate(
                        userId,
                        channelUserName,
                        deptId,
                        talentId,
                        null,
                        result.shortId(),
                        null,
                        result.pickSource(),
                        snapshot.getProductId(),
                        snapshot.getActivityId(),
                        snapshot.getDetailUrl(),
                        result.promoteLink(),
                        link.getId(),
                        finalScene,
                        result.pickExtra(),
                        nativeColonelBuyin.colonelBuyinId(),
                        PickSourceMappingService.SOURCE_TYPE_NATIVE
                );
            } else {
                log.warn("Skip native mapping creation because colonel_buyin_id is unresolved, activityId={}, productId={}, source={}",
                        snapshot.getActivityId(),
                        snapshot.getProductId(),
                        nativeColonelBuyin.source());
                pickSourceMappingService.saveOrUpdate(
                        userId,
                        channelUserName,
                        deptId,
                        talentId,
                        null,
                        result.shortId(),
                        null,
                        result.pickSource(),
                        snapshot.getProductId(),
                        snapshot.getActivityId(),
                        snapshot.getDetailUrl(),
                        result.promoteLink(),
                        link.getId(),
                        finalScene,
                        result.pickExtra()
                );
            }
            log.info("promotion_convert_result=success product_id={} channel_id={} activity_id={} pick_source={} scene={} result=success",
                    snapshot.getProductId(),
                    userId,
                    snapshot.getActivityId(),
                    result.pickSource(),
                    finalScene);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promotionScene", finalPromotionScene);
            payload.put("needShortLink", needShortLink);
            payload.put("externalUniqueId", finalExternalId);
            payload.put("scene", finalScene);
            payload.put("talentId", talentId);
            payload.put("shortLink", result.shortLink());
            payload.put("promoteLink", result.promoteLink());
            payload.put("pickSource", result.pickSource());
            if (relinkExistingProduct) {
                state.setPromoteLink(result.promoteLink());
                state.setShortLink(result.shortLink());
                state.setPromotionScene(finalPromotionScene);
                state.setExternalUniqueId(finalExternalId);
                persistOperationState(state);
                productBizStatusService.logStatusChange(
                        snapshot.getActivityId(),
                        snapshot.getProductId(),
                        "PROMOTION_LINK",
                        ProductBizStatus.LINKED,
                        ProductBizStatus.LINKED,
                        userId,
                        deptId,
                        payload,
                        "已转链商品重新生成推广链接",
                        true,
                        null
                );
                evictActivityProductCache(snapshot.getActivityId());
                publishPromotionLinkGenerated(snapshot, link, result, userId, deptId, talentId, finalPromotionScene, finalScene, idempotencyKey);
                return result;
            }
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.LINKED,
                    "PROMOTION_LINK",
                    userId,
                    deptId,
                    payload,
                    "转链成功",
                    current -> {
                        current.setPromoteLink(result.promoteLink());
                        current.setShortLink(result.shortLink());
                        current.setPromotionScene(finalPromotionScene);
                        current.setExternalUniqueId(finalExternalId);
                    }
            );
            evictActivityProductCache(snapshot.getActivityId());
            publishPromotionLinkGenerated(snapshot, link, result, userId, deptId, talentId, finalPromotionScene, finalScene, idempotencyKey);
            return result;
        } catch (RuntimeException ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promotionScene", finalPromotionScene);
            payload.put("needShortLink", needShortLink);
            payload.put("externalUniqueId", finalExternalId);
            payload.put("scene", finalScene);
            payload.put("talentId", talentId);
            productBizStatusService.logFailure(
                    activityId,
                    productId,
                    beforeStatus,
                    "PROMOTION_LINK",
                    userId,
                    deptId,
                    payload,
                    "转链失败",
                    ex.getMessage()
            );
            log.warn("promotion_convert_result=failed product_id={} channel_id={} activity_id={} pick_source={} scene={} result=failed error={}",
                    productId,
                    userId,
                    activityId,
                    attemptedPickSource,
                    finalScene,
                    ex.getMessage());
            throw ex;
        }
    }

    private void publishPromotionLinkGenerated(
            ProductSnapshot snapshot,
            PromotionLink link,
            DouyinPromotionGateway.PromotionLinkResult result,
            UUID userId,
            UUID deptId,
            String talentId,
            Integer promotionScene,
            String scene,
            String idempotencyKey) {
        productDomainEventPublisher.publishPromotionLinkGenerated(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                talentId,
                userId,
                deptId,
                link.getId(),
                result.pickSource(),
                result.promoteLink(),
                result.shortLink(),
                scene,
                promotionScene,
                idempotencyKey);
    }

    private String buildPickExtra(UUID userId, String channelCodeValue, String productId, String activityId) {
        if (userId == null) {
            return null;
        }
        String compactUserId = userId.toString().replace("-", "");
        String channelCode = StringUtils.hasText(channelCodeValue)
                ? channelCodeValue.trim().toLowerCase(Locale.ROOT)
                : compactUserId;
        com.colonel.saas.domain.config.facade.dto.PromotionTemplateDTO template =
                configDomainFacade == null ? null : configDomainFacade.getPromotionTemplate();
        String format = template == null || !StringUtils.hasText(template.pickExtraFormat())
                ? "channel_{channel_code}"
                : template.pickExtraFormat().trim();
        String candidate = format
                .replace("{channel_code}", channelCode)
                .replace("{channelCode}", channelCode)
                .replace("{channel_id}", compactUserId)
                .replace("{channelId}", compactUserId)
                .replace("{user_id}", compactUserId)
                .replace("{userId}", compactUserId)
                .replace("{product_id}", safePickExtraToken(productId))
                .replace("{productId}", safePickExtraToken(productId))
                .replace("{activity_id}", safePickExtraToken(activityId))
                .replace("{activityId}", safePickExtraToken(activityId));
        if (!StringUtils.hasText(candidate)) {
            candidate = "channel_" + channelCode;
        }
        return normalizePickExtraValue(encodePickExtra(candidate, template == null ? "none" : template.pickExtraEncode()));
    }

    private String safePickExtraToken(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String encodePickExtra(String candidate, String encode) {
        if (!StringUtils.hasText(candidate)) {
            return candidate;
        }
        String normalizedEncode = StringUtils.hasText(encode) ? encode.trim().toLowerCase(Locale.ROOT) : "none";
        return switch (normalizedEncode) {
            case "url" -> URLEncoder.encode(candidate.trim(), StandardCharsets.UTF_8);
            case "base64" -> Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(candidate.trim().getBytes(StandardCharsets.UTF_8));
            default -> candidate;
        };
    }

    private String normalizePickExtraValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toLowerCase(Locale.ROOT);
        if (normalized.length() <= 20) {
            return normalized;
        }
        if (normalized.startsWith("channel_")) {
            String tail = normalized.substring("channel_".length());
            int allowedTailLength = 20 - "channel_".length();
            if (tail.length() > allowedTailLength) {
                tail = tail.substring(0, allowedTailLength);
            }
            return "channel_" + tail;
        }
        return normalized.substring(0, 20);
    }

    private String normalizePromotionScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return "PRODUCT_LIBRARY";
        }
        String normalized = scene.trim().toUpperCase();
        return switch (normalized) {
            case "PRODUCT_LIBRARY", "PRODUCT_DETAIL", "TALENT_SHARE", "SAMPLE_DESK" -> normalized;
            default -> "PRODUCT_LIBRARY";
        };
    }

    /**
     * 从 colonel_activity 表按 activity_id 查询 colonel_buyin_id。
     * 返回的 Long 会自动转 String（19 位数字字符串），供 saveOrUpdate 写入 pick_source_mapping。
     */
    private NativeColonelBuyinResolution resolveColonelBuyinIdForNativeMapping(String activityId, String productId) {
        String colonelBuyinId = resolveColonelBuyinIdFromSnapshot(activityId, productId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.PRODUCT_SNAPSHOT);
        }
        colonelBuyinId = resolveColonelBuyinIdFromOperationState(activityId, productId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.PRODUCT_OPERATION_STATE);
        }
        colonelBuyinId = resolveColonelBuyinIdFromActivity(activityId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.COLONEL_ACTIVITY);
        }
        colonelBuyinId = hydrateColonelActivityMeta(activityId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.ACTIVITY_API_DETAIL);
        }
        return NativeColonelBuyinResolution.unresolved();
    }

    private String resolveColonelBuyinIdFromActivity(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
        if (activity != null && activity.getColonelBuyinId() != null) {
            return String.valueOf(activity.getColonelBuyinId());
        }
        // extra_data 回源：hydrateColonelActivityMeta 已将真实 colonel_buyin_id 存入 extraData JSONB
        Map<String, Object> extraData = colonelActivityMapper.selectExtraDataByActivityId(activityId);
        if (extraData != null && !extraData.isEmpty()) {
            String fromExtra = firstNonBlank(
                    readString(extraData, "colonel_buyin_id", "colonelBuyinId"),
                    readString(readNestedMap(extraData, "extra_data", "extraData"), "colonel_buyin_id", "colonelBuyinId")
            );
            if (StringUtils.hasText(fromExtra)) {
                return fromExtra;
            }
        }
        return null;
    }

    private String hydrateColonelActivityMeta(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        ColonelsettlementActivity previous = colonelActivityMapper.selectByActivityId(activityId);
        LocalDateTime previousEndTime = previous == null ? null : previous.getEndTime();
        try {
            Map<String, Object> response = douyinActivityGateway.activityDetail(null, activityId);
            Map<String, Object> data = readPrimaryDataNode(response);
            Long colonelBuyinId = readLong(data, "colonel_buyin_id", "colonelBuyinId");
            if (colonelBuyinId == null || colonelBuyinId <= 0L) {
                return null;
            }
            LocalDateTime newEndTime = parseDateTimeValue(readString(data, "activity_end_time", "activityEndTime", "end_time", "endTime"));
            colonelActivityMapper.upsertRealActivityMeta(
                    UUID.nameUUIDFromBytes(("real-activity-" + activityId).getBytes(StandardCharsets.UTF_8)),
                    activityId,
                    readString(data, "activity_name", "activityName", "name"),
                    readLong(data, "shop_id", "shopId"),
                    readString(data, "shop_name", "shopName"),
                    colonelBuyinId,
                    readRateDecimal(data, "commission_rate", "commissionRate"),
                    readRateDecimal(data, "service_rate", "serviceRate"),
                    parseDateTimeValue(readString(data, "activity_start_time", "activityStartTime", "start_time", "startTime")),
                    newEndTime,
                    readString(data, "status_text", "statusText", "status"),
                    LocalDateTime.now(),
                    data
            );
            detectAndPublishActivityExtended(activityId, previousEndTime, newEndTime);
            return String.valueOf(colonelBuyinId);
        } catch (Exception ex) {
            // 真实活动详情补水仅作为 native 映射生成前的兜底，不阻断转链主流程。
            log.warn("Hydrate colonel activity meta failed, activityId={}", activityId, ex);
            return null;
        }
    }

    private void detectAndPublishActivityExtended(String activityId, LocalDateTime previousEndTime, LocalDateTime newEndTime) {
        if (newEndTime == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean wasExpired = previousEndTime != null && previousEndTime.isBefore(now);
        boolean nowActive = newEndTime.isAfter(now);
        if (wasExpired && nowActive) {
            productDomainEventPublisher.publishActivityExtended(
                    activityId,
                    previousEndTime.toString(),
                    newEndTime.toString());
            productDisplayRuleService.applyForActivityId(activityId);
        }
    }

    private String resolveColonelBuyinIdFromSnapshot(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        ProductSnapshot snapshot = snapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getProductId, productId)
                .eq(ProductSnapshot::getDeleted, 0)
                .last("limit 1"));
        if (snapshot == null) {
            return null;
        }
        String fromPayload = resolveColonelBuyinIdFromPayload(snapshot.getRawPayload());
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload;
        }
        return extractColonelBuyinIdFromText(snapshot.getRawPayload());
    }

    private String resolveColonelBuyinIdFromOperationState(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        ProductOperationState state = operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId)
                .eq(ProductOperationState::getProductId, productId)
                .eq(ProductOperationState::getDeleted, 0)
                .last("limit 1"));
        if (state == null) {
            return null;
        }
        String fromPayload = resolveColonelBuyinIdFromPayload(state.getAuditPayload());
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload;
        }
        return extractColonelBuyinIdFromText(state.getAuditPayload());
    }

    private Map<String, Object> readPrimaryDataNode(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> data = readNestedMap(response, "data");
        if (!data.isEmpty()) {
            Map<String, Object> detail = readNestedMap(data, "data", "detail");
            if (!detail.isEmpty()) {
                return detail;
            }
            return data;
        }
        return response;
    }

    private Map<String, Object> parseJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String resolveColonelBuyinIdFromPayload(String raw) {
        Map<String, Object> payload = parseJsonObject(raw);
        if (payload.isEmpty()) {
            return null;
        }
        String fromRoot = readString(payload,
                "origin_colonel_buyin_id",
                "originColonelBuyinId",
                "colonel_buyin_id",
                "colonelBuyinId");
        if (StringUtils.hasText(fromRoot)) {
            return fromRoot;
        }
        Map<String, Object> extraData = readNestedMap(payload, "extra_data", "extraData");
        return readString(extraData,
                "origin_colonel_buyin_id",
                "originColonelBuyinId",
                "colonel_buyin_id",
                "colonelBuyinId");
    }

    private String extractColonelBuyinIdFromText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = BUYIN_ID_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Map<String, Object> readNestedMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return Map.of();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() != null) {
                        converted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return converted;
            }
        }
        return Map.of();
    }

    private String readString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            String text = formatValueAsString(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String formatValueAsString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.trim();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            }
            return number.toString();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long readLong(Map<String, Object> source, String... keys) {
        String text = readString(source, keys);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readDecimal(Map<String, Object> source, String... keys) {
        String text = readString(source, keys);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readRateDecimal(Map<String, Object> source, String... keys) {
        BigDecimal value = readDecimal(source, keys);
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value;
        BigDecimal abs = value.abs();
        if (abs.compareTo(new BigDecimal("100")) > 0) {
            normalized = value.divide(new BigDecimal("10000"), 8, RoundingMode.HALF_UP);
        } else if (abs.compareTo(BigDecimal.ONE) > 0) {
            normalized = value.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        }
        if (normalized.abs().compareTo(new BigDecimal("9.9999")) > 0) {
            return null;
        }
        return normalized.setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime parseDateTimeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record NativeColonelBuyinResolution(String colonelBuyinId, NativeColonelBuyinSource source) {
        private static NativeColonelBuyinResolution unresolved() {
            return new NativeColonelBuyinResolution(null, NativeColonelBuyinSource.UNRESOLVED);
        }

        private boolean resolved() {
            return StringUtils.hasText(colonelBuyinId);
        }
    }

    private enum NativeColonelBuyinSource {
        PRODUCT_SNAPSHOT,
        PRODUCT_OPERATION_STATE,
        COLONEL_ACTIVITY,
        ACTIVITY_API_DETAIL,
        UNRESOLVED
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startTalentFollow(
            UUID id,
            UUID talentId,
            String talentName,
            String followStatus,
            String content,
            java.time.LocalDateTime nextFollowTime,
            UUID operatorId,
            String operatorName) {
        ProductSnapshot snapshot = getSnapshotById(id);
        return startTalentFollow(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                talentId,
                talentName,
                followStatus,
                content,
                nextFollowTime,
                operatorId,
                operatorName
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startTalentFollow(
            String activityId,
            String productId,
            UUID talentId,
            String talentName,
            String followStatus,
            String content,
            java.time.LocalDateTime nextFollowTime,
            UUID operatorId,
            String operatorName) {
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        requireSelectedToLibrary(state, "创建达人跟进");
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        TalentFollowStatus normalizedStatus = normalizeFollowStatus(followStatus);

        TalentFollowRecord record = talentFollowService.createRecord(
                activityId,
                productId,
                talentId,
                talentName,
                normalizedStatus.name(),
                content,
                nextFollowTime,
                operatorId,
                operatorName
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("talentId", talentId);
        payload.put("talentName", talentName);
        payload.put("followStatus", normalizedStatus.name());
        payload.put("recordId", record.getId());
        if (beforeStatus == ProductBizStatus.FOLLOWING) {
            productBizStatusService.logStatusChange(
                    activityId,
                    productId,
                    "TALENT_FOLLOW_APPEND",
                    beforeStatus,
                    ProductBizStatus.FOLLOWING,
                    operatorId,
                    null,
                    payload,
                    "追加达人跟进记录",
                    true,
                    null
            );
        } else {
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.FOLLOWING,
                    "TALENT_FOLLOW",
                    operatorId,
                    null,
                    payload,
                    "达人跟进创建成功",
                    current -> {
                    }
            );
        }
        return getActivityProductDetail(activityId, productId);
    }

    public IPage<ProductOperationLog> getOperationLogs(String activityId, String productId, long page, long size) {
        Page<ProductOperationLog> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductOperationLog> wrapper = new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getActivityId, activityId)
                .eq(ProductOperationLog::getProductId, productId)
                .orderByDesc(ProductOperationLog::getCreateTime);
        return operationLogMapper.selectPage(query, wrapper);
    }

    private ProductSnapshot getSnapshotById(UUID id) {
        ProductSnapshot snapshot = snapshotMapper.selectById(id);
        if (snapshot == null) {
            throw BusinessException.notFound("商品不存在");
        }
        return snapshot;
    }

    private ProductSnapshot ensureSnapshotExists(String activityId, String productId) {
        ProductSnapshot snapshot = getSnapshot(activityId, productId);
        if (snapshot == null) {
            throw BusinessException.notFound("未找到商品快照，请先同步活动商品");
        }
        return snapshot;
    }

    private ProductSnapshot getSnapshot(String activityId, String productId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getProductId, productId)
                .last("limit 1"));
    }

    private ProductOperationState getOperationState(String activityId, String productId) {
        return operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId)
                .eq(ProductOperationState::getProductId, productId)
                .last("limit 1"));
    }

    private ProductOperationState getOrInitOperationState(String activityId, String productId) {
        ProductOperationState state = getOperationState(activityId, productId);
        if (state != null) {
            if (!StringUtils.hasText(state.getBizStatus())) {
                state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
            }
            return state;
        }
        state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        state.setAuditStatus(1);
        return state;
    }

    private void fillSnapshot(ProductSnapshot snapshot, DouyinProductGateway.ActivityProductItem item) {
        snapshot.setTitle(item.title());
        snapshot.setCover(item.cover());
        snapshot.setPrice(item.price());
        snapshot.setPriceText(item.priceText());
        snapshot.setShopId(item.shopId());
        snapshot.setShopName(item.shopName());
        snapshot.setStatus(productDisplayPolicy.normalizeActivityProductStatus(item.status()));
        snapshot.setStatusText(productDisplayPolicy.normalizeActivityProductStatusText(item.status(), item.statusText()));
        snapshot.setCategoryName(item.categoryName());
        snapshot.setProductStock(item.productStock());
        snapshot.setSales(item.sales());
        snapshot.setDetailUrl(item.detailUrl());
        snapshot.setPromotionStartTime(item.promotionStartTime());
        snapshot.setPromotionEndTime(item.promotionEndTime());
        snapshot.setActivityCosRatio(item.activityCosRatio());
        snapshot.setActivityCosRatioText(item.activityCosRatioText());
        snapshot.setCosType(item.cosType());
        snapshot.setCosTypeText(item.cosTypeText());
        snapshot.setAdServiceRatio(item.adServiceRatio());
        snapshot.setActivityAdCosRatio(item.activityAdCosRatio());
        snapshot.setHasDouinGoodsTag(item.hasDouinGoodsTag());
        snapshot.setRawPayload(writeSnapshotPayload(item));
        snapshot.setSyncTime(java.time.LocalDateTime.now());
    }

    /**
     * Phase 4-1.5 no-op 优化辅助方法：浅拷贝一个 snapshot 用于比较。
     * 浅拷贝即可，因为后续 fillSnapshot 只覆盖基本类型/字符串字段，引用类型不会被替换。
     */
    private ProductSnapshot cloneSnapshotForCompare(ProductSnapshot source) {
        if (source == null) {
            return null;
        }
        ProductSnapshot copy = new ProductSnapshot();
        copy.setId(source.getId());
        copy.setActivityId(source.getActivityId());
        copy.setProductId(source.getProductId());
        copy.setTitle(source.getTitle());
        copy.setCover(source.getCover());
        copy.setPrice(source.getPrice());
        copy.setPriceText(source.getPriceText());
        copy.setShopId(source.getShopId());
        copy.setShopName(source.getShopName());
        copy.setStatus(source.getStatus());
        copy.setStatusText(source.getStatusText());
        copy.setCategoryName(source.getCategoryName());
        copy.setProductStock(source.getProductStock());
        copy.setSales(source.getSales());
        copy.setDetailUrl(source.getDetailUrl());
        copy.setPromotionStartTime(source.getPromotionStartTime());
        copy.setPromotionEndTime(source.getPromotionEndTime());
        copy.setActivityCosRatio(source.getActivityCosRatio());
        copy.setActivityCosRatioText(source.getActivityCosRatioText());
        copy.setCosType(source.getCosType());
        copy.setCosTypeText(source.getCosTypeText());
        copy.setAdServiceRatio(source.getAdServiceRatio());
        copy.setActivityAdCosRatio(source.getActivityAdCosRatio());
        copy.setHasDouinGoodsTag(source.getHasDouinGoodsTag());
        copy.setSyncTime(source.getSyncTime());
        return copy;
    }

    private boolean snapshotFieldsEqual(ProductSnapshot a, ProductSnapshot b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return java.util.Objects.equals(a.getTitle(), b.getTitle())
                && java.util.Objects.equals(a.getCover(), b.getCover())
                && java.util.Objects.equals(a.getPrice(), b.getPrice())
                && java.util.Objects.equals(a.getPriceText(), b.getPriceText())
                && java.util.Objects.equals(a.getShopId(), b.getShopId())
                && java.util.Objects.equals(a.getShopName(), b.getShopName())
                && java.util.Objects.equals(a.getStatus(), b.getStatus())
                && java.util.Objects.equals(a.getStatusText(), b.getStatusText())
                && java.util.Objects.equals(a.getCategoryName(), b.getCategoryName())
                && java.util.Objects.equals(a.getProductStock(), b.getProductStock())
                && java.util.Objects.equals(a.getSales(), b.getSales())
                && java.util.Objects.equals(a.getDetailUrl(), b.getDetailUrl())
                && java.util.Objects.equals(a.getPromotionStartTime(), b.getPromotionStartTime())
                && java.util.Objects.equals(a.getPromotionEndTime(), b.getPromotionEndTime())
                && java.util.Objects.equals(a.getActivityCosRatio(), b.getActivityCosRatio())
                && java.util.Objects.equals(a.getActivityCosRatioText(), b.getActivityCosRatioText())
                && java.util.Objects.equals(a.getCosType(), b.getCosType())
                && java.util.Objects.equals(a.getCosTypeText(), b.getCosTypeText())
                && java.util.Objects.equals(a.getAdServiceRatio(), b.getAdServiceRatio())
                && java.util.Objects.equals(a.getActivityAdCosRatio(), b.getActivityAdCosRatio())
                && java.util.Objects.equals(a.getHasDouinGoodsTag(), b.getHasDouinGoodsTag());
    }

    private String writeSnapshotPayload(DouyinProductGateway.ActivityProductItem item) {
        try {
            return OBJECT_MAPPER.writeValueAsString(item.toMap());
        } catch (Exception ex) {
            log.warn("Serialize product snapshot payload failed, activityProductItem={}", item.productId(), ex);
            return String.valueOf(item.toMap());
        }
    }

    private Product toLegacyProduct(ProductSnapshot snapshot) {
        return toLegacyProduct(snapshot, null);
    }

    private Product toLegacyProduct(ProductSnapshot snapshot, ProductOperationState providedState) {
        return toLegacyProduct(snapshot, providedState, null, null);
    }

    private Product toLegacyProduct(
            ProductSnapshot snapshot,
            ProductOperationState providedState,
            Map<UUID, String> userDisplayNames) {
        return toLegacyProduct(snapshot, providedState, userDisplayNames, null);
    }

    private Product toLegacyProduct(
            ProductSnapshot snapshot,
            ProductOperationState providedState,
            Map<UUID, String> userDisplayNames,
            Map<String, String> activityNameMap) {
        Product product = new Product();
        product.setId(snapshot.getId());
        product.setProductId(snapshot.getProductId());
        product.setName(snapshot.getTitle());
        product.setPrice(snapshot.getPrice());
        product.setStatus(snapshot.getStatus());
        product.setCategory(snapshot.getCategoryName());
        product.setCategoryName(snapshot.getCategoryName());
        product.setStatusText(snapshot.getStatusText());
        product.setActivityId(toUuid(snapshot.getActivityId()));
        product.setSourceActivityId(snapshot.getActivityId());
        // [V1 必做] 商品库卡片 hover 抽屉需展示「活动」字段。
        // 旧 toLegacyProduct 漏传，导致 drawer 一直显示 -。从 activityNameMap 按 activityId 查名。
        product.setActivityName(
                activityNameMap == null ? null : activityNameMap.get(snapshot.getActivityId())
        );
        // [V1 必做] 商品库卡片 hover 抽屉需展示「店铺评分」字段。
        // 上游 rawPayload.shopScore 经 resolveShopScoreFromSnapshot 解析为 Integer。
        // 缺失/非法统一为 null，前端 parseShopScore 同步处理。
        product.setShopScore(resolveShopScoreFromSnapshot(snapshot));
        product.setCover(snapshot.getCover());
        product.setDetailUrl(snapshot.getDetailUrl());
        product.setShopName(snapshot.getShopName());
        product.setPromotionStartTime(snapshot.getPromotionStartTime());
        product.setPromotionEndTime(snapshot.getPromotionEndTime());
        product.setPriceText(snapshot.getPriceText());
        product.setActivityCosRatio(snapshot.getActivityCosRatio());
        product.setActivityCosRatioText(snapshot.getActivityCosRatioText());
        product.setCosType(snapshot.getCosType());
        product.setCosTypeText(snapshot.getCosTypeText());
        product.setAdServiceRatio(snapshot.getAdServiceRatio());
        product.setActivityAdCosRatio(snapshot.getActivityAdCosRatio());
        BigDecimal serviceFeeRate = resolveServiceFeeRate(snapshot);
        product.setServiceFeeRate(serviceFeeRate);
        product.setEstimatedServiceFee(estimateFee(snapshot.getPrice(), serviceFeeRate).toPlainString());
        product.setSales30d(snapshot.getSales() == null ? 0L : snapshot.getSales());
        product.setHasSampleRule(isSampleRuleAvailable(snapshot));
        product.setSystemTags(buildLibrarySystemTags(snapshot));
        product.setSyncTime(snapshot.getSyncTime());
        product.setCreateTime(snapshot.getCreateTime());
        product.setUpdateTime(snapshot.getUpdateTime());

        ProductOperationState state = providedState != null
                ? providedState
                : getOperationState(snapshot.getActivityId(), snapshot.getProductId());
        product.setAlertTags(buildLibraryAlertTags(snapshot, state));
        if (state != null) {
            product.setCheckStatus(state.getAuditStatus());
            product.setAuditRemark(state.getAuditRemark());
            product.setAssigneeId(state.getAssigneeId());
            product.setPromoteLink(state.getPromoteLink());
            product.setShortLink(state.getShortLink());
            product.setSelectedToLibrary(Boolean.TRUE.equals(state.getSelectedToLibrary()));
            product.setAssigneeName(resolveUserDisplayName(state.getAssigneeId(), userDisplayNames));
            product.setSelectedAt(state.getSelectedAt());
            Map<String, Object> auditSupplement = parseAuditPayload(state.getAuditPayload());
            product.setAuditSupplement(auditSupplement);
            Boolean supportsAds = readBoolean(auditSupplement, "supportsAds");
            if (supportsAds != null) {
                product.setSupportsAds(supportsAds);
            }
            String adsRule = readString(auditSupplement, "adsRule");
            if (StringUtils.hasText(adsRule)) {
                product.setAdsRule(adsRule);
            }
            ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
            if (bizStatus == null) {
                bizStatus = ProductBizStatus.PENDING_AUDIT;
            }
            product.setBizStatus(bizStatus.name());
            product.setBizStatusLabel(bizStatus.getLabel());
            product.setPinned(ProductPinPolicy.isPinned(state, java.time.LocalDateTime.now()));
            product.setPinnedUntil(state.getPinnedUntil());
            var displayPresentation = productDisplayPolicy.resolveDisplayPresentation(
                    true,
                    Boolean.TRUE.equals(state.getSelectedToLibrary()),
                    state.getDisplayStatus(),
                    state.getHiddenReason(),
                    state.getFirstDisplayedAt(),
                    state.getLastDisplayedAt());
            product.setDisplayStatus(displayPresentation.displayStatus().name());
            product.setDisplayStatusLabel(displayPresentation.displayMarkLabel());
            product.setHiddenReason(displayPresentation.hiddenReason());
        }
        return product;
    }

    private UUID buildSnapshotId(String activityId, String productId) {
        return UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8));
    }

    private UUID toUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            DecisionSummary decisionSummary,
            OrderReadFacade.ProductOrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant) {
        return toActivityProductView(snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant, null, null);
    }

    private Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            DecisionSummary decisionSummary,
            OrderReadFacade.ProductOrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant,
            Map<UUID, String> assigneeNameMap,
            String activityName) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", snapshot.getId());
        view.put("relationId", snapshot.getId());
        view.put("activityId", snapshot.getActivityId());
        view.put("activityName", activityName);
        view.put("productId", snapshot.getProductId());
        view.put("title", snapshot.getTitle());
        view.put("cover", snapshot.getCover());
        view.put("price", snapshot.getPrice());
        view.put("priceText", snapshot.getPriceText());
        view.put("shopId", snapshot.getShopId());
        view.put("shopName", snapshot.getShopName());
        Integer activityProductStatus = productDisplayPolicy.normalizeActivityProductStatus(snapshot.getStatus());
        String activityProductStatusText = productDisplayPolicy.normalizeActivityProductStatusText(
                snapshot.getStatus(), snapshot.getStatusText());
        view.put("status", activityProductStatus);
        view.put("statusText", activityProductStatusText);
        view.put("categoryName", snapshot.getCategoryName());
        view.put("productStock", snapshot.getProductStock());
        view.put("shopScore", resolveShopScoreFromSnapshot(snapshot));
        view.put("sales", snapshot.getSales());
        view.put("detailUrl", snapshot.getDetailUrl());
        view.put("promotionStartTime", snapshot.getPromotionStartTime());
        view.put("promotionEndTime", snapshot.getPromotionEndTime());
        view.put("activityCosRatio", snapshot.getActivityCosRatio());
        view.put("activityCosRatioText", snapshot.getActivityCosRatioText());
        view.put("cosType", snapshot.getCosType());
        view.put("cosTypeText", snapshot.getCosTypeText());
        view.put("adServiceRatio", snapshot.getAdServiceRatio());
        view.put("activityAdCosRatio", snapshot.getActivityAdCosRatio());
        view.put("hasDouinGoodsTag", snapshot.getHasDouinGoodsTag());
        view.put("syncTime", snapshot.getSyncTime());

        ProductBizStatus currentStatus = ProductBizStatus.PENDING_AUDIT;
        if (state != null) {
            ProductBizStatus resolvedStatus = productBizStatusService.readBizStatus(state);
            if (resolvedStatus != null) {
                currentStatus = resolvedStatus;
            }
        }
        view.put("bizStatus", currentStatus.name());
        view.put("bizStatusLabel", currentStatus.getLabel());

        if (state != null) {
            view.put("boundActivityId", state.getBoundActivityId());
            view.put("assigneeId", state.getAssigneeId());
            view.put("assigneeName", resolveUserDisplayName(state.getAssigneeId(), assigneeNameMap));
            view.put("auditStatus", state.getAuditStatus());
            view.put("auditRemark", state.getAuditRemark());
            view.put("shortLink", state.getShortLink());
            view.put("promoteLink", state.getPromoteLink());
            view.put("selectedToLibrary", Boolean.TRUE.equals(state.getSelectedToLibrary()));
            view.put("libraryVisible", Boolean.TRUE.equals(state.getSelectedToLibrary()));
            view.put("selectedAt", state.getSelectedAt());
            view.put("pinned", ProductPinPolicy.isPinned(state, LocalDateTime.now()));
            view.put("pinnedUntil", state.getPinnedUntil());
            Map<String, Object> auditSupplement = parseAuditPayload(state.getAuditPayload());
            view.put("auditSupplementSummary", buildAuditSupplementSummary(auditSupplement));
            view.put("auditSupplementComplete", isAuditSupplementComplete(auditSupplement));
            Boolean supportsAds = readBoolean(auditSupplement, "supportsAds");
            if (supportsAds != null) {
                view.put("supportsAds", supportsAds);
            }
            String adsRule = readString(auditSupplement, "adsRule");
            if (StringUtils.hasText(adsRule)) {
                view.put("adsRule", adsRule);
            }
            applyDisplayMark(view, state);
            applyActivityProductStatusFields(view, activityProductStatus, state);
        } else {
            applyDisplayMark(view, null);
            applyActivityProductStatusFields(view, activityProductStatus, null);
        }
        applyDecisionSummary(view, decisionSummary);

        BigDecimal commissionRate = resolveCommissionRate(snapshot);
        BigDecimal serviceFeeRate = resolveServiceFeeRate(snapshot);
        BigDecimal estimatedCommission = estimateFee(snapshot.getPrice(), commissionRate);
        BigDecimal estimatedServiceFee = estimateFee(snapshot.getPrice(), serviceFeeRate);
        LocalDateTime promotionEndTime = parseDateTime(snapshot.getPromotionEndTime());
        long remainingDays = calculateRemainingDays(promotionEndTime);
        boolean activityExpired = promotionEndTime != null && promotionEndTime.isBefore(LocalDateTime.now());
        boolean promotionAvailable = !activityExpired && StringUtils.hasText(snapshot.getDetailUrl());
        boolean hasMaterial = StringUtils.hasText(snapshot.getTitle()) && StringUtils.hasText(snapshot.getDetailUrl());
        boolean hasSampleRule = !activityExpired && StringUtils.hasText(snapshot.getStatusText());
        long platformSales = snapshot.getSales() == null ? 0L : snapshot.getSales();
        long orderCount = orderSummary == null ? 0L : orderSummary.orderCount();
        long attributedCount = orderSummary == null ? 0L : orderSummary.attributedCount();
        long unattributedCount = orderSummary == null ? 0L : orderSummary.unattributedCount();
        BigDecimal gmv = orderSummary == null ? yuan(0L) : yuan(orderSummary.gmvCent());
        BigDecimal serviceFee = orderSummary == null ? yuan(0L) : yuan(orderSummary.serviceFeeCent());

        view.put("commissionRate", commissionRate);
        view.put("serviceFeeRate", serviceFeeRate);
        view.put("estimatedCommission", estimatedCommission.toPlainString());
        view.put("estimatedCommissionAmount", estimatedCommission.toPlainString());
        view.put("estimatedServiceFee", estimatedServiceFee.toPlainString());
        view.put("estimatedServiceFeeAmount", estimatedServiceFee.toPlainString());
        view.put("activityExpired", activityExpired);
        view.put("activityRemainingDays", Math.max(remainingDays, 0));
        view.put("timeLeft", formatTimeLeft(promotionEndTime));
        view.put("promotionAvailable", promotionAvailable);
        view.put("hasMaterial", hasMaterial);
        view.put("hasSampleRule", hasSampleRule);
        view.put("sales30d", platformSales);
        view.put("promotionLinkCount", promotionSummary == null ? 0 : promotionSummary.linkCount());
        view.put("orderCount", orderCount);
        view.put("attributedCount", attributedCount);
        view.put("attributedOrderCount", attributedCount);
        view.put("unattributedCount", unattributedCount);
        view.put("unattributedOrderCount", unattributedCount);
        view.put("gmv", gmv.toPlainString());
        view.put("gmv30d", gmv.toPlainString());
        view.put("serviceFee", serviceFee.toPlainString());
        view.put("lastOrderTime", orderSummary == null ? null : orderSummary.lastOrderTime());
        view.put("attributionRate", formatRate(orderCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(attributedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)));

        if (merchant != null) {
            view.put("merchantId", merchant.getMerchantId());
            view.put("merchantName", merchant.getMerchantName());
            view.put("merchantShopName", merchant.getShopName());
            view.put("merchantStatus", merchant.getStatus());
        } else {
            view.put("merchantId", snapshot.getShopId() == null ? null : String.valueOf(snapshot.getShopId()));
            view.put("merchantName", snapshot.getShopName());
            view.put("merchantShopName", snapshot.getShopName());
            view.put("merchantStatus", null);
        }

        PromotionView promotionView = buildPromotionView(currentStatus, state, promotionSummary);
        view.put("promotionLinkStatus", promotionView.status());
        view.put("promotionLinkStatusLabel", promotionView.statusLabel());
        view.put("promotionLink", promotionView.link());
        view.put("promotionLinkGeneratedAt", promotionView.generatedAt());
        view.put("promotionLinkExpireAt", promotionView.expireAt());
        view.put("promotionLinkFailReason", promotionView.failReason());
        Map<String, Object> promotion = new LinkedHashMap<>();
        promotion.put("status", promotionView.status());
        promotion.put("statusLabel", promotionView.statusLabel());
        promotion.put("link", promotionView.link());
        promotion.put("generatedAt", promotionView.generatedAt());
        promotion.put("expireAt", promotionView.expireAt());
        promotion.put("failReason", promotionView.failReason());
        promotion.put("copyEnabled", StringUtils.hasText(promotionView.link()));
        view.put("promotion", promotion);

        view.put("systemTags", buildSystemTags(snapshot, state, commissionRate, serviceFeeRate, platformSales, promotionSummary, activityExpired, remainingDays));
        view.put("alertTags", buildAlertTags(snapshot, state, commissionRate, serviceFeeRate, activityExpired));
        return view;
    }

    private void applyDecisionSummary(Map<String, Object> view, DecisionSummary decisionSummary) {
        if (decisionSummary == null) {
            view.put("latestDecisionLevel", null);
            view.put("latestDecisionLabel", null);
            view.put("latestDecisionReason", null);
            view.put("latestDecisionAt", null);
            return;
        }
        view.put("latestDecisionLevel", decisionSummary.level());
        view.put("latestDecisionLabel", decisionSummary.label());
        view.put("latestDecisionReason", decisionSummary.reason());
        view.put("latestDecisionAt", decisionSummary.time());
    }

    private String resolveUserDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return resolveUserDisplayName(userId, null);
    }

    private String resolveUserDisplayName(UUID userId, Map<UUID, String> userDisplayNames) {
        if (userId == null) {
            return null;
        }
        if (userDisplayNames != null) {
            return userDisplayNames.get(userId);
        }
        Map<UUID, String> displayLabels = userDomainFacade.loadUserDisplayLabelsByIds(List.of(userId));
        if (displayLabels == null || displayLabels.isEmpty()) {
            return null;
        }
        return normalizeDisplayText(displayLabels.get(userId));
    }

    private UserOwnershipReference resolveUserOwnershipReference(UUID userId) {
        if (userId == null) {
            return null;
        }
        Map<UUID, UserOwnershipReference> references =
                userDomainFacade.loadUserOwnershipReferencesByIds(List.of(userId));
        if (references == null || references.isEmpty()) {
            return null;
        }
        return references.get(userId);
    }

    private int parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private BizStatusFilter resolveBizStatusFilter(String activityId, String bizStatus) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(bizStatus)) {
            return BizStatusFilter.none();
        }
        String normalizedStatus = bizStatus.trim().toUpperCase(Locale.ROOT);
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, activityId)
        );
        if (states == null || states.isEmpty()) {
            return ProductBizStatus.PENDING_AUDIT.name().equals(normalizedStatus)
                    ? BizStatusFilter.none()
                    : BizStatusFilter.empty();
        }
        Set<String> knownProductIds = states.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> matchedProductIds = states.stream()
                .filter(state -> normalizedStatus.equalsIgnoreCase(state.getBizStatus()))
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ProductBizStatus.PENDING_AUDIT.name().equals(normalizedStatus)) {
            return BizStatusFilter.pendingAudit(knownProductIds, matchedProductIds);
        }
        return matchedProductIds.isEmpty()
                ? BizStatusFilter.empty()
                : BizStatusFilter.includeOnly(matchedProductIds);
    }

    private void applyBizStatusFilter(LambdaQueryWrapper<ProductSnapshot> wrapper, BizStatusFilter filter) {
        if (wrapper == null || filter == null || filter.mode() == BizStatusFilterMode.NONE) {
            return;
        }
        if (filter.mode() == BizStatusFilterMode.EMPTY) {
            wrapper.apply("1 = 0");
            return;
        }
        if (filter.mode() == BizStatusFilterMode.INCLUDE_ONLY) {
            wrapper.in(ProductSnapshot::getProductId, filter.includeProductIds());
            return;
        }
        if (filter.mode() == BizStatusFilterMode.PENDING_AUDIT) {
            Set<String> includeIds = filter.includeProductIds();
            Set<String> excludeIds = filter.excludeProductIds();
            if (!includeIds.isEmpty() && !excludeIds.isEmpty()) {
                wrapper.and(w -> w.in(ProductSnapshot::getProductId, includeIds)
                        .or()
                        .notIn(ProductSnapshot::getProductId, excludeIds));
            } else if (!includeIds.isEmpty()) {
                wrapper.in(ProductSnapshot::getProductId, includeIds);
            } else if (!excludeIds.isEmpty()) {
                wrapper.notIn(ProductSnapshot::getProductId, excludeIds);
            }
        }
    }

    private Set<String> resolveActivityAuditTagProductScope(String activityId, String goodsTags, String productTags) {
        List<String> expectedGoodsTags = normalizeTagFilter(goodsTags);
        List<String> expectedProductTags = normalizeTagFilter(productTags);
        if (expectedGoodsTags.isEmpty() && expectedProductTags.isEmpty()) {
            return null;
        }
        if (!StringUtils.hasText(activityId)) {
            return Set.of();
        }
        return operationStateMapper.selectList(new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, activityId))
                .stream()
                .filter(state -> matchesAuditTagFilters(state, expectedGoodsTags, expectedProductTags))
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void applyProductIdScope(LambdaQueryWrapper<ProductSnapshot> wrapper, Set<String> productIds) {
        if (wrapper != null && productIds != null) {
            wrapper.in(ProductSnapshot::getProductId, productIds);
        }
    }

    private boolean matchesAuditTagFilters(
            ProductOperationState state,
            List<String> expectedGoodsTags,
            List<String> expectedProductTags) {
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        return matchesTagFilter(readStringList(auditSupplement, "goodsTags"), expectedGoodsTags)
                && matchesTagFilter(readStringList(auditSupplement, "productTags"), expectedProductTags);
    }

    private boolean matchesTagFilter(List<String> actualTags, List<String> expectedTags) {
        if (expectedTags == null || expectedTags.isEmpty()) {
            return true;
        }
        Set<String> actual = actualTags == null ? Set.of() : actualTags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (actual.isEmpty()) {
            return false;
        }
        return expectedTags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .anyMatch(actual::contains);
    }

    private List<String> normalizeTagFilter(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return List.of();
        }
        return java.util.Arrays.stream(rawTags.split("[,，\\n]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private TalentFollowStatus normalizeFollowStatus(String rawStatus) {
        TalentFollowStatus status = TalentFollowStatus.fromCode(rawStatus);
        return status == null ? TalentFollowStatus.NOT_CONTACTED : status;
    }

    private Map<String, DecisionSummary> buildDecisionSummaryMap(String activityId, Set<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return operationLogMapper.selectList(new LambdaQueryWrapper<ProductOperationLog>()
                        .eq(ProductOperationLog::getActivityId, activityId)
                        .eq(ProductOperationLog::getOperationType, "DECISION")
                        .in(ProductOperationLog::getProductId, productIds)
                        .orderByDesc(ProductOperationLog::getCreateTime))
                .stream()
                .collect(Collectors.toMap(
                        ProductOperationLog::getProductId,
                        log -> {
                            Map<String, String> payload = parseOperationPayloadText(log.getOperationPayload());
                            String level = payload.get("decisionLevel");
                            return new DecisionSummary(
                                    level,
                                    payload.getOrDefault("decisionLabel", decisionLabel(level)),
                                    normalizeFreeText(log.getOperationRemark()),
                                    log.getCreateTime() == null ? null : log.getCreateTime().toString()
                            );
                        },
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private DecisionSummary findDecisionSummary(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        ProductOperationLog log = operationLogMapper.selectOne(new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getActivityId, activityId)
                .eq(ProductOperationLog::getProductId, productId)
                .eq(ProductOperationLog::getOperationType, "DECISION")
                .orderByDesc(ProductOperationLog::getCreateTime)
                .last("limit 1"));
        if (log == null) {
            return null;
        }
        Map<String, String> payload = parseOperationPayloadText(log.getOperationPayload());
        String level = payload.get("decisionLevel");
        return new DecisionSummary(
                level,
                payload.getOrDefault("decisionLabel", decisionLabel(level)),
                normalizeFreeText(log.getOperationRemark()),
                log.getCreateTime() == null ? null : log.getCreateTime().toString()
        );
    }

    private Map<String, String> parseOperationPayloadText(String raw) {
        String text = normalizeFreeText(raw);
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        String trimmed = text.startsWith("{") && text.endsWith("}") ? text.substring(1, text.length() - 1) : text;
        Map<String, String> payload = new LinkedHashMap<>();
        for (String pair : trimmed.split(", ")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (StringUtils.hasText(key)) {
                payload.put(key, value);
            }
        }
        return payload;
    }

    private String normalizeDecisionLevel(String decisionLevel) {
        if (!StringUtils.hasText(decisionLevel)) {
            throw BusinessException.param("推进判断不能为空");
        }
        String normalized = decisionLevel.trim().toUpperCase();
        if (!Set.of("MAIN", "SECONDARY", "PAUSE", "DROP").contains(normalized)) {
            throw BusinessException.param("未知推进判断：" + decisionLevel);
        }
        return normalized;
    }

    private String decisionLabel(String decisionLevel) {
        return switch (decisionLevel) {
            case "MAIN" -> "主推";
            case "SECONDARY" -> "次推";
            case "PAUSE" -> "暂缓";
            case "DROP" -> "放弃";
            default -> decisionLevel;
        };
    }

    private Map<String, OrderReadFacade.ProductOrderSummary> buildOrderSummaryMap(String activityId, Set<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        if (orderReadFacade == null) {
            return Map.of();
        }
        Map<String, OrderReadFacade.ProductOrderSummary> summaries =
                orderReadFacade.summarizeProductOrdersByActivity(activityId, productIds);
        return summaries == null ? Map.of() : summaries;
    }

    private OrderReadFacade.ProductOrderSummary findOrderSummary(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        return buildOrderSummaryMap(activityId, Set.of(productId)).get(productId);
    }

    private Map<String, PromotionSummary> buildPromotionSummaryMap(String activityId, Set<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        if (promotionLinkRecordFacade == null) {
            return Map.of();
        }
        List<PromotionLink> links = promotionLinkRecordFacade.findByActivityAndProductIds(activityId, productIds);
        if (links == null || links.isEmpty()) {
            return Map.of();
        }
        Map<String, MutablePromotionSummary> mutableMap = new LinkedHashMap<>();
        for (PromotionLink link : links) {
            if (!StringUtils.hasText(link.getProductId())) {
                continue;
            }
            MutablePromotionSummary summary = mutableMap.computeIfAbsent(link.getProductId(), key -> new MutablePromotionSummary());
            summary.linkCount++;
            if (link.getCreatedAt() != null && (summary.lastLinkTime == null || link.getCreatedAt().isAfter(summary.lastLinkTime))) {
                summary.lastLinkTime = link.getCreatedAt();
            }
            if (summary.linkRecords.size() < 10) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", link.getId());
                item.put("channelUserId", link.getChannelUserId());
                item.put("channelUserName", link.getChannelUserName());
                item.put("pickSource", link.getPickSource());
                item.put("promotionUrl", link.getPromotionUrl());
                item.put("promoteLink", link.getPromotionUrl());
                item.put("shortUrl", link.getShortUrl());
                item.put("shortLink", link.getShortUrl());
                item.put("linkStatus", link.getLinkStatus());
                item.put("createdAt", link.getCreatedAt());
                item.put("expireTime", link.getExpireTime());
                summary.linkRecords.add(item);
            }
        }
        return mutableMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private PromotionSummary findPromotionSummary(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        if (promotionLinkRecordFacade == null) {
            return null;
        }
        List<PromotionLink> links = promotionLinkRecordFacade.findByActivityAndProductId(activityId, productId);
        if (links == null || links.isEmpty()) {
            return null;
        }
        MutablePromotionSummary summary = new MutablePromotionSummary();
        for (PromotionLink link : links) {
            summary.linkCount++;
            if (link.getCreatedAt() != null && (summary.lastLinkTime == null || link.getCreatedAt().isAfter(summary.lastLinkTime))) {
                summary.lastLinkTime = link.getCreatedAt();
            }
            if (summary.linkRecords.size() < 10) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", link.getId());
                item.put("channelUserId", link.getChannelUserId());
                item.put("channelUserName", link.getChannelUserName());
                item.put("pickSource", link.getPickSource());
                item.put("promotionUrl", link.getPromotionUrl());
                item.put("promoteLink", link.getPromotionUrl());
                item.put("shortUrl", link.getShortUrl());
                item.put("shortLink", link.getShortUrl());
                item.put("linkStatus", link.getLinkStatus());
                item.put("createdAt", link.getCreatedAt());
                item.put("expireTime", link.getExpireTime());
                summary.linkRecords.add(item);
            }
        }
        return summary.freeze();
    }

    private Map<String, Object> toPromotionLinkHistoryItem(PromotionLink link) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", link.getId());
        item.put("activityId", link.getActivityId());
        item.put("productId", link.getProductId());
        item.put("talentId", link.getTalentId());
        item.put("talentName", link.getTalentName());
        item.put("channelUserId", link.getChannelUserId());
        item.put("channelUserName", link.getChannelUserName());
        item.put("pickSource", link.getPickSource());
        item.put("pickExtra", link.getPickExtra());
        item.put("promotionUrl", link.getPromotionUrl());
        item.put("promoteLink", link.getPromotionUrl());
        item.put("shortUrl", link.getShortUrl());
        item.put("shortLink", link.getShortUrl());
        item.put("doukouling", link.getDoukouling());
        item.put("linkStatus", link.getLinkStatus());
        item.put("expireTime", link.getExpireTime());
        item.put("createdAt", link.getCreatedAt());
        item.put("operatorId", link.getOperatorId());
        item.put("operatorName", link.getOperatorName());
        return item;
    }

    private Map<Long, Merchant> buildMerchantMap(Set<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> validShopIds = shopIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (validShopIds.isEmpty()) {
            return Map.of();
        }
        return merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                        .in(Merchant::getShopId, validShopIds))
                .stream()
                .filter(merchant -> merchant.getShopId() != null)
                .collect(Collectors.toMap(
                        Merchant::getShopId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Merchant lookupMerchant(Map<Long, Merchant> merchantMap, Long shopId) {
        if (shopId == null || merchantMap == null || merchantMap.isEmpty()) {
            return null;
        }
        return merchantMap.get(shopId);
    }

    private Merchant findMerchant(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return null;
        }
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getShopId, shopId)
                .last("limit 1"));
    }

    private Map<String, Object> buildPromotionMaterialPack(
            ProductSnapshot snapshot,
            ProductOperationState state,
            Merchant merchant,
            Map<String, Object> auditSupplement) {
        Map<String, Object> pack = new LinkedHashMap<>();
        List<String> sellingPoints = readStringList(auditSupplement, "sellingPoints");
        if (sellingPoints.isEmpty()) {
            sellingPoints = new ArrayList<>();
            sellingPoints.add(snapshot.getTitle() + " 已进入活动商品库，可直接用于渠道选品。");
            if (StringUtils.hasText(snapshot.getActivityCosRatioText())) {
                sellingPoints.add("当前活动佣金率 " + snapshot.getActivityCosRatioText() + "，可直接作为达人沟通收益点。");
            }
            if (StringUtils.hasText(snapshot.getAdServiceRatio())) {
                sellingPoints.add("服务费率 " + normalizePercentText(snapshot.getAdServiceRatio()) + "，方便预估单品收益。");
            }
            if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
                sellingPoints.add("商品带有抖音商品标识，适合放入优先推广池。");
            }
        }

        String merchantName = merchant != null && StringUtils.hasText(merchant.getMerchantName())
                ? merchant.getMerchantName()
                : snapshot.getShopName();
        String commissionText = StringUtils.hasText(snapshot.getActivityCosRatioText())
                ? snapshot.getActivityCosRatioText()
                : formatRate(resolveCommissionRate(snapshot));
        String serviceFeeText = formatRate(resolveServiceFeeRate(snapshot));
        String promotionScript = readString(auditSupplement, "promotionScript");

        pack.put("sellingPoints", sellingPoints);
        pack.put("outreachScript", StringUtils.hasText(promotionScript)
                ? promotionScript
                : "你好，我这边是 " + safeText(merchantName, "品牌方")
                + " 的商品合作负责人。当前这款【" + safeText(snapshot.getTitle(), "活动商品")
                + "】已经进入团长活动商品库，佣金 " + commissionText
                + "，服务费率 " + serviceFeeText + "，如果你有短视频或直播档期，可以直接沟通合作。");
        pack.put("shortVideoScript", StringUtils.hasText(promotionScript)
                ? promotionScript
                : "短视频脚本建议：开场点出【" + safeText(snapshot.getTitle(), "活动商品")
                + "】核心使用场景，中段突出价格 " + safeText(snapshot.getPriceText(), "以页面为准")
                + " 和佣金优势，结尾引导点击专属推广链接下单。");
        pack.put("liveScript", "直播讲品建议：先讲痛点，再讲【" + safeText(snapshot.getTitle(), "活动商品")
                + "】卖点，补充价格 " + safeText(snapshot.getPriceText(), "以页面为准")
                + "、佣金 " + commissionText + "，并提醒使用专属链接下单。");
        pack.put("hasMaterial", StringUtils.hasText(snapshot.getTitle()) && StringUtils.hasText(snapshot.getDetailUrl()));
        pack.put("cover", snapshot.getCover());
        pack.put("detailUrl", snapshot.getDetailUrl());
        pack.put("promoteLink", state == null ? null : state.getPromoteLink());
        pack.put("shortLink", state == null ? null : state.getShortLink());
        pack.put("supportsAds", readBoolean(auditSupplement, "supportsAds"));
        String adsRule = readString(auditSupplement, "adsRule");
        if (StringUtils.hasText(adsRule)) {
            pack.put("adsRule", adsRule);
        }
        pack.put("materialFiles", readStringList(auditSupplement, "materialFiles"));
        return pack;
    }

    private boolean isRealPromotionWriteAllowed() {
        return realPromotionWriteEnabled && allowRealPromotionWrite;
    }

    private String buildProductBriefCopyText(ProductSnapshot snapshot, ProductOperationState state, String promotionLink) {
        String template = configDomainFacade == null
                ? null
                : configDomainFacade.getPromotionTemplate().copyBriefTemplate();
        if (StringUtils.hasText(template)) {
            return renderCopyBriefTemplate(template, snapshot, state, promotionLink);
        }
        return buildHardcodedProductBriefCopyText(snapshot, state, promotionLink);
    }

    private String renderCopyBriefTemplate(
            String template,
            ProductSnapshot snapshot,
            ProductOperationState state,
            String promotionLink) {
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        String productName = copyDisplayText(snapshot.getTitle());
        String commissionRate = copyDisplayText(snapshot.getActivityCosRatioText());
        String shortLink = copyDisplayText(firstText(promotionLink));
        String serviceFeeRate = copyDisplayText(formatRate(resolveServiceFeeRate(snapshot)));
        String customText = copyDisplayText(readString(auditSupplement, "exclusivePriceRemark"));
        return template
                .replace("{productName}", productName)
                .replace("{product_name}", productName)
                .replace("{productId}", copyDisplayText(snapshot.getProductId()))
                .replace("{product_id}", copyDisplayText(snapshot.getProductId()))
                .replace("{commissionRate}", commissionRate)
                .replace("{commission_rate}", commissionRate)
                .replace("{serviceFeeRate}", serviceFeeRate)
                .replace("{service_fee_rate}", serviceFeeRate)
                .replace("{shortLink}", shortLink)
                .replace("{promotion_link}", shortLink)
                .replace("{custom_text}", customText);
    }

    private String buildHardcodedProductBriefCopyText(
            ProductSnapshot snapshot,
            ProductOperationState state,
            String promotionLink) {
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        List<String> sellingPoints = readStringList(auditSupplement, "sellingPoints");
        String sellingPointText = sellingPoints.isEmpty() ? "-" : String.join("、", sellingPoints);
        String promotionScript = readString(auditSupplement, "promotionScript");
        String copyPromotionLink = firstText(promotionLink);
        List<String> lines = new ArrayList<>();
        lines.add("【商品】" + copyDisplayText(snapshot.getTitle()) + "（" + copyDisplayText(snapshot.getShopName()) + "）");
        lines.add("【售价】" + copyDisplayText(snapshot.getPriceText())
                + "  【佣金率】" + copyDisplayText(snapshot.getActivityCosRatioText())
                + "  【近30天】" + copyDisplayText(snapshot.getSales()));
        lines.add("【卖点】" + sellingPointText);
        lines.add("【话术】" + copyDisplayText(promotionScript));
        lines.add("【寄样门槛】销售额≥" + copyDisplayText(readString(auditSupplement, "sampleThresholdSales"))
                + " / 等级≥LV" + copyDisplayText(readString(auditSupplement, "sampleThresholdLevel")));
        lines.add("【专属价说明】" + copyDisplayText(readString(auditSupplement, "exclusivePriceRemark")));
        if (StringUtils.hasText(copyPromotionLink)) {
            lines.add("【链接】" + copyPromotionLink);
        } else {
            lines.add("【推广链接】未生成");
        }
        return String.join("\n", lines);
    }

    private String copyDisplayText(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)
                || "null".equalsIgnoreCase(text)
                || "undefined".equalsIgnoreCase(text)) {
            return "-";
        }
        return text;
    }

    private String firstText(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private void requireUpstreamPromotingForLibraryEntry(ProductSnapshot snapshot) {
        if (isUpstreamPromoting(snapshot)) {
            return;
        }
        throw BusinessException.stateInvalid("上游商品未处于推广中，暂不能加入商品库");
    }

    private void persistOperationState(ProductOperationState state) {
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
    }

    private void requireSelectedToLibrary(ProductOperationState state, String actionLabel) {
        if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            throw BusinessException.stateInvalid("请先将商品加入商品库后再" + actionLabel);
        }
    }

    private void assertCanBindActivityInDept(ProductOperationState state, UUID operatorDeptId) {
        if (state == null) {
            return;
        }
        UUID scopedUserId = state.getAssigneeId() != null ? state.getAssigneeId() : state.getSelectedBy();
        if (scopedUserId == null) {
            return;
        }
        UserOwnershipReference scopedUser = resolveUserOwnershipReference(scopedUserId);
        UUID scopedDeptId = scopedUser == null ? null : scopedUser.deptId();
        if (scopedDeptId == null) {
            return;
        }
        if (operatorDeptId == null || !scopedDeptId.equals(operatorDeptId)) {
            throw new ForbiddenException("无权跨部门修改商品绑定活动");
        }
    }

    private void ensurePostAuditLibraryFlag(ProductOperationState state, UUID operatorId) {
        if (Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            return;
        }
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        if (currentStatus != ProductBizStatus.APPROVED
                && currentStatus != ProductBizStatus.BOUND
                && currentStatus != ProductBizStatus.ASSIGNED
                && currentStatus != ProductBizStatus.LINKED
                && currentStatus != ProductBizStatus.FOLLOWING) {
            return;
        }
        state.setSelectedToLibrary(true);
        state.setSelectedAt(LocalDateTime.now());
        state.setSelectedBy(operatorId);
        persistOperationState(state);
    }

    private void validateAuditSupplement(Map<String, Object> supplement) {
        Map<String, Object> normalized = normalizeAuditSupplement(supplement);
        List<String> missing = new ArrayList<>();
        requireText(normalized, "exclusivePriceRemark", "专属价说明", missing);
        requireText(normalized, "shippingInfo", "发货信息", missing);
        requireList(normalized, "sellingPoints", "商品卖点", missing);
        requireText(normalized, "promotionScript", "推广话术", missing);
        if (!normalized.containsKey("supportsAds")) {
            missing.add("是否支持投流");
        }
        requireText(normalized, "rewardRemark", "奖励说明", missing);
        requireText(normalized, "participationRequirements", "参与要求", missing);
        requireText(normalized, "campaignTimeRemark", "活动时间", missing);
        requireList(normalized, "materialFiles", "手卡素材", missing);
        if (!missing.isEmpty()) {
            throw BusinessException.stateInvalid("审核通过前请补充：" + String.join("、", missing));
        }
    }

    private void assertNoRecentLibraryDuplicateForAudit(String activityId, String productId) {
        if (!StringUtils.hasText(productId)) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(AUDIT_LIBRARY_DUPLICATE_WINDOW_MONTHS);
        LambdaQueryWrapper<ProductOperationState> query = new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getProductId, productId.trim())
                .eq(ProductOperationState::getSelectedToLibrary, true)
                .ge(ProductOperationState::getSelectedAt, cutoff);
        if (StringUtils.hasText(activityId)) {
            query.and(w -> w.ne(ProductOperationState::getActivityId, activityId).or().isNull(ProductOperationState::getActivityId));
        }
        List<ProductOperationState> duplicates = operationStateMapper.selectList(query.last("limit 1"));
        if (duplicates == null || duplicates.isEmpty()) {
            return;
        }
        throw BusinessException.stateInvalid(
                "近三个月内商品库已存在同商品ID，禁止重复进入商品库：productId=" + productId.trim());
    }

    private void requireText(Map<String, Object> payload, String key, String label, List<String> missing) {
        if (!StringUtils.hasText(readString(payload, key))) {
            missing.add(label);
        }
    }

    private void requireList(Map<String, Object> payload, String key, String label, List<String> missing) {
        if (readStringList(payload, key).isEmpty()) {
            missing.add(label);
        }
    }

    private String writeAuditPayload(Map<String, Object> supplement) {
        Map<String, Object> normalized = normalizeAuditSupplement(supplement);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw BusinessException.conflict("审核补充信息保存失败");
        }
    }

    private void sortActivityProductItems(List<Map<String, Object>> items, String sortBy) {
        if (items == null || items.size() <= 1) {
            return;
        }
        if ("latest".equals(sortBy)) {
            items.sort(Comparator.comparing(
                    item -> asMapDateTime(item.get("syncTime")),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return;
        }
        items.sort(this::compareActivityProductItems);
    }

    private int compareActivityProductItems(Map<String, Object> left, Map<String, Object> right) {
        boolean leftPinned = Boolean.TRUE.equals(left.get("pinned"));
        boolean rightPinned = Boolean.TRUE.equals(right.get("pinned"));
        if (leftPinned != rightPinned) {
            return leftPinned ? -1 : 1;
        }
        boolean leftPromoted = productDisplayPolicy.hasPromotionLink(
                asMapText(left.get("promoteLink")),
                asMapText(left.get("shortLink")),
                asMapText(left.get("promotionLink")));
        boolean rightPromoted = productDisplayPolicy.hasPromotionLink(
                asMapText(right.get("promoteLink")),
                asMapText(right.get("shortLink")),
                asMapText(right.get("promotionLink")));
        if (leftPromoted != rightPromoted) {
            return leftPromoted ? -1 : 1;
        }
        BigDecimal leftCommission = parsePercentText(asMapText(left.get("activityCosRatioText")));
        BigDecimal rightCommission = parsePercentText(asMapText(right.get("activityCosRatioText")));
        int commissionCompare = rightCommission.compareTo(leftCommission);
        if (commissionCompare != 0) {
            return commissionCompare;
        }
        LocalDateTime leftSync = asMapDateTime(left.get("syncTime"));
        LocalDateTime rightSync = asMapDateTime(right.get("syncTime"));
        if (leftSync == null && rightSync == null) {
            return 0;
        }
        if (leftSync == null) {
            return 1;
        }
        if (rightSync == null) {
            return -1;
        }
        return rightSync.compareTo(leftSync);
    }

    private String asMapText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private LocalDateTime asMapDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private BigDecimal parsePercentText(String text) {
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

    private void applyDisplayMark(Map<String, Object> view, ProductOperationState state) {
        var presentation = productDisplayPolicy.resolveDisplayPresentation(
                state != null,
                state != null && Boolean.TRUE.equals(state.getSelectedToLibrary()),
                state == null ? null : state.getDisplayStatus(),
                state == null ? null : state.getHiddenReason(),
                state == null ? null : state.getFirstDisplayedAt(),
                state == null ? null : state.getLastDisplayedAt());
        view.put("displayStatus", presentation.displayStatus().name());
        view.put("displayMark", presentation.displayMark());
        view.put("displayMarkLabel", presentation.displayMarkLabel());
        if (state != null) {
            view.put("hiddenReason", presentation.hiddenReason());
            view.put("firstDisplayedAt", presentation.firstDisplayedAt());
            view.put("lastDisplayedAt", presentation.lastDisplayedAt());
            view.put("libraryVisible", presentation.libraryVisible());
        }
    }

    private void applyActivityProductStatusFields(
            Map<String, Object> view,
            Integer upstreamStatus,
            ProductOperationState state) {
        var presentation = productDisplayPolicy.resolveActivityProductStatusPresentation(
                upstreamStatus,
                readString(view, "statusText"),
                state == null ? null : state.getAuditStatus(),
                state == null ? null : state.getBizStatus(),
                state != null && Boolean.TRUE.equals(state.getManualDisabled()),
                state != null && Boolean.TRUE.equals(state.getSelectedToLibrary()),
                state != null && StringUtils.hasText(state.getPromoteLink()),
                state != null && StringUtils.hasText(state.getShortLink()),
                state == null ? null : state.getDisplayStatus(),
                state == null ? null : state.getHiddenReason());
        view.put("officialStatus", presentation.officialStatus());
        view.put("reviewStatus", presentation.reviewStatus());
        view.put("publishStatus", presentation.publishStatus());
        view.put("manualDisabled", presentation.manualDisabled());
        view.put("selectedToLibrary", presentation.selectedToLibrary());
        view.put("displayStatus", presentation.displayStatus().name());
        view.put("displayMark", presentation.displayMark());
        view.put("displayMarkLabel", presentation.displayMarkLabel());
        view.put("hiddenReason", presentation.hiddenReason());
    }

    private Map<String, Object> buildAuditSupplementSummary(Map<String, Object> auditSupplement) {
        if (auditSupplement == null || auditSupplement.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        copyAuditSummaryField(summary, auditSupplement, "exclusivePriceAmount");
        copyAuditSummaryField(summary, auditSupplement, "exclusivePriceRemark");
        copyAuditSummaryField(summary, auditSupplement, "sampleThresholdRemark");
        copyAuditSummaryField(summary, auditSupplement, "promotionScript");
        copyAuditSummaryField(summary, auditSupplement, "shippingInfo");
        copyAuditSummaryField(summary, auditSupplement, "rewardRemark");
        copyAuditSummaryField(summary, auditSupplement, "participationRequirements");
        copyAuditSummaryField(summary, auditSupplement, "campaignTimeRemark");
        if (auditSupplement.containsKey("supportsAds")) {
            summary.put("supportsAds", auditSupplement.get("supportsAds"));
        }
        copyAuditSummaryField(summary, auditSupplement, "adsRule");
        List<String> sellingPoints = readStringList(auditSupplement, "sellingPoints");
        if (!sellingPoints.isEmpty()) {
            summary.put("sellingPointCount", sellingPoints.size());
            summary.put("sellingPointsPreview", sellingPoints.stream().limit(2).toList());
        }
        List<String> materialFiles = readStringList(auditSupplement, "materialFiles");
        if (!materialFiles.isEmpty()) {
            summary.put("materialFileCount", materialFiles.size());
        }
        List<String> goodsTags = readStringList(auditSupplement, "goodsTags");
        if (!goodsTags.isEmpty()) {
            summary.put("goodsTags", goodsTags);
        }
        List<String> productTags = readStringList(auditSupplement, "productTags");
        if (!productTags.isEmpty()) {
            summary.put("productTags", productTags);
        }
        return summary;
    }

    private void copyAuditSummaryField(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, String.valueOf(value).trim());
        }
    }

    private boolean isAuditSupplementComplete(Map<String, Object> auditSupplement) {
        if (auditSupplement == null || auditSupplement.isEmpty()) {
            return false;
        }
        return StringUtils.hasText(readString(auditSupplement, "sampleThresholdRemark"))
                && StringUtils.hasText(readString(auditSupplement, "promotionScript"));
    }

    private Map<String, Object> parseAuditPayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return Map.of();
        }
        try {
            return normalizeAuditSupplement(OBJECT_MAPPER.readValue(rawPayload, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 解析商品快照的 rawPayload 字段，原样返回 Map。
     * <p>
     * 与 {@link #parseAuditPayload} 的区别：本方法不做字段归一化，用于读取
     * rawPayload 中未单独建字段的扩展数据（如抖音 shopScore 评分等），方便前端在
     * 不改数据库 schema 的前提下透传展示。
     * </p>
     */
    private Map<String, Object> parseSnapshotPayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 从商品快照的 rawPayload 中提取店铺评分。
     * rawPayload 来自抖音接口整体快照（item.toMap()），shopScore 字段即抖音 shop_score。
     * 该字段当前未在 ProductSnapshot 实体单独建列，先从 rawPayload 透传，
     * 等 V1 验证后再决定是否落库。
     */
    private Integer resolveShopScoreFromSnapshot(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> payload = parseSnapshotPayload(snapshot.getRawPayload());
        return parseInteger(readString(payload, "shopScore"));
    }

    private Map<String, Object> normalizeAuditSupplement(Map<String, Object> supplement) {
        if (supplement == null || supplement.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        putNormalizedDecimal(normalized, "exclusivePriceAmount", supplement.get("exclusivePriceAmount"));
        putNormalizedText(normalized, "exclusivePriceRemark", supplement.get("exclusivePriceRemark"));
        putNormalizedText(normalized, "shippingInfo", supplement.get("shippingInfo"));
        putNormalizedText(normalized, "promotionScript", supplement.get("promotionScript"));
        putNormalizedText(normalized, "rewardRemark", supplement.get("rewardRemark"));
        putNormalizedText(normalized, "participationRequirements", supplement.get("participationRequirements"));
        putNormalizedText(normalized, "campaignTimeRemark", supplement.get("campaignTimeRemark"));
        putNormalizedText(normalized, "sampleThresholdRemark", supplement.get("sampleThresholdRemark"));
        List<String> sellingPoints = normalizeStringList(supplement.get("sellingPoints"));
        if (!sellingPoints.isEmpty()) {
            normalized.put("sellingPoints", sellingPoints);
        }
        List<String> materialFiles = normalizeStringList(supplement.get("materialFiles"));
        if (!materialFiles.isEmpty()) {
            normalized.put("materialFiles", materialFiles);
        }
        List<String> goodsTags = normalizeStringList(supplement.get("goodsTags"));
        if (goodsTags.isEmpty()) {
            goodsTags = normalizeStringList(supplement.get("goods_tags"));
        }
        if (!goodsTags.isEmpty()) {
            normalized.put("goodsTags", goodsTags);
        }
        List<String> productTags = normalizeStringList(supplement.get("productTags"));
        if (productTags.isEmpty()) {
            productTags = normalizeStringList(supplement.get("product_tags"));
        }
        if (!productTags.isEmpty()) {
            normalized.put("productTags", productTags);
        }
        if (supplement.containsKey("supportsAds") && supplement.get("supportsAds") != null) {
            normalized.put("supportsAds", Boolean.parseBoolean(String.valueOf(supplement.get("supportsAds"))));
        }
        putNormalizedText(normalized, "adsRule", supplement.get("adsRule"));
        if (!normalized.containsKey("adsRule")) {
            putNormalizedText(normalized, "adsRule", supplement.get("投流规则"));
        }
        putNormalizedBoolean(normalized, "freeSample", supplement.get("freeSample"));
        putNormalizedBoolean(normalized, "sampleFree", supplement.get("sampleFree"));
        putNormalizedBoolean(normalized, "sample_free", supplement.get("sample_free"));
        putNormalizedText(normalized, "sampleType", supplement.get("sampleType"));
        putNormalizedBoolean(normalized, "materialDownloadAvailable", supplement.get("materialDownloadAvailable"));
        putNormalizedBoolean(normalized, "materialDownload", supplement.get("materialDownload"));
        putNormalizedBoolean(normalized, "exclusivePrice", supplement.get("exclusivePrice"));
        putNormalizedBoolean(normalized, "handCardAvailable", supplement.get("handCardAvailable"));
        putNormalizedBoolean(normalized, "handCard", supplement.get("handCard"));
        List<String> handCardFiles = normalizeStringList(supplement.get("handCardFiles"));
        if (!handCardFiles.isEmpty()) {
            normalized.put("handCardFiles", handCardFiles);
        }
        putNormalizedBoolean(normalized, "productChainGroup", supplement.get("productChainGroup"));
        putNormalizedBoolean(normalized, "productChain", supplement.get("productChain"));
        putNormalizedBoolean(normalized, "doubleCommission", supplement.get("doubleCommission"));
        putNormalizedBoolean(normalized, "dedupeSelection", supplement.get("dedupeSelection"));
        putNormalizedBoolean(normalized, "dedup", supplement.get("dedup"));
        putNormalizedBoolean(normalized, "notInProductPool", supplement.get("notInProductPool"));
        putNormalizedBoolean(normalized, "notInLibrary", supplement.get("notInLibrary"));
        putNormalizedNumber(normalized, "sampleThresholdSales", supplement.get("sampleThresholdSales"));
        putNormalizedNumber(normalized, "sampleThresholdLevel", supplement.get("sampleThresholdLevel"));
        return normalized;
    }

    private void putNormalizedText(Map<String, Object> payload, String key, Object rawValue) {
        String value = rawValue == null ? null : String.valueOf(rawValue).trim();
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private void putNormalizedNumber(Map<String, Object> payload, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Number number) {
            payload.put(key, number.longValue());
            return;
        }
        String value = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            payload.put(key, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            payload.put(key, value);
        }
    }

    private void putNormalizedDecimal(Map<String, Object> payload, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        BigDecimal value;
        try {
            if (rawValue instanceof BigDecimal decimal) {
                value = decimal;
            } else if (rawValue instanceof Number number) {
                value = new BigDecimal(String.valueOf(number));
            } else {
                value = new BigDecimal(String.valueOf(rawValue).trim());
            }
        } catch (NumberFormatException ex) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return;
        }
        payload.put(key, value.setScale(2, RoundingMode.HALF_UP));
    }

    private void putNormalizedBoolean(Map<String, Object> payload, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Boolean b) {
            payload.put(key, b);
            return;
        }
        String value = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(value)) {
            return;
        }
        payload.put(key, Boolean.parseBoolean(value));
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private Boolean readBoolean(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty() || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(payload.get(key)));
    }

    private List<String> readStringList(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        return normalizeStringList(payload.get(key));
    }

    private List<String> normalizeStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    normalized.add(String.valueOf(item).trim());
                }
            }
            return normalized;
        }
        String text = String.valueOf(rawValue).trim();
        if (StringUtils.hasText(text)) {
            normalized.add(text);
        }
        return normalized;
    }

    private PromotionView buildPromotionView(
            ProductBizStatus currentStatus,
            ProductOperationState state,
            PromotionSummary promotionSummary) {
        String link = state == null ? null : state.getPromoteLink();
        String generatedAt = promotionSummary == null || promotionSummary.lastLinkTime() == null
                ? null
                : promotionSummary.lastLinkTime().toString();
        String expireAt = null;
        if (StringUtils.hasText(link)) {
            return new PromotionView("READY", "已生成", link, generatedAt, expireAt, null);
        }
        if (currentStatus == ProductBizStatus.LINKED || currentStatus == ProductBizStatus.FOLLOWING) {
            return new PromotionView("FAILED", "生成失败", null, generatedAt, expireAt, "推广链接缺失，请后台重试");
        }
        if (currentStatus == ProductBizStatus.ASSIGNED) {
            return new PromotionView("PENDING", "生成中", null, generatedAt, expireAt, null);
        }
        return new PromotionView("PENDING", "未生成", null, generatedAt, expireAt, null);
    }

    private List<String> buildSystemTags(
            ProductSnapshot snapshot,
            ProductOperationState state,
            BigDecimal commissionRate,
            BigDecimal serviceFeeRate,
            long platformSales,
            PromotionSummary promotionSummary,
            boolean activityExpired,
            long remainingDays) {
        List<String> tags = new ArrayList<>();
        if (commissionRate.compareTo(BigDecimal.valueOf(20)) >= 0) {
            tags.add("高佣");
        }
        if (serviceFeeRate != null && serviceFeeRate.compareTo(BigDecimal.TEN) >= 0) {
            tags.add("高服务费");
        }
        if (platformSales >= 1_000) {
            tags.add("高销量");
        }
        if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
            tags.add("抖音商品标");
        }
        if (!activityExpired && remainingDays >= 0 && remainingDays <= 3) {
            tags.add("活动临期");
        }
        if (state != null && StringUtils.hasText(state.getPromoteLink())) {
            tags.add("已转链");
        }
        if (promotionSummary != null && promotionSummary.linkCount() > 0) {
            tags.add("已有推广记录");
        }
        return tags;
    }

    private List<String> buildAlertTags(
            ProductSnapshot snapshot,
            ProductOperationState state,
            BigDecimal commissionRate,
            BigDecimal serviceFeeRate,
            boolean activityExpired) {
        List<String> tags = new ArrayList<>();
        if (!StringUtils.hasText(snapshot.getDetailUrl())) {
            tags.add("无商品链接");
        }
        if (activityExpired) {
            tags.add("活动过期");
        }
        Integer stock = parseInteger(snapshot.getProductStock());
        if (stock != null && stock <= 10) {
            tags.add("库存不足");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) <= 0) {
            tags.add("佣金异常");
        }
        if (serviceFeeRate == null || serviceFeeRate.compareTo(BigDecimal.ZERO) <= 0) {
            tags.add("服务费异常");
        }
        if (state == null || state.getAssigneeId() == null) {
            tags.add("未分配负责人");
        }
        return tags;
    }

    private BigDecimal resolveCommissionRate(ProductSnapshot snapshot) {
        BigDecimal fromText = parsePercentValue(snapshot.getActivityCosRatioText());
        if (fromText.compareTo(BigDecimal.ZERO) > 0) {
            return fromText;
        }
        return normalizeRatioNumber(snapshot.getActivityCosRatio());
    }

    private BigDecimal resolveServiceFeeRate(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        Map<String, Object> payload = parseSnapshotPayload(snapshot.getRawPayload());
        boolean doubleCommission = Integer.valueOf(1).equals(snapshot.getCosType());

        // 普通佣金商品的服务费字段是上游 service_ratio；双佣商品的服务费字段是
        // ad_service_ratio。activity_ad_cos_ratio 是投放佣金率，不能作为服务费兜底。
        if (doubleCommission) {
            BigDecimal adServiceRate = parsePercentValueOrNull(snapshot.getAdServiceRatio());
            if (adServiceRate != null) {
                return adServiceRate;
            }
            adServiceRate = parsePercentValueOrNull(readString(payload, "ad_service_ratio", "adServiceRatio"));
            if (adServiceRate != null) {
                return adServiceRate;
            }
        } else {
            BigDecimal normalServiceRate = parsePercentValueOrNull(readString(payload, "service_ratio", "serviceRatio"));
            if (normalServiceRate != null) {
                return normalServiceRate;
            }
        }

        return null;
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
        BigDecimal parsed = parsePercentValueOrNull(raw);
        return parsed == null ? BigDecimal.ZERO : parsed;
    }

    private BigDecimal parsePercentValueOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim()
                .replace("%", "")
                .replace("％", "")
                .replace(",", "")
                .replace(" ", "");
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal estimateFee(Long priceCent, BigDecimal ratePercent) {
        if (priceCent == null || priceCent <= 0 || ratePercent == null || ratePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(priceCent)
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal yuan(Long cent) {
        long value = cent == null ? 0L : cent;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("^\\d{13}$")) {
            return AppZone.fromEpochMilli(Long.parseLong(value));
        }
        if (value.matches("^\\d{10}$")) {
            return AppZone.fromEpochSecond(Long.parseLong(value));
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == formatters.get(formatters.size() - 1)) {
                    return LocalDate.parse(value, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignore) {
                // try next
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private long calculateRemainingDays(LocalDateTime endTime) {
        if (endTime == null) {
            return -1;
        }
        long days = java.time.Duration.between(LocalDateTime.now(), endTime).toDays();
        if (days < 0) {
            return 0;
        }
        return days;
    }

    private String formatTimeLeft(LocalDateTime endTime) {
        if (endTime == null) {
            return "长期";
        }
        LocalDateTime now = LocalDateTime.now();
        if (endTime.isBefore(now)) {
            return "已结束";
        }
        java.time.Duration duration = java.time.Duration.between(now, endTime);
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        if (days > 0) {
            return days + "天 " + hours + "小时";
        }
        long minutes = duration.minusHours(duration.toHours()).toMinutes();
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        return Math.max(minutes, 1) + "分钟";
    }

    private String formatRate(BigDecimal rate) {
        BigDecimal value = rate == null ? BigDecimal.ZERO : rate.setScale(2, RoundingMode.HALF_UP);
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    private String normalizePercentText(String raw) {
        BigDecimal value = parsePercentValue(raw);
        return formatRate(value);
    }

    private String normalizeFreeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return ("null".equalsIgnoreCase(text) || "undefined".equalsIgnoreCase(text)) ? "" : text;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeDisplayText(String value) {
        return value == null ? null : value.trim();
    }

    private static final class MutablePromotionSummary {
        private int linkCount;
        private LocalDateTime lastLinkTime;
        private final List<Map<String, Object>> linkRecords = new ArrayList<>();

        private PromotionSummary freeze() {
            linkRecords.sort(Comparator.comparing(
                    record -> (LocalDateTime) record.getOrDefault("createdAt", LocalDateTime.MIN),
                    Comparator.reverseOrder()
            ));
            return new PromotionSummary(linkCount, lastLinkTime, List.copyOf(linkRecords));
        }
    }

    private record PromotionSummary(
            int linkCount,
            LocalDateTime lastLinkTime,
            List<Map<String, Object>> linkRecords) {
    }

    public record SelectedLibraryCursorPage(
            List<Product> records,
            long limit,
            boolean hasMore,
            String nextCursor
    ) {
        public static SelectedLibraryCursorPage empty(long limit) {
            return new SelectedLibraryCursorPage(List.of(), limit, false, null);
        }
    }

    private record SelectedLibraryCursor(
            LocalDateTime snapshotTime,
            int pinnedRank,
            LocalDateTime promotionStartTime,
            LocalDateTime syncTime,
            LocalDateTime selectedAt,
            String activityId,
            String productId
    ) {}

    public record SelectedLibraryFilter(
            String keyword,
            Integer status,
            String shopKeyword,
            String categoryName,
            String categories,
            String activityId,
            String assigneeId,
            String serviceFee,
            String supportsAds,
            String salesRange,
            String promotionLink,
            String allianceStatus,
            String commission,
            String hasSample,
            String assignee,
            String systemTag,
            String decision,
            String partnerId,
            String partnerType,
            String sortBy,
            String goodsTags,
            String productTags,
            String colonelName,
            String published,
            String cooperationType,
            String livePriceMin,
            String livePriceMax,
            String commissionMin,
            String commissionMax,
            String sampleSalesMin,
            String sampleSalesMax,
            String materialDownload,
            String exclusivePrice,
            String productChain,
            String handCard,
            String doubleCommission,
            String notInLibrary,
            String dedup,
            String recruitActivityId,
            String recruitActivityName,
            String listed,
            String freeSample,
            String productId
    ) {
        public static SelectedLibraryFilter empty() {
            return new SelectedLibraryFilter(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null);
        }

        public static SelectedLibraryFilter of(String keyword, Integer status) {
            SelectedLibraryFilter empty = empty();
            return new SelectedLibraryFilter(
                    trimToNull(keyword),
                    status,
                    empty.shopKeyword(),
                    empty.categoryName(),
                    empty.categories(),
                    empty.activityId(),
                    empty.assigneeId(),
                    empty.serviceFee(),
                    empty.supportsAds(),
                    empty.salesRange(),
                    empty.promotionLink(),
                    empty.allianceStatus(),
                    empty.commission(),
                    empty.hasSample(),
                    empty.assignee(),
                    empty.systemTag(),
                    empty.decision(),
                    empty.partnerId(),
                    empty.partnerType(),
                    empty.sortBy(),
                    empty.goodsTags(),
                    empty.productTags(),
                    empty.colonelName(),
                    empty.published(),
                    empty.cooperationType(),
                    empty.livePriceMin(),
                    empty.livePriceMax(),
                    empty.commissionMin(),
                    empty.commissionMax(),
                    empty.sampleSalesMin(),
                    empty.sampleSalesMax(),
                    empty.materialDownload(),
                    empty.exclusivePrice(),
                    empty.productChain(),
                    empty.handCard(),
                    empty.doubleCommission(),
                    empty.notInLibrary(),
                    empty.dedup(),
                    empty.recruitActivityId(),
                    empty.recruitActivityName(),
                    empty.listed(),
                    empty.freeSample(),
                    empty.productId());
        }

        private SelectedLibraryFilter normalized(ProductDisplayPolicy displayPolicy) {
            return new SelectedLibraryFilter(
                    trimToNull(keyword),
                    status,
                    trimToNull(shopKeyword),
                    trimToNull(categoryName),
                    trimToNull(categories),
                    trimToNull(activityId),
                    trimToNull(assigneeId),
                    normalizeToken(serviceFee),
                    normalizeToken(supportsAds),
                    normalizeToken(salesRange),
                    normalizeToken(promotionLink),
                    normalizeToken(allianceStatus),
                    normalizeToken(commission),
                    normalizeToken(hasSample),
                    normalizeToken(assignee),
                    normalizeToken(systemTag),
                    normalizeToken(decision),
                    trimToNull(partnerId),
                    normalizePartnerType(partnerType),
                    displayPolicy.normalizeSelectedLibrarySortBy(sortBy),
                    normalizeToken(goodsTags),
                    normalizeToken(productTags),
                    trimToNull(colonelName),
                    normalizeToken(published),
                    normalizeToken(cooperationType),
                    trimToNull(livePriceMin),
                    trimToNull(livePriceMax),
                    trimToNull(commissionMin),
                    trimToNull(commissionMax),
                    trimToNull(sampleSalesMin),
                    trimToNull(sampleSalesMax),
                    normalizeToken(materialDownload),
                    normalizeToken(exclusivePrice),
                    normalizeToken(productChain),
                    normalizeToken(handCard),
                    normalizeToken(doubleCommission),
                    normalizeToken(notInLibrary),
                    normalizeToken(dedup),
                    trimToNull(recruitActivityId),
                    trimToNull(recruitActivityName),
                    normalizeToken(listed),
                    normalizeToken(freeSample),
                    trimToNull(productId)
            );
        }

        private static String normalizePartnerType(String partnerType) {
            String trimmed = trimToNull(partnerType);
            if (trimmed == null) {
                return null;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if ("COLONEL".equals(upper) || "团长".equals(trimmed)) {
                return "COLONEL";
            }
            if ("MERCHANT".equals(upper) || "SHOP".equals(upper) || "商家".equals(trimmed)) {
                return "MERCHANT";
            }
            return upper;
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static String normalizeToken(String value) {
            String trimmed = trimToNull(value);
            return trimmed == null ? null : trimmed;
        }
    }

    private record BizStatusFilter(
            BizStatusFilterMode mode,
            Set<String> includeProductIds,
            Set<String> excludeProductIds
    ) {
        static BizStatusFilter none() {
            return new BizStatusFilter(BizStatusFilterMode.NONE, Set.of(), Set.of());
        }

        static BizStatusFilter empty() {
            return new BizStatusFilter(BizStatusFilterMode.EMPTY, Set.of(), Set.of());
        }

        static BizStatusFilter includeOnly(Set<String> includeProductIds) {
            return new BizStatusFilter(BizStatusFilterMode.INCLUDE_ONLY, Set.copyOf(includeProductIds), Set.of());
        }

        static BizStatusFilter pendingAudit(Set<String> knownProductIds, Set<String> pendingAuditProductIds) {
            return new BizStatusFilter(
                    BizStatusFilterMode.PENDING_AUDIT,
                    Set.copyOf(pendingAuditProductIds),
                    Set.copyOf(knownProductIds)
            );
        }

        boolean isEmptyFilter() {
            return mode == BizStatusFilterMode.EMPTY;
        }
    }

    private enum BizStatusFilterMode {
        NONE,
        EMPTY,
        INCLUDE_ONLY,
        PENDING_AUDIT
    }

    private record DecisionSummary(
            String level,
            String label,
            String reason,
            String time) {
    }

    private record PromotionView(
            String status,
            String statusLabel,
            String link,
            String generatedAt,
            String expireAt,
            String failReason) {
    }
}
