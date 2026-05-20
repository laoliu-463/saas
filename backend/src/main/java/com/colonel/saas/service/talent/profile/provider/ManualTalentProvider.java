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

@Component("profileManualTalentProvider")
public class ManualTalentProvider implements TalentProfileProvider {

    @Override
    public String providerCode() {
        return "manual";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean supports(TalentProfileQuery query) {
        return query != null && query.isManualFill() && query.getManualPayload() != null && !query.getManualPayload().isEmpty();
    }

    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
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

    private void track(List<String> fetched, String field, String value) {
        if (StringUtils.hasText(value)) {
            fetched.add(field);
        }
    }

    private void trackLong(List<String> fetched, String field, Long value) {
        if (value != null) {
            fetched.add(field);
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

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
