package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.talent.port.TalentVisibilityLookup;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.mapper.TalentClaimMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 以生效认领关系实现 PERSONAL/DEPT 达人可见性。 */
@Component
public class ClaimedTalentVisibilityLookup implements TalentVisibilityLookup {

    private final TalentClaimMapper talentClaimMapper;
    private final DataScopeResolver dataScopeResolver;

    public ClaimedTalentVisibilityLookup(
            TalentClaimMapper talentClaimMapper,
            DataScopeResolver dataScopeResolver) {
        this.talentClaimMapper = talentClaimMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    @Override
    public List<UUID> retainVisibleTalentIds(
            Collection<UUID> requestedTalentIds,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            boolean unrestricted) {
        List<UUID> requested = requestedTalentIds == null
                ? List.of()
                : requestedTalentIds.stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                List::copyOf));
        if (requested.isEmpty()) {
            return List.of();
        }
        if (unrestricted || dataScope == DataScope.ALL) {
            return requested;
        }
        if (dataScope == null) {
            throw new ForbiddenException("数据权限异常：缺少数据范围");
        }
        DataScopeResolver.ResolvedDataScope resolved =
                dataScopeResolver.resolve(userId, deptId, dataScope);
        if (!resolved.contextSatisfied()) {
            throw new ForbiddenException("数据权限异常：缺少范围上下文");
        }
        List<UUID> visible;
        if (resolved.filtersUser()) {
            visible = talentClaimMapper.selectActiveTalentIdsByUserAndTalentIds(userId, requested);
        } else if (resolved.filtersDept()) {
            visible = talentClaimMapper.selectActiveTalentIdsByDeptAndTalentIds(deptId, requested);
        } else {
            return requested;
        }
        if (visible == null || visible.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> visibleSet = visible.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return requested.stream().filter(visibleSet::contains).toList();
    }
}
