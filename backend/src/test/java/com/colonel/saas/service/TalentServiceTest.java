package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TalentServiceTest {

    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private TalentService talentService;

    @BeforeEach
    void setUp() {
        talentService = new TalentService(
                talentMapper,
                talentClaimMapper,
                orderMapper,
                sampleRequestMapper,
                redisTemplate
        );
    }

    @Test
    void getPublicPool_andPrivatePool_shouldBeSeparated() {
        UUID owner = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        UUID privateId = UUID.randomUUID();

        Talent publicTalent = new Talent();
        publicTalent.setId(publicId);
        publicTalent.setStatus(1);
        publicTalent.setDeleted(0);
        publicTalent.setFans(1000L);

        Talent privateTalent = new Talent();
        privateTalent.setId(privateId);
        privateTalent.setStatus(1);
        privateTalent.setDeleted(0);
        privateTalent.setFans(3000L);

        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(privateId);
        activeClaim.setUserId(owner);
        activeClaim.setStatus(1);
        activeClaim.setDeleted(0);

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(activeClaim));
        when(talentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(publicTalent, privateTalent));
        when(talentClaimMapper.findActiveByUserId(owner)).thenReturn(List.of(activeClaim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(privateTalent));

        List<Talent> publicPool = talentService.getPublicPool();
        List<Talent> privatePool = talentService.getPrivatePool(owner);

        assertThat(publicPool).extracting(Talent::getId).containsExactly(publicId);
        assertThat(privatePool).extracting(Talent::getId).containsExactly(privateId);
    }

    @Test
    void claim_shouldMoveTalentToPrivatePool() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_001");
        talent.setDeleted(0);

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findLastClaim(talentId)).thenReturn(null);

        Talent claimed = talentService.claim(talentId, userId, deptId);

        assertThat(claimed.getOwnerId()).isEqualTo(userId);
        assertThat(claimed.getClaimedAt()).isNotNull();
        verify(talentClaimMapper).insert(any(TalentClaim.class));
        verify(redisTemplate).delete("talent:claim:lock:" + talentId);
    }

    @Test
    void claim_shouldRejectWhenInProtectedPeriod() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_002");
        talent.setDeleted(0);

        TalentClaim lastClaim = new TalentClaim();
        lastClaim.setTalentId(talentId);
        lastClaim.setUserId(otherUser);
        lastClaim.setClaimedAt(LocalDateTime.now());
        lastClaim.setProtectedUntil(LocalDateTime.now().plusDays(1));
        lastClaim.setStatus(1);

        when(valueOperations.setIfAbsent(any(String.class), any(String.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findLastClaim(talentId)).thenReturn(lastClaim);

        assertThatThrownBy(() -> talentService.claim(talentId, userId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("保护期");

        verify(talentClaimMapper, never()).insert(any(TalentClaim.class));
        verify(redisTemplate).delete("talent:claim:lock:" + talentId);
    }

    // --- getById tests ---

    @Test
    void getById_returnsTalentWhenFound() {
        UUID id = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(id);
        talent.setDouyinUid("dy_find");
        talent.setDeleted(0);

        when(talentMapper.selectById(id)).thenReturn(talent);

        Talent result = talentService.getById(id);

        assertThat(result.getDouyinUid()).isEqualTo("dy_find");
    }

    @Test
    void getById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(talentMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> talentService.getById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人不存在");
    }

    @Test
    void getById_throwsWhenDeleted() {
        UUID id = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(id);
        talent.setDeleted(1);

        when(talentMapper.selectById(id)).thenReturn(talent);

        assertThatThrownBy(() -> talentService.getById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人不存在");
    }

    // --- create tests ---

    @Test
    void create_throwsWhenDouyinUidBlank() {
        Talent talent = new Talent();
        talent.setDouyinUid("   ");

        assertThatThrownBy(() -> talentService.create(talent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("douyinUid 不能为空");
    }

    @Test
    void create_throwsWhenDouyinUidDuplicate() {
        Talent talent = new Talent();
        talent.setDouyinUid("dy_duplicate");

        Talent existing = new Talent();
        existing.setDouyinUid("dy_duplicate");

        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> talentService.create(talent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人 douyinUid 已存在");
    }

    @Test
    void create_succeedsWithValidTalent() {
        Talent talent = new Talent();
        talent.setDouyinUid("dy_new");

        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentMapper.insert(any(Talent.class))).thenReturn(1);

        Talent result = talentService.create(talent);

        assertThat(result.getStatus()).isEqualTo(1);
        verify(talentMapper).insert(talent);
    }

    // --- update tests ---

    @Test
    void update_selectivelyUpdatesFields() {
        UUID id = UUID.randomUUID();
        Talent existing = new Talent();
        existing.setId(id);
        existing.setNickname("Old Name");
        existing.setFans(1000L);
        existing.setLevel("L1");
        existing.setStatus(1);
        existing.setDeleted(0);

        Talent request = new Talent();
        request.setNickname("New Name");
        request.setFans(5000L);

        when(talentMapper.selectById(id)).thenReturn(existing);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent result = talentService.update(id, request);

        assertThat(result.getNickname()).isEqualTo("New Name");
        assertThat(result.getFans()).isEqualTo(5000L);
        assertThat(result.getLevel()).isEqualTo("L1");
        assertThat(result.getStatus()).isEqualTo(1);
    }

    @Test
    void update_throwsWhenTalentNotFound() {
        UUID id = UUID.randomUUID();
        when(talentMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> talentService.update(id, new Talent()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人不存在");
    }

    // --- delete tests ---

    @Test
    void delete_removesTalent() {
        UUID id = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(id);
        talent.setDeleted(0);

        when(talentMapper.selectById(id)).thenReturn(talent);
        when(talentMapper.deleteById(id)).thenReturn(1);

        talentService.delete(id);

        verify(talentMapper).deleteById(id);
    }

    @Test
    void delete_throwsWhenTalentNotFound() {
        UUID id = UUID.randomUUID();
        when(talentMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> talentService.delete(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人不存在");
        verify(talentMapper, never()).deleteById(any());
    }

    // --- evaluateExclusive tests ---

    @Test
    void evaluateExclusive_eligibleWhenRatioAndSamplesSatisfied() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_exclusive");
        talent.setDeleted(0);

        com.colonel.saas.entity.ColonelsettlementOrder order1 = new com.colonel.saas.entity.ColonelsettlementOrder();
        order1.setId(UUID.randomUUID());
        order1.setSettleColonelCommission(70L);
        order1.setExtraData(Map.of("author_id", "dy_exclusive"));

        com.colonel.saas.entity.ColonelsettlementOrder order2 = new com.colonel.saas.entity.ColonelsettlementOrder();
        order2.setId(UUID.randomUUID());
        order2.setSettleColonelCommission(30L);
        order2.setExtraData(Map.of("author_id", "dy_other"));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order1, order2));
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(15L);

        TalentService.ExclusiveCheckResult result = talentService.evaluateExclusive(
                talentId, DataScope.PERSONAL, userId, null);

        assertThat(result.eligible()).isTrue();
        assertThat(result.serviceFeeRatio()).isEqualTo(70L);
        assertThat(result.monthlySamples()).isEqualTo(15L);
    }

    @Test
    void evaluateExclusive_notEligibleWhenRatioTooLow() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_low");
        talent.setDeleted(0);

        com.colonel.saas.entity.ColonelsettlementOrder order1 = new com.colonel.saas.entity.ColonelsettlementOrder();
        order1.setSettleColonelCommission(20L);
        order1.setExtraData(Map.of("author_id", "dy_low"));

        com.colonel.saas.entity.ColonelsettlementOrder order2 = new com.colonel.saas.entity.ColonelsettlementOrder();
        order2.setSettleColonelCommission(80L);
        order2.setExtraData(Map.of("author_id", "dy_other"));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order1, order2));
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(15L);

        TalentService.ExclusiveCheckResult result = talentService.evaluateExclusive(
                talentId, DataScope.PERSONAL, userId, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.serviceFeeRatio()).isEqualTo(20L);
    }

    @Test
    void evaluateExclusive_notEligibleWhenSamplesTooFew() {
        UUID talentId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_few");
        talent.setDeleted(0);

        com.colonel.saas.entity.ColonelsettlementOrder order = new com.colonel.saas.entity.ColonelsettlementOrder();
        order.setSettleColonelCommission(100L);
        order.setExtraData(Map.of("talent_uid", "dy_few"));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        TalentService.ExclusiveCheckResult result = talentService.evaluateExclusive(
                talentId, DataScope.DEPT, null, deptId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.monthlySamples()).isEqualTo(5L);
    }

    @Test
    void evaluateExclusive_zeroServiceFeeYieldsZeroRatio() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_empty");
        talent.setDeleted(0);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        TalentService.ExclusiveCheckResult result = talentService.evaluateExclusive(
                talentId, DataScope.PERSONAL, userId, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.serviceFeeRatio()).isZero();
    }

    // --- page tests ---

    @Test
    void page_personalScope_returnsEmptyWhenNoClaims() {
        UUID userId = UUID.randomUUID();
        when(talentClaimMapper.findActiveByUserId(userId)).thenReturn(List.of());

        var result = talentService.page(1, 20, null, DataScope.PERSONAL, userId, null);

        assertThat(result.getRecords()).isEmpty();
        verify(talentMapper, never()).selectPage(any(), any());
    }

    @Test
    void page_personalScope_filtersByClaimedIds() {
        UUID userId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();

        TalentClaim claim = new TalentClaim();
        claim.setTalentId(talentId);
        claim.setStatus(1);

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_page");
        talent.setDeleted(0);

        when(talentClaimMapper.findActiveByUserId(userId)).thenReturn(List.of(claim));
        when(talentMapper.selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, 1).setRecords(List.of(talent)));

        var result = talentService.page(1, 20, null, DataScope.PERSONAL, userId, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void page_deptScope_returnsEmptyWhenNoClaims() {
        UUID deptId = UUID.randomUUID();
        when(talentClaimMapper.findActiveByDeptId(deptId)).thenReturn(List.of());

        var result = talentService.page(1, 20, null, DataScope.DEPT, null, deptId);

        assertThat(result.getRecords()).isEmpty();
        verify(talentMapper, never()).selectPage(any(), any());
    }

    @Test
    void page_allScope_returnsAllActiveTalents() {
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("dy_all");
        talent.setDeleted(0);

        when(talentMapper.selectPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, 1).setRecords(List.of(talent)));

        var result = talentService.page(1, 20, null, DataScope.ALL, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }
}
