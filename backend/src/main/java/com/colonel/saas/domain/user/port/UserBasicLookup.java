package com.colonel.saas.domain.user.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户基础信息查询端口。
 */
public interface UserBasicLookup {

    Optional<BasicUser> findById(UUID userId);

    List<BasicUser> findByIds(Collection<UUID> userIds);

    record BasicUser(UUID id, String username, String realName, UUID deptId, String channelCode) {
    }
}
