package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductBizStatusServiceTest {

    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ProductOperationLogMapper operationLogMapper;

    private ProductBizStatusService service;

    @BeforeEach
    void setUp() {
        service = new ProductBizStatusService(operationStateMapper, operationLogMapper);
    }

    @Test
    void initStateIfAbsent_shouldCreatePendingAuditState() {
        ProductOperationState state = service.initStateIfAbsent(null, "10001", "9001", null, null, "同步商品");

        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.PENDING_AUDIT.name());
        assertThat(state.getAuditStatus()).isEqualTo(1);
        verify(operationStateMapper).insert(any(ProductOperationState.class));
        verify(operationLogMapper).insert(any(ProductOperationLog.class));
    }

    @Test
    void changeStatus_shouldApproveFromPendingAudit() {
        ProductOperationState state = buildState(ProductBizStatus.PENDING_AUDIT);

        service.changeStatus(
                state,
                ProductBizStatus.APPROVED,
                "AUDIT",
                null,
                null,
                Map.of("approved", true),
                "审核通过",
                current -> current.setAuditStatus(2)
        );

        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(state.getAuditStatus()).isEqualTo(2);
        verify(operationStateMapper).updateById(state);

        ArgumentCaptor<ProductOperationLog> captor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getBeforeStatus()).isEqualTo(ProductBizStatus.PENDING_AUDIT.name());
        assertThat(captor.getValue().getAfterStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
    }

    @Test
    void changeStatus_shouldRejectPendingAuditToLinked() {
        assertIllegal(ProductBizStatus.PENDING_AUDIT, ProductBizStatus.LINKED, "PROMOTION_LINK");
    }

    @Test
    void changeStatus_shouldRejectPendingAuditToAssigned() {
        assertIllegal(ProductBizStatus.PENDING_AUDIT, ProductBizStatus.ASSIGNED, "ASSIGN");
    }

    @Test
    void changeStatus_shouldRejectApprovedToFollowing() {
        assertIllegal(ProductBizStatus.APPROVED, ProductBizStatus.FOLLOWING, "TALENT_FOLLOW");
    }

    @Test
    void changeStatus_shouldRejectRejectedToAssigned() {
        assertIllegal(ProductBizStatus.REJECTED, ProductBizStatus.ASSIGNED, "ASSIGN");
    }

    @Test
    void changeStatus_shouldRejectRejectedToLinked() {
        assertIllegal(ProductBizStatus.REJECTED, ProductBizStatus.LINKED, "PROMOTION_LINK");
    }

    @Test
    void changeStatus_shouldRejectRejectedToFollowing() {
        assertIllegal(ProductBizStatus.REJECTED, ProductBizStatus.FOLLOWING, "TALENT_FOLLOW");
    }

    @Test
    void changeStatus_shouldRejectLinkedRollbackToApproved() {
        assertIllegal(ProductBizStatus.LINKED, ProductBizStatus.APPROVED, "AUDIT");
    }

    @Test
    void changeStatus_shouldRejectLinkedRollbackToAssigned() {
        assertIllegal(ProductBizStatus.LINKED, ProductBizStatus.ASSIGNED, "ASSIGN");
    }

    @Test
    void changeStatus_shouldRejectFollowingRollbackToLinked() {
        assertIllegal(ProductBizStatus.FOLLOWING, ProductBizStatus.LINKED, "PROMOTION_LINK");
    }

    @Test
    void changeStatus_shouldRejectFollowingToApproved() {
        assertIllegal(ProductBizStatus.FOLLOWING, ProductBizStatus.APPROVED, "AUDIT");
    }

    @Test
    void changeStatus_shouldAllowLinkedToFollowing() {
        ProductOperationState state = buildState(ProductBizStatus.LINKED);

        service.changeStatus(
                state,
                ProductBizStatus.FOLLOWING,
                "TALENT_FOLLOW",
                null,
                null,
                Map.of("followStatus", "INVITED"),
                "进入达人跟进",
                current -> {
                }
        );

        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.FOLLOWING.name());
        verify(operationStateMapper).updateById(state);
    }

    @Test
    void changeStatus_shouldAllowApprovedToAssigned() {
        ProductOperationState state = buildState(ProductBizStatus.APPROVED);

        service.changeStatus(
                state,
                ProductBizStatus.ASSIGNED,
                "ASSIGN",
                null,
                null,
                Map.of("assigneeId", UUID.randomUUID()),
                "分配招商",
                current -> current.setAssigneeId(UUID.randomUUID())
        );

        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.ASSIGNED.name());
        verify(operationStateMapper).updateById(state);
    }

    @Test
    void changeStatus_shouldRejectApprovedToBound() {
        ProductOperationState state = buildState(ProductBizStatus.APPROVED);

        assertThatThrownBy(() -> service.changeStatus(
                state,
                ProductBizStatus.BOUND,
                "BIND_ACTIVITY",
                null,
                null,
                Map.of("activityId", "10001"),
                "绑定活动",
                current -> {
                }
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void changeStatus_shouldAllowBoundToAssignedForLegacyData() {
        ProductOperationState state = buildState(ProductBizStatus.BOUND);

        service.changeStatus(
                state,
                ProductBizStatus.ASSIGNED,
                "ASSIGN",
                null,
                null,
                Map.of("assigneeId", UUID.randomUUID()),
                "分配招商",
                current -> current.setAssigneeId(UUID.randomUUID())
        );

        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.ASSIGNED.name());
        verify(operationStateMapper).updateById(state);
    }

    @Test
    void changeStatus_shouldAllowAssignedToLinked() {
        ProductOperationState state = buildState(ProductBizStatus.ASSIGNED);

        service.changeStatus(
                state,
                ProductBizStatus.LINKED,
                "PROMOTION_LINK",
                null,
                null,
                Map.of("linkId", "L001"),
                "生成推广链接",
                current -> current.setPromoteLink("https://link.example.com/abc")
        );

        assertThat(state.getBizStatus()).isEqualTo(ProductBizStatus.LINKED.name());
        verify(operationStateMapper).updateById(state);
    }

    @Test
    void readBizStatus_shouldReturnDefaultForNull() {
        assertThat(service.readBizStatus(null)).isEqualTo(ProductBizStatus.PENDING_AUDIT);
    }

    @Test
    void readBizStatus_shouldReturnDefaultForBlankCode() {
        ProductOperationState state = new ProductOperationState();
        state.setBizStatus("  ");

        assertThat(service.readBizStatus(state)).isEqualTo(ProductBizStatus.PENDING_AUDIT);
    }

    @Test
    void readBizStatus_shouldParseKnownCode() {
        ProductOperationState state = new ProductOperationState();
        state.setBizStatus("APPROVED");

        assertThat(service.readBizStatus(state)).isEqualTo(ProductBizStatus.APPROVED);
    }

    @Test
    void initStateIfAbsent_shouldReturnExistingWhenPresent() {
        ProductOperationState existing = buildState(ProductBizStatus.APPROVED);

        ProductOperationState result = service.initStateIfAbsent(existing, "10001", "9001", null, null, "同步商品");

        assertThat(result).isSameAs(existing);
        verify(operationStateMapper, never()).insert(any(ProductOperationState.class));
    }

    @Test
    void logFailure_shouldWriteLogWithoutStateChange() {
        service.logFailure(
                "10001",
                "9001",
                ProductBizStatus.PENDING_AUDIT,
                "SYNC",
                null,
                null,
                Map.of("error", "timeout"),
                "同步失败",
                "连接超时"
        );

        ArgumentCaptor<ProductOperationLog> captor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        ProductOperationLog log = captor.getValue();
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getErrorMessage()).isEqualTo("连接超时");
        assertThat(log.getBeforeStatus()).isEqualTo(ProductBizStatus.PENDING_AUDIT.name());
        assertThat(log.getAfterStatus()).isEqualTo(ProductBizStatus.PENDING_AUDIT.name());
    }

    private void assertIllegal(ProductBizStatus from, ProductBizStatus to, String operationType) {
        ProductOperationState state = buildState(from);
        assertThatThrownBy(() -> service.changeStatus(
                state,
                to,
                operationType,
                null,
                null,
                Map.of(),
                "非法流转",
                current -> {
                }
        )).isInstanceOf(BusinessException.class);
    }

    private ProductOperationState buildState(ProductBizStatus status) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus(status.name());
        return state;
    }
}
