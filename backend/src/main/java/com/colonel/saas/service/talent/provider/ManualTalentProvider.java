package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(90)
public class ManualTalentProvider implements TalentDataProvider {

    @Override
    public TalentDataSource source() {
        return TalentDataSource.MANUAL;
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        Talent talent = context.talent();
        Map<String, Object> fields = new LinkedHashMap<>();
        if (StringUtils.hasText(talent.getNickname())) {
            fields.put("nickname", talent.getNickname().trim());
        }
        if (StringUtils.hasText(talent.getAvatarUrl())) {
            fields.put("avatarUrl", talent.getAvatarUrl().trim());
        }
        if (talent.getFans() != null) {
            fields.put("fans", talent.getFans());
        }
        if (talent.getLikesCount() != null) {
            fields.put("likesCount", talent.getLikesCount());
        }
        if (talent.getFollowingCount() != null) {
            fields.put("followingCount", talent.getFollowingCount());
        }
        if (talent.getWorksCount() != null) {
            fields.put("worksCount", talent.getWorksCount());
        }
        if (StringUtils.hasText(talent.getIpLocation())) {
            fields.put("ipLocation", talent.getIpLocation().trim());
        }
        if (fields.isEmpty()) {
            return TalentEnrichResult.empty(source(), "manual data is empty");
        }
        return TalentEnrichResult.of(source(), fields, "manual data merged");
    }
}
