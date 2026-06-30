package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivitySyncScheduleMapperAdapterTest {

    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    @Test
    void findActivitiesDueForSync_shouldSelectActiveActivityIdsThroughMapper() {
        ProductActivitySyncScheduleMapperAdapter adapter =
                new ProductActivitySyncScheduleMapperAdapter(activityMapper);
        LocalDateTime lastSyncedBefore = LocalDateTime.of(2026, 6, 30, 14, 30);
        when(activityMapper.selectActiveActivityIds(20, lastSyncedBefore))
                .thenReturn(List.of("ACT-10", "ACT-20"));

        List<String> result = adapter.findActivitiesDueForSync(20, lastSyncedBefore);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activityMapper).selectActiveActivityIds(eq(20), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(lastSyncedBefore);
        assertThat(result).containsExactly("ACT-10", "ACT-20");
    }
}
