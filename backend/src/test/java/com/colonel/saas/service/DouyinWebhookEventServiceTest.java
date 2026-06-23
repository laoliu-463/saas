package com.colonel.saas.service;

import com.colonel.saas.entity.DouyinWebhookEvent;
import com.colonel.saas.mapper.DouyinWebhookEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinWebhookEventServiceTest {

    @Mock
    private DouyinWebhookEventMapper eventMapper;
    @Mock
    private OrderSyncService orderSyncService;
    @Mock
    private ProductActivityManualSyncService productActivityManualSyncService;

    private DouyinWebhookEventService service;

    @BeforeEach
    void setUp() {
        service = new DouyinWebhookEventService(
                eventMapper,
                new ObjectMapper(),
                orderSyncService,
                productActivityManualSyncService);
    }

    @Test
    void captureColonelOpenEvent_shouldInsertAndConsumeValidEvent() {
        String body = "{\"event\":\"doudian_alliance_colonelOpenEvent\",\"event_id\":\"evt-001\",\"data\":{\"test\":true}}";
        when(eventMapper.selectOne(any())).thenReturn(null);

        DouyinWebhookEventService.CaptureResult result = service.captureColonelOpenEvent(body);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.status()).isEqualTo(DouyinWebhookEventService.STATUS_CONSUMED);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        verify(eventMapper).updateById(captor.getValue());
        DouyinWebhookEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("doudian_alliance_colonelOpenEvent");
        assertThat(event.getEventKey()).isEqualTo("doudian_alliance_colonelOpenEvent:evt-001");
        assertThat(event.getStatus()).isEqualTo(DouyinWebhookEventService.STATUS_CONSUMED);
        assertThat(event.getConsumeResult()).isEqualTo("COLONEL_OPEN_EVENT_CAPTURED");
        assertThat(event.getRawPayload()).isEqualTo(body);
    }

    @Test
    void captureColonelOpenEvent_shouldSyncTargetOrdersWhenPayloadContainsOrderIds() {
        String body = "{\"event\":\"doudian_alliance_colonelOpenEvent\",\"event_id\":\"evt-002\",\"data\":{\"order_ids\":[\"ORDER_1\",\"ORDER_2\"]}}";
        when(eventMapper.selectOne(any())).thenReturn(null);
        when(orderSyncService.syncByOrderIds(List.of("ORDER_1", "ORDER_2")))
                .thenReturn(new OrderSyncService.SyncResult(0L, 0L, 1, 2, 1, 1, 1, 1, 0, false));

        DouyinWebhookEventService.CaptureResult result = service.captureColonelOpenEvent(body);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.status()).isEqualTo(DouyinWebhookEventService.STATUS_CONSUMED);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        verify(eventMapper).updateById(captor.getValue());
        assertThat(captor.getValue().getConsumeResult())
                .isEqualTo("COLONEL_OPEN_EVENT_SYNCED:fetched=2,created=1,updated=1,failed=0");
        verify(orderSyncService).syncByOrderIds(List.of("ORDER_1", "ORDER_2"));
    }

    @Test
    void captureColonelOpenEvent_shouldTriggerActivityProductSyncWhenPayloadContainsActivityIds() {
        String body = "{\"event\":\"doudian_alliance_colonelOpenEvent\",\"event_id\":\"evt-activity-001\",\"data\":{\"app_id\":\"APP-1\",\"activity_ids\":[\"ACT_1\",\"ACT_2\",\"ACT_1\"]}}";
        when(eventMapper.selectOne(any())).thenReturn(null);
        when(productActivityManualSyncService.trigger("ACT_1", "APP-1"))
                .thenReturn(new ProductActivityManualSyncService.SyncTriggerResult("ACT_1", "ACCEPTED"));
        when(productActivityManualSyncService.trigger("ACT_2", "APP-1"))
                .thenReturn(new ProductActivityManualSyncService.SyncTriggerResult("ACT_2", "RUNNING"));

        DouyinWebhookEventService.CaptureResult result = service.captureColonelOpenEvent(body);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.status()).isEqualTo(DouyinWebhookEventService.STATUS_CONSUMED);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        verify(eventMapper).updateById(captor.getValue());
        assertThat(captor.getValue().getConsumeResult())
                .isEqualTo("COLONEL_OPEN_EVENT_PRODUCT_SYNC_TRIGGERED:accepted=1,running=1,busy=0,invalid=0,failed=0");
        verify(productActivityManualSyncService).trigger("ACT_1", "APP-1");
        verify(productActivityManualSyncService).trigger("ACT_2", "APP-1");
    }

    @Test
    void captureColonelOpenEvent_shouldMarkFailedForReplayWhenActivitySyncQueueIsBusy() {
        String body = "{\"event\":\"doudian_alliance_colonelOpenEvent\",\"event_id\":\"evt-activity-busy\",\"data\":{\"activity_id\":\"ACT_BUSY\"}}";
        when(eventMapper.selectOne(any())).thenReturn(null);
        when(productActivityManualSyncService.trigger("ACT_BUSY", null))
                .thenReturn(new ProductActivityManualSyncService.SyncTriggerResult("ACT_BUSY", "BUSY"));

        DouyinWebhookEventService.CaptureResult result = service.captureColonelOpenEvent(body);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.status()).isEqualTo(DouyinWebhookEventService.STATUS_FAILED);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        verify(eventMapper).updateById(captor.getValue());
        assertThat(captor.getValue().getConsumeResult())
                .isEqualTo("COLONEL_OPEN_EVENT_PRODUCT_SYNC_TRIGGERED:accepted=0,running=0,busy=1,invalid=0,failed=0");
    }

    @Test
    void captureColonelOpenEvent_shouldNotConsumeDuplicateEvent() {
        DouyinWebhookEvent existing = new DouyinWebhookEvent();
        existing.setId(UUID.randomUUID());
        existing.setEventType("doudian_alliance_colonelOpenEvent");
        existing.setEventKey("doudian_alliance_colonelOpenEvent:evt-001");
        existing.setStatus(DouyinWebhookEventService.STATUS_CONSUMED);
        when(eventMapper.selectOne(any())).thenReturn(existing);

        DouyinWebhookEventService.CaptureResult result = service.captureColonelOpenEvent(
                "{\"event\":\"doudian_alliance_colonelOpenEvent\",\"event_id\":\"evt-001\"}"
        );

        assertThat(result.duplicate()).isTrue();
        assertThat(result.eventId()).isEqualTo(existing.getId());
        verify(eventMapper, never()).insert(any());
        verify(eventMapper, never()).updateById(any());
    }

    @Test
    void captureColonelOpenEvent_shouldPersistInvalidJsonAsIgnoredForAudit() {
        when(eventMapper.selectOne(any())).thenReturn(null);

        DouyinWebhookEventService.CaptureResult result = service.captureColonelOpenEvent("{bad-json");

        assertThat(result.status()).isEqualTo(DouyinWebhookEventService.STATUS_IGNORED);
        ArgumentCaptor<DouyinWebhookEvent> captor = ArgumentCaptor.forClass(DouyinWebhookEvent.class);
        verify(eventMapper).insert(captor.capture());
        verify(eventMapper).updateById(captor.getValue());
        assertThat(captor.getValue().getEventType()).isEqualTo("unknown");
        assertThat(captor.getValue().getConsumeResult()).isEqualTo("INVALID_JSON");
    }

    @Test
    void replayUnfinished_shouldConsumeReceivedAndFailedEvents() {
        DouyinWebhookEvent received = event("evt-received", DouyinWebhookEventService.STATUS_RECEIVED);
        DouyinWebhookEvent failed = event("evt-failed", DouyinWebhookEventService.STATUS_FAILED);
        when(eventMapper.selectList(any())).thenReturn(List.of(received, failed));

        DouyinWebhookEventService.ReplayResult result = service.replayUnfinished(20);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.consumed()).isEqualTo(2);
        assertThat(received.getStatus()).isEqualTo(DouyinWebhookEventService.STATUS_CONSUMED);
        assertThat(failed.getStatus()).isEqualTo(DouyinWebhookEventService.STATUS_CONSUMED);
        verify(eventMapper).updateById(received);
        verify(eventMapper).updateById(failed);
    }

    private DouyinWebhookEvent event(String eventKey, String status) {
        DouyinWebhookEvent event = new DouyinWebhookEvent();
        event.setId(UUID.randomUUID());
        event.setEventType("doudian_alliance_colonelOpenEvent");
        event.setEventKey(eventKey);
        event.setStatus(status);
        event.setRawPayload("{\"event\":\"doudian_alliance_colonelOpenEvent\"}");
        return event;
    }
}
