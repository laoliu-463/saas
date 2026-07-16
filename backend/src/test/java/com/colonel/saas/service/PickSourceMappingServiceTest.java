package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.facade.dto.PickSourceAttributionMappingDTO;
import com.colonel.saas.domain.product.facade.dto.PickSourceMappingReadDTO;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void findActiveAttributionMapping_shouldFallbackToPickExtraWhenPickSourceMissing() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(java.util.UUID.randomUUID());
        mapping.setDeptId(java.util.UUID.randomUUID());
        mapping.setActivityId("ACT-PE");
        mapping.setProductId("P-PE");
        mapping.setSourceType(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);

        PickSourceAttributionMappingDTO result = service.findActiveAttributionMapping(null, "PE-TRACE-1");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(mapping.getUserId());
        assertThat(result.activityId()).isEqualTo("ACT-PE");
        assertThat(result.productId()).isEqualTo("P-PE");
    }

    @Test
    void findActiveAttributionMapping_shouldFallbackToShortIdFromPickExtraSuffix() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(java.util.UUID.randomUUID());
        mapping.setDeptId(java.util.UUID.randomUUID());
        mapping.setActivityId("ACT-SHORT");
        mapping.setProductId("P-SHORT");
        mapping.setSourceType(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, mapping);

        PickSourceAttributionMappingDTO result =
                service.findActiveAttributionMapping(null, "prefix-12345678901234567890");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(mapping.getUserId());
        assertThat(result.activityId()).isEqualTo("ACT-SHORT");
        assertThat(result.productId()).isEqualTo("P-SHORT");
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

    @Test
    void saveOrUpdate_shouldInsertPickSourceMappingWithTraceableFields() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID deptId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID uuidSeed = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID promotionLinkId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);

        service.saveOrUpdate(
                userId,
                "channel-name",
                deptId,
                "talent-1",
                "Talent One",
                "SHORT12345",
                uuidSeed,
                "PS-TRACE",
                "P-TRACE",
                "ACT-TRACE",
                "https://source.example/item",
                "https://converted.example/item",
                promotionLinkId,
                "LIVE",
                "  PICK_EXTRA_TRACE  ",
                "  BUYIN-TRACE  ",
                "pick_source",
                "RECRUITER"
        );

        verify(pickSourceMappingMapper).insert(captor.capture());
        PickSourceMapping inserted = captor.getValue();
        assertThat(inserted.getId()).isNotNull();
        assertThat(inserted.getUserId()).isEqualTo(userId);
        assertThat(inserted.getChannelUserName()).isEqualTo("channel-name");
        assertThat(inserted.getDeptId()).isEqualTo(deptId);
        assertThat(inserted.getTalentId()).isEqualTo("talent-1");
        assertThat(inserted.getTalentName()).isEqualTo("Talent One");
        assertThat(inserted.getShortId()).isEqualTo("SHORT12345");
        assertThat(inserted.getUuidSeed()).isEqualTo(uuidSeed);
        assertThat(inserted.getPickSource()).isEqualTo("PS-TRACE");
        assertThat(inserted.getColonelBuyinId()).isEqualTo("BUYIN-TRACE");
        assertThat(inserted.getProductId()).isEqualTo("P-TRACE");
        assertThat(inserted.getActivityId()).isEqualTo("ACT-TRACE");
        assertThat(inserted.getSourceUrl()).isEqualTo("https://source.example/item");
        assertThat(inserted.getConvertedUrl()).isEqualTo("https://converted.example/item");
        assertThat(inserted.getPickExtra()).isEqualTo("PICK_EXTRA_TRACE");
        assertThat(inserted.getPromotionLinkId()).isEqualTo(promotionLinkId);
        assertThat(inserted.getScene()).isEqualTo("LIVE");
        assertThat(inserted.getSourceType()).isEqualTo(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        assertThat(inserted.getAttributionOwnerType()).isEqualTo("RECRUITER");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getValidFrom()).isNotNull();
        assertThat(inserted.getValidUntil()).isAfter(inserted.getValidFrom());
    }

    @Test
    void saveOrUpdate_shouldUsePromotionLinkIdAsIdempotencyKey() {
        UUID existingId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID promotionLinkId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        PickSourceMapping existing = new PickSourceMapping();
        existing.setId(existingId);
        existing.setPromotionLinkId(promotionLinkId);
        existing.setSourceType(PickSourceMappingService.SOURCE_TYPE_PICK_SOURCE);
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(pickSourceMappingMapper.updateById(any(PickSourceMapping.class))).thenReturn(1);
        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);

        service.saveOrUpdate(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                "updated-channel",
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                "talent-updated",
                "Talent Updated",
                "SHORTUPD",
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                "PS-IDEMPOTENT",
                "P-IDEMPOTENT",
                "ACT-IDEMPOTENT",
                "https://source.example/updated",
                "https://converted.example/updated",
                promotionLinkId,
                "VIDEO",
                "PE-IDEMPOTENT"
        );

        verify(pickSourceMappingMapper, never()).insert(any(PickSourceMapping.class));
        verify(pickSourceMappingMapper).updateById(captor.capture());
        PickSourceMapping updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(existingId);
        assertThat(updated.getPromotionLinkId()).isEqualTo(promotionLinkId);
        assertThat(updated.getPickSource()).isEqualTo("PS-IDEMPOTENT");
        assertThat(updated.getProductId()).isEqualTo("P-IDEMPOTENT");
        assertThat(updated.getActivityId()).isEqualTo("ACT-IDEMPOTENT");
        assertThat(updated.getPickExtra()).isEqualTo("PE-IDEMPOTENT");
        assertThat(updated.getScene()).isEqualTo("VIDEO");
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getValidUntil()).isNotNull();
    }

    @Test
    void saveOrUpdate_shouldPreserveNativeIdentityWhenNativeCompositeMatches() {
        UUID existingId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        PickSourceMapping existing = new PickSourceMapping();
        existing.setId(existingId);
        existing.setUserId(userId);
        existing.setShortId("NATIVEOLD");
        existing.setUuidSeed(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        existing.setColonelBuyinId("BUYIN-NATIVE");
        existing.setProductId("P-NATIVE");
        existing.setActivityId("ACT-NATIVE");
        existing.setSourceType(PickSourceMappingService.SOURCE_TYPE_NATIVE);
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(pickSourceMappingMapper.updateById(any(PickSourceMapping.class))).thenReturn(1);
        ArgumentCaptor<PickSourceMapping> captor = ArgumentCaptor.forClass(PickSourceMapping.class);

        service.saveOrUpdate(
                userId,
                "native-channel",
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "talent-native",
                "Talent Native",
                "NATIVENEW",
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "colonel_native_NATIVENEW",
                "P-NATIVE",
                "ACT-NATIVE",
                "colonel_native_NATIVENEW",
                "colonel_native_NATIVENEW",
                null,
                "ORDER_SYNC",
                " PE-NATIVE ",
                " BUYIN-NATIVE ",
                PickSourceMappingService.SOURCE_TYPE_NATIVE
        );

        verify(pickSourceMappingMapper).updateById(captor.capture());
        PickSourceMapping updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(existingId);
        assertThat(updated.getUserId()).isNull();
        assertThat(updated.getShortId()).isNull();
        assertThat(updated.getUuidSeed()).isNull();
        assertThat(updated.getColonelBuyinId()).isNull();
        assertThat(updated.getProductId()).isNull();
        assertThat(updated.getActivityId()).isNull();
        assertThat(updated.getSourceType()).isNull();
        assertThat(updated.getPickSource()).isEqualTo("colonel_native_NATIVENEW");
        assertThat(updated.getChannelUserName()).isEqualTo("native-channel");
        assertThat(updated.getTalentId()).isEqualTo("talent-native");
        assertThat(updated.getTalentName()).isEqualTo("Talent Native");
        assertThat(updated.getPickExtra()).isEqualTo("PE-NATIVE");
        assertThat(updated.getScene()).isEqualTo("ORDER_SYNC");
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getValidUntil()).isNotNull();
    }
}
