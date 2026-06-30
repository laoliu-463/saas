package com.colonel.saas.domain.colonel.application;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ColonelPartnerSyncApplicationService 单元测试（DDD-COLONEL-002 Slice 1）。
 *
 * <p>原 ColonelPartnerSyncServiceTest 中针对 syncAll / upsertSeed / resolveProductIdsByColonelName
 * 的断言已迁移到 Application；Service 委派壳为 1-line delegate，单独测试由集成测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColonelPartnerSyncApplicationServiceTest {

    @Mock
    private ColonelPartnerMapper colonelPartnerMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ProductDomainEventPublisher productDomainEventPublisher;
    @Captor
    private ArgumentCaptor<ColonelPartner> partnerCaptor;

    private ColonelPartnerSyncApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ColonelPartnerSyncApplicationService(
                colonelPartnerMapper, jdbcTemplate, productDomainEventPublisher);
    }

    @Test
    void syncAll_shouldReturnZeroWhenNoData() {
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of());

        int upserted = applicationService.syncAll();

        assertThat(upserted).isEqualTo(0);
        verify(productDomainEventPublisher).publishPartnerSyncCompleted(0);
    }

    @Test
    void syncAll_shouldUseCurrentSchemaTimestampColumns() {
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of());

        applicationService.syncAll();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).queryForList(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .anySatisfy(sql -> assertThat(sql).contains("COALESCE(last_sync_at, update_time, create_time)"))
                .anySatisfy(sql -> assertThat(sql).contains("COALESCE(update_time, create_time)"));
        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql)
                        .doesNotContain("COALESCE(last_sync_at, updated_at, created_at)")
                        .doesNotContain("COALESCE(updated_at, create_time)")
                        .doesNotContain("COALESCE(updated_at, created_at)"));
    }

    @Test
    void syncAll_shouldPublishCountSummary() {
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("colonel_activity")))
                .thenReturn(List.of(Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "colonel_name", "张团长",
                        "source_updated_at", LocalDateTime.now())));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("colonelsettlement_order")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("pick_source_mapping")))
                .thenReturn(List.of());
        when(colonelPartnerMapper.selectOne(any())).thenReturn(null);
        when(colonelPartnerMapper.insert(any())).thenReturn(1);

        int upserted = applicationService.syncAll();

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
        newPartner.setSourceUpdatedAt(LocalDateTime.now());

        boolean result = applicationService.upsertSeed(newPartner);

        assertThat(result).isTrue();
        verify(colonelPartnerMapper).insert(partnerCaptor.capture());
        assertThat(partnerCaptor.getValue().getColonelBuyinId()).isEqualTo("999");
    }

    @Test
    void upsertSeed_shouldPreserveContactNameAndContactPhoneWhenSeedHasNulls() {
        LocalDateTime oldTime = LocalDateTime.now().minusDays(10);
        ColonelPartner existing = new ColonelPartner();
        existing.setId(UUID.randomUUID());
        existing.setColonelBuyinId("7351155267604218149");
        existing.setColonelName("旧名称");
        existing.setContactName("张三");
        existing.setContactPhone("13800138000");
        existing.setSourceUpdatedAt(oldTime);

        when(colonelPartnerMapper.selectOne(any())).thenReturn(existing);
        when(colonelPartnerMapper.updateById(any())).thenReturn(1);

        ColonelPartner seed = new ColonelPartner();
        seed.setColonelBuyinId("7351155267604218149");
        seed.setColonelName("新名称");
        seed.setContactName(null);
        seed.setContactPhone(null);
        seed.setSourceUpdatedAt(LocalDateTime.now());

        boolean result = applicationService.upsertSeed(seed);

        assertThat(result).isTrue();
        verify(colonelPartnerMapper).updateById(partnerCaptor.capture());
        assertThat(partnerCaptor.getValue().getContactName()).isEqualTo("张三");
        assertThat(partnerCaptor.getValue().getContactPhone()).isEqualTo("13800138000");
        assertThat(partnerCaptor.getValue().getColonelName()).isEqualTo("新名称");
    }

    @Test
    void resolveProductIdsByColonelName_shouldReturnEmptySetWhenKeywordBlank() {
        Set<String> result = applicationService.resolveProductIdsByColonelName("");
        assertThat(result).isEmpty();
    }

    @Test
    void resolveProductIdsByColonelName_shouldReturnEmptySetWhenNoMatchingPartners() {
        when(colonelPartnerMapper.selectList(any())).thenReturn(List.of());

        Set<String> result = applicationService.resolveProductIdsByColonelName("不存在");

        assertThat(result).isEmpty();
    }
}