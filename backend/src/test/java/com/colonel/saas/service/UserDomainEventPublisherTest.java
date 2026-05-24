package com.colonel.saas.service;

import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.event.UserCreatedEvent;
import com.colonel.saas.domain.user.event.UserDisabledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private UserDomainEventPublisher publisher;

    @Test
    void publishUserCreated_shouldIncludeEventIdAndStatusLabel() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        publisher.publishUserCreated(
                userId,
                "alice",
                "Alice",
                UUID.randomUUID(),
                "channel",
                deptId,
                deptId,
                SysUserStatus.PENDING_ACTIVATION,
                operatorId);

        ArgumentCaptor<UserCreatedEvent> captor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        UserCreatedEvent event = captor.getValue();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.status()).isEqualTo("PENDING_ACTIVATION");
        assertThat(event.roleCode()).isEqualTo("channel");
        assertThat(event.operatorId()).isEqualTo(operatorId);
    }

    @Test
    void publishUserDisabled_shouldSwallowPublisherFailures() {
        doThrow(new RuntimeException("broker down"))
                .when(applicationEventPublisher)
                .publishEvent(any(UserDisabledEvent.class));

        publisher.publishUserDisabled(
                UUID.randomUUID(),
                SysUserStatus.ACTIVE,
                SysUserStatus.DISABLED,
                UUID.randomUUID());

        verify(applicationEventPublisher).publishEvent(any(UserDisabledEvent.class));
    }
}
