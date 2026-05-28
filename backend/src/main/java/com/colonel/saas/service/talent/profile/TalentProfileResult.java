package com.colonel.saas.service.talent.profile;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 达人资料同步结果 —— 封装一次达人资料同步操作返回的完整结果。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>记录同步状态（成功/部分成功/失败），通过 {@link #syncStatus} 区分三种状态常量</li>
 *   <li>携带采集到的达人资料字段（昵称、头像、粉丝数等），供上层写入 {@code Talent} 实体</li>
 *   <li>记录已成功获取的字段列表（{@link #fetchedFields}）和不支持的字段列表（{@link #unsupportedFields}）</li>
 *   <li>保留原始采集负载（{@link #rawPayload}），用于调试和问题排查</li>
 *   <li>提供 {@link #hasRealProfileData()} 判断是否有真实有效的资料数据（非仅凭 success 标志）</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>由各 {@link TalentProfileProvider} 实现类构造并返回，
 * 被 {@link TalentProfileSyncService} 消费。服务层根据 {@link #isSuccess()} 和
 * {@link #hasRealProfileData()} 决定是否将资料写入达人实体。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentProfileProvider
 * @see TalentProfileSyncService
 * @see TalentProfileFieldNames
 */
@Data
@Builder
public class TalentProfileResult {

    /** 同步状态：全部字段采集成功 */
    public static final String STATUS_SUCCESS = "success";

    /** 同步状态：部分字段采集成功，仍有不支持的字段（如达人等级、30天销售额） */
    public static final String STATUS_PARTIAL_SUCCESS = "partial_success";

    /** 同步状态：采集失败，无有效字段 */
    public static final String STATUS_FAILED = "failed";

    /**
     * 默认不支持的字段列表 —— 当前所有自动采集通道均无法获取的字段。
     * <p>包含 {@code talentLevel}（达人等级）和 {@code sales30d}（30天销售额）。
     * 仅手动填写或特定第三方接口可以填充这些字段，填充后会从 unsupported 列表中移除。</p>
     */
    public static final List<String> DEFAULT_UNSUPPORTED = List.of("talentLevel", "sales30d");

    /** 是否采集成功（{@code true} 表示至少有部分字段采集到） */
    private boolean success;

    /** 执行采集的提供者标识（如 "API"、"CRAWLER"、"manual"） */
    private String providerCode;

    /** 同步状态枚举：{@link #STATUS_SUCCESS} / {@link #STATUS_PARTIAL_SUCCESS} / {@link #STATUS_FAILED} */
    private String syncStatus;

    /** 错误码（如 "NOT_CONFIGURED"、"PUBLIC_WEB_BLOCKED"），成功时为 null */
    private String errorCode;

    /** 错误描述信息，成功时为 null */
    private String errorMessage;

    /** 采集到的抖音账号 */
    private String douyinAccount;

    /** 采集到的达人 UID */
    private String talentUid;

    /** 采集到的安全 UID（secUid） */
    private String secUid;

    /** 采集到的达人昵称 */
    private String nickname;

    /** 采集到的达人头像 URL */
    private String avatarUrl;

    /** 采集到的粉丝数 */
    private Long fansCount;

    /** 采集到的累计获赞数 */
    private Long likeCount;

    /** 采集到的关注数 */
    private Long followingCount;

    /** 采集到的作品数 */
    private Long worksCount;

    /** 采集到的 IP 属地 */
    private String ipLocation;

    /** 采集到的达人等级（通常不支持自动采集） */
    private String talentLevel;

    /** 采集到的 30 天销售额（通常不支持自动采集） */
    private Long sales30d;

    /** 已成功获取的字段名列表，值来自 {@link TalentProfileFieldNames} */
    @Builder.Default
    private List<String> fetchedFields = new ArrayList<>();

    /**
     * 不支持自动采集的字段名列表。
     * <p>初始值为 {@link #DEFAULT_UNSUPPORTED}；若提供者成功采集了某个不支持字段
     * （如手动填写了 talentLevel），则会从此列表中移除。</p>
     */
    @Builder.Default
    private List<String> unsupportedFields = new ArrayList<>(DEFAULT_UNSUPPORTED);

    /** 原始采集负载（JSON 响应、HTML 片段等），用于调试和问题排查 */
    @Builder.Default
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    /**
     * 判断是否包含真实有效的达人资料数据。
     *
     * <p>不仅检查 {@link #isSuccess()} 标志，还验证至少有一个核心字段
     * （昵称、粉丝数、获赞数、作品数、头像）有值。
     * 防止 Provider 返回 success=true 但实际未采集到任何有效数据的误判。</p>
     *
     * @return {@code true} 表示成功且至少包含一个核心资料字段
     */
    public boolean hasRealProfileData() {
        return success && (
                isPresent(nickname)
                        || fansCount != null
                        || likeCount != null
                        || worksCount != null
                        || isPresent(avatarUrl));
    }

    /**
     * 判断字符串值是否非空非空白。
     *
     * @param value 待判断的字符串
     * @return {@code true} 表示值非 null 且非空白
     */
    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
