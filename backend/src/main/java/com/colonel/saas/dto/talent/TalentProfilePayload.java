package com.colonel.saas.dto.talent;

import lombok.Builder;
import lombok.Data;

/**
 * 达人资料负载 DTO。
 * <p>
 * 封装从抖音平台同步回来的达人基础资料数据，包括账号信息、粉丝数据、等级和近 30 天销量等。
 * 关联业务领域：达人域（Talent）。
 * </p>
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
    /** 累计获赞数 */
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
