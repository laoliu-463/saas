package com.colonel.saas.domain.user.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 组织负责人展示名查询端口。
 */
@FunctionalInterface
public interface OrgLeaderDisplayLookup {

    Optional<LeaderDisplay> findDisplay(UUID leaderUserId);

    record LeaderDisplay(String displayName) {
    }
}
