package com.colonel.saas.service.talent.profile.provider;

import com.colonel.saas.service.talent.profile.TalentProfileFieldNames;
import com.colonel.saas.service.talent.profile.TalentProfileProvider;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动填写模式的达人资料提供者 —— 处理用户手动录入的达人资料数据。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>从查询请求的 {@code manualPayload} 中提取用户手动填写的达人资料字段</li>
 *   <li>将原始 Object 值安全地转换为 String 或 Long 类型</li>
 *   <li>跟踪已成功获取的字段列表和不支持的字段列表</li>
 *   <li>当手动数据中包含通常不支持的字段（达人等级、30天销售额）时，自动从 unsupported 列表移除</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为 {@link TalentProfileProvider} 策略链中优先级最低的提供者（order=90），
 * 仅在手动填写模式下（{@code query.isManualFill()=true}）生效。
 * 当所有自动采集通道均失败时，作为最终兜底方案。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域 / 手动录入通道</p>
 *
 * @see TalentProfileProvider
 * @see TalentProfileFieldNames
 */
@Component("profileManualTalentProvider")
public class ManualTalentProvider implements TalentProfileProvider {

    /** {@inheritDoc} 返回提供者唯一标识 "manual"，表示手动填写通道 */
    @Override
    public String providerCode() {
        return "manual";
    }

    /** {@inheritDoc} 返回最低优先级 90，仅在其他采集通道均不可用时被调用 */
    @Override
    public int order() {
        return 90;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅在手动填写模式下支持：查询非空、手动填写标志为 true、且手动数据不为空。</p>
     */
    @Override
    public boolean supports(TalentProfileQuery query) {
        return query != null && query.isManualFill() && query.getManualPayload() != null && !query.getManualPayload().isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>从用户手动填写的数据中提取达人资料。</p>
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：从 manualPayload 中按字段名提取各达人资料值</li>
     *   <li>第二步：将 Object 类型值安全转换为 String/Long</li>
     *   <li>第三步：跟踪成功获取的字段，对于不支持的字段（等级、30天销售额）自动从 unsupported 移除</li>
     *   <li>第四步：无有效字段时返回失败，否则构建成功结果</li>
     * </ol>
     *
     * @param query 达人资料查询请求（需包含 manualPayload）
     * @return 手动填写的达人资料结果
     */
    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        // 第一步：获取手动填写的原始数据负载
        Map<String, Object> payload = query.getManualPayload();
        List<String> fetched = new ArrayList<>();
        List<String> unsupported = new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED);

        String nickname = asText(payload.get(TalentProfileFieldNames.NICKNAME));
        String avatarUrl = asText(payload.get(TalentProfileFieldNames.AVATAR_URL));
        Long fans = asLong(payload.get(TalentProfileFieldNames.FANS_COUNT));
        Long likes = asLong(payload.get(TalentProfileFieldNames.LIKE_COUNT));
        Long following = asLong(payload.get(TalentProfileFieldNames.FOLLOWING_COUNT));
        Long works = asLong(payload.get(TalentProfileFieldNames.WORKS_COUNT));
        String ip = asText(payload.get(TalentProfileFieldNames.IP_LOCATION));
        String level = asText(payload.get(TalentProfileFieldNames.TALENT_LEVEL));
        Long sales = asLong(payload.get(TalentProfileFieldNames.SALES_30D));

        track(fetched, TalentProfileFieldNames.NICKNAME, nickname);
        track(fetched, TalentProfileFieldNames.AVATAR_URL, avatarUrl);
        trackLong(fetched, TalentProfileFieldNames.FANS_COUNT, fans);
        trackLong(fetched, TalentProfileFieldNames.LIKE_COUNT, likes);
        trackLong(fetched, TalentProfileFieldNames.FOLLOWING_COUNT, following);
        trackLong(fetched, TalentProfileFieldNames.WORKS_COUNT, works);
        track(fetched, TalentProfileFieldNames.IP_LOCATION, ip);
        if (StringUtils.hasText(level)) {
            fetched.add(TalentProfileFieldNames.TALENT_LEVEL);
            unsupported.remove(TalentProfileFieldNames.TALENT_LEVEL);
        }
        if (sales != null) {
            fetched.add(TalentProfileFieldNames.SALES_30D);
            unsupported.remove(TalentProfileFieldNames.SALES_30D);
        }

        if (fetched.isEmpty()) {
            return TalentProfileResult.builder()
                    .success(false)
                    .providerCode(providerCode())
                    .syncStatus(TalentProfileResult.STATUS_FAILED)
                    .errorCode("MANUAL_EMPTY")
                    .errorMessage("manual payload has no profile fields")
                    .unsupportedFields(unsupported)
                    .build();
        }

        Map<String, Object> raw = new LinkedHashMap<>(payload);
        raw.put("dataSource", "manual");
        return TalentProfileResult.builder()
                .success(true)
                .providerCode(providerCode())
                .syncStatus(unsupported.isEmpty()
                        ? TalentProfileResult.STATUS_SUCCESS
                        : TalentProfileResult.STATUS_PARTIAL_SUCCESS)
                .douyinAccount(asText(payload.get(TalentProfileFieldNames.DOUYIN_ACCOUNT)))
                .talentUid(asText(payload.get(TalentProfileFieldNames.TALENT_UID)))
                .secUid(asText(payload.get(TalentProfileFieldNames.SEC_UID)))
                .nickname(nickname)
                .avatarUrl(avatarUrl)
                .fansCount(fans)
                .likeCount(likes)
                .followingCount(following)
                .worksCount(works)
                .ipLocation(ip)
                .talentLevel(level)
                .sales30d(sales)
                .fetchedFields(fetched)
                .unsupportedFields(unsupported)
                .rawPayload(raw)
                .build();
    }

    /**
     * 追踪文本类型字段：若值非空非空白，将其字段名加入已获取列表。
     *
     * @param fetched 已获取字段名列表
     * @param field   字段名
     * @param value   字段值
     */
    private void track(List<String> fetched, String field, String value) {
        if (StringUtils.hasText(value)) {
            fetched.add(field);
        }
    }

    /**
     * 追踪数值类型字段：若值非空，将其字段名加入已获取列表。
     *
     * @param fetched 已获取字段名列表
     * @param field   字段名
     * @param value   字段值
     */
    private void trackLong(List<String> fetched, String field, Long value) {
        if (value != null) {
            fetched.add(field);
        }
    }

    /**
     * 将 Object 值安全地转换为 String，null 值直接返回 null。
     *
     * @param value 原始 Object 值
     * @return 转换后的 String 值（已 trim），null 时返回 null
     */
    private String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 将 Object 值安全地转换为 Long。
     * 支持 Number 类型直接转换和 String 类型解析，解析失败返回 null。
     *
     * @param value 原始 Object 值
     * @return 转换后的 Long 值，null 或解析失败时返回 null
     */
    private Long asLong(Object value) {
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
}
