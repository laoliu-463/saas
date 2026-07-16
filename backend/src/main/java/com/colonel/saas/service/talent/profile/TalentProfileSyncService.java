package com.colonel.saas.service.talent.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.dto.talent.TalentProfilePayload;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentProfileSyncLog;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.mapper.TalentProfileSyncLogMapper;
import com.colonel.saas.service.TalentService;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.TalentInputParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 达人资料同步服务 —— 达人资料采集子域的核心编排器。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>编排 {@link TalentProfileProvider} 策略链，按 {@code order()} 升序调用各提供者，采用 First-Win 策略
 *       第一个返回成功且包含真实数据的提供者胜出</li>
 *   <li>管理达人资料同步的三种入口：全新解析（{@link #resolveProfile}）、已有达人重新同步
 *       （{@link #syncExistingProfile}）、手动填写覆盖（{@link #applyManualProfile}）</li>
 *   <li>将采集到的资料字段映射到 {@link Talent} 实体并持久化到数据库</li>
 *   <li>维护同步状态机：success / partial_success / failed，支持智能跳过已成功的同步（非强制刷新时）</li>
 *   <li>通过 {@link TalentProfileSyncLogMapper} 记录每次同步的完整审计日志</li>
 *   <li>构造 {@link ResolveTalentProfileResponse} 响应 DTO 返回给上层调用方</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为达人资料采集子域的编排中心，向上对接 Controller / 任务触发层，
 * 向下调度 4 种 {@link TalentProfileProvider} 实现（API → 可配置HTTP → 公开页面爬取 → 手动填写）。
 * 所有公开方法均标注 {@code @Transactional}，确保资料写入和日志记录在同一事务中。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentProfileProvider
 * @see TalentProfileQuery
 * @see TalentProfileResult
 */
@Service
public class TalentProfileSyncService {

    /** 数据来源标识：手动填写 */
    private static final String DATA_SOURCE_MANUAL = "manual";

    /** 先前同步状态：全部成功（用于判断是否跳过重复同步） */
    private static final String PRIOR_SUCCESS = TalentProfileResult.STATUS_SUCCESS;

    /** 先前同步状态：部分成功（部分字段采集成功，仍有不支持字段） */
    private static final String PRIOR_PARTIAL = TalentProfileResult.STATUS_PARTIAL_SUCCESS;

    /** 按 order() 排序的提供者链（Spring 自动注入所有 TalentProfileProvider Bean） */
    private final List<TalentProfileProvider> providers;

    /** 达人实体 MyBatis-Plus Mapper，用于查询和更新达人记录 */
    private final TalentMapper talentMapper;

    /** 同步日志 Mapper，用于记录每次同步操作的审计日志 */
    private final TalentProfileSyncLogMapper syncLogMapper;

    /** 达人服务，提供 getById 等通用查询方法 */
    private final TalentService talentService;

    /**
     * 构造达人资料同步服务。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>接收 Spring 自动注入的所有 {@link TalentProfileProvider} 实现</li>
     *   <li>按 {@link TalentProfileProvider#order()} 升序排序，确保优先级高的提供者（数值小）先被调用</li>
     *   <li>将排序后的列表转为不可变 List，避免运行时被意外修改</li>
     *   <li>保存 Mapper 和 Service 引用供后续事务操作使用</li>
     * </ol>
     *
     * @param providers       Spring 注入的全部提供者 Bean（未排序）
     * @param talentMapper    达人实体 Mapper
     * @param syncLogMapper   同步日志 Mapper
     * @param talentService   达人服务
     */
    public TalentProfileSyncService(
            List<TalentProfileProvider> providers,
            TalentMapper talentMapper,
            TalentProfileSyncLogMapper syncLogMapper,
            TalentService talentService) {
        // 按提供者优先级升序排序：API(5) → 可配置HTTP(10) → 公开页面(20) → 手动(90)
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(TalentProfileProvider::order))
                .toList();
        this.talentMapper = talentMapper;
        this.syncLogMapper = syncLogMapper;
        this.talentService = talentService;
    }

    /**
     * 解析并同步达人资料 —— 主入口方法，覆盖全新解析和已有达人两种场景。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>解析用户输入文本，提取抖音号 / uid / secUid / 主页链接等结构化标识</li>
     *   <li>构建 {@link TalentProfileQuery} 查询对象，携带所有采集参数</li>
     *   <li>查询数据库中是否已存在匹配的达人记录（按 douyinUid → secUid → douyinNo 优先级匹配）</li>
     *   <li>若已存在且非强制刷新且先前同步已成功，则直接返回缓存资料，跳过采集</li>
     *   <li>调用提供者链执行资料采集（{@link #syncWithProviders}）</li>
     *   <li>记录同步日志（{@link #writeSyncLog}）</li>
     *   <li>若为已有达人且采集成功：将资料写入实体并更新数据库</li>
     *   <li>若为已有达人且采集失败且先前无成功记录：记录失败状态</li>
     *   <li>构造并返回 {@link ResolveTalentProfileResponse} 响应</li>
     * </ol>
     *
     * @param input          用户原始输入文本（抖音号、主页链接、secUid 等）
     * @param forceRefresh   是否强制刷新：true 时忽略已有成功记录，强制重新采集
     * @param manualFill     是否为手动填写模式
     * @param manualPayload  手动填写时的原始数据 Map（键为 {@link TalentProfileFieldNames} 字段名）
     * @return 同步结果响应，包含资料数据、同步状态、提供者标识等
     */
    @Transactional(rollbackFor = Exception.class)
    public ResolveTalentProfileResponse resolveProfile(
            String input,
            boolean forceRefresh,
            boolean manualFill,
            java.util.Map<String, Object> manualPayload) {
        // 第一步：解析用户输入，提取结构化达人标识
        TalentInputParseResult parsed = TalentInputParser.parse(input);
        // 第二步：构建查询对象
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input(input.trim())
                .forceRefresh(forceRefresh)
                .manualFill(manualFill)
                .manualPayload(manualPayload)
                .parsed(parsed)
                .build();

        // 第三步：查找数据库中已存在的达人记录
        Talent existing = findExistingByParsed(parsed);
        if (existing != null) {
            query.setTalentId(existing.getId());
            // 第四步：非强制刷新 + 先前同步已成功 → 直接返回缓存
            if (!forceRefresh && isPriorSuccessful(existing)) {
                return toResponse(existing, existing.getDataSource(), true);
            }
        }

        // 第五步：调用提供者链采集资料
        SyncAttempt attempt = syncWithProviders(query);
        // 第六步：记录同步日志
        writeSyncLog(query, attempt);

        if (existing != null) {
            if (attempt.result().isSuccess()) {
                // 第七步A：采集成功 → 将资料字段映射到已有达人实体并更新
                applySuccess(existing, attempt.result(), attempt.result().getProviderCode());
                talentMapper.updateById(existing);
                return toResponse(existing, existing.getDataSource(), true);
            }
            // 第七步B：采集失败且先前无成功记录 → 记录失败状态
            if (!isPriorSuccessful(existing)) {
                applyFailure(existing, attempt.result());
                talentMapper.updateById(existing);
            }
            return toResponse(existing, existing.getDataSource(), existing.getRawPayload() != null);
        }

        // 第八步：全新达人（数据库中无记录）→ 直接从采集结果构造响应
        return toResponse(attempt.result(), attempt.result().getProviderCode(), attempt.result().getRawPayload() != null);
    }

    /**
     * 重新同步已有达人的资料 —— 用于手动触发刷新、定时任务或接口调用。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>根据 {@code talentId} 加载已有达人实体</li>
     *   <li>若非强制刷新且先前同步已成功，直接返回缓存资料</li>
     *   <li>从达人实体中提取最佳输入源（profileUrl → douyinAccount → douyinNo → secUid → talentUid → douyinUid）</li>
     *   <li>解析输入并构建查询对象</li>
     *   <li>调用提供者链采集资料并记录日志</li>
     *   <li>采集成功：将资料映射到实体并更新数据库</li>
     *   <li>采集失败且先前无成功记录：记录失败状态并更新</li>
     *   <li>返回同步结果响应</li>
     * </ol>
     *
     * @param talentId      已有达人的 UUID
     * @param forceRefresh  是否强制刷新：true 时忽略已有成功记录
     * @return 同步结果响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ResolveTalentProfileResponse syncExistingProfile(UUID talentId, boolean forceRefresh) {
        // 第一步：加载已有达人实体
        Talent talent = talentService.getById(talentId);
        // 第二步：非强制刷新 + 先前已成功 → 直接返回缓存
        if (!forceRefresh && isPriorSuccessful(talent)) {
            return toResponse(talent, talent.getDataSource(), true);
        }
        // 第三步：从实体中提取最佳输入源（优先 profileUrl，兜底 douyinUid）
        String input = resolveInputValue(talent);
        TalentInputParseResult parsed = TalentInputParser.parse(input);
        // 第四步：构建查询对象
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input(input)
                .forceRefresh(forceRefresh)
                .talentId(talentId)
                .parsed(parsed)
                .build();

        // 第五步：调用提供者链采集资料
        SyncAttempt attempt = syncWithProviders(query);
        // 第六步：记录同步日志
        writeSyncLog(query, attempt);

        if (attempt.result().isSuccess()) {
            // 第七步A：采集成功 → 映射字段到实体并更新
            applySuccess(talent, attempt.result(), attempt.result().getProviderCode());
            talentMapper.updateById(talent);
            return toResponse(talent, talent.getDataSource(), true);
        }

        // 第七步B：采集失败且先前无成功记录 → 记录失败状态
        if (!isPriorSuccessful(talent)) {
            applyFailure(talent, attempt.result());
            talentMapper.updateById(talent);
        }
        return toResponse(talent, talent.getDataSource(), talent.getRawPayload() != null);
    }

    /**
     * 应用手动填写的达人资料 —— 覆盖已有达人的资料字段。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>根据 {@code talentId} 加载已有达人实体</li>
     *   <li>提取输入源并构建手动填写模式的查询对象（{@code manualFill=true}）</li>
     *   <li>调用提供者链（仅 {@code ManualTalentProvider} 的 {@code supports()} 会返回 true）</li>
     *   <li>记录同步日志</li>
     *   <li>成功时：将手动数据写入实体，数据来源强制设为 {@code "manual"}</li>
     *   <li>失败时：记录失败状态并更新实体</li>
     *   <li>返回同步结果响应</li>
     * </ol>
     *
     * @param talentId      已有达人的 UUID
     * @param manualPayload 手动填写的原始数据 Map（键为 {@link TalentProfileFieldNames} 字段名）
     * @return 同步结果响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ResolveTalentProfileResponse applyManualProfile(UUID talentId, java.util.Map<String, Object> manualPayload) {
        // 第一步：加载已有达人实体
        Talent talent = talentService.getById(talentId);
        // 第二步：提取输入源并构建手动模式查询对象
        String input = resolveInputValue(talent);
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input(input)
                .talentId(talentId)
                .manualFill(true)           // 手动填写模式标识
                .manualPayload(manualPayload)
                .parsed(TalentInputParser.parse(input))
                .build();
        // 第三步：调用提供者链（仅 ManualTalentProvider 会处理）
        SyncAttempt attempt = syncWithProviders(query);
        // 第四步：记录同步日志
        writeSyncLog(query, attempt);
        if (attempt.result().isSuccess()) {
            // 第五步A：成功 → 写入实体，数据来源标记为 manual
            applySuccess(talent, attempt.result(), DATA_SOURCE_MANUAL);
            talent.setDataSource(DATA_SOURCE_MANUAL);
            talentMapper.updateById(talent);
        } else {
            // 第五步B：失败 → 记录失败状态
            applyFailure(talent, attempt.result());
            talentMapper.updateById(talent);
        }
        return toResponse(talent, talent.getDataSource(), talent.getRawPayload() != null);
    }

    /**
     * 调用提供者链执行资料采集 —— 核心编排逻辑，采用 First-Win 策略。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>按 {@code order()} 升序遍历所有提供者（API → 可配置HTTP → 公开页面 → 手动）</li>
     *   <li>对每个提供者调用 {@code supports()} 判断是否支持当前查询，不支持则跳过</li>
     *   <li>调用 {@code fetch()} 执行资料采集</li>
     *   <li>判断采集结果：成功且 {@link TalentProfileResult#hasRealProfileData()} 为 true → 立即返回（First-Win）</li>
     *   <li>失败时保存最后一个失败结果作为兜底</li>
     *   <li>所有提供者均失败：返回最后一个失败结果</li>
     *   <li>无提供者响应（均不支持）：构造 {@code NO_PROVIDER} 错误</li>
     * </ol>
     *
     * @param query 达人资料查询请求
     * @return 采集尝试结果，包含采集数据或错误信息及提供者标识
     */
    private SyncAttempt syncWithProviders(TalentProfileQuery query) {
        TalentProfileResult lastFailed = null;
        // 按优先级遍历提供者链：API(5) → 可配置HTTP(10) → 公开页面(20) → 手动(90)
        for (TalentProfileProvider provider : providers) {
            // 第一步：判断当前提供者是否支持本次查询
            if (!provider.supports(query)) {
                continue;
            }
            // 第二步：执行资料采集
            TalentProfileResult result = provider.fetch(query);
            // 第三步：First-Win 策略 —— 成功且包含真实数据则立即返回
            if (result != null && result.isSuccess() && result.hasRealProfileData()) {
                return new SyncAttempt(result, provider.providerCode());
            }
            // 保存最后失败结果作为兜底
            if (result != null) {
                lastFailed = result;
            }
        }
        // 第四步：所有提供者均失败 → 返回最后一个失败结果
        if (lastFailed != null) {
            return new SyncAttempt(lastFailed, lastFailed.getProviderCode());
        }
        // 第五步：无提供者响应 → 构造 NO_PROVIDER 错误
        return new SyncAttempt(TalentProfileResult.builder()
                .success(false)
                .syncStatus(TalentProfileResult.STATUS_FAILED)
                .errorCode("NO_PROVIDER")
                .errorMessage("no profile provider returned data")
                .unsupportedFields(TalentProfileResult.DEFAULT_UNSUPPORTED)
                .build(), null);
    }

    /**
     * 写入同步日志 —— 记录每次同步操作的完整审计信息。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>创建 {@link TalentProfileSyncLog} 实体，设置全局唯一 ID</li>
     *   <li>关联达人 ID 和用户输入值</li>
     *   <li>记录实际执行采集的提供者标识</li>
     *   <li>记录同步状态（success / partial_success / failed）</li>
     *   <li>记录已获取字段列表和不支持字段列表</li>
     *   <li>保存原始采集负载用于问题排查</li>
     *   <li>记录错误码和错误信息（成功时为 null）</li>
     *   <li>设置开始/结束时间（当前简化为同一时刻）并持久化</li>
     * </ol>
     *
     * @param query   同步查询请求
     * @param attempt 采集尝试结果
     */
    private void writeSyncLog(TalentProfileQuery query, SyncAttempt attempt) {
        LocalDateTime now = LocalDateTime.now();
        TalentProfileSyncLog log = new TalentProfileSyncLog();
        log.setId(UUID.randomUUID());
        log.setTalentId(query.getTalentId());
        log.setInputValue(query.getInput());
        log.setProviderCode(attempt.providerCode());
        log.setSyncStatus(attempt.result().getSyncStatus());
        log.setFetchedFields(attempt.result().getFetchedFields());
        log.setUnsupportedFields(attempt.result().getUnsupportedFields());
        log.setRawPayload(attempt.result().getRawPayload());
        log.setErrorCode(attempt.result().getErrorCode());
        log.setErrorMessage(attempt.result().getErrorMessage());
        log.setStartedAt(now);
        log.setFinishedAt(now);
        syncLogMapper.insert(log);
    }

    /**
     * 将采集成功的结果映射到达人实体 —— 逐字段条件写入。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>逐字段检查结果中是否有有效值（非 null 非空白），有值时覆盖实体对应字段</li>
     *   <li>特殊映射：douyinAccount 同步写入 douyinNo；talentUid 同步写入 uid，首次时还写入 douyinUid</li>
     *   <li>talentLevel 和 sales30d 仅在结果明确返回值时更新，结果缺失时保留已有值，避免刷新资料造成数据丢失</li>
     *   <li>更新元信息字段：dataSource、syncStatus、lastSyncTime、rawPayload、unsupportedFields</li>
     *   <li>清除先前的同步错误信息（syncErrorCode 和 syncErrorMessage 置 null）</li>
     * </ol>
     *
     * <p>注意：此方法仅修改内存中的实体对象，调用方需自行调用 {@code talentMapper.updateById()} 持久化。</p>
     *
     * @param talent     待更新的达人实体
     * @param result     采集成功的结果
     * @param dataSource 数据来源标识（提供者 code 或 "manual"）
     */
    private void applySuccess(Talent talent, TalentProfileResult result, String dataSource) {
        // --- 基础身份字段映射 ---
        if (StringUtils.hasText(result.getDouyinAccount())) {
            talent.setDouyinAccount(result.getDouyinAccount());
            talent.setDouyinNo(result.getDouyinAccount());  // 同步写入 douyinNo
        }
        if (StringUtils.hasText(result.getTalentUid())) {
            talent.setTalentUid(result.getTalentUid());
            talent.setUid(result.getTalentUid());           // 同步写入 uid
            if (!StringUtils.hasText(talent.getDouyinUid())) {
                talent.setDouyinUid(result.getTalentUid()); // 首次才写入 douyinUid，避免覆盖
            }
        }
        if (StringUtils.hasText(result.getSecUid())) {
            talent.setSecUid(result.getSecUid());
        }
        // --- 个人资料字段映射 ---
        if (StringUtils.hasText(result.getNickname())) {
            talent.setNickname(result.getNickname());
        }
        if (StringUtils.hasText(result.getAvatarUrl())) {
            talent.setAvatarUrl(result.getAvatarUrl());
        }
        // --- 统计数据字段映射 ---
        if (result.getFansCount() != null) {
            talent.setFans(result.getFansCount());
        }
        if (result.getLikeCount() != null) {
            talent.setLikesCount(result.getLikeCount());
        }
        if (result.getFollowingCount() != null) {
            talent.setFollowingCount(result.getFollowingCount());
        }
        if (result.getWorksCount() != null) {
            talent.setWorksCount(result.getWorksCount());
        }
        if (StringUtils.hasText(result.getIpLocation())) {
            talent.setIpLocation(result.getIpLocation());
        }
        // --- 资料指标字段：仅使用明确返回的值更新，不能因本次来源不支持而清空旧值 ---
        if (StringUtils.hasText(result.getTalentLevel())) {
            talent.setTalentLevel(result.getTalentLevel());
        }
        if (result.getSales30d() != null) {
            talent.setSales30d(result.getSales30d());
        }
        // --- 同步元信息更新 ---
        talent.setDataSource(dataSource);
        talent.setSyncStatus(result.getSyncStatus());
        talent.setLastSyncTime(LocalDateTime.now());
        talent.setSyncErrorCode(null);       // 清除先前的错误信息
        talent.setSyncErrorMessage(null);
        if (result.getRawPayload() != null && !result.getRawPayload().isEmpty()) {
            talent.setRawPayload(result.getRawPayload());
        }
        talent.setUnsupportedFields(mergeUnsupportedFields(talent, result.getUnsupportedFields()));
    }

    /**
     * 将采集失败的信息写到达人实体 —— 记录失败状态和错误详情。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>设置同步状态为 {@code "failed"}</li>
     *   <li>记录同步时间为当前时刻</li>
     *   <li>写入错误码和错误描述</li>
     *   <li>若结果中有原始采集负载则保存（用于排查问题，空时不覆盖先前数据）</li>
     *   <li>更新不支持字段列表</li>
     * </ol>
     *
     * <p>注意：此方法仅修改内存中的实体对象，调用方需自行调用 {@code talentMapper.updateById()} 持久化。</p>
     *
     * @param talent 待更新的达人实体
     * @param result 采集失败的结果
     */
    private void applyFailure(Talent talent, TalentProfileResult result) {
        talent.setSyncStatus(TalentProfileResult.STATUS_FAILED);
        talent.setLastSyncTime(LocalDateTime.now());
        talent.setSyncErrorCode(result.getErrorCode());
        talent.setSyncErrorMessage(result.getErrorMessage());
        // 仅当结果中有原始负载时才覆盖，避免清空先前的数据
        if (result.getRawPayload() != null && !result.getRawPayload().isEmpty()) {
            talent.setRawPayload(result.getRawPayload());
        }
        talent.setUnsupportedFields(mergeUnsupportedFields(talent, result.getUnsupportedFields()));
    }

    /**
     * 合并资料来源声明，并清除已经有实际值的字段，避免 unsupported 标记与实体值矛盾。
     */
    private List<String> mergeUnsupportedFields(Talent talent, List<String> resultUnsupportedFields) {
        Set<String> unsupported = new LinkedHashSet<>();
        if (resultUnsupportedFields != null) {
            unsupported.addAll(resultUnsupportedFields);
        } else if (talent.getUnsupportedFields() != null) {
            unsupported.addAll(talent.getUnsupportedFields());
        }
        if (StringUtils.hasText(talent.getTalentLevel())) {
            unsupported.removeIf(field -> "talentLevel".equalsIgnoreCase(field));
        }
        if (talent.getSales30d() != null) {
            unsupported.removeIf(field -> "sales30d".equalsIgnoreCase(field));
        }
        return List.copyOf(unsupported);
    }

    /**
     * 根据解析后的标识信息查找数据库中已存在的达人记录。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>按优先级依次使用 douyinUid → secUid → douyinNo 查询</li>
     *   <li>每个查询条件均限制 {@code limit 1}，确保只返回一条匹配记录</li>
     *   <li>第一个找到的记录即返回，不再继续后续查询</li>
     *   <li>若所有标识均无匹配，返回 null</li>
     * </ol>
     *
     * @param parsed 解析后的达人标识信息
     * @return 匹配的达人实体，未找到时返回 null
     */
    private Talent findExistingByParsed(TalentInputParseResult parsed) {
        if (parsed == null) {
            return null;
        }
        // 优先级1：按 douyinUid 查询（最精确的标识）
        if (StringUtils.hasText(parsed.getDouyinUid())) {
            Talent byUid = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                    .eq(Talent::getDouyinUid, parsed.getDouyinUid())
                    .last("limit 1"));
            if (byUid != null) {
                return byUid;
            }
        }
        // 优先级2：按 secUid 查询
        if (StringUtils.hasText(parsed.getSecUid())) {
            return talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                    .eq(Talent::getSecUid, parsed.getSecUid())
                    .last("limit 1"));
        }
        // 优先级3：按 douyinNo 查询
        if (StringUtils.hasText(parsed.getDouyinNo())) {
            return talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                    .eq(Talent::getDouyinNo, parsed.getDouyinNo())
                    .last("limit 1"));
        }
        return null;
    }

    /**
     * 判断达人先前的同步是否已成功（全部成功或部分成功）。
     *
     * @param talent 达人实体
     * @return {@code true} 表示先前同步状态为 success 或 partial_success，可跳过重复采集
     */
    private boolean isPriorSuccessful(Talent talent) {
        if (talent == null || !StringUtils.hasText(talent.getSyncStatus())) {
            return false;
        }
        String status = talent.getSyncStatus().trim().toLowerCase();
        return PRIOR_SUCCESS.equals(status) || PRIOR_PARTIAL.equals(status);
    }

    /**
     * 从达人实体中提取最佳输入源 —— 用于重新同步时构造查询输入。
     *
     * <p>按优先级选择：profileUrl → douyinAccount → douyinNo → secUid → talentUid → douyinUid。
     * profileUrl 优先级最高是因为它可以提供最完整的达人定位信息。</p>
     *
     * @param talent 已有的达人实体
     * @return 用于查询的输入文本
     */
    private String resolveInputValue(Talent talent) {
        // 优先级从高到低：主页链接 > 抖音账号 > 抖音号 > secUid > 达人UID > 抖音UID
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return talent.getProfileUrl().trim();
        }
        if (StringUtils.hasText(talent.getDouyinAccount())) {
            return talent.getDouyinAccount().trim();
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return talent.getDouyinNo().trim();
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return talent.getSecUid().trim();
        }
        if (StringUtils.hasText(talent.getTalentUid())) {
            return talent.getTalentUid().trim();
        }
        return talent.getDouyinUid();
    }

    /**
     * 将达人实体转换为响应 DTO —— 用于已有达人场景。
     *
     * @param talent    达人实体（字段已在 applySuccess/applyFailure 中更新）
     * @param provider  数据来源标识（提供者 code 或 "manual"）
     * @param rawSaved  原始采集负载是否已保存
     * @return 达人资料同步响应 DTO
     */
    private ResolveTalentProfileResponse toResponse(Talent talent, String provider, boolean rawSaved) {
        return ResolveTalentProfileResponse.builder()
                .success(isPriorSuccessful(talent))
                .provider(provider)
                .syncStatus(talent.getSyncStatus())
                .profile(buildPayload(talent))
                .unsupportedFields(talent.getUnsupportedFields())
                .rawPayloadSaved(rawSaved && talent.getRawPayload() != null)
                .dataSource(talent.getDataSource())
                .syncErrorCode(talent.getSyncErrorCode())
                .syncErrorMessage(talent.getSyncErrorMessage())
                .build();
    }

    /**
     * 将采集结果直接转换为响应 DTO —— 用于全新达人场景（数据库中无记录）。
     *
     * @param result    采集结果
     * @param provider  数据来源标识
     * @param rawSaved  原始采集负载是否已保存
     * @return 达人资料同步响应 DTO
     */
    private ResolveTalentProfileResponse toResponse(TalentProfileResult result, String provider, boolean rawSaved) {
        return ResolveTalentProfileResponse.builder()
                .success(result.isSuccess())
                .provider(provider)
                .syncStatus(result.getSyncStatus())
                .profile(buildPayload(result))
                .unsupportedFields(result.getUnsupportedFields())
                .rawPayloadSaved(rawSaved)
                .dataSource(result.isSuccess() ? provider : null)
                .syncErrorCode(result.getErrorCode())
                .syncErrorMessage(result.getErrorMessage())
                .build();
    }

    /**
     * 从达人实体构建资料负载 DTO —— 映射所有业务字段。
     *
     * @param talent 达人实体
     * @return 资料负载 DTO
     */
    private TalentProfilePayload buildPayload(Talent talent) {
        return TalentProfilePayload.builder()
                .douyinAccount(talent.getDouyinAccount())
                .talentUid(talent.getTalentUid())
                .nickname(talent.getNickname())
                .avatarUrl(talent.getAvatarUrl())
                .fansCount(talent.getFans())
                .likeCount(talent.getLikesCount())
                .followingCount(talent.getFollowingCount())
                .worksCount(talent.getWorksCount())
                .ipLocation(talent.getIpLocation())
                .talentLevel(talent.getTalentLevel())
                .sales30d(talent.getSales30d())
                .build();
    }

    /**
     * 从采集结果构建资料负载 DTO —— 用于全新达人场景。
     *
     * @param result 采集结果
     * @return 资料负载 DTO
     */
    private TalentProfilePayload buildPayload(TalentProfileResult result) {
        return TalentProfilePayload.builder()
                .douyinAccount(result.getDouyinAccount())
                .talentUid(result.getTalentUid())
                .nickname(result.getNickname())
                .avatarUrl(result.getAvatarUrl())
                .fansCount(result.getFansCount())
                .likeCount(result.getLikeCount())
                .followingCount(result.getFollowingCount())
                .worksCount(result.getWorksCount())
                .ipLocation(result.getIpLocation())
                .talentLevel(result.getTalentLevel())
                .sales30d(result.getSales30d())
                .build();
    }

    /**
     * 同步尝试结果内部记录 —— 封装一次提供者链调用的最终结果。
     *
     * @param result       采集结果（成功时包含资料字段，失败时包含错误信息）
     * @param providerCode 实际执行采集的提供者标识（如 "API"、"configurable_http"），无提供者响应时为 null
     */
    private record SyncAttempt(TalentProfileResult result, String providerCode) {
    }
}
