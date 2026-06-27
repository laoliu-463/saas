package com.colonel.saas.service;

import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void saveOrUpdate_shouldReturnInsertedMappingId() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        UUID mappingId = service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-a",
                UUID.randomUUID(),
                "talent-1",
                "达人",
                "SID-1",
                UUID.randomUUID(),
                "PS-1",
                "P-1",
                "ACT-1",
                "https://source",
                "https://converted",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "pick-extra",
                "46128341673481000",
                PickSourceMappingService.SOURCE_TYPE_NATIVE);

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        assertThat(mappingId).isEqualTo(captor.getValue().getId());
    }

    @Test
    void saveOrUpdate_shouldReturnExistingMappingIdAfterUpdate() {
        UUID existingId = UUID.randomUUID();
        PickSourceMapping existing = new PickSourceMapping();
        existing.setId(existingId);
        existing.setSourceType(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(existing);
        when(pickSourceMappingMapper.updateById(any())).thenReturn(1);

        UUID mappingId = service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-a",
                UUID.randomUUID(),
                "talent-1",
                "达人",
                "SID-1",
                UUID.randomUUID(),
                "PS-1",
                "P-1",
                "ACT-1",
                "https://source",
                "https://converted",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "pick-extra",
                null,
                PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);

        assertThat(mappingId).isEqualTo(existingId);
    }
}
