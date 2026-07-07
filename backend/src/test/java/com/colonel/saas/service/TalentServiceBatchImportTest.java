package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.domain.talent.application.TalentBatchImportApplicationService;
import com.colonel.saas.domain.talent.application.TalentClaimApplicationService;
import com.colonel.saas.domain.talent.application.TalentEnrichmentApplicationService;
import com.colonel.saas.domain.talent.application.ExclusiveTalentCheckApplicationService;
import com.colonel.saas.domain.talent.application.TalentPageApplicationService;
import com.colonel.saas.domain.talent.application.TalentPoolApplicationService;
import com.colonel.saas.domain.talent.application.TalentProfileApplicationService;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentServiceBatchImportTest {

    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private TalentEnrichTaskMapper talentEnrichTaskMapper;
    @Mock
    private TalentEnrichOrchestrator talentEnrichOrchestrator;
    @Mock
    private SampleDomainFacade sampleDomainFacade;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock
    private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock
    private BusinessRuleConfigService businessRuleConfigService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private OrderReadFacade orderReadFacade;

    private TalentService talentService;

    @BeforeEach
    void setUp() {
        talentService = new TalentService(
                talentMapper,
                talentClaimMapper,
                talentEnrichTaskMapper,
                talentEnrichOrchestrator,
                orderReadFacade,
                sampleDomainFacade,
                redisTemplate,
                crawlerTalentInfoService,
                false,
                configDomainFacade,
                businessRuleConfigService,
                new TalentProfileApplicationService(
                        talentMapper,
                        talentClaimMapper,
                        talentEnrichTaskMapper,
                        talentEnrichOrchestrator,
                        crawlerTalentInfoService,
                        businessRuleConfigService,
                        false),
                new TalentBatchImportApplicationService(
                        talentMapper,
                        new TalentProfileApplicationService(
                                talentMapper,
                                talentClaimMapper,
                                talentEnrichTaskMapper,
                                talentEnrichOrchestrator,
                                crawlerTalentInfoService,
                                businessRuleConfigService,
                                false),
                        operationLogService),
                new TalentPoolApplicationService(talentMapper, talentClaimMapper),
                new TalentEnrichmentApplicationService(talentMapper),
                new TalentPageApplicationService(talentMapper, talentClaimMapper, new DataScopeResolver(new DataScopePolicy()), new DddRefactorProperties()),
                new ExclusiveTalentCheckApplicationService(talentMapper, orderReadFacade, sampleDomainFacade, configDomainFacade, new DataScopeResolver(new DataScopePolicy()), new DddRefactorProperties()),
                new TalentClaimApplicationService(
                        talentClaimMapper,
                        talentMapper,
                        orderReadFacade,
                        configDomainFacade,
                        userDomainFacade,
                        new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                        new DataScopeResolver(new DataScopePolicy()),
                        operationLogService,
                        new DddRefactorProperties(),
                        redisTemplate),
                operationLogService,
                userDomainFacade,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                new DataScopeResolver(new DataScopePolicy()),
                new DddRefactorProperties());
    }

    @Test
    void batchImport_shouldSkipExistingTalent() {
        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Talent existing = new Talent();
            existing.setId(UUID.randomUUID());
            existing.setDouyinUid("123456789");
            return existing;
        });

        TalentBatchImportResult result = talentService.batchImport(List.of("123456789"), UUID.randomUUID());

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.items().get(0).status()).isEqualTo("SKIPPED");
    }

    @Test
    void batchImport_shouldCreateNewTalent() {
        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Talent talent = invocation.getArgument(0);
            if (talent.getId() == null) {
                talent.setId(UUID.randomUUID());
            }
            return 1;
        }).when(talentMapper).insert(any(Talent.class));
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);
        when(talentEnrichOrchestrator.enrich(any(Talent.class), anyBoolean()))
                .thenReturn(new TalentEnrichOrchestrator.OrchestrateResult(false, "wait manual", "TEST"));

        TalentBatchImportResult result = talentService.batchImport(List.of("987654321"), UUID.randomUUID());

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.items().get(0).status()).isEqualTo("CREATED");
    }
}
