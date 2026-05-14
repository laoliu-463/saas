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
        assertThat(saved.getPickExtra()).isNull();
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
    void saveOrUpdate_shouldPersistExplicitPickExtra() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-2",
                "Talent B",
                "NEWID999",
                UUID.randomUUID(),
                "PS_EXPLICIT",
                "pid_explicit",
                "act_explicit",
                "source_url",
                "converted_url",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "channel_user-1"
        );

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getPickExtra()).isEqualTo("channel_user-1");
        assertThat(captor.getValue().getShortId()).isEqualTo("NEWID999");
    }

    @Test
    void saveOrUpdate_shouldPersistNativeColonelBuyinId() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-native",
                "Talent Native",
                "NATIVE01",
                UUID.randomUUID(),
                "PS_NATIVE",
                "pid_native",
                "act_native",
                "source_url",
                "converted_url",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "channel_native",
                "7351155267604218149"
        );

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getColonelBuyinId()).isEqualTo("7351155267604218149");
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
    void saveOrUpdate_shouldInsertDistinctRowsWhenSamePickSourceUsedByDifferentProducts() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-1",
                "Talent A",
                "REALID001",
                UUID.randomUUID(),
                "v.MxZLIw",
                "product-a",
                "activity-a",
                "source_url_a",
                "target_url_a",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "channel_user"
        );

        service.saveOrUpdate(
                UUID.randomUUID(),
                "channel-user",
                UUID.randomUUID(),
                "talent-2",
                "Talent B",
                "REALID002",
                UUID.randomUUID(),
                "v.MxZLIw",
                "product-b",
                "activity-b",
                "source_url_b",
                "target_url_b",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "channel_user"
        );

        verify(pickSourceMappingMapper, never()).updateById(any(PickSourceMapping.class));
        verify(pickSourceMappingMapper, org.mockito.Mockito.times(2)).insert(any(PickSourceMapping.class));
    }

    @Test
    void saveOrUpdate_shouldNotSilentlyOverwriteDifferentUsersForSameNativeKey() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();

        service.saveOrUpdate(
                firstUser,
                "channel-user-a",
                UUID.randomUUID(),
                "talent-1",
                "Talent A",
                "NATIVEA1",
                UUID.randomUUID(),
                "v.MxZLIw",
                "3816127512791089531",
                "3859423",
                "source_url_a",
                "target_url_a",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "channel_user_a",
                "7293293346398011698",
                PickSourceMappingService.SOURCE_TYPE_NATIVE
        );

        service.saveOrUpdate(
                secondUser,
                "channel-user-b",
                UUID.randomUUID(),
                "talent-2",
                "Talent B",
                "NATIVEB1",
                UUID.randomUUID(),
                "v.MxZLIw",
                "3816127512791089531",
                "3859423",
                "source_url_b",
                "target_url_b",
                UUID.randomUUID(),
                "PRODUCT_LIBRARY",
                "channel_user_b",
                "7293293346398011698",
                PickSourceMappingService.SOURCE_TYPE_NATIVE
        );

        verify(pickSourceMappingMapper, never()).updateById(any(PickSourceMapping.class));
        verify(pickSourceMappingMapper, org.mockito.Mockito.times(2)).insert(any(PickSourceMapping.class));
    }

    @Test
    void saveOrUpdate_shouldRecoverFromConcurrentDuplicateInsert() {
        PickSourceMapping concurrent = new PickSourceMapping();
        concurrent.setId(UUID.randomUUID());
        concurrent.setPickSource("PS_DUP");
        when(pickSourceMappingMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(null)
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
        when(pickSourceMappingMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(null);
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
