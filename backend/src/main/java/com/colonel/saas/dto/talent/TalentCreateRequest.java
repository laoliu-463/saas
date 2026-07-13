package com.colonel.saas.dto.talent;

import com.colonel.saas.entity.Talent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 达人创建请求 DTO。
 * <p>
 * 用于新建达人记录，至少需要提供抖音号、UID 或主页链接中的一种标识信息。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
@Data
public class TalentCreateRequest {

    /** 抖音平台 UID，最大 128 字符 */
    @Size(max = 128, message = "抖音 UID 不能超过 128 个字符")
    private String douyinUid;

    /** 抖音号（用户自定义的账号标识），最大 128 字符 */
    @Size(max = 128, message = "抖音号不能超过 128 个字符")
    private String douyinNo;

    /** 通用 UID，最大 128 字符 */
    @Size(max = 128, message = "UID 不能超过 128 个字符")
    private String uid;

    /** 安全 UID（secUid），最大 256 字符 */
    @Size(max = 256, message = "secUid 不能超过 256 个字符")
    private String secUid;

    /** 达人抖音主页链接，最大 512 字符 */
    @Size(max = 512, message = "主页链接不能超过 512 个字符")
    private String profileUrl;

    /** 达人昵称，最大 120 字符 */
    @Size(max = 120, message = "达人昵称不能超过 120 个字符")
    private String nickname;

    /** 粉丝数，不能为负数 */
    @PositiveOrZero(message = "粉丝数不能为负数")
    private Long fansCount;

    /** 达人等级标识，最大 64 字符 */
    @Size(max = 64, message = "达人等级不能超过 64 个字符")
    private String level;

    /** 达人头像链接，最大 512 字符 */
    @Size(max = 512, message = "头像链接不能超过 512 个字符")
    private String avatarUrl;

    /** 达人擅长类目，最大 512 字符 */
    @Size(max = 512, message = "达人类目不能超过 512 个字符")
    private String categories;

    /** 联系电话，最大 64 字符 */
    @Size(max = 64, message = "联系方式不能超过 64 字符")
    private String contactPhone;

    /** 达人简介，最大 1000 字符 */
    @Size(max = 1000, message = "达人简介不能超过 1000 个字符")
    private String intro;

    /** 抖音账号展示值，手动创建时可由前端预填 */
    @Size(max = 128, message = "抖音账号不能超过 128 个字符")
    private String douyinAccount;

    /** 资料来源，例如 manual */
    @Size(max = 64, message = "资料来源不能超过 64 个字符")
    private String dataSource;

    /** 资料同步状态，例如 success */
    @Size(max = 64, message = "资料同步状态不能超过 64 个字符")
    private String syncStatus;

    /** 当前创建请求明确未提供的资料字段 */
    private List<@Size(max = 64, message = "未支持字段名不能超过 64 个字符") String> unsupportedFields;

    /**
     * 校验达人身份标识是否至少提供了一种。
     * <p>
     * 至少需要提供 douyinUid、douyinNo、uid、secUid、profileUrl 中的一个。
     * </p>
     *
     * @return 至少存在一个标识字段时返回 true
     */
    @AssertTrue(message = "达人抖音号或链接不能为空")
    @JsonIgnore
    public boolean isIdentityPresent() {
        return StringUtils.hasText(douyinUid)
                || StringUtils.hasText(douyinNo)
                || StringUtils.hasText(uid)
                || StringUtils.hasText(secUid)
                || StringUtils.hasText(profileUrl);
    }

    /**
     * 将请求 DTO 转换为达人实体对象。
     * <p>
     * 字符串字段会自动去除首尾空白，空字符串转为 null。
     * </p>
     *
     * @return 转换后的 {@link Talent} 实体
     */
    public Talent toTalent() {
        Talent talent = new Talent();
        talent.setDouyinUid(trimToNull(douyinUid));
        talent.setDouyinNo(trimToNull(douyinNo));
        talent.setUid(trimToNull(uid));
        talent.setSecUid(trimToNull(secUid));
        talent.setProfileUrl(trimToNull(profileUrl));
        talent.setNickname(trimToNull(nickname));
        talent.setFans(fansCount);
        talent.setLevel(trimToNull(level));
        talent.setAvatarUrl(trimToNull(avatarUrl));
        talent.setCategories(trimToNull(categories));
        talent.setContactPhone(trimToNull(contactPhone));
        talent.setIntro(trimToNull(intro));
        talent.setDouyinAccount(trimToNull(douyinAccount));
        talent.setDataSource(trimToNull(dataSource));
        talent.setSyncStatus(trimToNull(syncStatus));
        talent.setUnsupportedFields(unsupportedFields == null ? null : new ArrayList<>(unsupportedFields));
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
