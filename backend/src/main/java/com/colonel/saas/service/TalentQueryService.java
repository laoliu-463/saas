package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.domain.sample.facade.dto.TalentRecentSampleDTO;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.mapper.TalentClaimMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 达人查询服务 (god service - 边缘服务, 不再 DDD 切片).
 *
 * <p><strong>当前状态 (2026-07-14):</strong></p>
 * <ul>
 *   <li>1435 行 / 4 public method, method 体大 (单 method 可达数百行)</li>
 *   <li>Batch3 DDD 化已部分完成 (TalentQueryApplicationService + DomainFacade 已就位)</li>
 *   <li>不切理由: 4 method 体大, 切片需逐 method 拆, 工作量大</li>
 * </ul>
 * 达人域 — 达人查询服务。
 * <p>负责达人列表分页查询、详情展示和操作权限校验，是达人域面向上层的核心只读入口。</p>
 *
 * <ul>
 *   <li>分页查询：基于数据库分页 + 内存多条件过滤的混合模式，支持认领状态、视图、分类、区间等筛选</li>
 *   <li>详情组装：聚合达人基本信息、认领关系、寄样记录、订单汇总，按数据范围脱敏</li>
 *   <li>权限校验：基于数据范围（ALL / DEPT / PERSONAL）和角色（ADMIN / BIZ_STAFF / CHANNEL_LEADER / CHANNEL_STAFF）控制访问</li>
 *   <li>卡牌富化：为列表和详情中的达人卡牌填充认领归属、寄样数、订单数、区间标签等衍生字段</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域（Talent）</p>
 *
 * <p><b>协作关系：</b></p>
 * <ul>
 *   <li>{@link TalentService} — 委托分页查询与单条查询</li>
 *   <li>{@link TalentClaimMapper} — 读取认领记录</li>
 *   <li>{@link UserDomainFacade} ：查询认领人显示标签</li>
 *   <li>{@link SampleDomainFacade} — 寄样域只读门面</li>
 *   <li>{@link OrderReadFacade} — 订单域只读门面</li>
 * </ul>
 *
 * @see TalentService
 * @see TalentClaimMapper
 */
@Service
public class TalentQueryService {

    /** 认领状态：生效中 */
    private static final int CLAIM_STATUS_ACTIVE = 1;
    /** 认领状态：已过期 */
    private static final int CLAIM_STATUS_EXPIRED = 2;
    /** 单次数据库分页最大拉取条数，防止内存溢出 */
    private static final int TALENT_QUERY_BATCH_SIZE = 200;

    /** 达人核心服务，委托分页与单条查询 */
    private final TalentService talentService;
    /** 达人认领 Mapper，查询认领归属关系 */
    private final TalentClaimMapper talentClaimMapper;
    private final UserDomainFacade userDomainFacade;
    /** 寄样域只读门面，提供达人维度寄样摘要 */
    private final SampleDomainFacade sampleDomainFacade;
    /** 订单域只读门面，提供达人维度订单摘要和最近订单 */
    private final OrderReadFacade orderReadFacade;
    /** 用户域权限检查器，用于统一角色编码集合解析和匹配 */
    private final CurrentUserPermissionChecker currentUserPermissionChecker;
    /** 用户域数据范围 Resolver，用于灰度开启后的详情访问范围决策 */
    private final DataScopeResolver dataScopeResolver;
    /** DDD 灰度开关，默认关闭时保留 Legacy 访问判断 */
    private final DddRefactorProperties dddRefactorProperties;

    /**
     * 构造函数，通过依赖注入初始化所有服务和仓储。
     *
     * @param talentService        达人服务（基础 CRUD 和分页查询）
     * @param talentClaimMapper    达人认领记录 Mapper
     * @param userDomainFacade     用户领域门面（查询认领人显示标签）
     * @param sampleDomainFacade   寄样域只读门面（统计寄样次数和最近寄样记录）
     * @param orderReadFacade     订单域只读门面
     * @param currentUserPermissionChecker 用户域权限检查器（角色编码匹配）
     * @param dataScopeResolver     用户域数据范围 Resolver
     * @param dddRefactorProperties DDD 灰度开关
     */
    public TalentQueryService(
            TalentService talentService,
            TalentClaimMapper talentClaimMapper,
            UserDomainFacade userDomainFacade,
            SampleDomainFacade sampleDomainFacade,
            OrderReadFacade orderReadFacade,
            CurrentUserPermissionChecker currentUserPermissionChecker,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this.talentService = talentService;
        this.talentClaimMapper = talentClaimMapper;
        this.userDomainFacade = userDomainFacade;
        this.sampleDomainFacade = sampleDomainFacade;
        this.orderReadFacade = orderReadFacade;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 达人列表分页查询（数据库分页 + 内存多条件过滤）。
     * <p>由于部分筛选条件（认领状态、视图类型、指标区间等）无法下推至 SQL，
     * 采用「逐批拉取 + 内存过滤」的混合策略：每次从数据库拉取一批，富化后逐条匹配所有筛选条件，
     * 直到收集够当前页的数据或遍历完所有批次。</p>
     *
     * <ol>
     *   <li>计算请求分页参数，确定单批拉取大小和目标索引范围</li>
     *   <li>循环从 {@link TalentService#page} 拉取数据库分页批次</li>
     *   <li>对每批记录调用 {@link #enrichTalentCards} 填充认领归属、寄样数、订单统计等衍生字段</li>
     *   <li>逐条执行 17 项内存过滤条件（池状态、归属人、视图、平台、认领状态、分类、等级、区域、
     *       抖音号、昵称、6 个指标区间、联系状态）</li>
     *   <li>仅将目标页索引范围内的记录加入结果集</li>
     *   <li>组装分页结果返回</li>
     * </ol>
     *
     * @param query 分页查询条件（包含分页参数、数据范围、多维筛选条件）
     * @return 分页结果，records 为目标页记录，total 为过滤后总条数
     */
    public IPage<Talent> page(TalentPageQuery query) {
        rejectUnsupportedFilters(query);
        // 第一步：计算分页参数
        long requestedPage = query == null ? 1L : Math.max(query.getPage(), 1L);
        long requestedSize = query == null ? 10L : Math.max(query.getSize(), 1L);
        long fetchSize = normalizeFetchSize(requestedSize);
        long fromIndex = Math.max(0L, (requestedPage - 1) * requestedSize);
        long toIndexExclusive = fromIndex + requestedSize;

        List<Talent> pageRecords = new ArrayList<>();
        long filteredTotal = 0L;
        long current = 1L;
        long pages = 1L;
        DataScope baseScope = resolveBaseScope(query);
        String listKeyword = resolveListKeyword(query);
        // 第二步：循环拉取数据库分页批次，富化后逐条过滤
        while (current <= pages) {
            IPage<Talent> batchPage = talentService.page(
                    current,
                    fetchSize,
                    listKeyword,
                    query.getRegion(),
                    query.getMinFans(),
                    query.getMaxFans(),
                    baseScope,
                    query.getUserId(),
                    query.getDeptId());
            List<Talent> records = new ArrayList<>(batchPage.getRecords());
            // 第三步：富化卡牌字段（认领归属、寄样数、订单数、区间标签）
            enrichTalentCards(records, query.getUserId());
            // 第四步：逐条匹配全部筛选条件，不满足则跳过
            for (Talent talent : records) {
                if (!matchesPoolStatus(talent, query.getPoolStatus())
                        || !matchesOwnerKeyword(talent, query.getOwnerKeyword())
                        || !matchesView(talent, query)
                        || !matchesPlatform(query.getPlatform())
                        || !matchesClaimStatus(talent, query.getClaimStatus())
                        || !matchesCategory(talent, query.getCategory())
                        || !matchesLevel(talent, query.getLevel())
                        || !matchesRegion(talent, query.getRegion())
                        || !matchesDouyinNo(talent, query.getDouyinNo())
                        || !matchesNickname(talent, query.getNickname())
                        || !matchesMetricBand(talent.getLiveSalesBand(), query.getLiveSalesBand())
                        || !matchesMetricBand(talent.getLiveViewBand(), query.getLiveViewBand())
                        || !matchesMetricBand(talent.getLiveGpmBand(), query.getLiveGpmBand())
                        || !matchesMetricBand(talent.getVideoSalesBand(), query.getVideoSalesBand())
                        || !matchesMetricBand(talent.getVideoPlayBand(), query.getVideoPlayBand())
                        || !matchesMetricBand(talent.getVideoGpmBand(), query.getVideoGpmBand())
                        || !matchesContactStatus(talent, query.getContactStatus())) {
                    continue;
                }
                // 第五步：仅收集目标页范围内的记录
                if (filteredTotal >= fromIndex && filteredTotal < toIndexExclusive) {
                    pageRecords.add(talent);
                }
                filteredTotal++;
            }
            pages = batchPage.getPages();
            if (pages <= 0 || batchPage.getRecords().isEmpty()) {
                break;
            }
            current++;
        }

        // 第六步：组装分页结果
        Page<Talent> result = new Page<>(requestedPage, requestedSize, filteredTotal);
        result.setRecords(pageRecords);
        return result;
    }

    /**
     * 归一化单批拉取大小。
     * <p>列表结果页大小与扫描批次大小解耦：即使前端只请求 10 条，也使用固定批次读取，
     * 避免内存过滤场景按结果页大小反复访问数据库。</p>
     *
     * @param requestedSize 请求的每页条数（仅用于保持调用契约，批次大小固定受控）
     * @return 归一化后的批次大小
     */
    private long normalizeFetchSize(long requestedSize) {
        if (requestedSize <= 0) {
            return TALENT_QUERY_BATCH_SIZE;
        }
        return TALENT_QUERY_BATCH_SIZE;
    }

    /**
     * 拦截当前没有事实字段支撑的筛选，避免前端或 API 调用方误以为筛选已生效。
     */
    private void rejectUnsupportedFilters(TalentPageQuery query) {
        if (query != null && StringUtils.hasText(query.getGender())) {
            throw BusinessException.param("gender 筛选当前不支持：达人表尚无 gender 字段，请移除该筛选后重试");
        }
    }

    /**
     * 解析底层数据库查询的数据范围。
     * <p>当视图为 TEAM_PUBLIC（团队公海）时强制使用 ALL 范围，确保公海池数据完整展示。</p>
     *
     * @param query 分页查询条件
     * @return 数据范围枚举值
     */
    private DataScope resolveBaseScope(TalentPageQuery query) {
        if (query == null) {
            return DataScope.ALL;
        }
        // 注意：团队公海视图不按数据范围过滤，确保所有公海达人可见
        if ("TEAM_PUBLIC".equalsIgnoreCase(firstNonBlank(query.getView(), ""))) {
            return DataScope.ALL;
        }
        return query.getDataScope() != null ? query.getDataScope() : DataScope.ALL;
    }

    /**
     * 达人详情查询（含认领、寄样、订单聚合）。
     *
     * <ol>
     *   <li>通过 {@link TalentService#getById} 获取达人基础信息</li>
     *   <li>加载认领关系映射（{@link #loadClaimMaps}）</li>
     *   <li>执行数据范围访问校验（{@link #assertCanAccess}），越权抛出 {@link ForbiddenException}</li>
     *   <li>富化卡牌衍生字段（寄样数、订单统计、区间标签等）</li>
     *   <li>根据数据范围判断是否脱敏敏感字段</li>
     *   <li>组装达人基本信息、认领详情、最近寄样记录、最近订单记录返回</li>
     * </ol>
     *
     * @param talentId      达人 ID
     * @param currentUserId 当前操作用户 ID
     * @param currentDeptId 当前操作用户所属部门 ID
     * @param dataScope     数据范围（ALL / DEPT / PERSONAL）
     * @return 达人详情响应，包含达人信息、认领信息、寄样列表、订单列表
     * @throws ForbiddenException 无权查看该达人详情时抛出
     */
    public TalentDetailResponse detail(UUID talentId, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        // 第一步：获取达人基础信息
        Talent talent = talentService.getById(talentId);
        UUID resolvedTalentId = talent == null ? null : talent.getId();
        // 第二步：加载认领关系映射
        ClaimMaps claimMaps = resolvedTalentId == null
                ? new ClaimMaps(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), List.of())
                : loadClaimMaps(Set.of(resolvedTalentId));
        // 第三步：校验数据范围访问权限
        assertCanAccess(talent, currentUserId, currentDeptId, dataScope,
                claimMaps.activeClaimsByTalent().getOrDefault(resolvedTalentId, List.of()));
        Map<UUID, String> ownerLabelMap = loadOwnerLabels(claimMaps.allClaims());
        // 第四步：富化卡牌衍生字段
        enrichTalentCards(List.of(talent), currentUserId, claimMaps, ownerLabelMap);

        // 第五步：根据数据范围判断是否脱敏，组装详情响应
        boolean redactSensitiveFields = shouldRedactSensitiveFields(dataScope);
        TalentDetailResponse response = new TalentDetailResponse();
        response.setTalent(toTalentInfo(talent, redactSensitiveFields));
        response.setClaim(toClaimInfo(
                talent,
                currentUserId,
                redactSensitiveFields,
                claimMaps.activeClaimsByTalent().getOrDefault(resolvedTalentId, List.of()),
                ownerLabelMap));
        // 第六步：加载最近寄样记录和订单记录
        response.setSamples(loadSamples(talent));
        response.setOrders(loadOrders(talent, redactSensitiveFields));
        return response;
    }

    /**
     * 校验当前用户是否有权操作指定达人。
     * <p>权限判断优先级：ADMIN 直接放行 &gt; CHANNEL_LEADER 按部门归属放行 &gt; BIZ_STAFF / CHANNEL_STAFF 按个人归属放行。
     * 任一层级匹配成功即放行，全部不匹配则抛出 {@link ForbiddenException}。</p>
     *
     * <ol>
     *   <li>查询达人基础信息，达人不存在时直接放行（由下游业务层处理）</li>
     *   <li>ADMIN 角色直接放行</li>
     *   <li>CHANNEL_LEADER 角色：检查当前用户所在部门是否为该达人的认领部门</li>
     *   <li>BIZ_STAFF / CHANNEL_STAFF 角色：检查当前用户是否为该达人的认领人</li>
     *   <li>以上均不满足，抛出 ForbiddenException</li>
     * </ol>
     *
     * @param talentId      达人 ID
     * @param currentUserId 当前操作用户 ID
     * @param currentDeptId 当前操作用户所属部门 ID
     * @param roleCodes     当前用户的角色编码集合
     * @throws ForbiddenException 无权操作该达人时抛出
     */
    public void assertCanOperate(UUID talentId, UUID currentUserId, UUID currentDeptId, Collection<?> roleCodes) {
        Talent talent = talentService.getById(talentId);
        UUID resolvedTalentId = talent == null ? null : talent.getId();
        // 注意：达人不存在时放行，由下游业务层处理不存在场景
        if (resolvedTalentId == null || currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        // 查询该达人的所有生效认领记录
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(resolvedTalentId);
        List<TalentClaim> safeActiveClaims = activeClaims == null ? List.of() : activeClaims;
        // 渠道主管：所属部门认领了该达人即有权操作
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER)) {
            boolean ownedByCurrentDept = currentDeptId != null && safeActiveClaims.stream()
                    .anyMatch(claim -> currentDeptId.equals(claim.getDeptId()));
            if (ownedByCurrentDept) {
                return;
            }
        }
        // 招商专员 / 渠道专员：个人认领了该达人即有权操作
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_STAFF)) {
            boolean ownedByCurrentUser = currentUserId != null && safeActiveClaims.stream()
                    .anyMatch(claim -> currentUserId.equals(claim.getUserId()));
            if (ownedByCurrentUser) {
                return;
            }
        }
        throw new ForbiddenException("无权操作该达人");
    }

    /**
     * 达人详情访问权限校验（便捷重载，自动加载认领记录）。
     *
     * @param talent        达人实体
     * @param currentUserId 当前操作用户 ID
     * @param currentDeptId 当前操作用户所属部门 ID
     * @param dataScope     数据范围
     * @throws ForbiddenException 无权查看时抛出
     */
    private void assertCanAccess(Talent talent, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        List<TalentClaim> activeClaims = talent == null || talent.getId() == null
                ? List.of()
                : talentClaimMapper.findActiveByTalentId(talent.getId());
        assertCanAccess(talent, currentUserId, currentDeptId, dataScope, activeClaims);
    }

    /**
     * 达人详情访问权限校验（核心实现）。
     * <p>根据数据范围判断当前用户是否有权查看该达人：</p>
     * <ul>
     *   <li>ALL — 所有人可看，直接放行</li>
     *   <li>PERSONAL — 仅认领人本人可看</li>
     *   <li>DEPT — 仅同部门认领人可看</li>
     * </ul>
     *
     * @param talent        达人实体
     * @param currentUserId 当前操作用户 ID
     * @param currentDeptId 当前操作用户所属部门 ID
     * @param dataScope     数据范围
     * @param activeClaims  该达人的生效认领记录列表
     * @throws ForbiddenException 无权查看时抛出
     */
    private void assertCanAccess(
            Talent talent,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            List<TalentClaim> activeClaims) {
        if (talent == null || talent.getId() == null || dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            assertCanAccessLegacy(currentUserId, currentDeptId, dataScope, activeClaims);
            return;
        }
        assertCanAccessWithPolicy(currentUserId, currentDeptId, dataScope, activeClaims);
    }

    private void assertCanAccessLegacy(
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            List<TalentClaim> activeClaims) {
        List<TalentClaim> safeActiveClaims = activeClaims == null ? List.of() : activeClaims;
        if (dataScope == DataScope.PERSONAL) {
            boolean ownedByCurrentUser = currentUserId != null && safeActiveClaims.stream()
                    .anyMatch(claim -> currentUserId.equals(claim.getUserId()));
            if (!ownedByCurrentUser) {
                throw new ForbiddenException("无权查看该达人详情");
            }
            return;
        }
        // DEPT 范围：当前用户所在部门是认领部门之一时放行
        boolean ownedByCurrentDept = currentDeptId != null && safeActiveClaims.stream()
                .anyMatch(claim -> currentDeptId.equals(claim.getDeptId()));
        if (!ownedByCurrentDept) {
            throw new ForbiddenException("无权查看该达人详情");
        }
    }

    private void assertCanAccessWithPolicy(
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            List<TalentClaim> activeClaims) {
        DataScopeResolver.ResolvedDataScope resolvedScope =
                dataScopeResolver.resolve(currentUserId, currentDeptId, dataScope);
        if (!resolvedScope.contextSatisfied()) {
            throw new ForbiddenException("无权查看该达人详情");
        }

        if (resolvedScope.noFilter()) {
            return;
        }

        List<TalentClaim> safeActiveClaims = activeClaims == null ? List.of() : activeClaims;
        if (resolvedScope.filtersUser()) {
            boolean ownedByCurrentUser = safeActiveClaims.stream()
                    .anyMatch(claim -> currentUserId.equals(claim.getUserId()));
            if (!ownedByCurrentUser) {
                throw new ForbiddenException("无权查看该达人详情");
            }
            return;
        }
        if (resolvedScope.filtersDept()) {
            boolean ownedByCurrentDept = safeActiveClaims.stream()
                    .anyMatch(claim -> currentDeptId.equals(claim.getDeptId()));
            if (!ownedByCurrentDept) {
                throw new ForbiddenException("无权查看该达人详情");
            }
        }
    }

    /**
     * 达人卡牌富化（便捷重载，内部自行加载认领映射）。
     *
     * @param talents       达人列表
     * @param currentUserId 当前操作用户 ID
     */
    private void enrichTalentCards(List<Talent> talents, UUID currentUserId) {
        enrichTalentCards(talents, currentUserId, null);
    }

    /**
     * 达人卡牌富化（核心实现）。
     * <p>批量加载认领关系、用户信息、寄样统计和订单汇总，然后逐条填充每个达人的衍生字段：</p>
     * <ul>
     *   <li>认领归属：池状态（PRIVATE/PUBLIC）、归属人、认领时间、保护期</li>
     *   <li>业务统计：寄样数、订单数、服务费贡献、月销售额</li>
     *   <li>指标区间标签：直播销售额、直播观看、直播 GPM、视频销售额、视频播放、视频 GPM</li>
     *   <li>主营分类</li>
     * </ul>
     *
     * @param talents             达人列表
     * @param currentUserId       当前操作用户 ID
     * @param preloadedClaimMaps  预加载的认领映射（为 null 时内部重新加载）
     */
    private void enrichTalentCards(List<Talent> talents, UUID currentUserId, ClaimMaps preloadedClaimMaps) {
        if (talents == null || talents.isEmpty()) {
            return;
        }

        // 第一步：批量加载关联数据
        Set<UUID> talentIds = talents.stream().map(Talent::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        ClaimMaps claimMaps = preloadedClaimMaps == null ? loadClaimMaps(talentIds) : preloadedClaimMaps;
        Map<UUID, String> ownerLabelMap = loadOwnerLabels(claimMaps.allClaims());
        enrichTalentCards(talents, currentUserId, claimMaps, ownerLabelMap);
    }

    /**
     * 使用调用方已加载的认领人标签继续富化，避免详情查询重复访问认领与用户表。
     */
    private void enrichTalentCards(
            List<Talent> talents,
            UUID currentUserId,
            ClaimMaps claimMaps,
            Map<UUID, String> ownerLabelMap) {
        Set<UUID> talentIds = talents.stream().map(Talent::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Long> sampleCountMap = loadSampleCounts(
                talentIds
        );
        Map<String, OrderReadFacade.TalentOrderSummary> orderAggregateMap = loadOrderAggregates(
                talents.stream().map(Talent::getDouyinUid).filter(StringUtils::hasText).collect(Collectors.toSet())
        );

        // 第二步：逐条填充衍生字段
        for (Talent talent : talents) {
            List<TalentClaim> activeClaims = claimMaps.activeClaimsByTalent().getOrDefault(talent.getId(), List.of());
            // 判断当前用户是否认领了该达人
            TalentClaim currentClaim = activeClaims.stream()
                    .filter(claim -> currentUserId != null && currentUserId.equals(claim.getUserId()))
                    .findFirst()
                    .orElse(null);
            talent.setActiveClaimCount(activeClaims.size());
            if (currentClaim != null) {
                // 当前用户已认领：标记为私有池
                talent.setPoolStatus("PRIVATE");
                talent.setOwnerId(currentClaim.getUserId());
                talent.setClaimedAt(currentClaim.getClaimedAt());
                talent.setProtectedUntil(currentClaim.getProtectedUntil());
                talent.setOwnerName(buildClaimSummary(activeClaims, ownerLabelMap, currentUserId));
            } else {
                // 当前用户未认领：标记为公有池，展示公海认领提示
                talent.setPoolStatus("PUBLIC");
                talent.setOwnerId(null);
                applyPublicClaimHint(talent, activeClaims, claimMaps.latestClaims().get(talent.getId()), ownerLabelMap);
            }

            // 填充寄样统计
            Long sampleCount = sampleCountMap.getOrDefault(talent.getId(), 0L);
            talent.setSampleCount(sampleCount);

            // 填充订单统计与衍生指标区间标签
            OrderReadFacade.TalentOrderSummary aggregate = orderAggregateMap.get(talent.getDouyinUid());
            long orderCount = aggregate == null ? 0L : aggregate.orderCount();
            long serviceFee = aggregate == null ? 0L : aggregate.serviceFeeCent();
            long monthlySales = aggregate == null ? 0L : aggregate.orderAmountCent();
            talent.setOrderCount(orderCount);
            talent.setServiceFeeContribution(serviceFee);
            talent.setMonthlySales(monthlySales);
            talent.setNaturalOrderTalent(orderCount > 0);
            talent.setMainCategory(resolveMainCategory(talent.getCategories()));
            // 计算 6 个指标区间标签
            talent.setLiveSalesBand(toSalesBand(monthlySales));
            talent.setLiveViewBand(toFansBand(talent.getFans()));
            talent.setLiveGpmBand(toGpmBand(monthlySales, talent.getFans()));
            talent.setVideoSalesBand(orderCount > 0 ? toSalesBand(Math.max(monthlySales / Math.max(orderCount, 1), 0L)) : null);
            talent.setVideoPlayBand(orderCount > 0 ? toPlayBand(talent.getFans(), orderCount) : null);
            talent.setVideoGpmBand(orderCount > 0 ? toGpmBand(Math.max(monthlySales / Math.max(orderCount, 1), 0L), talent.getFans()) : null);
        }
    }

    /**
     * 批量加载达人认领关系映射。
     * <p>一次性查询指定达人集合的全部认领记录（按认领时间降序），并构建三类映射：</p>
     * <ul>
     *   <li>activeClaims — 每个达人的第一条生效认领（用于快速查询主归属人）</li>
     *   <li>activeClaimsByTalent — 每个达人的所有生效认领列表（用于多人认领场景）</li>
     *   <li>latestClaims — 每个达人的最新一条认领记录（含已过期，用于公海释放提示）</li>
     * </ul>
     *
     * @param talentIds 达人 ID 集合
     * @return 认领关系映射容器
     */
    private ClaimMaps loadClaimMaps(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return new ClaimMaps(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), List.of());
        }
        // 查询所有未删除的认领记录，按认领时间降序排列
        List<TalentClaim> claims = talentClaimMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TalentClaim>()
                        .in(TalentClaim::getTalentId, talentIds)
                        .eq(TalentClaim::getDeleted, 0)
                        .orderByDesc(TalentClaim::getClaimedAt)
        );
        Map<UUID, TalentClaim> activeClaims = new LinkedHashMap<>();
        Map<UUID, List<TalentClaim>> activeClaimsByTalent = new LinkedHashMap<>();
        Map<UUID, TalentClaim> latestClaims = new LinkedHashMap<>();
        for (TalentClaim claim : claims) {
            if (claim.getTalentId() == null) {
                continue;
            }
            // 每个达人仅保留第一条（最新的）作为 latestClaim
            latestClaims.putIfAbsent(claim.getTalentId(), claim);
            // 仅 status=ACTIVE 的认领记录归入生效映射
            if (claim.getStatus() != null && claim.getStatus() == CLAIM_STATUS_ACTIVE) {
                activeClaimsByTalent.computeIfAbsent(claim.getTalentId(), key -> new ArrayList<>()).add(claim);
                if (!activeClaims.containsKey(claim.getTalentId())) {
                    activeClaims.put(claim.getTalentId(), claim);
                }
            }
        }
        return new ClaimMaps(activeClaims, activeClaimsByTalent, latestClaims, claims);
    }

    /**
     * 批量加载认领人显示标签映射（userId → displayLabel）。
     *
     * @param claims 认领记录列表
     * @return 用户 ID 到显示标签的映射
     */
    private Map<UUID, String> loadOwnerLabels(Collection<TalentClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> userIds = claims.stream()
                .map(TalentClaim::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userDomainFacade.loadUserDisplayLabelsByIds(userIds);
    }

    /**
     * 批量加载达人寄样统计（talentId → 寄样次数）。
     * <p>通过寄样域门面读取，避免达人查询服务直接依赖寄样表结构。</p>
     *
     * @param talentIds 达人 ID 集合
     * @return 达人 ID 到寄样次数的映射
     */
    private Map<UUID, Long> loadSampleCounts(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sampleDomainFacade.countSamplesByTalentIds(talentIds);
    }

    /**
     * 批量加载达人订单汇总（douyinUid → TalentOrderSummary）。
     * <p>通过订单域门面读取，避免达人查询服务直接依赖订单表结构。</p>
     *
     * @param douyinUids 达人抖音 UID 集合
     * @return 抖音 UID 到订单聚合数据的映射
     */
    private Map<String, OrderReadFacade.TalentOrderSummary> loadOrderAggregates(Set<String> douyinUids) {
        if (douyinUids == null || douyinUids.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderReadFacade.summarizeTalentOrdersByDouyinUid(douyinUids, LocalDateTime.now().minusDays(30));
    }

    /**
     * 将达人实体转换为详情响应中的达人信息 DTO。
     * <p>当需要脱敏时（数据范围为 PERSONAL），隐藏 profileUrl 等敏感字段。</p>
     *
     * @param talent               达人实体（已富化）
     * @param redactSensitiveFields 是否脱敏敏感字段
     * @return 达人信息 DTO
     */
    private TalentDetailResponse.TalentInfo toTalentInfo(Talent talent, boolean redactSensitiveFields) {
        TalentDetailResponse.TalentInfo info = new TalentDetailResponse.TalentInfo();
        info.setId(talent.getId() == null ? null : talent.getId().toString());
        info.setNickname(talent.getNickname());
        info.setDouyinUid(talent.getDouyinUid());
        info.setDouyinNo(talent.getDouyinNo());
        info.setUid(talent.getUid());
        // 注意：脱敏时隐藏主页链接
        if (!redactSensitiveFields) {
            info.setProfileUrl(talent.getProfileUrl());
        }
        info.setFansCount(talent.getFans());
        info.setLikesCount(talent.getLikesCount());
        info.setWorksCount(talent.getWorksCount());
        info.setIpLocation(talent.getIpLocation());
        info.setLevel(talent.getLevel());
        info.setMonthlySales(talent.getMonthlySales());
        info.setMainCategory(talent.getMainCategory());
        info.setLiveSalesBand(talent.getLiveSalesBand());
        info.setLiveViewBand(talent.getLiveViewBand());
        info.setLiveGpmBand(talent.getLiveGpmBand());
        info.setVideoSalesBand(talent.getVideoSalesBand());
        info.setVideoPlayBand(talent.getVideoPlayBand());
        info.setVideoGpmBand(talent.getVideoGpmBand());
        info.setBlacklisted(Boolean.TRUE.equals(talent.getBlacklisted()));
        info.setBlacklistReason(talent.getBlacklistReason());
        info.setOrderCount(talent.getOrderCount());
        info.setSampleCount(talent.getSampleCount());
        info.setServiceFeeContribution(talent.getServiceFeeContribution());
        info.setContactPhone(firstNonBlank(talent.getContactPhone(), talent.getContactWechat()));
        info.setRemark(talent.getIntro());
        info.setAvatarUrl(talent.getAvatarUrl());
        info.setTags(talent.getTags());
        info.setTagUpdatedBy(talent.getTagUpdatedBy() == null ? null : talent.getTagUpdatedBy().toString());
        info.setShippingRecipientName(talent.getShippingRecipientName());
        info.setShippingRecipientPhone(talent.getShippingRecipientPhone());
        info.setShippingRecipientAddress(talent.getShippingRecipientAddress());
        return info;
    }

    /**
     * 将达人认领信息转换为详情响应中的认领信息 DTO。
     * <p>包含池状态、归属人、认领时间、保护期、收货地址（优先当前用户的认领记录），
     * 以及生效认领人列表（脱敏时隐藏）。</p>
     *
     * @param talent               达人实体（已富化）
     * @param currentUserId        当前操作用户 ID
     * @param redactSensitiveFields 是否脱敏敏感字段
     * @param activeClaims         该达人的生效认领记录列表
     * @return 认领信息 DTO
     */
    private TalentDetailResponse.ClaimInfo toClaimInfo(
            Talent talent,
            UUID currentUserId,
            boolean redactSensitiveFields,
            List<TalentClaim> activeClaims,
            Map<UUID, String> ownerLabelMap) {
        TalentDetailResponse.ClaimInfo info = new TalentDetailResponse.ClaimInfo();
        info.setPoolStatus(talent.getPoolStatus());
        info.setOwnerId(talent.getOwnerId() == null ? null : talent.getOwnerId().toString());
        info.setOwnerName(talent.getOwnerName());
        info.setClaimedAt(talent.getClaimedAt());
        info.setProtectedUntil(talent.getProtectedUntil());
        info.setActiveClaimCount(talent.getActiveClaimCount());
        // 优先取当前用户的认领记录作为收货地址来源
        TalentClaim claimAddress = resolveClaimAddress(activeClaims, currentUserId);
        if (claimAddress != null) {
            info.setRecipientName(claimAddress.getRecipientName());
            info.setRecipientPhone(claimAddress.getRecipientPhone());
            info.setRecipientAddress(claimAddress.getRecipientAddress());
        }
        // 脱敏时隐藏生效认领人列表
        info.setActiveClaimOwners(redactSensitiveFields
                ? List.of()
                : toActiveClaimOwners(activeClaims, currentUserId, ownerLabelMap));
        return info;
    }

    /**
     * 解析收货地址来源：优先匹配当前用户的认领记录，兜底取第一条。
     *
     * @param activeClaims  生效认领记录列表
     * @param currentUserId 当前操作用户 ID
     * @return 包含收货地址的认领记录，无记录时返回 null
     */
    private TalentClaim resolveClaimAddress(List<TalentClaim> activeClaims, UUID currentUserId) {
        if (activeClaims == null || activeClaims.isEmpty()) {
            return null;
        }
        // 优先匹配当前用户的认领记录
        if (currentUserId != null) {
            for (TalentClaim claim : activeClaims) {
                if (currentUserId.equals(claim.getUserId())) {
                    return claim;
                }
            }
        }
        // 兜底取第一条
        return activeClaims.get(0);
    }

    /**
     * 加载达人最近 20 条寄样记录。
     * <p>通过寄样域门面读取，达人域只负责组装详情响应。</p>
     *
     * @param talent 达人实体
     * @return 寄样记录列表
     */
    private List<TalentDetailResponse.SampleItem> loadSamples(Talent talent) {
        if (talent == null || talent.getId() == null) {
            return List.of();
        }
        List<TalentRecentSampleDTO> samples = sampleDomainFacade.listRecentSamplesByTalentId(talent.getId(), 20);
        List<TalentDetailResponse.SampleItem> items = new ArrayList<>();
        for (TalentRecentSampleDTO sample : samples) {
            TalentDetailResponse.SampleItem item = new TalentDetailResponse.SampleItem();
            item.setSampleRequestId(sample.sampleRequestId());
            item.setProductName(sample.productName());
            item.setStatus(sample.status());
            item.setStatusText(sample.statusText());
            item.setCreateTime(sample.createTime());
            item.setCompleteTime(sample.completeTime());
            items.add(item);
        }
        return items;
    }

    /**
     * 加载达人最近 20 条订单记录。
     * <p>通过订单域门面读取，按创建时间降序。脱敏时隐藏渠道人员姓名。</p>
     *
     * @param talent               达人实体
     * @param redactSensitiveFields 是否脱敏敏感字段
     * @return 订单记录列表
     */
    private List<TalentDetailResponse.OrderItem> loadOrders(Talent talent, boolean redactSensitiveFields) {
        if (talent == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return List.of();
        }
        List<OrderReadFacade.TalentRecentOrder> rows = orderReadFacade.findRecentOrdersByTalentUid(talent.getDouyinUid(), 20);
        List<TalentDetailResponse.OrderItem> items = new ArrayList<>();
        for (OrderReadFacade.TalentRecentOrder row : rows) {
            TalentDetailResponse.OrderItem item = new TalentDetailResponse.OrderItem();
            item.setOrderId(row.orderId());
            item.setProductName(row.productName());
            item.setOrderAmount(row.orderAmountCent());
            item.setServiceFee(row.serviceFeeCent());
            // 注意：脱敏时隐藏渠道人员姓名
            if (!redactSensitiveFields) {
                item.setChannelName(row.channelName());
            }
            item.setCreateTime(row.createTime());
            items.add(item);
        }
        return items;
    }

    /**
     * 判断是否需要脱敏敏感字段。当数据范围为 PERSONAL 时返回 true。
     *
     * @param dataScope 数据范围
     * @return 需要脱敏返回 true
     */
    private boolean shouldRedactSensitiveFields(DataScope dataScope) {
        return dataScope == DataScope.PERSONAL;
    }

    /**
     * 为公海达人（当前用户未认领）填充认领提示信息。
     * <p>展示逻辑分三种情况：</p>
     * <ul>
     *   <li>有其他人生效认领中 → 显示认领人摘要和最近保护期</li>
     *   <li>有历史认领但已过期 → 显示「已过期释放 · 原归属 XXX」</li>
     *   <li>从未被认领 → 全部置空</li>
     * </ul>
     *
     * @param talent      达人实体（结果写入该对象）
     * @param activeClaims 该达人的所有生效认领记录
     * @param latestClaim  该达人的最新认领记录（含已过期）
     * @param ownerLabelMap 用户 ID 到显示标签的映射
     */
    private void applyPublicClaimHint(Talent talent,
                                      List<TalentClaim> activeClaims,
                                      TalentClaim latestClaim,
                                      Map<UUID, String> ownerLabelMap) {
        if (activeClaims != null && !activeClaims.isEmpty()) {
            // 有生效认领：展示认领人摘要，保护期取所有认领中的最晚值
            talent.setOwnerName(buildClaimSummary(activeClaims, ownerLabelMap, null));
            TalentClaim latestActiveClaim = activeClaims.get(0);
            talent.setClaimedAt(latestActiveClaim.getClaimedAt());
            talent.setProtectedUntil(activeClaims.stream()
                    .map(TalentClaim::getProtectedUntil)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(latestActiveClaim.getProtectedUntil()));
            return;
        }
        if (latestClaim == null) {
            // 从未被认领：全部置空
            talent.setOwnerName(null);
            talent.setClaimedAt(null);
            talent.setProtectedUntil(null);
            return;
        }
        // 有历史认领但已过期：展示过期释放提示
        talent.setClaimedAt(latestClaim.getClaimedAt());
        talent.setProtectedUntil(latestClaim.getProtectedUntil());
        if (latestClaim.getStatus() != null && latestClaim.getStatus() == CLAIM_STATUS_EXPIRED) {
            String ownerName = ownerLabelMap.get(latestClaim.getUserId());
            talent.setOwnerName(StringUtils.hasText(ownerName) ? "已过期释放 · 原归属 " + ownerName : "已过期释放");
            return;
        }
        talent.setOwnerName(null);
    }

    /**
     * 构建认领人摘要文本。
     * <p>单人认领显示姓名，多人认领显示「XXX 等 N 人」，当前用户置顶展示。</p>
     *
     * @param claims        生效认领记录列表
     * @param ownerLabelMap 用户 ID 到显示标签的映射
     * @param currentUserId 当前操作用户 ID（可为 null）
     * @return 认领人摘要文本
     */
    private String buildClaimSummary(List<TalentClaim> claims, Map<UUID, String> ownerLabelMap, UUID currentUserId) {
        if (claims == null || claims.isEmpty()) {
            return null;
        }
        // 收集所有认领人的去重姓名
        List<String> names = claims.stream()
                .map(TalentClaim::getUserId)
                .filter(Objects::nonNull)
                .map(ownerLabelMap::get)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        // 优先取当前用户的姓名作为主展示名
        String primaryName = null;
        if (currentUserId != null) {
            primaryName = claims.stream()
                    .filter(claim -> currentUserId.equals(claim.getUserId()))
                    .map(TalentClaim::getUserId)
                    .map(ownerLabelMap::get)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
        }
        if (!StringUtils.hasText(primaryName) && !names.isEmpty()) {
            primaryName = names.get(0);
        }
        if (!StringUtils.hasText(primaryName)) {
            primaryName = "多人认领";
        }
        if (names.size() <= 1) {
            return primaryName;
        }
        return primaryName + " 等 " + names.size() + " 人";
    }

    /**
     * 加载达人详情页的生效认领人列表。
     * <p>处理流程：</p>
     * <p>调用方已在详情查询开始阶段加载认领记录和认领人标签，这里只负责 DTO 映射。</p>
     *
     * @param activeClaims  已加载的生效认领记录
     * @param currentUserId 当前操作用户 ID（可为 null）
     * @return 生效认领人列表，无认领记录时返回空列表
     */
    private List<TalentDetailResponse.ClaimOwnerItem> toActiveClaimOwners(
            List<TalentClaim> activeClaims,
            UUID currentUserId,
            Map<UUID, String> ownerLabelMap) {
        if (activeClaims == null || activeClaims.isEmpty()) {
            return List.of();
        }
        return activeClaims.stream()
                .map(claim -> {
                    TalentDetailResponse.ClaimOwnerItem item = new TalentDetailResponse.ClaimOwnerItem();
                    item.setUserId(claim.getUserId() == null ? null : claim.getUserId().toString());
                    String ownerName = claim.getUserId() == null ? null : ownerLabelMap.get(claim.getUserId());
                    if (currentUserId != null && currentUserId.equals(claim.getUserId()) && StringUtils.hasText(ownerName)) {
                        ownerName = ownerName + "（我）";
                    }
                    item.setOwnerName(ownerName);
                    item.setClaimedAt(claim.getClaimedAt());
                    item.setProtectedUntil(claim.getProtectedUntil());
                    return item;
                })
                .toList();
    }

    /**
     * 判断达人是否匹配指定的池状态筛选条件。
     * <p>未指定筛选条件时默认通过；达人未设置池状态时默认视为 PUBLIC。</p>
     *
     * @param talent     达人实体
     * @param poolStatus 目标池状态（PUBLIC / PRIVATE），可为空
     * @return 匹配返回 true
     */
    private boolean matchesPoolStatus(Talent talent, String poolStatus) {
        if (!StringUtils.hasText(poolStatus)) {
            return true;
        }
        return poolStatus.equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"));
    }

    /**
     * 判断达人归属人是否匹配关键词筛选。
     * <p>仅对私有池达人（PRIVATE）进行归属人模糊匹配；公海达人直接跳过。</p>
     *
     * @param talent      达人实体
     * @param ownerKeyword 归属人关键词
     * @return 匹配返回 true
     */
    private boolean matchesOwnerKeyword(Talent talent, String ownerKeyword) {
        if (!StringUtils.hasText(ownerKeyword)) {
            return true;
        }
        if (!"PRIVATE".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"))) {
            return false;
        }
        String ownerName = textOrEmpty(talent.getOwnerName());
        return ownerName.toLowerCase(Locale.ROOT).contains(ownerKeyword.toLowerCase(Locale.ROOT));
    }

    /**
     * 判断达人是否匹配当前视图筛选条件。
     * <p>视图类型决定不同的过滤逻辑：</p>
     * <ul>
     *   <li>TEAM_PUBLIC — 公海达人（非黑名单），个人数据范围时还需无生效认领</li>
     *   <li>MY_TALENTS — 私有池且归属人为当前用户</li>
     *   <li>TEAM_PRIVATE — 有生效认领且非黑名单</li>
     *   <li>NATURAL_ORDERS — 自然出单达人</li>
     *   <li>BLACKLIST — 黑名单达人</li>
     * </ul>
     *
     * @param talent 达人实体
     * @param query  分页查询条件
     * @return 匹配返回 true
     */
    private boolean matchesView(Talent talent, TalentPageQuery query) {
        String view = query.getView();
        if (!StringUtils.hasText(view)) {
            return true;
        }
        boolean personalPublicView = "TEAM_PUBLIC".equalsIgnoreCase(view)
                && query.getDataScope() == DataScope.PERSONAL;
        return switch (view) {
            case "TEAM_PUBLIC" -> "PUBLIC".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"))
                    && !Boolean.TRUE.equals(talent.getBlacklisted())
                    && (!personalPublicView || itemOrZero(talent.getActiveClaimCount()) == 0);
            case "MY_TALENTS" -> "PRIVATE".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"))
                    && Objects.equals(talent.getOwnerId(), query.getUserId());
            case "TEAM_PRIVATE" -> itemOrZero(talent.getActiveClaimCount()) > 0
                    && !Boolean.TRUE.equals(talent.getBlacklisted());
            case "NATURAL_ORDERS" -> Boolean.TRUE.equals(talent.getNaturalOrderTalent());
            case "BLACKLIST" -> Boolean.TRUE.equals(talent.getBlacklisted());
            default -> true;
        };
    }

    /**
     * 安全取 Number 值，null 时返回 0。
     *
     * @param value 数值对象，可为 null
     * @return 非 null 时返回 longValue()，否则返回 0
     */
    private long itemOrZero(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /**
     * 判断达人是否匹配认领状态筛选。
     * <p>CLAIMED 对应私有池（PRIVATE），UNCLAIMED 对应公海（PUBLIC）。</p>
     *
     * @param talent      达人实体
     * @param claimStatus 认领状态（CLAIMED / UNCLAIMED），可为空
     * @return 匹配返回 true
     */
    private boolean matchesClaimStatus(Talent talent, String claimStatus) {
        if (!StringUtils.hasText(claimStatus)) {
            return true;
        }
        return switch (claimStatus) {
            case "CLAIMED" -> "PRIVATE".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"));
            case "UNCLAIMED" -> "PUBLIC".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"));
            default -> true;
        };
    }

    /**
     * 判断达人是否匹配品类筛选。
     * <p>优先从主品类匹配，兜底从完整品类列表中模糊匹配。</p>
     *
     * @param talent   达人实体
     * @param category 目标品类关键词
     * @return 匹配返回 true
     */
    private boolean matchesCategory(Talent talent, String category) {
        if (!StringUtils.hasText(category)) {
            return true;
        }
        String needle = category.trim();
        String mainCategory = firstNonBlank(
                talent.getMainCategory(),
                resolveMainCategory(talent.getCategories()));
        if (StringUtils.hasText(mainCategory) && mainCategory.contains(needle)) {
            return true;
        }
        String categories = firstNonBlank(talent.getCategories());
        return StringUtils.hasText(categories) && categories.contains(needle);
    }

    /**
     * 判断达人是否匹配等级筛选。
     * <p>支持精确匹配和数字级别匹配（如 "S" 匹配 "S"，"5" 匹配 "Lv5"）。</p>
     *
     * @param talent 达人实体
     * @param level  目标等级
     * @return 匹配返回 true
     */
    private boolean matchesLevel(Talent talent, String level) {
        if (!StringUtils.hasText(level)) {
            return true;
        }
        String expected = level.trim();
        String actual = firstNonBlank(talent.getLevel(), talent.getTalentLevel(), "");
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        if (expected.equalsIgnoreCase(actual)) {
            return true;
        }
        String expectedDigits = expected.replaceAll("(?i)^lv", "");
        String actualDigits = actual.replaceAll("(?i)^lv", "");
        return expectedDigits.equalsIgnoreCase(actual)
                || expectedDigits.equalsIgnoreCase(actualDigits);
    }

    /**
     * 解析列表页的有效搜索关键词。
     * <p>按优先级依次取 keyword → nickname → douyinNo，取第一个非空值。</p>
     *
     * @param query 分页查询条件
     * @return 有效的搜索关键词，全部为空时返回 null
     */
    private String resolveListKeyword(TalentPageQuery query) {
        if (query == null) {
            return null;
        }
        if (StringUtils.hasText(query.getKeyword())) {
            return query.getKeyword().trim();
        }
        if (StringUtils.hasText(query.getNickname())) {
            return query.getNickname().trim();
        }
        if (StringUtils.hasText(query.getDouyinNo())) {
            return query.getDouyinNo().trim();
        }
        return null;
    }

    /**
     * 判断达人是否匹配地域筛选。
     * <p>通过达人 IP 归属地字段进行模糊匹配。</p>
     *
     * @param talent 达人实体
     * @param region 目标地域关键词
     * @return 匹配返回 true
     */
    private boolean matchesRegion(Talent talent, String region) {
        if (!StringUtils.hasText(region)) {
            return true;
        }
        return textOrEmpty(talent.getIpLocation()).contains(region.trim());
    }

    /**
     * 判断是否匹配平台筛选。
     * <p>当前仅排除快手（kuaishou）平台，其他平台均通过。</p>
     *
     * @param platform 平台标识
     * @return 非快手平台返回 true
     */
    private boolean matchesPlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return true;
        }
        return !"kuaishou".equalsIgnoreCase(platform.trim());
    }

    /**
     * 判断达人是否匹配抖音号筛选。
     * <p>从 douyinNo、douyinUid、uid 三个字段中模糊匹配。</p>
     *
     * @param talent  达人实体
     * @param douyinNo 目标抖音号
     * @return 匹配返回 true
     */
    private boolean matchesDouyinNo(Talent talent, String douyinNo) {
        if (!StringUtils.hasText(douyinNo)) {
            return true;
        }
        String normalized = douyinNo.trim();
        return textOrEmpty(talent.getDouyinNo(), talent.getDouyinUid(), talent.getUid()).contains(normalized);
    }

    /**
     * 判断达人是否匹配昵称筛选。
     *
     * @param talent   达人实体
     * @param nickname 目标昵称关键词
     * @return 匹配返回 true
     */
    private boolean matchesNickname(Talent talent, String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return true;
        }
        return textOrEmpty(talent.getNickname()).contains(nickname.trim());
    }

    /**
     * 判断达人指标分段是否匹配目标分段。
     *
     * @param actual   达人实际分段（如 "1W~5W"）
     * @param expected 目标分段筛选值
     * @return 匹配返回 true
     */
    private boolean matchesMetricBand(String actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return expected.equals(actual);
    }

    /**
     * 判断达人是否匹配联系方式状态筛选。
     * <p>HAS_CONTACT 要求有手机号或微信号，NO_CONTACT 要求均无。</p>
     *
     * @param talent        达人实体
     * @param contactStatus 联系方式状态（HAS_CONTACT / NO_CONTACT），可为空
     * @return 匹配返回 true
     */
    private boolean matchesContactStatus(Talent talent, String contactStatus) {
        if (!StringUtils.hasText(contactStatus)) {
            return true;
        }
        boolean hasContact = StringUtils.hasText(talent.getContactPhone())
                || StringUtils.hasText(talent.getContactWechat());
        return switch (contactStatus) {
            case "HAS_CONTACT" -> hasContact;
            case "NO_CONTACT" -> !hasContact;
            default -> true;
        };
    }

    /**
     * 从品类字符串中解析主品类（第一个品类）。
     * <p>支持 JSON 数组格式和逗号分隔格式，去除方括号和引号后取第一个非空项。</p>
     *
     * @param categories 品类原始字符串（如 "[\"美妆\",\"护肤\"]" 或 "美妆,护肤"）
     * @return 主品类名称，无法解析时返回 null
     */
    private String resolveMainCategory(String categories) {
        if (!StringUtils.hasText(categories)) {
            return null;
        }
        String normalized = categories.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("{", "")
                .replace("}", "");
        String[] parts = normalized.split("[,，]");
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                return part.trim();
            }
        }
        return StringUtils.hasText(normalized) ? normalized.trim() : null;
    }

    /**
     * 将销售额（单位：分）转换为区间分段标签。
     * <p>分段：1W以下、1W~2.5W、2.5W~5W、5W~7.5W、7.5W~10W、10W~25W、25W~50W、50W以上。</p>
     *
     * @param amount 销售额（分），null 或 ≤ 0 返回 null
     * @return 区间分段标签
     */
    private String toSalesBand(Long amount) {
        if (amount == null || amount <= 0) {
            return null;
        }
        long yuan = amount / 100;
        if (yuan < 10000) return "1W以下";
        if (yuan < 25000) return "1W~2.5W";
        if (yuan < 50000) return "2.5W~5W";
        if (yuan < 75000) return "5W~7.5W";
        if (yuan < 100000) return "7.5W~10W";
        if (yuan < 250000) return "10W~25W";
        if (yuan < 500000) return "25W~50W";
        return "50W以上";
    }

    /**
     * 将粉丝数转换为区间分段标签。
     * <p>分段：1W以下、1W~5W、5W~10W、10W~50W、50W~100W、100W以上。</p>
     *
     * @param fans 粉丝数，null 或 ≤ 0 返回 null
     * @return 区间分段标签
     */
    private String toFansBand(Long fans) {
        if (fans == null || fans <= 0) {
            return null;
        }
        if (fans < 10000) return "1W以下";
        if (fans < 50000) return "1W~5W";
        if (fans < 100000) return "5W~10W";
        if (fans < 500000) return "10W~50W";
        if (fans < 1000000) return "50W~100W";
        return "100W以上";
    }

    /**
     * 将场均播放量转换为区间分段标签。
     * <p>计算公式：粉丝数 / 订单数，代表每次带货的平均覆盖粉丝量。
     * 分段：5千以下、5千~1W、1W~5W、5W以上。</p>
     *
     * @param fans       粉丝数
     * @param orderCount 订单数
     * @return 区间分段标签，数据不足时返回 null
     */
    private String toPlayBand(Long fans, long orderCount) {
        if (fans == null || fans <= 0 || orderCount <= 0) {
            return null;
        }
        long score = Math.max(fans / Math.max(orderCount, 1), 1);
        if (score < 5000) return "5千以下";
        if (score < 10000) return "5千~1W";
        if (score < 50000) return "1W~5W";
        return "5W以上";
    }

    /**
     * 将 GPM（千次播放成交额）转换为区间分段标签。
     * <p>计算公式：(销售额/100) * 1000 / 粉丝数。分段：50~100、100~500、500~1000、1000+。</p>
     *
     * @param amount 销售额（单位：分）
     * @param fans   粉丝数
     * @return 区间分段标签，数据不足时返回 null
     */
    private String toGpmBand(Long amount, Long fans) {
        if (amount == null || amount <= 0 || fans == null || fans <= 0) {
            return null;
        }
        long gpm = Math.max((amount / 100) * 1000 / fans, 1);
        if (gpm < 100) return "50~100";
        if (gpm < 500) return "100~500";
        if (gpm < 1000) return "500~1000";
        return "1000+";
    }

    /**
     * 返回可变参数中第一个非空白字符串。
     *
     * @param values 候选字符串数组
     * @return 第一个非空白值，全部为空白或参数为 null 时返回 null
     */
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

    /**
     * 返回第一个非空白字符串，无有效值时返回空字符串（永不返回 null）。
     *
     * @param values 候选字符串数组
     * @return 非空白值或空字符串
     */
    private String textOrEmpty(String... values) {
        String value = firstNonBlank(values);
        return value == null ? "" : value;
    }

    /**
     * 将原始对象安全转换为字符串，null 时返回 null。
     *
     * @param raw 原始对象（来自数据库 Map 查询结果）
     * @return 字符串表示，null 时返回 null
     */
    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /**
     * 将原始对象安全转换为 long 值，null 或解析失败时返回 0。
     * <p>支持 Number 类型直接转换和字符串解析两种方式。</p>
     *
     * @param raw 原始对象
     * @return long 值，转换失败返回 0
     */
    private long asLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ex) {
            return 0L;
        }
    }

    /**
     * 将原始对象安全转换为 Integer 值，null 或解析失败时返回 null。
     *
     * @param raw 原始对象
     * @return Integer 值，转换失败返回 null
     */
    private Integer asInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 将原始对象安全解析为 UUID，null 或解析失败时返回 null。
     *
     * @param raw 原始对象（支持 UUID 类型直接返回或字符串解析）
     * @return UUID 对象，解析失败返回 null
     */
    private UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 将原始对象解析为 UUID 字符串表示，null 时返回 null。
     *
     * @param raw 原始对象
     * @return UUID 字符串，解析失败返回 null
     */
    private String uuidText(Object raw) {
        UUID uuid = parseUuid(raw);
        return uuid == null ? null : uuid.toString();
    }


    /**
     * 达人认领关系数据集合。
     * <p>一次加载完成所有认领关联查询，避免 N+1 问题。</p>
     *
     * @param activeClaims         用户 ID → 该用户的最新生效认领记录
     * @param activeClaimsByTalent 达人 ID → 该达人的所有生效认领记录列表
     * @param latestClaims         达人 ID → 该达人的最新认领记录（含已过期）
     * @param allClaims            所有查询到的认领记录原始列表
     */
    private record ClaimMaps(
            Map<UUID, TalentClaim> activeClaims,
            Map<UUID, List<TalentClaim>> activeClaimsByTalent,
            Map<UUID, TalentClaim> latestClaims,
            List<TalentClaim> allClaims) {
    }
}
