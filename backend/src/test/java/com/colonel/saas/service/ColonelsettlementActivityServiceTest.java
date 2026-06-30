package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.colonel.application.ColonelsettlementActivityApplicationService;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ColonelsettlementActivityService 委派壳冒烟测试（DDD-COLONEL-002 Slice 2）。
 *
 * <p>Service 已是 1-line delegate；本测试仅验证委派路径打通，详细业务逻辑断言
 * 见 {@link ColonelsettlementActivityApplicationServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColonelsettlementActivityServiceTest {

    @Mock
    private ColonelsettlementActivityApplicationService applicationService;

    private ColonelsettlementActivityService service;

    @BeforeEach
    void setUp() {
        service = new ColonelsettlementActivityService(applicationService);
    }

    @Test
    void getPage_shouldDelegateToApplication() {
        @SuppressWarnings("unchecked")
        IPage<ColonelsettlementActivity> page = (IPage<ColonelsettlementActivity>) org.mockito.Mockito.mock(IPage.class);
        when(applicationService.getPage(1, 10, 2)).thenReturn(page);

        IPage<ColonelsettlementActivity> result = service.getPage(1, 10, 2);

        assertThat(result).isSameAs(page);
        verify(applicationService).getPage(1, 10, 2);
    }

    @Test
    void syncActivitySummaryFromUpstream_shouldDelegateToApplication() {
        when(applicationService.syncActivitySummaryFromUpstream("act-1", "app-1")).thenReturn(true);

        boolean result = service.syncActivitySummaryFromUpstream("act-1", "app-1");

        assertThat(result).isTrue();
        verify(applicationService).syncActivitySummaryFromUpstream("act-1", "app-1");
    }

    @Test
    void findByActivityId_shouldDelegateToApplication() {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        when(applicationService.findByActivityId("act-1")).thenReturn(activity);

        ColonelsettlementActivity result = service.findByActivityId("act-1");

        assertThat(result).isSameAs(activity);
        verify(applicationService).findByActivityId("act-1");
    }

    @Test
    void isPromotingActivity_shouldDelegateToApplication() {
        when(applicationService.isPromotingActivity("act-1")).thenReturn(true);

        boolean result = service.isPromotingActivity("act-1");

        assertThat(result).isTrue();
        verify(applicationService).isPromotingActivity("act-1");
    }

    @Test
    void syncFromGatewayItem_shouldDelegateToApplication() {
        DouyinActivityGateway.ActivityItem item = org.mockito.Mockito.mock(DouyinActivityGateway.ActivityItem.class);

        service.syncFromGatewayItem(item);

        verify(applicationService).syncFromGatewayItem(item);
    }
}