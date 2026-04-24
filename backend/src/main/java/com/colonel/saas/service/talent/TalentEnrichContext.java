package com.colonel.saas.service.talent;

import com.colonel.saas.entity.Talent;

public record TalentEnrichContext(Talent talent, boolean forceRefresh) {
}

