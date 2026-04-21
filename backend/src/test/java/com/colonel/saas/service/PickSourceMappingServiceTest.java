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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickSourceMappingServiceTest {

    @Mock
    private PickSourceMappingMapper pickSourceMappingMapper;

    private PickSourceMappingService pickSourceMappingService;

    @BeforeEach
    void setUp() {
        pickSourceMappingService = new PickSourceMappingService(pickSourceMappingMapper, 3);
    }

    @Test
    void ensureFromOrder_shouldInsertWhenShortIdCanBeExtracted() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("usr_ABC12345_1712000000");
        order.setUserId(UUID.randomUUID());
        order.setDeptId(UUID.randomUUID());
        order.setProductId("pid_1");

        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        pickSourceMappingService.ensureFromOrder(order);

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        PickSourceMapping saved = captor.getValue();
        assertThat(saved.getShortId()).isEqualTo("ABC12345");
        assertThat(saved.getPickExtra()).isEqualTo("ABC12345");
        assertThat(saved.getPickSource()).isEqualTo(order.getPickSource());
    }

    @Test
    void ensureFromOrder_shouldSkipWhenShortIdAlreadyExists() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("usr_ABC12345_1712000000");

        PickSourceMapping existing = new PickSourceMapping();
        existing.setId(UUID.randomUUID());
        existing.setShortId("ABC12345");
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(existing);

        pickSourceMappingService.ensureFromOrder(order);

        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
    }

    @Test
    void saveOrUpdate_shouldUpdateWhenPickSourceAlreadyExists() {
        PickSourceMapping existing = new PickSourceMapping();
        existing.setId(UUID.randomUUID());
        existing.setPickSource("PS_001");

        when(pickSourceMappingMapper.selectOne(any())).thenReturn(existing);

        pickSourceMappingService.saveOrUpdate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ABCD1234",
                UUID.randomUUID(),
                "PS_001",
                "pid_9",
                "act_9",
                "source_url",
                "target_url"
        );

        verify(pickSourceMappingMapper).updateById(existing);
        assertThat(existing.getShortId()).isEqualTo("ABCD1234");
        assertThat(existing.getPickExtra()).isEqualTo("ABCD1234");
    }

    // --- extractShortId private method coverage ---

    @Test
    void extractShortId_pureUpperNumeric_shouldReturnAsIs() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        pickSourceMappingService.ensureFromOrder(makeOrder("ABCDE12345"));

        verify(pickSourceMappingMapper).insert(any(PickSourceMapping.class));
        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getShortId()).isEqualTo("ABCDE12345");
    }

    @Test
    void extractShortId_withEmbeddedPattern_shouldExtract() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        // Pattern: "prefix 8-10 uppercase digits" embedded in URL
        pickSourceMappingService.ensureFromOrder(makeOrder("https://domain.com/p/ABC12345?id=xyz"));

        verify(pickSourceMappingMapper).insert(any(PickSourceMapping.class));
        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getShortId()).isEqualTo("ABC12345");
    }

    @Test
    void extractShortId_onlyDigitsUnder10Chars_shouldReturnAsIs() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        pickSourceMappingService.ensureFromOrder(makeOrder("12345678"));

        verify(pickSourceMappingMapper).insert(any(PickSourceMapping.class));
        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getShortId()).isEqualTo("12345678");
    }

    @Test
    void ensureFromOrder_nullOrder_shouldReturnEarly() {
        // null order → no interaction
        pickSourceMappingService.ensureFromOrder(null);
        verify(pickSourceMappingMapper, never()).selectOne(any());
        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
    }

    @Test
    void ensureFromOrder_nullPickSource_shouldReturnEarly() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource(null);
        order.setUserId(UUID.randomUUID());

        pickSourceMappingService.ensureFromOrder(order);

        verify(pickSourceMappingMapper, never()).selectOne(any());
        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
    }

    @Test
    void ensureFromOrder_blankPickSource_shouldReturnEarly() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("   ");
        order.setUserId(UUID.randomUUID());

        pickSourceMappingService.ensureFromOrder(order);

        verify(pickSourceMappingMapper, never()).selectOne(any());
        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
    }

    @Test
    void ensureFromOrder_duplicateKeyException_shouldBeSilentlyIgnored() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                .when(pickSourceMappingMapper).insert(any(PickSourceMapping.class));

        // Should NOT throw
        pickSourceMappingService.ensureFromOrder(makeOrder("ABCD12345"));

        verify(pickSourceMappingMapper).insert(any(PickSourceMapping.class));
    }

    // --- saveOrUpdate insert path ---

    @Test
    void saveOrUpdate_newPickSource_shouldInsert() {
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        pickSourceMappingService.saveOrUpdate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "NEWID123",
                UUID.randomUUID(),
                "PS_NEW",
                "pid_new",
                "act_new",
                "source_url",
                "converted_url"
        );

        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);
        verify(pickSourceMappingMapper).insert(captor.capture());
        PickSourceMapping saved = captor.getValue();
        assertThat(saved.getPickSource()).isEqualTo("PS_NEW");
        assertThat(saved.getShortId()).isEqualTo("NEWID123");
        assertThat(saved.getPickExtra()).isEqualTo("NEWID123");
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    void saveOrUpdate_concurrentDuplicateKey_shouldRecoverAndUpdate() {
        // First selectOne returns null, then insert throws DuplicateKeyException,
        // then second selectOne finds the row created by concurrent insert
        when(pickSourceMappingMapper.selectOne(any()))
                .thenReturn(null)
                .thenAnswer(inv -> {
                    PickSourceMapping concurrent = new PickSourceMapping();
                    concurrent.setId(UUID.randomUUID());
                    concurrent.setPickSource("PS_DUP");
                    return concurrent;
                });
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                .when(pickSourceMappingMapper).insert(any(PickSourceMapping.class));

        pickSourceMappingService.saveOrUpdate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DUPMID1",
                UUID.randomUUID(),
                "PS_DUP",
                "pid_dup",
                "act_dup",
                "s_url",
                "c_url"
        );

        // Should have fallen through to updateById
        verify(pickSourceMappingMapper).updateById(any(PickSourceMapping.class));
    }

    @Test
    void saveOrUpdate_concurrentDuplicateKeyAndStillNull_shouldThrow() {
        // First selectOne returns null, insert throws DuplicateKeyException,
        // second selectOne also returns null → should re-throw
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                .when(pickSourceMappingMapper).insert(any(PickSourceMapping.class));

        assertThatThrownBy(() -> pickSourceMappingService.saveOrUpdate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ERRID1",
                UUID.randomUUID(),
                "PS_ERR",
                "pid_err",
                "act_err",
                "s",
                "c"
        )).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private ColonelsettlementOrder makeOrder(String pickSource) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource(pickSource);
        order.setUserId(UUID.randomUUID());
        order.setDeptId(UUID.randomUUID());
        order.setProductId("pid_1");
        return order;
    }
}

