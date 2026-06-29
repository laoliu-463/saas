package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.facade.dto.PickSourceAttributionMappingDTO;
import com.colonel.saas.domain.product.facade.dto.PickSourceMappingReadDTO;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void findActiveAttributionMapping_shouldMapPickSourceResult() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(java.util.UUID.randomUUID());
        mapping.setDeptId(java.util.UUID.randomUUID());
        mapping.setActivityId("ACT-1");
        mapping.setProductId("P-1");
        mapping.setColonelBuyinId("BUYIN-1");
        mapping.setSourceType(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        mapping.setCreateTime(LocalDateTime.of(2026, 6, 29, 13, 0));
        mapping.setUpdateTime(LocalDateTime.of(2026, 6, 29, 13, 1));
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);

        PickSourceAttributionMappingDTO result = service.findActiveAttributionMapping("PS-1", "PE-1");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(mapping.getUserId());
        assertThat(result.deptId()).isEqualTo(mapping.getDeptId());
        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.productId()).isEqualTo("P-1");
        assertThat(result.colonelBuyinId()).isEqualTo("BUYIN-1");
        assertThat(result.sourceType()).isEqualTo(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        assertThat(result.createTime()).isEqualTo(mapping.getCreateTime());
        assertThat(result.updateTime()).isEqualTo(mapping.getUpdateTime());
    }

    @Test
    void findNativeAttributionMappings_shouldMapListResults() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(java.util.UUID.randomUUID());
        mapping.setDeptId(java.util.UUID.randomUUID());
        mapping.setActivityId("ACT-NATIVE");
        mapping.setProductId("P-NATIVE");
        mapping.setColonelBuyinId("BUYIN-NATIVE");
        mapping.setSourceType(PickSourceMappingService.SOURCE_TYPE_NATIVE);
        when(pickSourceMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));

        List<PickSourceAttributionMappingDTO> result =
                service.findNativeAttributionMappings("BUYIN-NATIVE", "ACT-NATIVE", "P-NATIVE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(mapping.getUserId());
        assertThat(result.get(0).activityId()).isEqualTo("ACT-NATIVE");
        assertThat(result.get(0).sourceType()).isEqualTo(PickSourceMappingService.SOURCE_TYPE_NATIVE);
    }
}
