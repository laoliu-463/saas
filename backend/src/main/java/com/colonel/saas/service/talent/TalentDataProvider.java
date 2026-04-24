package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;

public interface TalentDataProvider {

    TalentDataSource source();

    default int priority() {
        return 100;
    }

    boolean supports(TalentEnrichContext context);

    TalentEnrichResult enrich(TalentEnrichContext context);
}
