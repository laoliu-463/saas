package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.colonel.saas.domain.order.policy.OrderAttributionInput;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPickSourceMappingAdapterTest {

    private static final LocalDateTime BUSINESS_TIME = LocalDateTime.of(2026, 7, 16, 14, 6, 24);

    private PickSourceMappingMapper mapper;
    private OrderPickSourceMappingAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = mock(PickSourceMappingMapper.class);
        adapter = new OrderPickSourceMappingAdapter(mapper);
    }

    @Test
    void exactPickSourceShouldWinBeforeNativeKeys() {
        UUID userId = UUID.randomUUID();
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                mapping(userId, AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.minusDays(1))));

        OrderLinkAttributionResolution result = adapter.resolve(input("ps-1", "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.UNIQUE);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.ownerType()).isEqualTo(AttributionOwnerType.RECRUITER);
        assertThat(result.source()).isEqualTo(AttributionSource.PICK_SOURCE);
        assertThat(result.nativeKeyMatched()).isFalse();
        verify(mapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    void nativeBuyinActivityProductShouldResolveUniqueOwner() {
        UUID userId = UUID.randomUUID();
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                mapping(userId, AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.minusDays(1))));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.UNIQUE);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.source()).isEqualTo(AttributionSource.NATIVE_UNIQUE_LINK_OWNER);
        assertThat(result.nativeKeyMatched()).isTrue();
        assertThat(result.colonelBuyinIdMismatch()).isFalse();
    }

    @Test
    void activityProductUniqueOwnerShouldAllowBuyinMismatchAndRecordIt() {
        UUID userId = UUID.randomUUID();
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(mapping(
                        userId, AttributionOwnerType.RECRUITER, "different-buyin", BUSINESS_TIME.minusDays(1))));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.UNIQUE);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.source()).isEqualTo(AttributionSource.NATIVE_UNIQUE_LINK_OWNER);
        assertThat(result.nativeKeyMatched()).isTrue();
        assertThat(result.colonelBuyinIdMismatch()).isTrue();
    }

    @Test
    void multipleRowsForSameUserAndOwnerTypeShouldRemainUnique() {
        UUID userId = UUID.randomUUID();
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                mapping(userId, AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.minusDays(2)),
                mapping(userId, AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.minusDays(1))));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.UNIQUE);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.mappingCreatedAt()).isEqualTo(BUSINESS_TIME.minusDays(2));
    }

    @Test
    void distinctOwnersShouldBeAmbiguousInsteadOfChoosingLatest() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                mapping(firstUser, AttributionOwnerType.RECRUITER, "buyin-1", LocalDateTime.of(2026, 7, 15, 8, 47)),
                mapping(secondUser, AttributionOwnerType.RECRUITER, "buyin-1", LocalDateTime.of(2026, 7, 15, 9, 0))));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.AMBIGUOUS);
        assertThat(result.source()).isEqualTo(AttributionSource.AMBIGUOUS);
        assertThat(result.userId()).isNull();
    }

    @Test
    void missingOwnerTypeShouldNotBeSilentlyAccepted() {
        PickSourceMapping row = mapping(UUID.randomUUID(), AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.minusDays(1));
        row.setAttributionOwnerType(null);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.OWNER_TYPE_MISSING);
        assertThat(result.userId()).isNull();
        assertThat(result.source()).isEqualTo(AttributionSource.UNATTRIBUTED);
    }

    @Test
    void mappingCreatedAfterOrderShouldNotOwnEarlierOrder() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                mapping(UUID.randomUUID(), AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.plusMinutes(1))));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.MAPPING_AFTER_ORDER);
        assertThat(result.userId()).isNull();
        assertThat(result.source()).isEqualTo(AttributionSource.UNATTRIBUTED);
    }

    @Test
    void expiredMappingShouldBeIgnored() {
        PickSourceMapping row = mapping(
                UUID.randomUUID(), AttributionOwnerType.RECRUITER, "buyin-1", BUSINESS_TIME.minusDays(3));
        row.setValidUntil(BUSINESS_TIME.minusSeconds(1));
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of(row))
                .thenReturn(List.of(row));

        OrderLinkAttributionResolution result = adapter.resolve(input(null, "buyin-1"));

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.userId()).isNull();
        assertThat(result.source()).isEqualTo(AttributionSource.UNATTRIBUTED);
    }

    private OrderAttributionInput input(String pickSource, String colonelBuyinId) {
        return new OrderAttributionInput(
                "3829804874841849888",
                "3916506",
                pickSource,
                null,
                null,
                null,
                colonelBuyinId,
                null,
                null,
                BUSINESS_TIME);
    }

    private PickSourceMapping mapping(
            UUID userId,
            AttributionOwnerType ownerType,
            String colonelBuyinId,
            LocalDateTime createTime) {
        PickSourceMapping row = new PickSourceMapping();
        row.setUserId(userId);
        row.setDeptId(UUID.randomUUID());
        row.setAttributionOwnerType(ownerType.name());
        row.setColonelBuyinId(colonelBuyinId);
        row.setProductId("3829804874841849888");
        row.setActivityId("3916506");
        row.setSourceType("NATIVE");
        row.setStatus(1);
        row.setDeleted(0);
        row.setValidFrom(createTime);
        row.setValidUntil(BUSINESS_TIME.plusDays(30));
        row.setCreateTime(createTime);
        return row;
    }
}
