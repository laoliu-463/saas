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
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "test")
public class TestTalentProvider implements TalentDataProvider {

    @Override
    public TalentDataSource source() {
        return TalentDataSource.TEST;
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

        if (input.contains("test_fail")) {
            throw new IllegalStateException("test provider simulated failure");
        }
        if (input.contains("test_empty")) {
            return TalentEnrichResult.empty(source(), "test provider returns empty data");
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        long seed = Math.abs(resolveInput(talent).hashCode());
        fields.put("nickname", "演示达人-" + (seed % 1000));
        fields.put("avatarUrl", "https://test.local/avatar/" + (seed % 100) + ".png");
        fields.put("fans", 10_000L + (seed % 900_000L));
        fields.put("likesCount", 100_000L + (seed % 9_000_000L));
        fields.put("followingCount", 100L + (seed % 2000L));
        fields.put("worksCount", 10L + (seed % 500L));
        fields.put("ipLocation", testRegion(seed));

        if (input.contains("test_partial")) {
            fields.remove("ipLocation");
            fields.remove("followingCount");
        }

        return TalentEnrichResult.of(source(), fields, "test provider generated demo talent data");
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
        return "test_default";
    }

    private String testRegion(long seed) {
        String[] regions = {"广东深圳", "浙江杭州", "江苏南京", "上海浦东", "北京朝阳", "山东青岛"};
        return regions[(int) (seed % regions.length)];
    }
}


