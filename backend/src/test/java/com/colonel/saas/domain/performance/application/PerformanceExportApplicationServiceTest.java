package com.colonel.saas.domain.performance.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.service.PerformanceQueryService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerformanceExportApplicationService 直接行为验证（DDD-PERFORMANCE Slice 6）。
 *
 * <p>核心目标：验证 Application 层独立持有完整的 Excel 导出逻辑，
 * 不依赖 Service 中转。Service 层委派是 thin shell，
 * 真实业务逻辑（POI 工作簿 / HEADERS / 30 列写入 / 分转元 / 时间格式化 / null 安全）
 * 必须由本测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PerformanceExportApplicationServiceTest {

    @Mock
    private PerformanceQueryService performanceQueryService;

    private PerformanceExportApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new PerformanceExportApplicationService(performanceQueryService);
    }

    @Test
    void exportXlsx_shouldWriteHeadersAmountsAndFormattedTimes() throws Exception {
        PerformanceListQuery query = new PerformanceListQuery();
        PerformanceAccessContext context = PerformanceAccessContext.of(
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of("admin"));
        PerformanceDetailDTO detail = new PerformanceDetailDTO();
        detail.setOrderId("ORDER-2001");
        detail.setProductId("PRODUCT-8001");
        detail.setProductName("测试商品-Application");
        detail.setPayTime(LocalDateTime.of(2026, 6, 1, 12, 0, 0));
        detail.setSettleTime(LocalDateTime.of(2026, 6, 2, 13, 5, 6));
        detail.setPayAmount(99000L);
        detail.setSettleAmount(95000L);
        detail.setEstimateGrossProfit(1500L);
        detail.setEffectiveGrossProfit(1400L);
        when(performanceQueryService.listDetailsForExport(query, context)).thenReturn(List.of(detail));

        byte[] bytes = applicationService.exportXlsx(query, context);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("业绩明细");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("订单ID");
            assertThat(sheet.getRow(0).getCell(29).getStringCellValue()).isEqualTo("结算毛利");

            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("ORDER-2001");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("测试商品-Application");
            assertThat(row.getCell(14).getStringCellValue()).isEqualTo("2026-06-01 12:00:00");
            assertThat(row.getCell(15).getStringCellValue()).isEqualTo("2026-06-02 13:05:06");
            assertThat(row.getCell(16).getNumericCellValue()).isEqualTo(990.0D);
            assertThat(row.getCell(17).getNumericCellValue()).isEqualTo(950.0D);
            assertThat(row.getCell(28).getNumericCellValue()).isEqualTo(15.0D);
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(14.0D);
        }
        verify(performanceQueryService).listDetailsForExport(query, context);
    }

    @Test
    void exportXlsx_shouldHandleEmptyResultSet() throws Exception {
        PerformanceListQuery query = new PerformanceListQuery();
        PerformanceAccessContext context = PerformanceAccessContext.of(
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of("admin"));
        when(performanceQueryService.listDetailsForExport(query, context)).thenReturn(List.of());

        byte[] bytes = applicationService.exportXlsx(query, context);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("业绩明细");
            assertThat(sheet).isNotNull();
            // HEADERS 行存在, 无数据行
            // (Row 类型不是 AssertJ Iterable 支持, 用 Java assert)
            if (sheet.getRow(0) == null) {
                throw new AssertionError("HEADERS row should not be null");
            }
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("订单ID");
            assertThat(sheet.getLastRowNum()).isEqualTo(0);
        }
    }
}