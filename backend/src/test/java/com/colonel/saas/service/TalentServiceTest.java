package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import com.colonel.saas.service.OperationLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    private BusinessRuleConfigService businessRuleConfigService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private SysUserMapper sysUserMapper;
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
                true,
                businessRuleConfigService,
                operationLogService,
                sysUserMapper
        );
        when(businessRuleConfigService.getTalentProtectionDays()).thenReturn(30);
        when(businessRuleConfigService.getTalentExclusiveRatioThreshold()).thenReturn(new java.math.BigDecimal("70"));
        when(businessRuleConfigService.getTalentExclusiveMonthlySamples()).thenReturn(10);
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
        assertThat(claimCaptor.getValue().getId()).isNotNull();
        assertThat(claimCaptor.getValue().getClaimType()).isEqualTo(1);
        verify(redisTemplate).delete("talent:claim:lock:" + talentId);
    }

    @Test
    void claim_shouldUseConfiguredProtectDays() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_cfg");
        talent.setDeleted(0);

        when(businessRuleConfigService.getTalentProtectionDays()).thenReturn(15);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);
        when(talentClaimMapper.findLastClaim(talentId)).thenReturn(null);

        LocalDateTime before = LocalDateTime.now();
        talentService.claim(talentId, userId, UUID.randomUUID());

        ArgumentCaptor<TalentClaim> claimCaptor = ArgumentCaptor.forClass(TalentClaim.class);
        verify(talentClaimMapper).insert(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getProtectedUntil()).isAfterOrEqualTo(before.plusDays(15).minusSeconds(1));
    }

    @Test
    void claim_shouldAllowOtherUserDuringProtectedPeriod() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_002");
        talent.setDeleted(0);

        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(talentId);
        activeClaim.setUserId(otherUserId);
        activeClaim.setClaimedAt(LocalDateTime.now());
        activeClaim.setProtectedUntil(LocalDateTime.now().plusDays(1));
        activeClaim.setStatus(1);

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);
        when(talentClaimMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Talent claimed = talentService.claim(talentId, userId, UUID.randomUUID());

        assertThat(claimed.getOwnerId()).isEqualTo(userId);
        verify(talentClaimMapper).insert(any(TalentClaim.class));
    }

    @Test
    void claim_shouldReuseExpiredClaimOfSameUser() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_reclaim");
        talent.setDeleted(0);

        TalentClaim expiredClaim = new TalentClaim();
        expiredClaim.setId(UUID.randomUUID());
        expiredClaim.setTalentId(talentId);
        expiredClaim.setUserId(userId);
        expiredClaim.setStatus(2);
        expiredClaim.setClaimedAt(LocalDateTime.now().minusDays(45));
        expiredClaim.setProtectedUntil(LocalDateTime.now().minusDays(15));

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);
        when(talentClaimMapper.findLastClaim(talentId)).thenReturn(expiredClaim);
        when(talentClaimMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(expiredClaim);

        Talent claimed = talentService.claim(talentId, userId, deptId);

        assertThat(claimed.getOwnerId()).isEqualTo(userId);
        assertThat(expiredClaim.getStatus()).isEqualTo(1);
        assertThat(expiredClaim.getDeptId()).isEqualTo(deptId);
        verify(talentClaimMapper, never()).insert(any(TalentClaim.class));
        verify(talentClaimMapper, times(1)).updateById(expiredClaim);
    }

    @Test
    void release_shouldAllowSelfRelease() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_release_self");
        talent.setDeleted(0);

        TalentClaim selfClaim = new TalentClaim();
        selfClaim.setId(UUID.randomUUID());
        selfClaim.setTalentId(talentId);
        selfClaim.setUserId(userId);
        selfClaim.setDeptId(deptId);
        selfClaim.setStatus(1);
        selfClaim.setClaimedAt(LocalDateTime.now().minusDays(2));
        selfClaim.setProtectedUntil(LocalDateTime.now().plusDays(10));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(selfClaim));

        Talent released = talentService.release(talentId, userId, deptId, List.of("channel_leader"));

        assertThat(released.getId()).isEqualTo(talentId);
        assertThat(selfClaim.getStatus()).isEqualTo(3);
        verify(talentClaimMapper).updateById(selfClaim);
        verify(talentMapper).updateById(talent);
        assertThat(talent.getOwnerId()).isNull();
    }

    @Test
    void release_shouldRejectDeptLeaderReleasingOtherUsersClaim() {
        UUID talentId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_release_forbidden");
        talent.setDeleted(0);

        TalentClaim memberClaim = new TalentClaim();
        memberClaim.setId(UUID.randomUUID());
        memberClaim.setTalentId(talentId);
        memberClaim.setUserId(memberUserId);
        memberClaim.setDeptId(deptId);
        memberClaim.setStatus(1);
        memberClaim.setClaimedAt(LocalDateTime.now().minusDays(2));
        memberClaim.setProtectedUntil(LocalDateTime.now().plusDays(10));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(memberClaim));

        assertThatThrownBy(() -> talentService.release(
                talentId,
                leaderUserId,
                deptId,
                List.of("channel_leader")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅认领人或管理员可以释放达人");

        verify(talentClaimMapper, never()).updateById(memberClaim);
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
    void create_shouldKeepTalentForManualFillWhenEnrichProviderFails() {
        Talent talent = new Talent();
        talent.setDouyinUid("dy_test_fail");

        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentMapper.insert(any(Talent.class))).thenReturn(1);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);
        when(talentEnrichOrchestrator.enrich(any(Talent.class), eq(false)))
                .thenThrow(new IllegalStateException("test provider simulated failure"));

        Talent result = talentService.create(talent);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getDouyinUid()).isEqualTo("dy_test_fail");
        assertThat(result.getEnrichStatus()).isEqualTo("FAILED");
        assertThat(result.getLastEnrichTime()).isNotNull();
        verify(talentMapper).insert(any(Talent.class));
        verify(talentMapper, times(1)).updateById(any(Talent.class));
        ArgumentCaptor<TalentEnrichTask> taskCaptor = ArgumentCaptor.forClass(TalentEnrichTask.class);
        verify(talentEnrichTaskMapper, times(2)).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .extracting(TalentEnrichTask::getTaskStatus)
                .containsExactly("RUNNING", "FAILED");
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
                false,
                businessRuleConfigService,
                operationLogService,
                sysUserMapper
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
    void refresh_shouldRecordFailureWithoutThrowingWhenProviderFails() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_refresh_fail");
        talent.setDeleted(0);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);
        when(talentEnrichOrchestrator.enrich(any(Talent.class), eq(true)))
                .thenThrow(new IllegalStateException("test provider simulated failure"));

        Talent refreshed = talentService.refresh(talentId);

        assertThat(refreshed.getEnrichStatus()).isEqualTo("FAILED");
        assertThat(refreshed.getLastEnrichTime()).isNotNull();
        verify(talentMapper).updateById(talent);
        verify(talentEnrichTaskMapper).updateById(any(TalentEnrichTask.class));
    }

    @Test
    void releaseExpiredClaims_shouldKeepClaimsWithOutput() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_output");

        TalentClaim expiredWindowClaim = new TalentClaim();
        expiredWindowClaim.setId(UUID.randomUUID());
        expiredWindowClaim.setTalentId(talentId);
        expiredWindowClaim.setStatus(1);
        expiredWindowClaim.setClaimedAt(LocalDateTime.now().minusDays(35));
        expiredWindowClaim.setProtectedUntil(LocalDateTime.now().minusDays(1));

        com.colonel.saas.entity.ColonelsettlementOrder outputOrder = new com.colonel.saas.entity.ColonelsettlementOrder();
        outputOrder.setCreateTime(LocalDateTime.now().minusDays(3));
        outputOrder.setExtraData(Map.of("talent_uid", "dy_output"));

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expiredWindowClaim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        Page<com.colonel.saas.entity.ColonelsettlementOrder> orderPage = new Page<>(1, 2000, 1);
        orderPage.setRecords(List.of(outputOrder));
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(orderPage);

        talentService.releaseExpiredClaims(LocalDateTime.now());

        verify(talentClaimMapper, never()).updateById(any(TalentClaim.class));
    }

    @Test
    void releaseExpiredClaims_shouldExpireClaimsWithoutOutput() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_no_output");

        TalentClaim expiredWindowClaim = new TalentClaim();
        expiredWindowClaim.setId(UUID.randomUUID());
        expiredWindowClaim.setTalentId(talentId);
        expiredWindowClaim.setStatus(1);
        expiredWindowClaim.setClaimedAt(LocalDateTime.now().minusDays(35));
        expiredWindowClaim.setProtectedUntil(LocalDateTime.now().minusDays(1));

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expiredWindowClaim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        Page<com.colonel.saas.entity.ColonelsettlementOrder> orderPage = new Page<>(1, 2000, 0);
        orderPage.setRecords(List.of());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(orderPage);

        talentService.releaseExpiredClaims(LocalDateTime.now());

        assertThat(expiredWindowClaim.getStatus()).isEqualTo(2);
        verify(talentClaimMapper).updateById(expiredWindowClaim);
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
        Page<com.colonel.saas.entity.ColonelsettlementOrder> orderPage = new Page<>(1, 2000, 2);
        orderPage.setRecords(List.of(order1, order2));
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(orderPage);
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

    @Test
    void blacklist_shouldMarkTalent() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(talent);

        Talent result = talentService.blacklist(talentId, "重复违约");

        assertThat(result.getBlacklisted()).isTrue();
        assertThat(result.getBlacklistReason()).isEqualTo("重复违约");
        verify(talentMapper).updateById(talent);
    }

    @Test
    void blacklist_shouldRejectPersonalScopeWhenViewerHasNoActiveClaim() {
        UUID talentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        TalentClaim otherClaim = new TalentClaim();
        otherClaim.setTalentId(talentId);
        otherClaim.setUserId(otherUserId);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(otherClaim));

        assertThatThrownBy(() -> talentService.blacklist(
                talentId,
                "重复违约",
                currentUserId,
                null,
                DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");

        verify(talentMapper, never()).updateById(talent);
    }

    @Test
    void blacklist_shouldAllowDeptScopeWhenTalentClaimedBySameDept() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        TalentClaim deptClaim = new TalentClaim();
        deptClaim.setTalentId(talentId);
        deptClaim.setUserId(UUID.randomUUID());
        deptClaim.setDeptId(deptId);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(deptClaim));

        Talent result = talentService.blacklist(talentId, null, userId, deptId, DataScope.DEPT);

        assertThat(result.getBlacklisted()).isTrue();
        assertThat(result.getBlacklistReason()).isEqualTo("手动拉黑");
        verify(talentMapper).updateById(talent);
    }

    @Test
    void unblacklist_shouldClearStatus() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        talent.setBlacklisted(true);
        talent.setBlacklistReason("重复违约");
        when(talentMapper.selectById(talentId)).thenReturn(talent);

        Talent result = talentService.unblacklist(talentId);

        assertThat(result.getBlacklisted()).isFalse();
        assertThat(result.getBlacklistReason()).isNull();
        verify(talentMapper).updateById(talent);
    }

    @Test
    void unblacklist_shouldRejectDeptScopeWhenTalentClaimedByOtherDept() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        UUID otherDeptId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        talent.setBlacklisted(true);
        TalentClaim otherDeptClaim = new TalentClaim();
        otherDeptClaim.setTalentId(talentId);
        otherDeptClaim.setUserId(UUID.randomUUID());
        otherDeptClaim.setDeptId(otherDeptId);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(otherDeptClaim));

        assertThatThrownBy(() -> talentService.unblacklist(
                talentId,
                userId,
                currentDeptId,
                DataScope.DEPT))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");

        verify(talentMapper, never()).updateById(talent);
    }

    @Test
    void overrideTalentAssignment_shouldThrowWhenNewUserIdNull() {
        assertThatThrownBy(
                () -> talentService.overrideTalentAssignment(UUID.randomUUID(), null, "reason", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新负责人ID不能为空");
    }

    @Test
    void overrideTalentAssignment_shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(
                () -> talentService.overrideTalentAssignment(UUID.randomUUID(), userId, "reason", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    @Test
    void overrideTalentAssignment_shouldThrowWhenUserDeleted() {
        UUID userId = UUID.randomUUID();
        SysUser deletedUser = new SysUser();
        deletedUser.setDeleted(1);
        when(sysUserMapper.selectById(userId)).thenReturn(deletedUser);

        assertThatThrownBy(
                () -> talentService.overrideTalentAssignment(UUID.randomUUID(), userId, "reason", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    @Test
    void overrideTalentAssignment_shouldExpireClaimsAndCreateNewManualClaim() {
        UUID talentId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        SysUser user = new SysUser();
        user.setDeleted(0);
        user.setDeptId(UUID.randomUUID());
        when(sysUserMapper.selectById(newUserId)).thenReturn(user);

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_override");
        talent.setNickname("test-nickname");
        talent.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(talent);

        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setId(UUID.randomUUID());
        activeClaim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(activeClaim));

        Talent result = talentService.overrideTalentAssignment(talentId, newUserId, "测试覆盖", currentUserId);

        assertThat(result.getOwnerId()).isEqualTo(newUserId);
        assertThat(activeClaim.getStatus()).isEqualTo(2); // EXPIRED
        verify(talentClaimMapper).updateById(activeClaim);
        verify(talentClaimMapper).insert(any(TalentClaim.class));
        verify(talentMapper).updateById(talent);
        verify(operationLogService).recordSystemAction(
                eq(currentUserId), eq("达人管理"), eq("归属覆盖"), eq("POST"),
                eq("talent"), eq(talentId.toString()), eq("test-nickname"),
                eq(String.format("归属覆盖: 新负责人=%s, 原因=%s", newUserId, "测试覆盖")));
    }
}
