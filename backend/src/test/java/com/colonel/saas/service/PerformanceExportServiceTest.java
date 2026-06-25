package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

@ExtendWith(MockitoExtension.class)
class PerformanceExportServiceTest {

    @Mock
    private PerformanceQueryService performanceQueryService;

    @Test
    void exportXlsx_shouldWriteHeadersAmountsAndFormattedTimes() throws Exception {
        PerformanceExportService service = new PerformanceExportService(performanceQueryService);
        PerformanceListQuery query = new PerformanceListQuery();
        PerformanceAccessContext context = PerformanceAccessContext.of(
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of("admin"));
        PerformanceDetailDTO detail = new PerformanceDetailDTO();
        detail.setOrderId("ORDER-1001");
        detail.setProductId("PRODUCT-9001");
        detail.setProductName("测试商品");
        detail.setActivityId("ACT-1");
        detail.setActivityName("春季活动");
        detail.setPartnerName("合作方A");
        detail.setTalentName("达人A");
        detail.setDefaultChannelName("默认渠道");
        detail.setFinalChannelName("最终渠道");
        detail.setDefaultRecruiterName("默认招商");
        detail.setFinalRecruiterName("最终招商");
        detail.setChannelAttributionType("pick_source");
        detail.setRecruiterAttributionType("activity_owner");
        detail.setOrderStatus("SETTLED");
        detail.setPayTime(LocalDateTime.of(2026, 5, 24, 10, 30, 45));
        detail.setSettleTime(LocalDateTime.of(2026, 5, 25, 11, 5, 6));
        detail.setPayAmount(12345L);
        detail.setSettleAmount(12000L);
        detail.setEstimateServiceFee(2345L);
        detail.setEffectiveServiceFee(2200L);
        detail.setEstimateTechServiceFee(345L);
        detail.setEffectiveTechServiceFee(300L);
        detail.setEstimateServiceProfit(2000L);
        detail.setEffectiveServiceProfit(1900L);
        detail.setEstimateRecruiterCommission(800L);
        detail.setEffectiveRecruiterCommission(700L);
        detail.setEstimateChannelCommission(600L);
        detail.setEffectiveChannelCommission(500L);
        detail.setEstimateGrossProfit(600L);
        detail.setEffectiveGrossProfit(700L);
        when(performanceQueryService.listDetailsForExport(query, context)).thenReturn(List.of(detail));

        byte[] bytes = service.exportXlsx(query, context);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("业绩明细");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("订单ID");
            assertThat(sheet.getRow(0).getCell(29).getStringCellValue()).isEqualTo("结算毛利");

            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("ORDER-1001");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("测试商品");
            assertThat(row.getCell(14).getStringCellValue()).isEqualTo("2026-05-24 10:30:45");
            assertThat(row.getCell(15).getStringCellValue()).isEqualTo("2026-05-25 11:05:06");
            assertThat(row.getCell(16).getNumericCellValue()).isEqualTo(123.45D);
            assertThat(row.getCell(17).getNumericCellValue()).isEqualTo(120.0D);
            assertThat(row.getCell(29).getNumericCellValue()).isEqualTo(7.0D);
        }
        verify(performanceQueryService).listDetailsForExport(query, context);
    }

    @Test
    void exportXlsx_shouldKeepBlankStringsAndZeroAmountsForNullValues() throws Exception {
        PerformanceExportService service = new PerformanceExportService(performanceQueryService);
        PerformanceListQuery query = new PerformanceListQuery();
        PerformanceAccessContext context = PerformanceAccessContext.of(
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of("admin"));
        PerformanceDetailDTO detail = new PerformanceDetailDTO();
        detail.setOrderId("ORDER-EMPTY");
        when(performanceQueryService.listDetailsForExport(query, context)).thenReturn(List.of(detail));

        byte[] bytes = service.exportXlsx(query, context);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var row = workbook.getSheet("业绩明细").getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("ORDER-EMPTY");
            assertThat(row.getCell(2).getStringCellValue()).isEmpty();
            assertThat(row.getCell(14).getStringCellValue()).isEmpty();
            assertThat(row.getCell(16).getNumericCellValue()).isZero();
            assertThat(row.getCell(29).getNumericCellValue()).isZero();
        }
    }
}
