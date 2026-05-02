package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class TalentServiceTest {

    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private TalentEnrichTaskMapper talentEnrichTaskMapper;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock
    private TalentEnrichOrchestrator talentEnrichOrchestrator;
    @Mock
    private ValueOperations<String, Object> valueOperations;

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
                true
        );
        when(talentEnrichTaskMapper.insert(any(TalentEnrichTask.class))).thenAnswer(invocation -> {
            TalentEnrichTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(UUID.randomUUID());
            }
            return 1;
        });
        when(talentEnrichOrchestrator.enrich(any(Talent.class), anyBoolean()))
                .thenReturn(new TalentEnrichOrchestrator.OrchestrateResult(false, null, "no data"));
    }

    @Test
    void claim_shouldInsertClaim() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_001");
        talent.setDeleted(0);

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);
        when(talentClaimMapper.findLastClaim(talentId)).thenReturn(null);

        Talent claimed = talentService.claim(talentId, userId, UUID.randomUUID());

        assertThat(claimed.getOwnerId()).isEqualTo(userId);
        ArgumentCaptor<TalentClaim> claimCaptor = ArgumentCaptor.forClass(TalentClaim.class);
        verify(talentClaimMapper).insert(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getClaimType()).isEqualTo(1);
        verify(redisTemplate).delete("talent:claim:lock:" + talentId);
    }

    @Test
    void claim_shouldRejectWhenInProtectedPeriod() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_002");
        talent.setDeleted(0);

        TalentClaim lastClaim = new TalentClaim();
        lastClaim.setTalentId(talentId);
        lastClaim.setUserId(UUID.randomUUID());
        lastClaim.setClaimedAt(LocalDateTime.now());
        lastClaim.setProtectedUntil(LocalDateTime.now().plusDays(1));

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);
        when(talentClaimMapper.findLastClaim(talentId)).thenReturn(lastClaim);

        assertThatThrownBy(() -> talentService.claim(talentId, userId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void create_shouldNotTriggerPublicPageCrawl() {
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("dy_new");

        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentMapper.insert(any(Talent.class))).thenReturn(1);
        when(crawlerTalentInfoService.findByTalentId("dy_new")).thenReturn(null);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent result = talentService.create(talent);

        assertThat(result.getStatus()).isEqualTo(1);
        verify(crawlerTalentInfoService, never()).crawlAndSave(any());
        verify(talentEnrichTaskMapper).insert(any(TalentEnrichTask.class));
    }

    @Test
    void refresh_shouldUpdateTalentFromCrawlerDataWhenEnabled() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_refresh");
        talent.setDeleted(0);

        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId("dy_refresh");
        info.setNickname("crawler-name");
        info.setFansCount(8888L);
        info.setRegion("zhejiang");

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(crawlerTalentInfoService.crawlAndSave(any())).thenReturn(1);
        when(crawlerTalentInfoService.findByTalentId("dy_refresh")).thenReturn(info);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent refreshed = talentService.refresh(talentId);

        assertThat(refreshed.getNickname()).isEqualTo("crawler-name");
        assertThat(refreshed.getFans()).isEqualTo(8888L);
        assertThat(refreshed.getIpLocation()).isEqualTo("zhejiang");
        verify(talentEnrichTaskMapper).insert(any(TalentEnrichTask.class));
        verify(talentEnrichTaskMapper).updateById(any(TalentEnrichTask.class));
    }

    @Test
    void refresh_shouldRejectWhenPublicPageCrawlDisabled() {
        TalentService disabledService = new TalentService(
                talentMapper,
                talentClaimMapper,
                talentEnrichTaskMapper,
                talentEnrichOrchestrator,
                orderMapper,
                sampleRequestMapper,
                redisTemplate,
                crawlerTalentInfoService,
                false
        );

        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_refresh_disabled");
        talent.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(talent);

        Talent refreshed = disabledService.refresh(talentId);
        assertThat(refreshed).isNotNull();
        verify(talentEnrichTaskMapper).insert(any(TalentEnrichTask.class));
        verify(talentEnrichTaskMapper).updateById(any(TalentEnrichTask.class));
        verify(crawlerTalentInfoService, never()).crawlAndSave(any());
    }

    @Test
    void getById_shouldThrowWhenMissing() {
        UUID id = UUID.randomUUID();
        when(talentMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> talentService.getById(id))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void evaluateExclusive_shouldCalculateRatio() {
        UUID talentId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_exclusive");
        talent.setDeleted(0);

        com.colonel.saas.entity.ColonelsettlementOrder order1 = new com.colonel.saas.entity.ColonelsettlementOrder();
        order1.setSettleColonelCommission(70L);
        order1.setExtraData(Map.of("author_id", "dy_exclusive"));

        com.colonel.saas.entity.ColonelsettlementOrder order2 = new com.colonel.saas.entity.ColonelsettlementOrder();
        order2.setSettleColonelCommission(30L);
        order2.setExtraData(Map.of("author_id", "dy_other"));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order1, order2));
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(15L);

        TalentService.ExclusiveCheckResult result = talentService.evaluateExclusive(
                talentId, DataScope.PERSONAL, UUID.randomUUID(), null);

        assertThat(result.eligible()).isTrue();
        assertThat(result.serviceFeeRatio()).isEqualTo(70L);
        assertThat(result.monthlySamples()).isEqualTo(15L);
    }

    @Test
    void getLatestEnrichTask_shouldQueryMapper() {
        UUID talentId = UUID.randomUUID();
        TalentEnrichTask task = new TalentEnrichTask();
        task.setTalentId(talentId);
        task.setTaskStatus("SUCCESS");
        when(talentEnrichTaskMapper.findLatestByTalentId(talentId)).thenReturn(task);

        TalentEnrichTask result = talentService.getLatestEnrichTask(talentId);

        assertThat(result).isNotNull();
        assertThat(result.getTaskStatus()).isEqualTo("SUCCESS");
        verify(talentEnrichTaskMapper).findLatestByTalentId(talentId);
    }

    @Test
    void create_shouldParseDouyinUidFromProfileUrl_whenDouyinUidMissing() {
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setProfileUrl("https://www.douyin.com/user/abc_123");

        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentMapper.insert(any(Talent.class))).thenReturn(1);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);
        when(crawlerTalentInfoService.findByTalentId("abc_123")).thenReturn(null);

        Talent result = talentService.create(talent);

        assertThat(result.getDouyinUid()).isEqualTo("abc_123");
        verify(talentMapper).insert(any(Talent.class));
    }

    @Test
    void manualFill_shouldUpdateTalentAndMarkManualSource() {
        UUID talentId = UUID.randomUUID();
        Talent existing = new Talent();
        existing.setId(talentId);
        existing.setDeleted(0);
        existing.setDouyinUid("dy_001");
        when(talentMapper.selectById(talentId)).thenReturn(existing);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent request = new Talent();
        request.setNickname("manual-name");
        request.setFans(1000L);
        request.setLikesCount(5000L);
        request.setFollowingCount(100L);
        request.setWorksCount(88L);
        request.setIpLocation("浙江");

        Talent result = talentService.manualFill(talentId, request);

        assertThat(result.getNickname()).isEqualTo("manual-name");
        assertThat(result.getFans()).isEqualTo(1000L);
        assertThat(result.getDataSource()).isEqualTo("MANUAL");
        assertThat(result.getEnrichStatus()).isEqualTo("SUCCESS");
        verify(talentMapper).updateById(any(Talent.class));
    }
}
