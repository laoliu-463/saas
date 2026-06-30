package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductActivitySyncStateMapperAdapterTest {

    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    @Test
    void markActivitySyncCompleted_shouldTouchLastSyncAtThroughMapper() {
        ProductActivitySyncStateMapperAdapter adapter =
                new ProductActivitySyncStateMapperAdapter(activityMapper);
        LocalDateTime completedAt = LocalDateTime.of(2026, 6, 30, 13, 30);

        adapter.markActivitySyncCompleted("ACT-1", completedAt);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activityMapper).touchLastSyncAt(eq("ACT-1"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(completedAt);
    }
}
