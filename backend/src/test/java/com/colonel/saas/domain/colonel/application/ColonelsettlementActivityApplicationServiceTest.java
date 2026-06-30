package com.colonel.saas.domain.colonel.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ColonelsettlementActivityApplicationService 单元测试（DDD-COLONEL-002 Slice 2）。
 *
 * <p>原 ColonelsettlementActivityServiceTest 中针对 getPage 的断言已迁移到 Application；
 * Service 委派壳为 1-line delegate，单独测试由集成测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColonelsettlementActivityApplicationServiceTest {

    @Mock
    private ColonelsettlementActivityMapper activityMapper;
    @Mock
    private DouyinActivityGateway douyinActivityGateway;

    private ColonelsettlementActivityApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ColonelsettlementActivityApplicationService(
                activityMapper, douyinActivityGateway, true);
    }

    @Test
    void getPage_shouldSeedDemoActivitiesWhenTableIsEmpty() {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setName("主链路演示活动-1");
        when(activityMapper.countLocalActivities()).thenReturn(0L);
        when(activityMapper.selectPage(eq(0L), eq(3L), eq(null), any(LocalDateTime.class)))
                .thenReturn(List.of(activity));
        when(activityMapper.countPage(eq(null), any(LocalDateTime.class))).thenReturn(1L);

        IPage<ColonelsettlementActivity> page = applicationService.getPage(1, 3, null);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(ColonelsettlementActivity::getName)
                .containsExactly("主链路演示活动-1");
        verify(activityMapper, times(5)).insertSeedActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getPage_shouldReadExistingActivitiesWithoutReseeding() {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setName("已落库活动");
        activity.setStatus(1);
        when(activityMapper.countLocalActivities()).thenReturn(2L);
        when(activityMapper.selectPage(eq(3L), eq(3L), eq(1), any(LocalDateTime.class)))
                .thenReturn(List.of(activity));
        when(activityMapper.countPage(eq(1), any(LocalDateTime.class))).thenReturn(4L);

        IPage<ColonelsettlementActivity> page = applicationService.getPage(2, 3, 1);

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getName()).isEqualTo("已落库活动");
        verify(activityMapper, never()).insertSeedActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getPage_shouldNormalizeInvalidPagingArguments() {
        when(activityMapper.countLocalActivities()).thenReturn(1L);
        when(activityMapper.selectPage(eq(0L), eq(1L), eq(null), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(activityMapper.countPage(eq(null), any(LocalDateTime.class))).thenReturn(0L);

        IPage<ColonelsettlementActivity> page = applicationService.getPage(0, 0, null);

        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(1);
    }

    @Test
    void getPage_shouldNotSeedWhenDemoSeedingDisabled() {
        applicationService = new ColonelsettlementActivityApplicationService(
                activityMapper, douyinActivityGateway, false);
        when(activityMapper.selectPage(eq(0L), eq(2L), eq(null), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(activityMapper.countPage(eq(null), any(LocalDateTime.class))).thenReturn(0L);

        IPage<ColonelsettlementActivity> page = applicationService.getPage(1, 2, null);

        assertThat(page.getTotal()).isZero();
        verify(activityMapper, never()).countLocalActivities();
        verify(activityMapper, never()).insertSeedActivity(any(), any(), any(), any(), any(), any(), any());
    }
}