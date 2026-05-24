package com.colonel.saas.listener;

import com.colonel.saas.domain.user.event.UserCreatedEvent;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserEventAuditListenerTest {

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private UserEventAuditListener listener;

    @Test
    void onUserCreated_shouldRecordDomainEventAuditLog() {
        UUID operatorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        listener.onUserCreated(new UserCreatedEvent(
                UUID.randomUUID(),
                userId,
                "alice",
                "Alice",
                UUID.randomUUID(),
                "channel",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PENDING_ACTIVATION",
                operatorId,
                LocalDateTime.now(),
                "trace-1"));

        verify(operationLogService).recordSystemAction(
                eq(operatorId),
                eq("用户域事件"),
                eq("用户已创建"),
                eq("EVENT"),
                eq("UserCreatedEvent"),
                anyString(),
                eq("alice"),
                anyString());
    }
}
