package com.colonel.saas.domain.user.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 组织负责人候选人查询端口。
 */
@FunctionalInterface
public interface OrgLeaderCandidateLookup {

    Optional<LeaderCandidate> findActiveLeaderCandidate(UUID leaderUserId);

    record LeaderCandidate(UUID userId, String realName, String username, Set<String> roleCodes) {
        public LeaderCandidate {
            roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        }
    }
}
