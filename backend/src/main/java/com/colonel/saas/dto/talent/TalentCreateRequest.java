package com.colonel.saas.dto.talent;

import com.colonel.saas.entity.Talent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class TalentCreateRequest {

    @Size(max = 128, message = "抖音 UID 不能超过 128 个字符")
    private String douyinUid;

    @Size(max = 128, message = "抖音号不能超过 128 个字符")
    private String douyinNo;

    @Size(max = 128, message = "UID 不能超过 128 个字符")
    private String uid;

    @Size(max = 256, message = "secUid 不能超过 256 个字符")
    private String secUid;

    @Size(max = 512, message = "主页链接不能超过 512 个字符")
    private String profileUrl;

    @Size(max = 120, message = "达人昵称不能超过 120 个字符")
    private String nickname;

    @PositiveOrZero(message = "粉丝数不能为负数")
    private Long fansCount;

    @Size(max = 64, message = "达人等级不能超过 64 个字符")
    private String level;

    @Size(max = 512, message = "头像链接不能超过 512 个字符")
    private String avatarUrl;

    @Size(max = 512, message = "达人类目不能超过 512 个字符")
    private String categories;

    @Size(max = 64, message = "联系方式不能超过 64 个字符")
    private String contactPhone;

    @Size(max = 1000, message = "达人简介不能超过 1000 个字符")
    private String intro;

    @AssertTrue(message = "达人抖音号或链接不能为空")
    @JsonIgnore
    public boolean isIdentityPresent() {
        return StringUtils.hasText(douyinUid)
                || StringUtils.hasText(douyinNo)
                || StringUtils.hasText(uid)
                || StringUtils.hasText(secUid)
                || StringUtils.hasText(profileUrl);
    }

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
        return talent;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
