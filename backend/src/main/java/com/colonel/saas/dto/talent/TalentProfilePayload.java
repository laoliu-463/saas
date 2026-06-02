package com.colonel.saas.dto.talent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 达人资料负载 DTO。
 * <p>
 * 封装从抖音平台同步回来的达人基础资料数据，包括账号信息、粉丝数据、等级和近 30 天销量等。
 * 关联业务领域：达人域（Talent）。
 * </p>
 *
 * <p><b>[V1 必做 t4-talent 2026-06-02] 字段命名一致性：</b>{@code likeCount} 字段通过
 * {@code @JsonProperty("likesCount")} 输出为 {@code likesCount}，与 {@code Talent.likesCount} /
 * {@code TalentDetailResponse.TalentInfo.likesCount} / {@code TalentUpdateRequest.likesCount} 对齐，
 * 避免前端消费侧出现 {@code undefined}。Java 字段名仍为 {@code likeCount} 以兼容
 * {@code TalentProfileFieldNames.LIKE_COUNT} 常量与所有 Provider 实现。</p>
 */
@Data
@Builder
public class TalentProfilePayload {

    /** 抖音账号 */
    private String douyinAccount;
    /** 达人平台 UID */
    private String talentUid;
    /** 达人 secUid（抖音平台安全标识） */
    private String secUid;
    /** 达人昵称 */
    private String nickname;
    /** 达人头像 URL */
    private String avatarUrl;
    /** 粉丝数 */
    private Long fansCount;
    /** 累计获赞数 — JSON 输出 {@code likesCount} */
    @JsonProperty("likesCount")
    private Long likeCount;
    /** 关注数 */
    private Long followingCount;
    /** 作品数 */
    private Long worksCount;
    /** IP 归属地 */
    private String ipLocation;
    /** 达人等级标识 */
    private String talentLevel;
    /** 近 30 天销售额（单位：分） */
    private Long sales30d;
}
