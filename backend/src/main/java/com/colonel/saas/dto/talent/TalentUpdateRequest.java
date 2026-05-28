package com.colonel.saas.dto.talent;

import com.colonel.saas.entity.Talent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 达人更新请求 DTO。
 * <p>
 * 用于更新达人基本信息和手动补充数据，包括昵称、粉丝数、等级、头像、社交数据等。
 * 提供两种转换模式：基础更新（toUpdateTalent）和手动填充（toManualFillTalent）。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
@Data
public class TalentUpdateRequest {

    /** 达人昵称，最大 120 字符 */
    @Size(max = 120, message = "达人昵称不能超过 120 个字符")
    private String nickname;

    /** 粉丝数，不能为负数 */
    @PositiveOrZero(message = "粉丝数不能为负数")
    private Long fansCount;

    /** 达人等级标识，最大 64 字符 */
    @Size(max = 64, message = "达人等级不能超过 64 个字符")
    private String level;

    /** 达人状态，0=禁用，1=正常 */
    @Min(value = 0, message = "达人状态不正确")
    @Max(value = 1, message = "达人状态不正确")
    private Integer status;

    /** 达人头像链接，最大 512 字符（仅手动填充模式使用） */
    @Size(max = 512, message = "头像链接不能超过 512 个字符")
    private String avatarUrl;

    /** 累计获赞数，不能为负数（仅手动填充模式使用） */
    @PositiveOrZero(message = "点赞数不能为负数")
    private Long likesCount;

    /** 关注数，不能为负数（仅手动填充模式使用） */
    @PositiveOrZero(message = "关注数不能为负数")
    private Long followingCount;

    /** 作品数，不能为负数（仅手动填充模式使用） */
    @PositiveOrZero(message = "作品数不能为负数")
    private Long worksCount;

    /** IP 归属地，最大 128 字符（仅手动填充模式使用） */
    @Size(max = 128, message = "地区不能超过 128 个字符")
    private String ipLocation;

    /** 联系电话，最大 64 字符 */
    @Size(max = 64, message = "联系方式不能超过 64 字符")
    private String contactPhone;

    /** 达人简介，最大 1000 字符 */
    @Size(max = 1000, message = "达人简介不能超过 1000 个字符")
    private String intro;

    /**
     * 转换为基础更新模式的达人实体。
     * <p>
     * 仅包含基础字段：昵称、粉丝数、等级、状态、联系方式、简介。
     * </p>
     *
     * @return 基础字段填充后的 {@link Talent} 实体
     */
    public Talent toUpdateTalent() {
        Talent talent = new Talent();
        talent.setNickname(trimToNull(nickname));
        talent.setFans(fansCount);
        talent.setLevel(trimToNull(level));
        talent.setStatus(status);
        talent.setContactPhone(trimToNull(contactPhone));
        talent.setIntro(trimToNull(intro));
        return talent;
    }

    /**
     * 转换为手动填充模式的达人实体。
     * <p>
     * 在基础更新字段之上，额外包含头像、点赞数、关注数、作品数、IP 归属地等社交数据字段。
     * </p>
     *
     * @return 包含社交数据字段的 {@link Talent} 实体
     */
    public Talent toManualFillTalent() {
        Talent talent = toUpdateTalent();
        talent.setAvatarUrl(trimToNull(avatarUrl));
        talent.setLikesCount(likesCount);
        talent.setFollowingCount(followingCount);
        talent.setWorksCount(worksCount);
        talent.setIpLocation(trimToNull(ipLocation));
        return talent;
    }

    /**
     * 去除字符串首尾空白，若为空或仅含空白则返回 null。
     *
     * @param value 待处理的字符串
     * @return 去除空白后的字符串，或 null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
