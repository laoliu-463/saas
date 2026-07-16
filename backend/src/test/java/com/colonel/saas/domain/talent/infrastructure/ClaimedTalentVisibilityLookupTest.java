package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.mapper.TalentClaimMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimedTalentVisibilityLookupTest {

    private TalentClaimMapper mapper;
    private ClaimedTalentVisibilityLookup lookup;

    @BeforeEach
    void setUp() {
        mapper = mock(TalentClaimMapper.class);
        lookup = new ClaimedTalentVisibilityLookup(
                mapper, new DataScopeResolver(new DataScopePolicy()));
    }

    @Test
    void retainVisibleTalentIds_shouldUseOnePersonalBatchQuery() {
        UUID userId = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        when(mapper.selectActiveTalentIdsByUserAndTalentIds(
                userId, List.of(visible, hidden))).thenReturn(List.of(visible));

        assertThat(lookup.retainVisibleTalentIds(
                List.of(visible, hidden), userId, null, DataScope.PERSONAL, false))
                .containsExactly(visible);

        verify(mapper).selectActiveTalentIdsByUserAndTalentIds(
                userId, List.of(visible, hidden));
        verify(mapper, never()).selectActiveTalentIdsByDeptAndTalentIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retainVisibleTalentIds_shouldUseOneDepartmentBatchQuery() {
        UUID deptId = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        when(mapper.selectActiveTalentIdsByDeptAndTalentIds(
                deptId, List.of(visible, hidden))).thenReturn(List.of(visible));

        assertThat(lookup.retainVisibleTalentIds(
                List.of(visible, hidden), null, deptId, DataScope.DEPT, false))
                .containsExactly(visible);
    }

    @Test
    void retainVisibleTalentIds_shouldBypassClaimsForAdminOrAll() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(lookup.retainVisibleTalentIds(
                List.of(first, first, second), null, null, DataScope.PERSONAL, true))
                .containsExactly(first, second);
        assertThat(lookup.retainVisibleTalentIds(
                List.of(first), null, null, DataScope.ALL, false))
                .containsExactly(first);

        verify(mapper, never()).selectActiveTalentIdsByUserAndTalentIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).selectActiveTalentIdsByDeptAndTalentIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retainVisibleTalentIds_shouldFailClosedWhenScopeContextIsMissing() {
        assertThatThrownBy(() -> lookup.retainVisibleTalentIds(
                List.of(UUID.randomUUID()), null, null, DataScope.PERSONAL, false))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> lookup.retainVisibleTalentIds(
                List.of(UUID.randomUUID()), UUID.randomUUID(), null, DataScope.DEPT, false))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> lookup.retainVisibleTalentIds(
                List.of(UUID.randomUUID()), UUID.randomUUID(), null, null, false))
                .isInstanceOf(ForbiddenException.class);
    }
}
