package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    void ensureFromOrder_shouldInsertWhenShortIdCanBeExtracted() {
        ColonelsettlementOrder order = makeOrder("usr_ABC12345_1712000000");
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        service.ensureFromOrder(order);

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        PickSourceMapping saved = captor.getValue();
        assertThat(saved.getShortId()).isEqualTo("ABC12345");
        assertThat(saved.getPickExtra()).isEqualTo("ABC12345");
        assertThat(saved.getPickSource()).isEqualTo(order.getPickSource());
    }

    @Test
    void ensureFromOrder_shouldSkipWhenShortIdAlreadyExists() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(new PickSourceMapping());

        service.ensureFromOrder(makeOrder("usr_ABC12345_1712000000"));

        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
    }

    @Test
    void ensureFromOrder_shouldIgnoreDuplicateInsert() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("dup"))
                .when(pickSourceMappingMapper).insert(any(PickSourceMapping.class));

        service.ensureFromOrder(makeOrder("ABCDE12345"));

        verify(pickSourceMappingMapper).insert(any(PickSourceMapping.class));
    }

    @Test
    void ensureFromOrder_shouldReturnEarlyForBlankPickSource() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("  ");

        service.ensureFromOrder(order);

        verify(pickSourceMappingMapper, never()).selectOne(any());
        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
    }

    @Test
    void saveOrUpdate_shouldInsertWhenPickSourceIsNew() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-1",
                "Talent A",
                "NEWID123",
                UUID.randomUUID(),
                "PS_NEW",
                "pid_new",
                "act_new",
                "source_url",
                "converted_url",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY"
        );

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        PickSourceMapping saved = captor.getValue();
        assertThat(saved.getChannelUserName()).isEqualTo("channel-user");
        assertThat(saved.getShortId()).isEqualTo("NEWID123");
        assertThat(saved.getPickSource()).isEqualTo("PS_NEW");
        assertThat(saved.getScene()).isEqualTo("PRODUCT_LIBRARY");
    }

    @Test
    void saveOrUpdate_shouldUpdateWhenPickSourceAlreadyExists() {
        PickSourceMapping existing = new PickSourceMapping();
        existing.setId(UUID.randomUUID());
        existing.setPickSource("PS_001");
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(existing);

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-1",
                "Talent A",
                "ABCD1234",
                UUID.randomUUID(),
                "PS_001",
                "pid_9",
                "act_9",
                "source_url",
                "target_url",
                UUID.randomUUID()
        );

        verify(pickSourceMappingMapper).updateById(existing);
        assertThat(existing.getShortId()).isEqualTo("ABCD1234");
        assertThat(existing.getPickExtra()).isEqualTo("ABCD1234");
    }

    @Test
    void saveOrUpdate_shouldRecoverFromConcurrentDuplicateInsert() {
        PickSourceMapping concurrent = new PickSourceMapping();
        concurrent.setId(UUID.randomUUID());
        concurrent.setPickSource("PS_DUP");
        when(pickSourceMappingMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(concurrent);
        doThrow(new DuplicateKeyException("dup"))
                .when(pickSourceMappingMapper).insert(any(PickSourceMapping.class));

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-1",
                "Talent A",
                "DUPMID1",
                UUID.randomUUID(),
                "PS_DUP",
                "pid_dup",
                "act_dup",
                "s_url",
                "c_url",
                UUID.randomUUID()
        );

        verify(pickSourceMappingMapper).updateById(concurrent);
    }

    @Test
    void saveOrUpdate_shouldRethrowWhenConcurrentRowStillMissing() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("dup"))
                .when(pickSourceMappingMapper).insert(any(PickSourceMapping.class));

        assertThatThrownBy(() -> service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-1",
                "Talent A",
                "ERRID1",
                UUID.randomUUID(),
                "PS_ERR",
                "pid_err",
                "act_err",
                "s",
                "c",
                UUID.randomUUID()
        )).isInstanceOf(DuplicateKeyException.class);
    }

    private ColonelsettlementOrder makeOrder(String pickSource) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource(pickSource);
        order.setAttributionStatus("ATTRIBUTED");
        order.setUserId(UUID.randomUUID());
        order.setDeptId(UUID.randomUUID());
        order.setProductId("pid_1");
        return order;
    }
}
