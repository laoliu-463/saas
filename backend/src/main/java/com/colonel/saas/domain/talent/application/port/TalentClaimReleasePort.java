package com.colonel.saas.domain.talent.application.port;

import java.time.LocalDateTime;

public interface TalentClaimReleasePort {

    void releaseExpiredClaims(LocalDateTime now);
}
