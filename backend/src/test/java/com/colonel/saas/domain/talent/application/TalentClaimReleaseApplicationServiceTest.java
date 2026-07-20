package com.colonel.saas.domain.talent.application;

import com.colonel.saas.domain.talent.application.port.TalentClaimReleasePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TalentClaimReleaseApplicationServiceTest {

    @Mock
    private TalentClaimReleasePort talentClaimReleasePort;

    private TalentClaimReleaseApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new TalentClaimReleaseApplicationService(talentClaimReleasePort);
    }

    @Test
    void releaseExpiredClaimsShouldDelegateSameTimeExactlyOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 2, 15);

        applicationService.releaseExpiredClaims(now);

        verify(talentClaimReleasePort, times(1)).releaseExpiredClaims(same(now));
    }

    @Test
    void releaseExpiredClaimsShouldPropagateSameRuntimeException() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 2, 15);
        RuntimeException failure = new RuntimeException("release failed");
        doThrow(failure).when(talentClaimReleasePort).releaseExpiredClaims(same(now));

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> applicationService.releaseExpiredClaims(now));

        assertThat(thrown).isSameAs(failure);
        verify(talentClaimReleasePort, times(1)).releaseExpiredClaims(same(now));
    }
}
