package com.colonel.saas.domain.talent.application.port;

import java.time.LocalDateTime;

/** 达人认领释放端口。 */
public interface TalentClaimReleasePort {

    void releaseExpiredClaims(LocalDateTime now);
}
