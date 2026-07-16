package com.colonel.saas.service.talent;

import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentFieldSource;
import com.colonel.saas.mapper.TalentFieldSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 达人数据补全编排器 —— 协调多个 {@link TalentDataProvider} 完成达人字段补全。
 *
 * <p>采用策略模式 + 链式优先级调度：将所有已注册的 Provider 按 priority 升序排列，
 * 依次调用直到第一个返回有效数据的 Provider 命中为止（first-wins 策略）。</p>
 *
 * <ul>
 *   <li><b>职责</b>：
 *     <ul>
 *       <li>按优先级遍历 Provider，选择第一个返回有效数据的数据源</li>
 *       <li>将 Provider 返回的字段映射应用到 {@code Talent} 实体</li>
 *       <li>记录每个字段的数据来源到 {@code TalentFieldSource} 表，用于数据溯源</li>
 *       <li>维护 Talent 的补全状态（SUCCESS / NO_DATA）和最后补全时间</li>
 *     </ul>
 *   </li>
 *   <li><b>架构角色</b>：策略模式的编排者（Context），持有所有策略引用并决定调度顺序</li>
 *   <li><b>业务域</b>：达人域 → 数据补全子领域</li>
 *   <li><b>访问控制</b>：仅内部服务调用，不对外暴露</li>
 * </ul>
 *
 * <p>当前已注册的 Provider（按优先级排序）：</p>
 * <ol>
 *   <li>{@link com.colonel.saas.service.talent.provider.TestTalentProvider} — priority=1（仅测试模式）</li>
 *   <li>{@link com.colonel.saas.service.talent.provider.ThirdPartyTalentProvider} — priority=10</li>
 *   <li>{@link com.colonel.saas.service.talent.provider.InternalBusinessTalentProvider} — priority=20</li>
 *   <li>{@link com.colonel.saas.service.talent.provider.ManualTalentProvider} — priority=90（兜底）</li>
 * </ol>
 *
 * @see TalentDataProvider  策略接口定义
 * @see TalentEnrichContext  补全上下文
 * @see TalentEnrichResult  单个 Provider 的返回结果
 * @see OrchestrateResult  编排器的最终返回结果
 */
@Service
public class TalentEnrichOrchestrator {

    /** 已注册的 Provider 列表，按 priority 升序排列（构造函数中排序） */
    private final List<TalentDataProvider> providers;

    /** 达人字段来源 Mapper，用于记录每条字段的数据溯源 */
    private final TalentFieldSourceMapper talentFieldSourceMapper;

    /**
     * 构造注入所有已注册的 Provider，并在构造时按 priority 升序排列。
     *
     * @param providers               Spring 自动注入的所有 {@link TalentDataProvider} 实现
     * @param talentFieldSourceMapper 字段来源 Mapper
     */
    public TalentEnrichOrchestrator(List<TalentDataProvider> providers, TalentFieldSourceMapper talentFieldSourceMapper) {
        // 第一步：按优先级升序排列（值越小越优先）
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(TalentDataProvider::priority))
                .toList();
        this.talentFieldSourceMapper = talentFieldSourceMapper;
    }

    /**
     * 执行达人数据补全的主入口方法。
     *
     * <p>采用 first-wins 策略：按优先级遍历 Provider，第一个返回有效数据的 Provider 命中后立即停止。</p>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>校验达人实体非空，构建 {@link TalentEnrichContext}</li>
     *   <li>按优先级升序遍历所有 Provider</li>
     *   <li>对每个 Provider：调用 {@code supports()} 判断是否满足条件；若满足则调用 {@code enrich()}</li>
     *   <li>若 Provider 返回有效数据（{@code hasFields() == true}）：
     *     <ul>
     *       <li>调用 {@link #applyFields} 将字段映射到 Talent 实体</li>
     *       <li>设置数据源标识、补全状态为 SUCCESS、更新最后补全时间</li>
     *       <li>调用 {@link #recordFieldSources} 记录字段来源</li>
     *       <li>返回 {@link OrchestrateResult#updated}</li>
     *     </ul>
     *   </li>
     *   <li>若所有 Provider 均无数据：设置补全状态为 NO_DATA，返回 {@link OrchestrateResult#noData}</li>
     * </ol>
     *
     * @param talent       待补全的达人实体
     * @param forceRefresh 是否强制刷新（传递给 Provider，由 Provider 自行决定是否忽略缓存）
     * @return 编排结果，包含是否更新成功、数据源类型、提示信息
     */
    public OrchestrateResult enrich(Talent talent, boolean forceRefresh) {
        // 第一步：空值防御
        if (talent == null) {
            return OrchestrateResult.noData("talent is null");
        }

        // 第二步：构建补全上下文
        TalentEnrichContext context = new TalentEnrichContext(talent, forceRefresh);

        // 第三步：按优先级遍历 Provider（first-wins 策略）
        for (TalentDataProvider provider : providers) {
            // 第四步：检查 Provider 是否支持当前上下文
            if (!provider.supports(context)) {
                continue;
            }

            // 第五步：调用 Provider 执行补全
            TalentEnrichResult result = provider.enrich(context);

            // 第六步：检查返回结果是否有效
            if (result == null || !result.hasFields()) {
                continue;
            }

            // 第七步：应用字段到 Talent 实体
            applyFields(talent, result.fields());

            // 第八步：更新 Talent 的数据源、状态和时间戳
            talent.setDataSource(result.source().name());
            talent.setEnrichStatus("SUCCESS");
            talent.setLastEnrichTime(LocalDateTime.now());

            // 第九步：记录每个字段的数据来源，用于溯源追踪
            recordFieldSources(talent.getId(), result.source().name(), result.fields());

            return OrchestrateResult.updated(result.source().name(), result.message());
        }

        // 第十步：所有 Provider 均未返回数据，标记为 NO_DATA
        talent.setEnrichStatus("NO_DATA");
        talent.setLastEnrichTime(LocalDateTime.now());
        return OrchestrateResult.noData("no provider returned data");
    }

    /**
     * 将 Provider 返回的字段映射应用到 Talent 实体。
     *
     * <p>支持的字段：nickname、avatarUrl、fans、likesCount、followingCount、worksCount、ipLocation、
     * talentLevel、sales30d。
     * 不在映射表中的字段会被静默忽略。</p>
     *
     * @param talent 目标达人实体
     * @param fields Provider 返回的字段名 → 值映射
     */
    private void applyFields(Talent talent, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            switch (key) {
                case "nickname" -> talent.setNickname(String.valueOf(value));
                case "avatarUrl" -> talent.setAvatarUrl(String.valueOf(value));
                case "fans" -> talent.setFans(toLong(value));
                case "likesCount" -> talent.setLikesCount(toLong(value));
                case "followingCount" -> talent.setFollowingCount(toLong(value));
                case "worksCount" -> talent.setWorksCount(toLong(value));
                case "ipLocation" -> talent.setIpLocation(String.valueOf(value));
                case "talentLevel" -> {
                    talent.setTalentLevel(String.valueOf(value));
                    removeUnsupportedField(talent, "talentLevel");
                }
                case "sales30d" -> {
                    Long sales30d = toLong(value);
                    if (sales30d != null) {
                        talent.setSales30d(sales30d);
                        removeUnsupportedField(talent, "sales30d");
                    }
                }
                default -> {
                    // 忽略不支持的字段键
                }
            }
        }
    }

    private void removeUnsupportedField(Talent talent, String fieldName) {
        List<String> current = talent.getUnsupportedFields();
        if (current == null || current.isEmpty()) {
            talent.setUnsupportedFields(List.of());
            return;
        }
        Set<String> remaining = new LinkedHashSet<>(current);
        remaining.removeIf(field -> fieldName.equalsIgnoreCase(field));
        talent.setUnsupportedFields(new ArrayList<>(remaining));
    }

    /**
     * 批量记录达人字段的数据来源到 {@code TalentFieldSource} 表。
     *
     * <p>为每个非空字段插入一条来源记录，包含达人 ID、字段名、数据源类型、字段值和校验时间。</p>
     *
     * @param talentId   达人 ID
     * @param sourceType 数据源类型名称（如 THIRD_PARTY、MANUAL 等）
     * @param fields     Provider 返回的字段名 → 值映射
     */
    private void recordFieldSources(UUID talentId, String sourceType, Map<String, Object> fields) {
        // 第一步：防御性校验
        if (talentId == null || !StringUtils.hasText(sourceType)) {
            return;
        }

        // 第二步：批量插入字段来源记录
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            TalentFieldSource source = new TalentFieldSource();
            source.setTalentId(talentId);
            source.setFieldName(entry.getKey());
            source.setSourceType(sourceType);
            source.setSourceValue(String.valueOf(entry.getValue()));
            source.setVerifiedTime(now);
            talentFieldSourceMapper.insert(source);
        }
    }

    /**
     * 将任意对象安全地转换为 {@code Long}。
     *
     * <p>支持 {@link Number} 类型直接转换；字符串类型尝试 {@link Long#parseLong}；
     * 转换失败时返回 null，不抛出异常。</p>
     *
     * @param value 待转换的值
     * @return 转换后的 Long 值，或 null（输入为 null、空白字符串、或无法解析时）
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 编排结果记录 —— 封装达人补全的最终执行结果。
     *
     * @param updated    是否成功更新了达人数据
     * @param sourceType 数据源类型（仅 updated=true 时有值）
     * @param message    结果提示信息
     */
    public record OrchestrateResult(boolean updated, String sourceType, String message) {

        /**
         * 构造"更新成功"结果。
         *
         * @param sourceType 命中的数据源类型
         * @param message    提示信息
         * @return 更新成功的编排结果
         */
        static OrchestrateResult updated(String sourceType, String message) {
            return new OrchestrateResult(true, sourceType, message);
        }

        /**
         * 构造"无数据"结果。
         *
         * @param message 提示信息
         * @return 无数据的编排结果
         */
        static OrchestrateResult noData(String message) {
            return new OrchestrateResult(false, null, message);
        }
    }
}
