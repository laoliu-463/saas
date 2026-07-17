package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.service.SampleStatusLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleApplicationPortPermissionTest {

    @Mock private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock private TalentDomainFacade talentDomainFacade;
    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private ConfigDomainFacade configDomainFacade;
    @Mock private SampleEligibilityService sampleEligibilityService;
    @Mock private SampleStatusLogService sampleStatusLogService;
    @Mock private DouyinQuickSampleGateway douyinQuickSampleGateway;
    @Mock private SampleDomainEventPublisher sampleDomainEventPublisher;

    private SampleApplicationPortImpl port;

    @BeforeEach
    void setUp() {
        port = new SampleApplicationPortImpl(
                crawlerTalentInfoService,
                talentDomainFacade,
                sampleRequestMapper,
                configDomainFacade,
                sampleEligibilityService,
                sampleStatusLogService,
                douyinQuickSampleGateway,
                sampleDomainEventPublisher,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
    }

    @Test
    void businessRolePath_shouldNotRequireChannelTalentClaim() {
        UUID userId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                port,
                "ensureChannelTalentClaim",
                userId,
                talentId,
                List.of(RoleCodes.BIZ_STAFF)))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                port,
                "ensureChannelTalentClaim",
                userId,
                talentId,
                List.of(RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_STAFF)))
                .doesNotThrowAnyException();
    }

    @Test
    void channelOnlyPath_shouldStillRequireActiveTalentClaim() {
        UUID userId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        when(talentDomainFacade.hasActiveClaim(talentId, userId)).thenReturn(false);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                port,
                "ensureChannelTalentClaim",
                userId,
                talentId,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("请先认领");
        verify(talentDomainFacade).hasActiveClaim(talentId, userId);
    }
}
