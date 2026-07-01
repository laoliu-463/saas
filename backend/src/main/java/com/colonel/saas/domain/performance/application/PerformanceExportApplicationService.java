package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.service.PerformanceQueryService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 业绩明细 Excel 导出应用服务（DDD-PERFORMANCE Slice 6）。
 *
 * <p>从 {@code service.PerformanceExportService} 整体迁移过来的 Excel 导出逻辑：
 * <ul>
 *   <li>{@link #exportXlsx} - 将业绩明细导出为 .xlsx 字节数组（30 列详细字段，
 *       金额字段自动从分转换为元）</li>
 * </ul>
 *
 * <p>本类是 performance 域数据导出的独立入口。承接原 Service 的所有 private helper
 * （null 安全 / 分转元 / 时间格式化 / HEADERS 常量）。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link PerformanceQueryService} —— 业绩查询服务（提供导出明细数据）</li>
 *   <li>Apache POI —— .xlsx 写入</li>
 * </ul>
 */
@Service
public class PerformanceExportApplicationService {

    /** 日期时间格式化器，用于导出中的时间列 */
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Excel 表头列名，共 30 列 */
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

    public PerformanceExportApplicationService(PerformanceQueryService performanceQueryService) {
        this.performanceQueryService = performanceQueryService;
    }

    /**
     * 将业绩明细导出为 Excel (.xlsx) 字节数组。
     *
     * <ol>
     *   <li>第一步：通过 {@link PerformanceQueryService#listDetailsForExport} 获取带权限的明细数据</li>
     *   <li>第二步：创建工作簿和工作表，写入 30 列表头</li>
     *   <li>第三步：遍历每条记录，逐列写入：文本字段直接写入，时间字段格式化，
     *       金额字段从分转换为元（{@link #cent}）</li>
     *   <li>第四步：将工作簿写入字节流并返回</li>
     * </ol>
     *
     * @param query   查询条件（时间范围、筛选条件等）
     * @param context 访问权限上下文，决定数据可见范围
     * @return Excel 文件的字节数组
     * @throws IOException 写入字节流失败时抛出
     */
    public byte[] exportXlsx(PerformanceListQuery query, PerformanceAccessContext context)
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

    /**
     * null 安全的字符串转换，null 转为空字符串。
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将分（cent）转换为元（yuan），用于 Excel 中金额列的展示。
     */
    private double cent(Long value) {
        return value == null ? 0D : value / 100.0;
    }

    /**
     * 格式化时间为字符串，null 时返回空字符串。
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? "" : DT.format(time);
    }
}