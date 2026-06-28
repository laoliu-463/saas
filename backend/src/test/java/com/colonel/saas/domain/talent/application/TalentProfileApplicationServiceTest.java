package com.colonel.saas.domain.talent.application;

import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.BusinessRuleConfigService;
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
class TalentProfileApplicationServiceTest {

    @Mock
    TalentMapper talentMapper;

    @Mock
    TalentEnrichTaskMapper talentEnrichTaskMapper;

    @Mock
    BusinessRuleConfigService businessRuleConfigService;

    private TalentProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TalentProfileApplicationService(
                talentMapper,
                talentEnrichTaskMapper,
                businessRuleConfigService);
    }

    @Test
    void updateTagsNormalizesAndPersistsTags() {
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(businessRuleConfigService.getPresetTalentTags()).thenReturn(List.of("美妆", "高转化", "带货"));
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        List<String> result = service.updateTags(talentId, List.of(" 美妆 ", "高转化", "美妆"), operatorId);

        assertThat(result).containsExactly("美妆", "高转化");
        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTags()).containsExactly("美妆", "高转化");
        assertThat(captor.getValue().getTagUpdatedBy()).isEqualTo(operatorId);
    }

    @Test
    void updateTagsThrowsWhenTalentNotFound() {
        UUID talentId = UUID.randomUUID();
        when(talentMapper.selectById(talentId)).thenReturn(null);

        assertThatThrownBy(() -> service.updateTags(talentId, List.of("美妆"), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人不存在");
    }
}
