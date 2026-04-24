package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "mock")
public class MockTalentProvider implements TalentDataProvider {

    @Override
    public TalentDataSource source() {
        return TalentDataSource.MOCK;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        Talent talent = context.talent();
        String input = resolveInput(talent).toLowerCase();

        if (input.contains("mock_fail")) {
            throw new IllegalStateException("mock provider simulated failure");
        }
        if (input.contains("mock_empty")) {
            return TalentEnrichResult.empty(source(), "mock provider returns empty data");
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        long seed = Math.abs(resolveInput(talent).hashCode());
        fields.put("nickname", "Mock达人" + (seed % 1000));
        fields.put("avatarUrl", "https://mock.local/avatar/" + (seed % 100) + ".png");
        fields.put("fans", 10_000L + (seed % 900_000L));
        fields.put("likesCount", 100_000L + (seed % 9_000_000L));
        fields.put("followingCount", 100L + (seed % 2000L));
        fields.put("worksCount", 10L + (seed % 500L));
        fields.put("ipLocation", mockRegion(seed));

        if (input.contains("mock_partial")) {
            fields.remove("ipLocation");
            fields.remove("followingCount");
        }

        return TalentEnrichResult.of(source(), fields, "mock provider data generated");
    }

    private String resolveInput(Talent talent) {
        if (StringUtils.hasText(talent.getDouyinUid())) {
            return talent.getDouyinUid().trim();
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return talent.getDouyinNo().trim();
        }
        if (StringUtils.hasText(talent.getUid())) {
            return talent.getUid().trim();
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return talent.getSecUid().trim();
        }
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return talent.getProfileUrl().trim();
        }
        return "mock_default";
    }

    private String mockRegion(long seed) {
        String[] regions = {"广东", "浙江", "江苏", "上海", "北京", "山东"};
        return regions[(int) (seed % regions.length)];
    }
}

