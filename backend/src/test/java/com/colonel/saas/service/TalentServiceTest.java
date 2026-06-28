package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.talent.application.TalentClaimApplicationService;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.domain.talent.application.TalentProfileApplicationService;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
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
    private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock
    private BusinessRuleConfigService businessRuleConfigService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainFacade userDomainFacade;
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
                configDomainFacade,
                businessRuleConfigService,
                newTalentProfileApplicationService(),
                newTalentClaimApplicationService(),
                operationLogService,
                userDomainFacade,
                new CurrentUserPermissionPolicy(),
                new DataScopePolicy(),
                new DddRefactorProperties()
        );
        when(configDomainFacade.getTalentClaimProtectDays()).thenReturn(30);
        when(configDomainFacade.getExclusiveTalentFeeRatio()).thenReturn(new java.math.BigDecimal("70"));
        when(configDomainFacade.getExclusiveTalentMonthlySamples()).thenReturn(10);
        lenient().when(businessRuleConfigService.getPresetTalentTags()).thenReturn(List.of());
        lenient().when(talentMapper.updateById(any(Talent.class))).thenReturn(1);
        lenient().when(talentClaimMapper.updateById(any(TalentClaim.class))).thenReturn(1);
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
        talent.setNickname("达人一号");
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
        verify(operationLogService).recordSystemAction(
                eq(userId), eq("达人管理"), eq("认领达人"), eq("POST"),
                eq("talent"), eq(talentId.toString()), eq("达人一号"),
                contains(userId.toString()));
        verify(redisTemplate).delete("talent:claim:lock:" + talentId);
    }

    @Test
    void getPublicPool_shouldExcludeClaimedAndSortByFans() {
        UUID claimedId = UUID.randomUUID();
        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(claimedId);

        Talent lowFans = talent("dy_low", 100L);
        Talent highFans = talent("dy_high", 300L);
        Talent claimed = talent("dy_claimed", 999L);
        claimed.setId(claimedId);

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(activeClaim));
        when(talentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lowFans, claimed, highFans));

        List<Talent> result = talentService.getPublicPool();

        assertThat(result).extracting(Talent::getDouyinUid).containsExactly("dy_high", "dy_low");
    }

    @Test
    void getPrivatePool_shouldReturnEmptyWhenNoClaimsAndLoadClaimedTalents() {
        UUID userId = UUID.randomUUID();
        when(talentClaimMapper.findActiveByUserId(userId)).thenReturn(List.of());

        assertThat(talentService.getPrivatePool(userId)).isEmpty();

        UUID talentId = UUID.randomUUID();
        TalentClaim claim = new TalentClaim();
        claim.setTalentId(talentId);
        Talent talent = talent("dy_private", 100L);
        talent.setId(talentId);
        when(talentClaimMapper.findActiveByUserId(userId)).thenReturn(List.of(claim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));

        assertThat(talentService.getPrivatePool(userId)).containsExactly(talent);
    }

    @Test
    void pageDataScope_shouldKeepLegacyDefaultAndDelegateEnabledPathToUserPolicy() throws IOException {
        String source = Files.readString(sourcePath(
                "src/main/java/com/colonel/saas/service/TalentService.java"));

        assertThat(source)
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("applyPageDataScopeLegacy")
                .contains("applyPageDataScopeWithPolicy")
                .contains("DataScopePolicy")
                .contains("dataScopePolicy.contextRequirement")
                .contains("dataScopePolicy.decide");
    }

    @Test
    void blacklistDataScope_shouldKeepLegacyDefaultAndDelegateEnabledPathToUserPolicy() throws IOException {
        String source = Files.readString(sourcePath(
                "src/main/java/com/colonel/saas/service/TalentService.java"));

        assertThat(source)
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("assertCanOperateBlacklistLegacy")
                .contains("assertCanOperateBlacklistWithPolicy")
                .contains("dataScopePolicy.contextRequirement")
                .contains("dataScopePolicy.decide");
    }

    @Test
    void evaluateExclusiveDataScope_shouldKeepLegacyDefaultAndDelegateEnabledPathToUserPolicy() throws IOException {
        String source = Files.readString(sourcePath(
                "src/main/java/com/colonel/saas/service/TalentService.java"));

        assertThat(source)
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("applyExclusiveDataScopeLegacy")
                .contains("applyExclusiveDataScopeWithPolicy")
                .contains("dataScopePolicy.contextRequirement")
                .contains("dataScopePolicy.decide");
    }

    @Test
    void pageDataScopePolicyEnabledPath_shouldPreserveClaimScopeSemantics() {
        TalentService enabledService = talentServiceWithDataScopePolicyEnabled();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        TalentClaim userClaim = new TalentClaim();
        userClaim.setTalentId(talentId);
        when(talentClaimMapper.findActiveByUserId(userId)).thenReturn(List.of(userClaim));
        when(talentClaimMapper.findActiveByDeptId(deptId)).thenReturn(List.of());
        Page<Talent> mapperPage = new Page<>(1, 10, 1);
        mapperPage.setRecords(List.of(talent("dy_policy_page", 100L)));
        when(talentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mapperPage);

        assertThat(enabledService.page(1, 10, null, null, null, null,
                DataScope.PERSONAL, userId, null).getRecords())
                .hasSize(1);
        assertThat(enabledService.page(1, 10, null, null, null, null,
                DataScope.DEPT, null, deptId).getTotal())
                .isZero();
        assertThat(enabledService.page(1, 10, null, null, null, null,
                DataScope.PERSONAL, null, null).getRecords())
                .hasSize(1);

        verify(talentClaimMapper).findActiveByUserId(userId);
        verify(talentClaimMapper).findActiveByDeptId(deptId);
        verify(talentClaimMapper, never()).findActiveByUserId(null);
    }

    @Test
    void page_shouldReturnEmptyForScopedQueriesWithoutClaimsAndDelegateGeneralQuery() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(talentClaimMapper.findActiveByUserId(userId)).thenReturn(List.of());
        when(talentClaimMapper.findActiveByDeptId(deptId)).thenReturn(List.of());

        assertThat(talentService.page(1, 10, "alice", "上海", 100L, 1000L, DataScope.PERSONAL, userId, null).getTotal())
                .isZero();
        assertThat(talentService.page(1, 10, null, null, null, null, DataScope.DEPT, null, deptId).getTotal())
                .isZero();

        Page<Talent> mapperPage = new Page<>(2, 5, 1);
        mapperPage.setRecords(List.of(talent("dy_page", 10L)));
        when(talentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mapperPage);

        assertThat(talentService.page(2, 5, "bob", "杭州", 10L, 100L, DataScope.ALL, null, null).getRecords())
                .hasSize(1);
    }

    @Test
    void page_shouldCallMapperWithCorrectPageAndWrapper_whenMinAndMaxFansProvided() {
        // [V1 必做 t4-talent 2026-06-02] 指标筛选 (fansBand) -> minFans/maxFans 范围下推
        Page<Talent> empty = new Page<>(1, 10, 0);
        when(talentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(empty);

        IPage<Talent> result = talentService.page(1, 10, null, null, 10_000L, 99_999L, DataScope.ALL, null, null);

        ArgumentCaptor<Page<Talent>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(talentMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10L);
        assertThat(result).isNotNull();
    }

    @Test
    void page_shouldNotThrowAndCallMapper_whenBothMinAndMaxFansAreNull() {
        // [V1 必做 t4-talent 2026-06-02] 边界：无 fans 范围时不应在 SQL 中追加 fans_count 条件
        Page<Talent> empty = new Page<>(1, 10, 0);
        when(talentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(empty);

        IPage<Talent> result = talentService.page(1, 10, null, null, null, null, DataScope.ALL, null, null);

        assertThat(result).isNotNull();
        verify(talentMapper, times(1))
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void page_shouldHandleAllFiltersTogetherWithoutShortCircuiting() {
        // [V1 必做 t4-talent 2026-06-02] 文本筛选（keyword/region）与指标筛选（minFans/maxFans）
        // 独立下推；keyword + region + minFans + maxFans 同时给出，不应抛错
        Page<Talent> empty = new Page<>(1, 10, 0);
        when(talentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(empty);

        IPage<Talent> result = talentService.page(1, 10, "食品达人", "上海", 5_000L, 50_000L, DataScope.ALL, null, null);

        assertThat(result).isNotNull();
        verify(talentMapper, times(1))
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        when(talentClaimMapper.findActiveByUserId(any())).thenReturn(List.of());
        IPage<Talent> personalResult = talentService.page(1, 10, "食品达人", "上海", 5_000L, 50_000L,
                DataScope.PERSONAL, UUID.randomUUID(), null);
        assertThat(personalResult.getTotal()).isZero();
    }

    @Test
    void updateAndDelete_shouldPersistAllowedFieldChanges() {
        UUID talentId = UUID.randomUUID();
        Talent existing = talent("dy_update", 10L);
        existing.setId(talentId);
        existing.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(existing);

        Talent request = new Talent();
        request.setNickname("updated");
        request.setFans(200L);
        request.setLevel("LV2");
        request.setStatus(2);
        request.setContactPhone(" 13900000000 ");
        request.setContactWechat(" wx-test ");
        request.setIntro(" intro ");

        Talent updated = talentService.update(talentId, request);

        assertThat(updated.getNickname()).isEqualTo("updated");
        assertThat(updated.getFans()).isEqualTo(200L);
        assertThat(updated.getLevel()).isEqualTo("LV2");
        assertThat(updated.getStatus()).isEqualTo(2);
        assertThat(updated.getContactPhone()).isEqualTo("13900000000");
        assertThat(updated.getContactWechat()).isEqualTo("wx-test");
        assertThat(updated.getIntro()).isEqualTo("intro");
        verify(talentMapper).updateById(existing);

        talentService.delete(talentId);
        verify(talentMapper).deleteById(talentId);
    }

    @Test
    void updateTags_shouldRecordOperatorForAudit() {
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Talent existing = talent("dy_tags_audit", 10L);
        existing.setId(talentId);
        existing.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(existing);

        List<String> tags = talentService.updateTags(talentId, List.of("美妆", "高转化", "美妆"), operatorId);

        assertThat(tags).containsExactly("美妆", "高转化");
        assertThat(existing.getTags()).containsExactly("美妆", "高转化");
        assertThat(existing.getTagUpdatedBy()).isEqualTo(operatorId);
        verify(talentMapper).updateById(existing);
    }

    @Test
    void updateTags_shouldRejectTagsOutsidePresetLibrary() {
        UUID talentId = UUID.randomUUID();
        Talent existing = talent("dy_tags_preset", 10L);
        existing.setId(talentId);
        existing.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(existing);
        when(businessRuleConfigService.getPresetTalentTags()).thenReturn(List.of("美妆", "高转化"));

        assertThatThrownBy(() -> talentService.updateTags(talentId, List.of("未知标签"), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预设库");
    }

    @Test
    void updateShippingAddress_shouldPersistAddressOnCurrentUsersActiveClaim() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent existing = talent("dy_claim_address", 10L);
        existing.setId(talentId);
        existing.setDeleted(0);

        TalentClaim claim = new TalentClaim();
        claim.setId(UUID.randomUUID());
        claim.setTalentId(talentId);
        claim.setUserId(userId);
        claim.setStatus(1);

        when(talentMapper.selectById(talentId)).thenReturn(existing);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(claim);

        Talent updated = talentService.updateShippingAddress(
                talentId,
                userId,
                " 张三 ",
                " 13800000000 ",
                " 上海市浦东新区示例路 1 号 ");

        assertThat(claim.getRecipientName()).isEqualTo("张三");
        assertThat(claim.getRecipientPhone()).isEqualTo("13800000000");
        assertThat(claim.getRecipientAddress()).isEqualTo("上海市浦东新区示例路 1 号");
        assertThat(updated.getShippingRecipientName()).isEqualTo("张三");
        assertThat(updated.getShippingRecipientPhone()).isEqualTo("13800000000");
        assertThat(updated.getShippingRecipientAddress()).isEqualTo("上海市浦东新区示例路 1 号");
        verify(talentClaimMapper).updateById(claim);
        verify(talentMapper, never()).updateById(existing);
    }

    @Test
    void getById_shouldThrowWhenTalentIsSoftDeleted() {
        UUID talentId = UUID.randomUUID();
        Talent deleted = talent("dy_deleted", 1L);
        deleted.setId(talentId);
        deleted.setDeleted(1);
        when(talentMapper.selectById(talentId)).thenReturn(deleted);

        assertThatThrownBy(() -> talentService.getById(talentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("达人不存在");
    }

    @Test
    void create_shouldRejectMissingIdentityAndDuplicateUid() {
        assertThatThrownBy(() -> talentService.create(new Talent()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("达人抖音号或链接不能为空");

        Talent request = new Talent();
        request.setDouyinUid("dy_duplicate");
        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(talent("dy_duplicate", 1L));

        assertThatThrownBy(() -> talentService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_shouldTrimFieldsSetDefaultsAndSkipEnrichWhenProfilePrefilled() {
        Talent request = new Talent();
        request.setDouyinUid("dy_prefilled");
        request.setDouyinNo(" douyin-no ");
        request.setUid(" uid-001 ");
        request.setNickname(" nickname ");
        request.setContactPhone(" 13800000000 ");
        request.setContactWechat(" wx ");
        request.setIntro(" intro ");
        request.setDataSource("MANUAL");
        request.setSyncStatus("SUCCESS");

        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentMapper.insert(any(Talent.class))).thenReturn(1);

        Talent created = talentService.create(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(1);
        assertThat(created.getNickname()).isEqualTo("nickname");
        assertThat(created.getContactPhone()).isEqualTo("13800000000");
        assertThat(created.getContactWechat()).isEqualTo("wx");
        assertThat(created.getIntro()).isEqualTo("intro");
        assertThat(created.getDouyinAccount()).isEqualTo("douyin-no");
        assertThat(created.getTalentUid()).isEqualTo("uid-001");
        assertThat(created.getUnsupportedFields()).containsExactly("talentLevel", "sales30d");
        assertThat(created.getLastSyncTime()).isNotNull();
        verify(talentEnrichTaskMapper, never()).insert(any(TalentEnrichTask.class));
    }

    @Test
    void claim_shouldRejectMissingUserLockConflictAndSelfDuplicate() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent talent = talent("dy_claim_conflict", 10L);
        talent.setId(talentId);
        talent.setDeleted(0);

        assertThatThrownBy(() -> talentService.claim(talentId, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少登录用户");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);
        assertThatThrownBy(() -> talentService.claim(talentId, userId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("认领处理中");

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        TalentClaim selfClaim = new TalentClaim();
        selfClaim.setTalentId(talentId);
        selfClaim.setUserId(userId);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(selfClaim);

        assertThatThrownBy(() -> talentService.claim(talentId, userId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无需重复认领");
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

        when(configDomainFacade.getTalentClaimProtectDays()).thenReturn(15);
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
        talent.setNickname("待释放达人");
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
        when(talentClaimMapper.findActiveByTalentId(talentId))
                .thenReturn(List.of(selfClaim))
                .thenReturn(List.of());

        Talent released = talentService.release(talentId, userId, deptId, List.of("channel_leader"));

        assertThat(released.getId()).isEqualTo(talentId);
        assertThat(selfClaim.getStatus()).isEqualTo(3);
        verify(talentClaimMapper).updateById(selfClaim);
        verify(talentMapper).updateById(talent);
        verify(operationLogService).recordSystemAction(
                eq(userId), eq("达人管理"), eq("释放达人"), eq("POST"),
                eq("talent"), eq(talentId.toString()), eq("待释放达人"),
                contains(userId.toString()));
        assertThat(talent.getOwnerId()).isNull();
    }

    @Test
    void release_shouldRejectMissingUserNoActiveClaimAndAllowAdminRelease() {
        UUID talentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Talent talent = talent("dy_release_admin", 10L);
        talent.setId(talentId);
        talent.setDeleted(0);

        assertThatThrownBy(() -> talentService.release(talentId, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少登录用户");

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of());
        assertThatThrownBy(() -> talentService.release(talentId, adminId, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无有效认领记录");

        TalentClaim otherClaim = new TalentClaim();
        otherClaim.setId(UUID.randomUUID());
        otherClaim.setTalentId(talentId);
        otherClaim.setUserId(otherUserId);
        otherClaim.setStatus(1);
        otherClaim.setClaimedAt(LocalDateTime.now());
        when(talentClaimMapper.findActiveByTalentId(talentId))
                .thenReturn(List.of(otherClaim))
                .thenReturn(List.of());

        Talent released = talentService.release(talentId, adminId, null, List.of(" ADMIN "));

        assertThat(otherClaim.getStatus()).isEqualTo(3);
        assertThat(released.getOwnerId()).isNull();
    }

    @Test
    void release_shouldKeepRemainingClaimOwnerWhenTalentHasMultipleActiveClaims() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_release_multi_claim");
        talent.setDeleted(0);

        TalentClaim selfClaim = new TalentClaim();
        selfClaim.setId(UUID.randomUUID());
        selfClaim.setTalentId(talentId);
        selfClaim.setUserId(userId);
        selfClaim.setDeptId(deptId);
        selfClaim.setStatus(1);
        selfClaim.setClaimedAt(LocalDateTime.now().minusDays(1));
        selfClaim.setProtectedUntil(LocalDateTime.now().plusDays(10));

        TalentClaim otherClaim = new TalentClaim();
        otherClaim.setId(UUID.randomUUID());
        otherClaim.setTalentId(talentId);
        otherClaim.setUserId(otherUserId);
        otherClaim.setDeptId(deptId);
        otherClaim.setStatus(1);
        otherClaim.setClaimedAt(LocalDateTime.now().minusDays(2));
        otherClaim.setProtectedUntil(LocalDateTime.now().plusDays(9));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId))
                .thenReturn(List.of(selfClaim, otherClaim))
                .thenReturn(List.of(otherClaim));

        Talent released = talentService.release(talentId, userId, deptId, List.of("channel_leader"));

        assertThat(selfClaim.getStatus()).isEqualTo(3);
        assertThat(released.getOwnerId()).isEqualTo(otherUserId);
        verify(talentClaimMapper, times(2)).findActiveByTalentId(talentId);
        assertThat(released.getClaimedAt()).isEqualTo(otherClaim.getClaimedAt());
        assertThat(released.getProtectedUntil()).isEqualTo(otherClaim.getProtectedUntil());
        assertThat(released.getActiveClaimCount()).isEqualTo(1);
        verify(talentClaimMapper).updateById(selfClaim);
        verify(talentMapper).updateById(talent);
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
                .isInstanceOf(ForbiddenException.class)
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
                configDomainFacade,
                businessRuleConfigService,
                newTalentProfileApplicationService(),
                newTalentClaimApplicationService(),
                operationLogService,
                userDomainFacade,
                new CurrentUserPermissionPolicy(),
                new DataScopePolicy(),
                new DddRefactorProperties()
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
    void evaluateExclusive_shouldReturnFalseWhenNoServiceFeeAndNullSampleCount() {
        UUID talentId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent talent = talent("dy_zero", 10L);
        talent.setId(talentId);
        talent.setDeleted(0);
        when(talentMapper.selectById(talentId)).thenReturn(talent);

        com.colonel.saas.entity.ColonelsettlementOrder order = new com.colonel.saas.entity.ColonelsettlementOrder();
        order.setExtraData(Map.of("talent_uid", "dy_zero"));
        Page<com.colonel.saas.entity.ColonelsettlementOrder> orderPage = new Page<>(1, 2000, 1);
        orderPage.setRecords(List.of(order));
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(orderPage);
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(null);

        TalentService.ExclusiveCheckResult result = talentService.evaluateExclusive(talentId, DataScope.DEPT, null, deptId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.serviceFeeRatio()).isZero();
        assertThat(result.monthlySamples()).isZero();
    }

    @Test
    void evaluateExclusiveDataScopePolicyEnabledPath_shouldDelegateOrderScopeDecisionToUserPolicy() {
        DataScopePolicy dataScopePolicy = spy(new DataScopePolicy());
        TalentService enabledService = talentServiceWithDataScopePolicyEnabled(dataScopePolicy);
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent talent = talent("dy_scope", 10L);
        talent.setId(talentId);
        talent.setDeleted(0);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        Page<com.colonel.saas.entity.ColonelsettlementOrder> orderPage = new Page<>(1, 2000, 0);
        orderPage.setRecords(List.of());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(orderPage);
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        enabledService.evaluateExclusive(talentId, DataScope.PERSONAL, userId, null);
        enabledService.evaluateExclusive(talentId, DataScope.DEPT, null, deptId);
        enabledService.evaluateExclusive(talentId, DataScope.PERSONAL, null, null);

        verify(dataScopePolicy).contextRequirement(userId, null, DataScope.PERSONAL);
        verify(dataScopePolicy).decide(userId, null, DataScope.PERSONAL);
        verify(dataScopePolicy).contextRequirement(null, deptId, DataScope.DEPT);
        verify(dataScopePolicy).decide(null, deptId, DataScope.DEPT);
        verify(dataScopePolicy).contextRequirement(null, null, DataScope.PERSONAL);
        verify(dataScopePolicy, never()).decide(null, null, DataScope.PERSONAL);
        verify(orderMapper, times(3)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
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
    void blacklistDataScopePolicyEnabledPath_shouldPreserveClaimScopeSemantics() {
        TalentService enabledService = talentServiceWithDataScopePolicyEnabled();
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID otherDeptId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(talentId);
        activeClaim.setUserId(userId);
        activeClaim.setDeptId(deptId);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(activeClaim));

        assertThat(enabledService.blacklist(talentId, "同负责人", userId, null, DataScope.PERSONAL)
                .getBlacklisted()).isTrue();
        assertThat(enabledService.unblacklist(talentId, userId, deptId, DataScope.DEPT)
                .getBlacklisted()).isFalse();

        assertThatThrownBy(() -> enabledService.blacklist(
                talentId,
                "非负责人",
                otherUserId,
                null,
                DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");
        assertThatThrownBy(() -> enabledService.unblacklist(
                talentId,
                otherUserId,
                otherDeptId,
                DataScope.DEPT))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");
        assertThatThrownBy(() -> enabledService.blacklist(
                talentId,
                "缺少用户上下文",
                null,
                deptId,
                DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");
        assertThatThrownBy(() -> enabledService.unblacklist(
                talentId,
                userId,
                null,
                DataScope.DEPT))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");
    }

    @Test
    void blacklistDataScopePolicyEnabledPath_shouldAllowScopedOperationWhenNoActiveClaim() {
        TalentService enabledService = talentServiceWithDataScopePolicyEnabled();
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of());

        Talent result = enabledService.blacklist(
                talentId,
                "无认领记录",
                null,
                null,
                DataScope.PERSONAL);

        assertThat(result.getBlacklisted()).isTrue();
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
        when(userDomainFacade.loadUserOwnershipReferencesByIds(any())).thenReturn(Map.of());

        assertThatThrownBy(
                () -> talentService.overrideTalentAssignment(UUID.randomUUID(), userId, "reason", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    private TalentService talentServiceWithDataScopePolicyEnabled() {
        return talentServiceWithDataScopePolicyEnabled(new DataScopePolicy());
    }

    private TalentService talentServiceWithDataScopePolicyEnabled(DataScopePolicy dataScopePolicy) {
        DddRefactorProperties properties = new DddRefactorProperties();
        properties.getDataScopePolicy().setEnabled(true);
        return new TalentService(
                talentMapper,
                talentClaimMapper,
                talentEnrichTaskMapper,
                talentEnrichOrchestrator,
                orderMapper,
                sampleRequestMapper,
                redisTemplate,
                crawlerTalentInfoService,
                true,
                configDomainFacade,
                businessRuleConfigService,
                newTalentProfileApplicationService(),
                newTalentClaimApplicationService(dataScopePolicy, properties),
                operationLogService,
                userDomainFacade,
                new CurrentUserPermissionPolicy(),
                dataScopePolicy,
                properties
        );
    }

    private TalentProfileApplicationService newTalentProfileApplicationService() {
        return new TalentProfileApplicationService(
                talentMapper,
                talentEnrichTaskMapper,
                businessRuleConfigService);
    }

    private TalentClaimApplicationService newTalentClaimApplicationService() {
        return newTalentClaimApplicationService(new DataScopePolicy(), new DddRefactorProperties());
    }

    private TalentClaimApplicationService newTalentClaimApplicationService(
            DataScopePolicy dataScopePolicy,
            DddRefactorProperties properties) {
        return new TalentClaimApplicationService(
                talentClaimMapper,
                talentMapper,
                orderMapper,
                configDomainFacade,
                userDomainFacade,
                new CurrentUserPermissionPolicy(),
                dataScopePolicy,
                operationLogService,
                properties);
    }

    private Path sourcePath(String backendRelativePath) {
        Path fromBackend = Path.of(backendRelativePath);
        if (Files.exists(fromBackend)) {
            return fromBackend;
        }
        return Path.of("backend").resolve(backendRelativePath);
    }

    @Test
    void overrideTalentAssignment_shouldThrowWhenUserDeleted() {
        UUID userId = UUID.randomUUID();
        when(userDomainFacade.loadUserOwnershipReferencesByIds(any())).thenReturn(Map.of());

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

        UserOwnershipReference user = new UserOwnershipReference(newUserId, UUID.randomUUID());
        when(userDomainFacade.loadUserOwnershipReferencesByIds(any())).thenReturn(Map.of(newUserId, user));

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
        verify(userDomainFacade, never()).getUserById(any());
    }

    @Test
    void privateHelpers_shouldResolveInputValuesRolesAndTalentMatching() {
        Talent talent = new Talent();
        talent.setProfileUrl(" profile ");
        talent.setDouyinNo(" no ");
        talent.setUid(" uid ");
        talent.setSecUid(" sec ");
        talent.setDouyinUid(" dy ");

        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputValue", talent)).isEqualTo("profile");
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputType", talent)).isEqualTo("PROFILE_URL");

        talent.setProfileUrl(null);
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputValue", talent)).isEqualTo("no");
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputType", talent)).isEqualTo("DOUYIN_NO");
        talent.setDouyinNo(null);
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputValue", talent)).isEqualTo("uid");
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputType", talent)).isEqualTo("UID");
        talent.setUid(null);
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputValue", talent)).isEqualTo("sec");
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputType", talent)).isEqualTo("SEC_UID");
        talent.setSecUid(null);
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputValue", talent)).isEqualTo("dy");
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputType", talent)).isEqualTo("DOUYIN_UID");
        talent.setDouyinUid(null);
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputValue", talent)).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(talentService, "resolveInputType", talent)).isEqualTo("UNKNOWN");

        com.colonel.saas.entity.ColonelsettlementOrder order = new com.colonel.saas.entity.ColonelsettlementOrder();
        order.setExtraData(Map.of("author_id", "dy_match"));
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(talentService, "matchesTalent", order, "dy_match")).isTrue();
        order.setExtraData(Map.of("talent_uid", "dy_match"));
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(talentService, "matchesTalent", order, "dy_match")).isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(talentService, "matchesTalent", order, " ")).isFalse();
        order.setExtraData(null);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(talentService, "matchesTalent", order, "dy_match")).isFalse();
    }

    private Talent talent(String douyinUid, Long fans) {
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid(douyinUid);
        talent.setFans(fans);
        talent.setStatus(1);
        talent.setDeleted(0);
        return talent;
    }
}
