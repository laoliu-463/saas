package com.colonel.saas.dto.talent;

import com.colonel.saas.entity.Talent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class TalentUpdateRequest {

    @Size(max = 120, message = "达人昵称不能超过 120 个字符")
    private String nickname;

    @PositiveOrZero(message = "粉丝数不能为负数")
    private Long fansCount;

    @Size(max = 64, message = "达人等级不能超过 64 个字符")
    private String level;

    @Min(value = 0, message = "达人状态不正确")
    @Max(value = 1, message = "达人状态不正确")
    private Integer status;

    @Size(max = 512, message = "头像链接不能超过 512 个字符")
    private String avatarUrl;

    @PositiveOrZero(message = "点赞数不能为负数")
    private Long likesCount;

    @PositiveOrZero(message = "关注数不能为负数")
    private Long followingCount;

    @PositiveOrZero(message = "作品数不能为负数")
    private Long worksCount;

    @Size(max = 128, message = "地区不能超过 128 个字符")
    private String ipLocation;

    @Size(max = 64, message = "联系方式不能超过 64 个字符")
    private String contactPhone;

    @Size(max = 1000, message = "达人简介不能超过 1000 个字符")
    private String intro;

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

    public Talent toManualFillTalent() {
        Talent talent = toUpdateTalent();
        talent.setAvatarUrl(trimToNull(avatarUrl));
        talent.setLikesCount(likesCount);
        talent.setFollowingCount(followingCount);
        talent.setWorksCount(worksCount);
        talent.setIpLocation(trimToNull(ipLocation));
        return talent;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
