package com.colonel.saas.domain.user.application;

import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationTokenRejectedException;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.port.AuthorizationPrincipalStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationPrincipalApplicationServiceTest {

    @Mock
    private AuthorizationPrincipalStore store;

    @InjectMocks
    private AuthorizationPrincipalApplicationService service;

    @Test
    void principal_rejectsNullUserId() {
        assertThatThrownBy(() -> new AuthorizationPrincipal(null, null, "alice", 1L, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");
    }

    @Test
    void principal_rejectsNonPositiveVersion() {
        assertThatThrownBy(() -> new AuthorizationPrincipal(
                UUID.randomUUID(), null, "alice", 0L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authzVersion must be positive");
    }

    @Test
    void requireCurrent_returnsDatabasePrincipalWhenVersionMatches() {
        UUID userId = UUID.randomUUID();
        AuthorizationPrincipal current = new AuthorizationPrincipal(
                userId, UUID.randomUUID(), "alice", 7L, false);
        when(store.loadLoginEligible(userId)).thenReturn(Optional.of(current));

        assertThat(service.requireCurrent(userId, 7L)).isEqualTo(current);
    }

    @Test
    void requireCurrent_rejectsInvalidInputWithoutLoadingStore() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.requireCurrent(null, 1L))
                .isInstanceOf(AuthorizationTokenRejectedException.class)
                .hasMessage("授权令牌已失效，请重新登录");
        assertThatThrownBy(() -> service.requireCurrent(userId, null))
                .isInstanceOf(AuthorizationTokenRejectedException.class);
        assertThatThrownBy(() -> service.requireCurrent(userId, 0L))
                .isInstanceOf(AuthorizationTokenRejectedException.class);
        assertThatThrownBy(() -> service.requireCurrent(userId, -1L))
                .isInstanceOf(AuthorizationTokenRejectedException.class);
        verifyNoInteractions(store);
    }

    @Test
    void requireCurrent_rejectsMissingPrincipal() {
        UUID userId = UUID.randomUUID();
        when(store.loadLoginEligible(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireCurrent(userId, 1L))
                .isInstanceOf(AuthorizationTokenRejectedException.class);
    }

    @Test
    void requireCurrent_rejectsStaleVersion() {
        UUID userId = UUID.randomUUID();
        when(store.loadLoginEligible(userId)).thenReturn(Optional.of(
                new AuthorizationPrincipal(userId, null, "alice", 8L, false)));

        assertThatThrownBy(() -> service.requireCurrent(userId, 7L))
                .isInstanceOf(AuthorizationTokenRejectedException.class);
    }

    @Test
    void requireCurrent_mapsDataAccessFailureToUnavailableAndPreservesCause() {
        UUID userId = UUID.randomUUID();
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("db unavailable");
        when(store.loadLoginEligible(userId)).thenThrow(failure);

        assertThatThrownBy(() -> service.requireCurrent(userId, 1L))
                .isInstanceOf(AuthorizationUnavailableException.class)
                .hasMessage("授权事实暂时不可用")
                .hasCause(failure);
    }

    @Test
    void requireCurrent_propagatesOtherRuntimeFailureUnchanged() {
        UUID userId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("invalid store state");
        when(store.loadLoginEligible(userId)).thenThrow(failure);

        assertThatThrownBy(() -> service.requireCurrent(userId, 1L))
                .isSameAs(failure);
    }
}
