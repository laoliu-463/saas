package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.order.facade.PromotionLinkRecordFacade;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceActivityAssignTest {

    @Mock private com.colonel.saas.domain.product.application.port.DouyinConvertPort douyinConvertPort;
    @Mock private com.colonel.saas.gateway.douyin.DouyinProductGateway douyinProductGateway;
    @Mock private com.colonel.saas.mapper.ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private com.colonel.saas.mapper.ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkRecordFacade promotionLinkRecordFacade;
    @Mock private OrderReadFacade orderReadFacade;
    @Mock private com.colonel.saas.mapper.MerchantMapper merchantMapper;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private PickSourceMappingService pickSourceMappingService;
    @Mock private ProductBizStatusService productBizStatusService;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private TalentFollowService talentFollowService;
    @Mock private com.colonel.saas.gateway.douyin.DouyinActivityGateway douyinActivityGateway;
    @Mock private PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    @Mock private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock private com.colonel.saas.domain.product.event.ProductDomainEventPublisher productDomainEventPublisher;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                douyinConvertPort,
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                promotionLinkRecordFacade,
                orderReadFacade,
                merchantMapper,
                userDomainFacade,
                pickSourceMappingService,
                productBizStatusService,
                colonelActivityMapper,
                talentFollowService,
                douyinActivityGateway,
                promotionLinkIdempotencyService,
                configDomainFacade,
                productDisplayRuleService,
                colonelPartnerSyncService,
                productDomainEventPublisher,
                new com.colonel.saas.domain.product.policy.ProductDisplayPolicy(),
                null);
    }

    @Test
    void assignActivity_shouldPersistActivityAndCascadeAssignee() {
        String activityId = "100018";
        UUID assigneeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID operatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        UUID deptId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        ColonelsettlementActivity existing = new ColonelsettlementActivity();
        existing.setActivityId(activityId);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(existing);
        when(userDomainFacade.loadUserOwnershipReferencesByIds(any()))
                .thenReturn(Map.of(assigneeId, new UserOwnershipReference(assigneeId, deptId)));
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(assigneeId, "招商组长甲"));
        when(colonelActivityMapper.updateRecruiterAssignment(
                eq(activityId), eq(assigneeId), eq(deptId), any(), eq(operatorId)))
                .thenReturn(1);
        when(operationStateMapper.update(any(), any())).thenReturn(1);

        Map<String, Object> payload = productService.assignActivity(activityId, assigneeId, operatorId);

        assertThat(payload.get("activityId")).isEqualTo(activityId);
        assertThat(payload.get("assigneeId")).isEqualTo(assigneeId);
        assertThat(payload.get("activityAssigneeName")).isEqualTo("招商组长甲");

        verify(operationStateMapper).update(eq(null), any());
        verify(colonelActivityMapper).updateRecruiterAssignment(
                eq(activityId), eq(assigneeId), eq(deptId), any(), eq(operatorId));
    }
}
