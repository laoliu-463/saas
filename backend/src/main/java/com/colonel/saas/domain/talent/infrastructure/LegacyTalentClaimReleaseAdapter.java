package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.domain.talent.application.port.TalentClaimReleasePort;
import com.colonel.saas.service.TalentService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LegacyTalentClaimReleaseAdapter implements TalentClaimReleasePort {

    private final TalentService talentService;

    public LegacyTalentClaimReleaseAdapter(TalentService talentService) {
        this.talentService = talentService;
    }

    @Override
    public void releaseExpiredClaims(LocalDateTime now) {
        talentService.releaseExpiredClaims(now);
    }
}
