package com.colonel.saas.domain.sample.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductCommand;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductResult;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.Talent;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleApplicationPortImplTest {

    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock
    private TalentDomainFacade talentDomainFacade;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private ConfigDomainFacade configDomainFacade;
    @Mock
    private SampleEligibilityService sampleEligibilityService;
    @Mock
    private SampleStatusLogService sampleStatusLogService;
    @Mock
    private DouyinQuickSampleGateway douyinQuickSampleGateway;
    @Mock
    private SampleDomainEventPublisher sampleDomainEventPublisher;

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
    void quickSampleShouldBypassEligibilityRejection() {
        UUID talentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TalentReadDTO talent = new TalentReadDTO(
                talentId,
                "talent-uid",
                "douyin-no",
                "测试达人",
                12000L,
                1,
                "https://example.test/avatar.png",
                "美妆",
                "美妆",
                "上海",
                "LV2",
                68000L,
                List.of());

        when(crawlerTalentInfoService.findByTalentId("talent-uid")).thenReturn(null);
        when(talentDomainFacade.findByDouyinUid("talent-uid")).thenReturn(talent);
        when(talentDomainFacade.findOrCreateSampleTalent("talent-uid", "测试达人", 12000L)).thenReturn(talent);
        when(sampleEligibilityService.evaluate(any(Talent.class), any()))
                .thenReturn(new SampleEligibilityService.EligibilityResult(
                        false,
                        List.of("默认标准未通过"),
                        new SampleEligibilityService.SampleDefaultStandard(30000L, "LV1", java.util.Map.of()),
                        new SampleEligibilityService.TalentSnapshot(68000L, "LV2")));
        when(sampleRequestMapper.insert(any())).thenReturn(1);

        ApplySampleFromProductCommand command = new ApplySampleFromProductCommand(
                productId,
                "product-1",
                UUID.randomUUID(),
                List.of("talent-uid"),
                null,
                1,
                null,
                "收件人",
                "13800000000",
                "上海市",
                "product_quick_sample",
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(RoleCodes.ADMIN),
                "测试商品",
                1000L,
                "activity-1",
                null,
                false,
                false,
                null);

        ApplySampleFromProductResult result = port.applyFromProduct(command);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
        verify(sampleRequestMapper).insert(any());
    }
}
