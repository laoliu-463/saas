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

/**
 * 业绩明细 Excel 导出服务。
 *
 * <p>将业绩查询结果导出为 .xlsx 格式的 Excel 文件，包含 30 列详细字段。
 * 所有金额字段自动从分转换为元。</p>
 *
 * <ul>
 *   <li>提供 {@link #exportXlsx} 将业绩明细导出为 Excel 字节数组</li>
 * </ul>
 *
 * <p><b>业务领域：</b>业绩域 — 数据导出</p>
 * <p><b>协作关系：</b>依赖 {@link PerformanceQueryService} 获取导出数据</p>
 *
 * @see PerformanceQueryService
 * @see PerformanceDetailDTO
 */
@Service
public class PerformanceExportService {

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

    /** 业绩查询服务，提供导出所需的数据列表 */
    private final PerformanceQueryService performanceQueryService;

    public PerformanceExportService(PerformanceQueryService performanceQueryService) {
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
    public byte[] exportXlsx(PerformanceListQuery query, com.colonel.saas.domain.performance.policy.PerformanceAccessContext context)
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
     *
     * @param value 原始字符串
     * @return 非 null 时原样返回，null 时返回空字符串
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将分（cent）转换为元（yuan），用于 Excel 中金额列的展示。
     *
     * @param value 金额值（单位：分）
     * @return 转换后的元金额，null 视为 0
     */
    private double cent(Long value) {
        return value == null ? 0D : value / 100.0;
    }

    /**
     * 格式化时间为字符串，null 时返回空字符串。
     *
     * @param time 待格式化的时间
     * @return 格式化后的时间字符串（yyyy-MM-dd HH:mm:ss），null 时返回空字符串
     */
    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? "" : DT.format(time);
    }
}
