package com.colonel.saas.service;

import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelPartnerSyncServiceTest {

    @Mock
    private ColonelPartnerMapper colonelPartnerMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ProductDomainEventPublisher productDomainEventPublisher;
    @Captor
    private ArgumentCaptor<ColonelPartner> partnerCaptor;

    private ColonelPartnerSyncService service;

    @BeforeEach
    void setUp() {
        service = new ColonelPartnerSyncService(colonelPartnerMapper, jdbcTemplate, productDomainEventPublisher);
    }

    @Test
    void syncAll_shouldReturnZeroWhenNoData() {
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of());

        int upserted = service.syncAll();

        assertThat(upserted).isEqualTo(0);
        verify(productDomainEventPublisher).publishPartnerSyncCompleted(0);
    }

    @Test
    void syncAll_shouldPublishCountSummary() {
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("colonel_activity")))
                .thenReturn(List.of(Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "colonel_name", "张团长",
                        "source_updated_at", java.time.LocalDateTime.now())));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("colonelsettlement_order")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("pick_source_mapping")))
                .thenReturn(List.of());
        when(colonelPartnerMapper.selectOne(any())).thenReturn(null);
        when(colonelPartnerMapper.insert(any())).thenReturn(1);

        int upserted = service.syncAll();

        assertThat(upserted).isEqualTo(1);
        verify(productDomainEventPublisher).publishPartnerSyncCompleted(1);
    }

    @Test
    void upsertSeed_shouldInsertNewPartner() {
        when(colonelPartnerMapper.selectOne(any())).thenReturn(null);
        when(colonelPartnerMapper.insert(any())).thenReturn(1);

        ColonelPartner newPartner = new ColonelPartner();
        newPartner.setColonelBuyinId("999");
        newPartner.setColonelName("新团长");
        newPartner.setSourceUpdatedAt(java.time.LocalDateTime.now());

        boolean result = service.upsertSeed(newPartner);

        assertThat(result).isTrue();
        verify(colonelPartnerMapper).insert(partnerCaptor.capture());
        assertThat(partnerCaptor.getValue().getColonelBuyinId()).isEqualTo("999");
    }

    @Test
    void upsertSeed_shouldPreserveContactNameAndContactPhoneWhenSeedHasNulls() {
        // Given: existing partner with manual contact info
        java.time.LocalDateTime oldTime = java.time.LocalDateTime.now().minusDays(10);
        ColonelPartner existing = new ColonelPartner();
        existing.setId(java.util.UUID.randomUUID());
        existing.setColonelBuyinId("7351155267604218149");
        existing.setColonelName("旧名称");
        existing.setContactName("张三");
        existing.setContactPhone("13800138000");
        existing.setSourceUpdatedAt(oldTime);

        when(colonelPartnerMapper.selectOne(any())).thenReturn(existing);
        when(colonelPartnerMapper.updateById(any())).thenReturn(1);

        // When: seed has null contact info (from sync, not manual entry) but newer timestamp
        ColonelPartner seed = new ColonelPartner();
        seed.setColonelBuyinId("7351155267604218149");
        seed.setColonelName("新名称");
        seed.setContactName(null);
        seed.setContactPhone(null);
        seed.setSourceUpdatedAt(java.time.LocalDateTime.now()); // newer than existing

        boolean result = service.upsertSeed(seed);

        assertThat(result).isTrue();
        verify(colonelPartnerMapper).updateById(partnerCaptor.capture());
        // contact info should be preserved from existing
        assertThat(partnerCaptor.getValue().getContactName()).isEqualTo("张三");
        assertThat(partnerCaptor.getValue().getContactPhone()).isEqualTo("13800138000");
        // but colonelName should be updated
        assertThat(partnerCaptor.getValue().getColonelName()).isEqualTo("新名称");
    }

    @Test
    void resolveProductIdsByColonelName_shouldReturnEmptySetWhenKeywordBlank() {
        Set<String> result = service.resolveProductIdsByColonelName("");
        assertThat(result).isEmpty();
    }

    @Test
    void resolveProductIdsByColonelName_shouldReturnEmptySetWhenNoMatchingPartners() {
        when(colonelPartnerMapper.selectList(any())).thenReturn(List.of());

        Set<String> result = service.resolveProductIdsByColonelName("不存在");

        assertThat(result).isEmpty();
    }
}
