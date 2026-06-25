package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.talent.application.ExclusiveTalentApplicationService;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExclusiveTalentServiceTest {

    @Mock
    private ExclusiveTalentMapper exclusiveTalentMapper;
    @Mock
    private ExclusiveTalentApplicationService applicationService;

    private ExclusiveTalentService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveTalentService(applicationService, exclusiveTalentMapper);
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnOwner() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ExclusiveTalent record = new ExclusiveTalent();
        record.setUserId(userId);
        record.setDeptId(deptId);
        when(exclusiveTalentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(record);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByTalentUid("talent-1");

        assertThat(owner).isNotNull();
        assertThat(owner.userId()).isEqualTo(userId);
        assertThat(owner.deptId()).isEqualTo(deptId);
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnNullWhenNotFound() {
        when(exclusiveTalentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByTalentUid("talent-x");

        assertThat(owner).isNull();
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnNullForBlankUid() {
        assertThat(service.findActiveOwnerByTalentUid(null)).isNull();
        assertThat(service.findActiveOwnerByTalentUid("")).isNull();
    }

    @Test
    void evaluatePreviousMonthAndApplyCurrentMonth_delegatesAndReturnsValue() {
        when(applicationService.evaluatePreviousMonthAndApplyCurrentMonth()).thenReturn(7);
        assertThat(service.evaluatePreviousMonthAndApplyCurrentMonth()).isEqualTo(7);
    }

    @Test
    void evaluateMonth_delegatesAndReturnsValue() {
        when(applicationService.evaluateMonth(
                java.time.YearMonth.of(2026, 5),
                java.time.YearMonth.of(2026, 6))).thenReturn(3);
        assertThat(service.evaluateMonth(java.time.YearMonth.of(2026, 5), java.time.YearMonth.of(2026, 6)))
                .isEqualTo(3);
    }

    @Test
    void evaluateMonth_swallowsApplicationException() {
        when(applicationService.evaluateMonth(
                java.time.YearMonth.of(2026, 5),
                java.time.YearMonth.of(2026, 6)))
                .thenThrow(new RuntimeException("boom"));
        assertThat(service.evaluateMonth(java.time.YearMonth.of(2026, 5), java.time.YearMonth.of(2026, 6)))
                .isEqualTo(0);
    }
}