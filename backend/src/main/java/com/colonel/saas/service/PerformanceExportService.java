package com.colonel.saas.service;

import com.colonel.saas.domain.performance.application.PerformanceExportApplicationService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 业绩明细 Excel 导出服务（DDD 委派壳，DDD-PERFORMANCE Slice 6）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑（POI 工作簿创建 / HEADERS 表头 /
 * 30 列数据写入 / 分转元 / 时间格式化 / null 安全）已搬至
 * {@link PerformanceExportApplicationService}。现有调用方（{@code PerformanceController}）
 * 继续通过本类调用，行为零变化。</p>
 */
@Service
public class PerformanceExportService {

    private final PerformanceExportApplicationService exportApplicationService;

    public PerformanceExportService(PerformanceExportApplicationService exportApplicationService) {
        this.exportApplicationService = exportApplicationService;
    }

    /**
     * 委派到 {@link PerformanceExportApplicationService#exportXlsx}
     * （DDD-PERFORMANCE Slice 6）。
     */
    public byte[] exportXlsx(PerformanceListQuery query, PerformanceAccessContext context)
            throws IOException {
        return exportApplicationService.exportXlsx(query, context);
    }
}