package com.colonel.saas.domain.talent.application;

import com.colonel.saas.domain.talent.application.port.TalentClaimReleasePort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 达人认领释放应用服务，隔离定时任务与 legacy 业务服务。 */
@Service
public class TalentClaimReleaseApplicationService {

    private final TalentClaimReleasePort talentClaimReleasePort;

    public TalentClaimReleaseApplicationService(TalentClaimReleasePort talentClaimReleasePort) {
        this.talentClaimReleasePort = talentClaimReleasePort;
    }

    public void releaseExpiredClaims(LocalDateTime now) {
        talentClaimReleasePort.releaseExpiredClaims(now);
    }
}
