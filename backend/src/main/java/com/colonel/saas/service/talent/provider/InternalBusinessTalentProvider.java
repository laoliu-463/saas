package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "real", matchIfMissing = true)
public class InternalBusinessTalentProvider implements TalentDataProvider {

    @Override
    public TalentDataSource source() {
        return TalentDataSource.INTERNAL_BUSINESS;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        // P0 placeholder: internal business aggregate fields can be merged here later.
        return TalentEnrichResult.empty(source(), "internal business provider has no mapping yet");
    }
}
