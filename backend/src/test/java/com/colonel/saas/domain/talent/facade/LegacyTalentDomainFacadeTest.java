package com.colonel.saas.domain.talent.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyTalentDomainFacadeTest {

    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;

    private TalentDomainFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacyTalentDomainFacade(talentMapper, talentClaimMapper);
    }

    @Test
    void findTalentById_shouldMapCoreFields() {
        UUID id = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(id);
        talent.setDouyinUid("DY-100");
        talent.setNickname("测试达人");
        talent.setFans(10000L);
        talent.setStatus(1);
        when(talentMapper.selectById(id)).thenReturn(talent);

        TalentReadDTO dto = facade.findTalentById(id);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.douyinUid()).isEqualTo("DY-100");
        assertThat(dto.nickname()).isEqualTo("测试达人");
        assertThat(dto.fansCount()).isEqualTo(10000L);
    }

    @Test
    void findTalentById_nullReturnsNull() {
        assertThat(facade.findTalentById(null)).isNull();
    }

    @Test
    void findTalentById_notFoundReturnsNull() {
        when(talentMapper.selectById(any(UUID.class))).thenReturn(null);
        assertThat(facade.findTalentById(UUID.randomUUID())).isNull();
    }

    @Test
    void findByDouyinUid_shouldQueryByUid() {
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("DY-200");
        talent.setNickname("UID达人");
        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(talent);

        TalentReadDTO dto = facade.findByDouyinUid("DY-200");

        assertThat(dto).isNotNull();
        assertThat(dto.douyinUid()).isEqualTo("DY-200");
        assertThat(dto.nickname()).isEqualTo("UID达人");
    }

    @Test
    void findByDouyinUid_blankReturnsNull() {
        assertThat(facade.findByDouyinUid("")).isNull();
        assertThat(facade.findByDouyinUid(null)).isNull();
    }

    @Test
    void existsById_nullReturnsFalse() {
        assertThat(facade.existsById(null)).isFalse();
    }

    @Test
    void existsById_foundReturnsTrue() {
        UUID id = UUID.randomUUID();
        when(talentMapper.selectById(id)).thenReturn(new Talent());
        assertThat(facade.existsById(id)).isTrue();
    }

    @Test
    void existsById_notFoundReturnsFalse() {
        when(talentMapper.selectById(any(UUID.class))).thenReturn(null);
        assertThat(facade.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void loadNicknamesByIds_shouldReturnDistinctNicknames() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Talent t1 = new Talent();
        t1.setId(id1);
        t1.setNickname("达人A");
        Talent t2 = new Talent();
        t2.setId(id2);
        t2.setNickname("达人B");
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(t1, t2));

        assertThat(facade.loadNicknamesByIds(List.of(id1, id2, id1)))
                .containsEntry(id1, "达人A")
                .containsEntry(id2, "达人B");
    }

    @Test
    void loadNicknamesByIds_emptyCollectionReturnsEmptyMap() {
        assertThat(facade.loadNicknamesByIds(Collections.emptyList())).isEmpty();
    }

    @Test
    void loadNicknamesByIds_nullCollectionReturnsEmptyMap() {
        assertThat(facade.loadNicknamesByIds(null)).isEmpty();
    }

    @Test
    void hasActiveClaimOwnerConflict_shouldDetectDifferentOwner() {
        UUID talentId = UUID.randomUUID();
        UUID expectedOwner = UUID.randomUUID();
        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(talentId);
        activeClaim.setUserId(UUID.randomUUID());
        activeClaim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(activeClaim));

        assertThat(facade.hasActiveClaimOwnerConflict(talentId, expectedOwner)).isTrue();
    }

    @Test
    void hasActiveClaimOwnerConflict_sameOwnerReturnsFalse() {
        UUID talentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(talentId);
        activeClaim.setUserId(ownerId);
        activeClaim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(activeClaim));

        assertThat(facade.hasActiveClaimOwnerConflict(talentId, ownerId)).isFalse();
    }
}
