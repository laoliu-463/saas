package com.colonel.saas.service;

import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.application.UserDomainEventPublisherApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDomainEventPublisherTest {

    @Mock
    private UserDomainEventPublisherApplicationService applicationService;

    @InjectMocks
    private UserDomainEventPublisher publisher;

    @Test
    void publishUserCreated_shouldDelegateToApplicationService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        publisher.publishUserCreated(
                userId,
                "alice",
                "Alice",
                roleId,
                "channel",
                deptId,
                deptId,
                SysUserStatus.PENDING_ACTIVATION,
                operatorId);

        verify(applicationService).publishUserCreated(
                userId, "alice", "Alice", roleId, "channel", deptId, deptId, SysUserStatus.PENDING_ACTIVATION, operatorId
        );
    }

    @Test
    void publishUserDisabled_shouldDelegateToApplicationService() {
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        publisher.publishUserDisabled(
                userId,
                SysUserStatus.ACTIVE,
                SysUserStatus.DISABLED,
                operatorId);

        verify(applicationService).publishUserDisabled(
                userId, SysUserStatus.ACTIVE, SysUserStatus.DISABLED, operatorId
        );
    }
}
