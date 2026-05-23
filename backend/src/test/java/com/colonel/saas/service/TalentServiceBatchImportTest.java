package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SysUserMapper;
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
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock
    private BusinessRuleConfigService businessRuleConfigService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private SysUserMapper sysUserMapper;

    private TalentService talentService;

    @BeforeEach
    void setUp() {
        talentService = new TalentService(
                talentMapper,
                talentClaimMapper,
                talentEnrichTaskMapper,
                talentEnrichOrchestrator,
                orderMapper,
                sampleRequestMapper,
                redisTemplate,
                crawlerTalentInfoService,
                false,
                businessRuleConfigService,
                operationLogService,
                sysUserMapper);
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
        when(talentEnrichOrchestrator.enrich(any(Talent.class), any(Boolean.class)))
                .thenReturn(new TalentEnrichOrchestrator.OrchestrateResult(false, "wait manual", "TEST"));

        TalentBatchImportResult result = talentService.batchImport(List.of("987654321"), UUID.randomUUID());

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.items().get(0).status()).isEqualTo("CREATED");
    }
}
