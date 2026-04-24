package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "real", matchIfMissing = true)
public class ThirdPartyTalentProvider implements TalentDataProvider {

    @Override
    public TalentDataSource source() {
        return TalentDataSource.THIRD_PARTY;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        // P0 placeholder: bind real third-party API adapter in P1.
        return TalentEnrichResult.empty(source(), "third party provider is not configured");
    }
}
