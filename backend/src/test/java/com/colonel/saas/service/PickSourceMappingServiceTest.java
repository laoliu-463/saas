package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.facade.dto.PickSourceMappingReadDTO;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickSourceMappingServiceTest {

    @Mock
    private PickSourceMappingMapper pickSourceMappingMapper;

    private PickSourceMappingService service;

    @BeforeEach
    void setUp() {
        service = new PickSourceMappingService(pickSourceMappingMapper, 3);
    }

    @Test
    void findLatestActiveMapping_shouldMapReadModel() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setShortId("SHORT1");
        mapping.setProductId("P1");
        mapping.setActivityId("A1");
        mapping.setPickSource("PS1");
        mapping.setPickExtra("PE1");
        mapping.setTalentId("T1");
        mapping.setTalentName("Talent One");
        mapping.setStatus(1);
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);

        PickSourceMappingReadDTO result = service.findLatestActiveMapping();

        assertThat(result).isNotNull();
        assertThat(result.shortId()).isEqualTo("SHORT1");
        assertThat(result.productId()).isEqualTo("P1");
        assertThat(result.activityId()).isEqualTo("A1");
        assertThat(result.pickSource()).isEqualTo("PS1");
        assertThat(result.pickExtra()).isEqualTo("PE1");
        assertThat(result.talentId()).isEqualTo("T1");
        assertThat(result.talentName()).isEqualTo("Talent One");
    }

    @Test
    void findLatestActiveMapping_noMappingReturnsNull() {
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(service.findLatestActiveMapping()).isNull();
    }
}
