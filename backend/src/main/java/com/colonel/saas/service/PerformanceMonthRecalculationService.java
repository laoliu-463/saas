package com.colonel.saas.service;

import com.colonel.saas.domain.performance.application.PerformanceMonthRecalculationApplicationService;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthResponse;
import org.springframework.stereotype.Service;

/**
 * 业绩月度重算服务（DDD 委派壳，DDD-PERFORMANCE Slice 5）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑（月份解析 / 时间区间 / 逐笔重算 / 容错 /
 * 响应组装）已搬至 {@link PerformanceMonthRecalculationApplicationService}。
 * 现有调用方（{@code PerformanceController} 等）继续通过本类调用，行为零变化。</p>
 *
 * <p>Service 不再直接依赖 {@code PerformanceCalculationApplicationService} /
 * {@code OrderReadFacade}，而是通过新 Application 间接委派。
 * 架构边界检查 {@code DddPerf001CalculationApplicationServiceRoutingTest}
 * 已跟随调整，验证入口委派形态而非文件 import 字符串。</p>
 */
@Service
public class PerformanceMonthRecalculationService {

    private final PerformanceMonthRecalculationApplicationService monthRecalculationApplicationService;

    public PerformanceMonthRecalculationService(
            PerformanceMonthRecalculationApplicationService monthRecalculationApplicationService) {
        this.monthRecalculationApplicationService = monthRecalculationApplicationService;
    }

    /**
     * 委派到 {@link PerformanceMonthRecalculationApplicationService#recalculateMonth}
     * （DDD-PERFORMANCE Slice 5）。
     */
    public PerformanceRecalculateMonthResponse recalculateMonth(String month, String reason) {
        return monthRecalculationApplicationService.recalculateMonth(month, reason);
    }
}