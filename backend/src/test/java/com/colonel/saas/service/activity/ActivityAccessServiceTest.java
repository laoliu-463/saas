package com.colonel.saas.service.activity;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityAccessServiceTest {

    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    private ActivityAccessService activityAccessService;

    @BeforeEach
    void setUp() {
        activityAccessService = new ActivityAccessService(activityMapper);
    }

    @Test
    void resolveEffectiveAssignmentFilter_shouldForceMineForRecruiter() {
        assertThat(activityAccessService.resolveEffectiveAssignmentFilter("all", List.of(RoleCodes.BIZ_STAFF)))
                .isEqualTo("mine");
    }

    @Test
    void resolveEffectiveAssignmentFilter_shouldRespectAdminChoice() {
        assertThat(activityAccessService.resolveEffectiveAssignmentFilter("assigned", List.of(RoleCodes.ADMIN)))
                .isEqualTo("assigned");
    }

    @Test
    void assertActivityReadable_shouldAllowAdminWithoutLookup() {
        activityAccessService.assertActivityReadable(
                "100018",
                UUID.randomUUID(),
                List.of(RoleCodes.ADMIN));
    }

    @Test
    void assertActivityReadable_shouldRejectUnassignedActivityForRecruiter() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(activityMapper.selectByActivityId("100018")).thenReturn(null);

        assertThatThrownBy(() -> activityAccessService.assertActivityReadable(
                "100018",
                userId,
                List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该活动");
    }

    @Test
    void assertActivityReadable_shouldAllowAssignedRecruiter() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("100018");
        activity.setRecruiterUserId(userId);
        when(activityMapper.selectByActivityId("100018")).thenReturn(activity);

        activityAccessService.assertActivityReadable("100018", userId, List.of(RoleCodes.BIZ_LEADER));
    }
}
