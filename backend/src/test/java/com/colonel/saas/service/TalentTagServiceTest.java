package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentTag;
import com.colonel.saas.entity.TalentTagRelation;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.mapper.TalentTagMapper;
import com.colonel.saas.mapper.TalentTagRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentTagServiceTest {

    @Mock
    private TalentTagMapper talentTagMapper;
    @Mock
    private TalentTagRelationMapper talentTagRelationMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private TalentMapper talentMapper;

    private TalentTagService talentTagService;

    @BeforeEach
    void setUp() {
        talentTagService = new TalentTagService(
                talentTagMapper,
                talentTagRelationMapper,
                talentClaimMapper,
                talentMapper
        );
    }

    @Test
    void replaceTags_rejectsMoreThanThreeTags() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);

        TalentClaim claim = new TalentClaim();
        claim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(claim);

        assertThatThrownBy(() -> talentTagService.replaceTags(
                talent,
                userId,
                List.of("A", "B", "C", "D"),
                false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void replaceTags_replacesExistingRelations() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);

        TalentClaim claim = new TalentClaim();
        claim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(claim);

        TalentTagRelation oldRelation = new TalentTagRelation();
        oldRelation.setId(UUID.randomUUID());
        when(talentTagRelationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldRelation));
        when(talentTagMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentTagMapper.insert(any(TalentTag.class))).thenReturn(1);
        when(talentTagRelationMapper.insert(any(TalentTagRelation.class))).thenReturn(1);

        List<String> saved = talentTagService.replaceTags(
                talent,
                userId,
                List.of("高意向", "可直签"),
                false);

        assertThat(saved).containsExactly("高意向", "可直签");
        verify(talentTagRelationMapper).deleteById(oldRelation.getId());
        ArgumentCaptor<TalentTagRelation> relationCaptor = ArgumentCaptor.forClass(TalentTagRelation.class);
        verify(talentTagRelationMapper, org.mockito.Mockito.times(2)).insert(relationCaptor.capture());
        assertThat(relationCaptor.getAllValues()).allMatch(item -> talentId.equals(item.getTalentId()));
    }

    @Test
    void replaceTags_requiresActiveClaimForNonAdmin() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);

        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);

        assertThatThrownBy(() -> talentTagService.replaceTags(talent, userId, List.of("A"), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void replaceTags_uuid_verifiesAuthBeforeFetch() {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 1. 测试未认领直接抛出异常，而不会去查库获取达人
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);

        assertThatThrownBy(() -> talentTagService.replaceTags(talentId, userId, List.of("A"), false))
                .isInstanceOf(BusinessException.class);

        // 验证没有调用 talentMapper.selectById
        org.mockito.Mockito.verifyNoInteractions(talentMapper);

        // 2. 测试认领正常，再获取达人
        TalentClaim claim = new TalentClaim();
        claim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(claim);

        Talent talent = new Talent();
        talent.setId(talentId);
        when(talentMapper.selectById(talentId)).thenReturn(talent);

        // 我们还需要 mock replaceTags(Talent, ...) 里涉及的 mapper 调用
        when(talentTagRelationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(talentTagMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentTagMapper.insert(any(TalentTag.class))).thenReturn(1);
        when(talentTagRelationMapper.insert(any(TalentTagRelation.class))).thenReturn(1);

        List<String> saved = talentTagService.replaceTags(talentId, userId, List.of("高意向"), false);
        assertThat(saved).containsExactly("高意向");
        verify(talentMapper).selectById(talentId);
    }
}