package com.colonel.saas.service;

import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PerformanceExportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] HEADERS = {
            "订单ID", "商品ID", "商品名称", "活动ID", "活动名称", "合作方", "达人",
            "默认渠道", "最终渠道", "默认招商", "最终招商",
            "渠道归因", "招商归因", "订单状态", "付款时间", "结算时间",
            "成交金额", "结算金额",
            "预估服务费", "结算服务费", "预估技术服务费", "结算技术服务费",
            "预估服务费收益", "结算服务费收益",
            "预估招商提成", "结算招商提成", "预估渠道提成", "结算渠道提成",
            "预估毛利", "结算毛利"
    };

    private final PerformanceQueryService performanceQueryService;

    public PerformanceExportService(PerformanceQueryService performanceQueryService) {
        this.performanceQueryService = performanceQueryService;
    }

    public byte[] exportXlsx(PerformanceListQuery query, com.colonel.saas.service.performance.PerformanceAccessContext context)
            throws IOException {
        List<PerformanceDetailDTO> rows = performanceQueryService.listDetailsForExport(query, context);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("业绩明细");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIndex = 1;
            for (PerformanceDetailDTO item : rows) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(nvl(item.getOrderId()));
                row.createCell(col++).setCellValue(nvl(item.getProductId()));
                row.createCell(col++).setCellValue(nvl(item.getProductName()));
                row.createCell(col++).setCellValue(nvl(item.getActivityId()));
                row.createCell(col++).setCellValue(nvl(item.getActivityName()));
                row.createCell(col++).setCellValue(nvl(item.getPartnerName()));
                row.createCell(col++).setCellValue(nvl(item.getTalentName()));
                row.createCell(col++).setCellValue(nvl(item.getDefaultChannelName()));
                row.createCell(col++).setCellValue(nvl(item.getFinalChannelName()));
                row.createCell(col++).setCellValue(nvl(item.getDefaultRecruiterName()));
                row.createCell(col++).setCellValue(nvl(item.getFinalRecruiterName()));
                row.createCell(col++).setCellValue(nvl(item.getChannelAttributionType()));
                row.createCell(col++).setCellValue(nvl(item.getRecruiterAttributionType()));
                row.createCell(col++).setCellValue(nvl(item.getOrderStatus()));
                row.createCell(col++).setCellValue(formatTime(item.getPayTime()));
                row.createCell(col++).setCellValue(formatTime(item.getSettleTime()));
                row.createCell(col++).setCellValue(cent(item.getPayAmount()));
                row.createCell(col++).setCellValue(cent(item.getSettleAmount()));
                row.createCell(col++).setCellValue(cent(item.getEstimateServiceFee()));
                row.createCell(col++).setCellValue(cent(item.getEffectiveServiceFee()));
                row.createCell(col++).setCellValue(cent(item.getEstimateTechServiceFee()));
                row.createCell(col++).setCellValue(cent(item.getEffectiveTechServiceFee()));
                row.createCell(col++).setCellValue(cent(item.getEstimateServiceProfit()));
                row.createCell(col++).setCellValue(cent(item.getEffectiveServiceProfit()));
                row.createCell(col++).setCellValue(cent(item.getEstimateRecruiterCommission()));
                row.createCell(col++).setCellValue(cent(item.getEffectiveRecruiterCommission()));
                row.createCell(col++).setCellValue(cent(item.getEstimateChannelCommission()));
                row.createCell(col++).setCellValue(cent(item.getEffectiveChannelCommission()));
                row.createCell(col++).setCellValue(cent(item.getEstimateGrossProfit()));
                row.createCell(col++).setCellValue(cent(item.getEffectiveGrossProfit()));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private double cent(Long value) {
        return value == null ? 0D : value / 100.0;
    }

    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? "" : DT.format(time);
    }
}
