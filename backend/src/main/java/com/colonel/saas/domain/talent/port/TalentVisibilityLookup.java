package com.colonel.saas.domain.talent.port;

import com.colonel.saas.common.enums.DataScope;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 按当前可信身份上下文收敛可见达人 ID 的窄查询端口。 */
public interface TalentVisibilityLookup {

    List<UUID> retainVisibleTalentIds(
            Collection<UUID> requestedTalentIds,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            boolean unrestricted);
}
