package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLifecycleServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private SampleStatusLogService sampleStatusLogService;

    private SampleLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new SampleLifecycleService(jdbcTemplate, sampleRequestMapper, sampleStatusLogService);
    }

    @Test
    void completePendingHomeworkByOrder_shouldCompleteMatchedRequests() {
        UUID requestId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-1");
        order.setProductId("dp-1");
        order.setChannelUserId(channelUserId);
        order.setExtraData(Map.of("talent_uid", "talent-1"));

        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(),
                any(),
                any()))
                .thenReturn(List.of(requestId));

        SampleRequest sample = new SampleRequest();
        sample.setId(requestId);
        sample.setStatus(5);
        when(sampleRequestMapper.selectById(requestId)).thenReturn(sample);

        int completed = service.completePendingHomeworkByOrder(order);

        assertThat(completed).isEqualTo(1);
        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(6);
        verify(sampleStatusLogService).log(requestId, 5, 6, null, "auto complete by order: order-1");
    }

    @Test
    void completePendingHomeworkByOrder_shouldSkipWhenNoTalentUid() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("dp-1");
        order.setChannelUserId(UUID.randomUUID());
        order.setExtraData(Map.of());

        int completed = service.completePendingHomeworkByOrder(order);

        assertThat(completed).isZero();
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void autoCloseTimeoutPendingHomework_shouldCloseTimedOutRequests() {
        UUID requestId = UUID.randomUUID();
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(LocalDateTime.class)))
                .thenReturn(List.of(requestId));

        SampleRequest sample = new SampleRequest();
        sample.setId(requestId);
        sample.setStatus(5);
        when(sampleRequestMapper.selectById(requestId)).thenReturn(sample);

        int closed = service.autoCloseTimeoutPendingHomework(30);

        assertThat(closed).isEqualTo(1);
        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(8);
        assertThat(captor.getValue().getCloseReason()).contains("30天");
        verify(sampleStatusLogService).log(requestId, 5, 8, null, "超时30天未出单自动关闭");
    }
}
