package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.LogisticsImportRow;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SampleLogisticsImportService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final int STATUS_PENDING_SHIP = 2;
    private static final int STATUS_SHIPPING = 3;

    private static final List<String> REQUIRED_HEADERS = List.of(
            "申请编号", "sampleNo", "sampleRequestId");

    private final SampleRequestMapper sampleRequestMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    public SampleLogisticsImportService(
            SampleRequestMapper sampleRequestMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleDomainEventPublisher sampleDomainEventPublisher) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("物流导入");
            Row header = sheet.createRow(0);
            String[] columns = {"申请编号", "商品ID", "达人账号", "物流公司", "物流单号", "备注"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("SM20260523EXAMPLE");
            example.createCell(3).setCellValue("SF");
            example.createCell(4).setCellValue("SF1234567890");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public LogisticsImportResult importTrackingNumbers(
            MultipartFile file,
            UUID currentUserId,
            Object roleCodes,
            boolean allowOverwrite) {
        ensureImportPermission(roleCodes, allowOverwrite);
        validateFile(file);
        List<LogisticsImportRow> rows = parseRows(file);
        if (rows.isEmpty()) {
            throw BusinessException.param("Excel 无有效数据行");
        }
        if (rows.size() > MAX_ROWS) {
            throw BusinessException.param("单次导入不能超过 " + MAX_ROWS + " 行");
        }

        List<LogisticsImportResult.LogisticsImportItemResult> items = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (LogisticsImportRow row : rows) {
            LogisticsImportResult.LogisticsImportItemResult item = applyRow(row, currentUserId, roleCodes, allowOverwrite);
            items.add(item);
            if (item.isSuccess()) {
                success++;
            } else {
                failed++;
            }
        }
        return LogisticsImportResult.builder()
                .total(rows.size())
                .successCount(success)
                .failedCount(failed)
                .items(items)
                .build();
    }

    List<LogisticsImportRow> parseRows(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw BusinessException.param("Excel 工作表为空");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.param("缺少表头行");
            }
            Map<String, Integer> headerIndex = readHeaderIndex(headerRow);
            validateHeaders(headerIndex);
            int sampleCol = resolveSampleColumn(headerIndex);
            int productCol = headerIndex.getOrDefault("商品id", headerIndex.getOrDefault("productid", -1));
            int talentCol = headerIndex.getOrDefault("达人账号", headerIndex.getOrDefault("talentaccount", -1));
            int companyCol = resolveRequiredColumn(headerIndex, "物流公司", "logisticscompany");
            int trackingCol = resolveRequiredColumn(headerIndex, "物流单号", "trackingno");
            int remarkCol = headerIndex.getOrDefault("备注", headerIndex.getOrDefault("remark", -1));

            DataFormatter formatter = new DataFormatter();
            List<LogisticsImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row, formatter)) {
                    continue;
                }
                rows.add(LogisticsImportRow.builder()
                        .rowNo(i + 1)
                        .sampleNo(readCell(row, sampleCol, formatter))
                        .sampleRequestId(readCell(row, sampleCol, formatter))
                        .productId(productCol >= 0 ? readCell(row, productCol, formatter) : null)
                        .talentAccount(talentCol >= 0 ? readCell(row, talentCol, formatter) : null)
                        .logisticsCompany(readCell(row, companyCol, formatter))
                        .trackingNo(readCell(row, trackingCol, formatter))
                        .remark(remarkCol >= 0 ? readCell(row, remarkCol, formatter) : null)
                        .build());
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.param("Excel 解析失败: " + ex.getMessage());
        }
    }

    LogisticsImportResult.LogisticsImportItemResult applyRow(
            LogisticsImportRow row,
            UUID currentUserId,
            Object roleCodes,
            boolean allowOverwrite) {
        String validationError = validateRow(row);
        if (validationError != null) {
            return itemResult(row, null, false, validationError);
        }
        SampleRequest sample = resolveSample(row.getSampleNo());
        if (sample == null) {
            return itemResult(row, null, false, "申请不存在");
        }
        if (!canOperateSample(sample, roleCodes)) {
            return itemResult(row, sample.getId(), false, "权限不足");
        }
        if (StringUtils.hasText(row.getProductId())) {
            String mismatch = validateOptionalProduct(sample, row.getProductId());
            if (mismatch != null) {
                return itemResult(row, sample.getId(), false, mismatch);
            }
        }
        if (StringUtils.hasText(row.getTalentAccount())) {
            String mismatch = validateOptionalTalent(sample, row.getTalentAccount());
            if (mismatch != null) {
                return itemResult(row, sample.getId(), false, mismatch);
            }
        }
        Integer status = sample.getStatus();
        if (status == null || (status != STATUS_PENDING_SHIP && status != STATUS_SHIPPING)) {
            return itemResult(row, sample.getId(), false, "申请状态不允许录入物流");
        }
        if (StringUtils.hasText(sample.getTrackingNo()) && !allowOverwrite) {
            return itemResult(row, sample.getId(), false, "已存在物流单号");
        }

        int fromStatus = sample.getStatus();
        LocalDateTime now = LocalDateTime.now();
        sample.setTrackingNo(row.getTrackingNo().trim());
        sample.setShipperCode(row.getLogisticsCompany().trim());
        sample.setStatus(STATUS_SHIPPING);
        sample.setShipTime(now);
        putExtraValue(sample, "logisticsSource", "EXCEL_IMPORT");
        if (StringUtils.hasText(row.getRemark())) {
            sample.setRemark(row.getRemark().trim());
        }
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
        sampleStatusLogService.log(sample.getId(), fromStatus, STATUS_SHIPPING, currentUserId,
                "Excel 批量导入物流: " + row.getTrackingNo());
        sampleDomainEventPublisher.publishSampleShipped(sample, currentUserId, now);
        return itemResult(row, sample.getId(), true, "OK");
    }

    private String validateRow(LogisticsImportRow row) {
        if (!StringUtils.hasText(row.getSampleNo())) {
            return "申请编号不能为空";
        }
        if (!StringUtils.hasText(row.getTrackingNo())) {
            return "物流单号不能为空";
        }
        if (!StringUtils.hasText(row.getLogisticsCompany())) {
            return "物流公司不能为空";
        }
        return null;
    }

    private SampleRequest resolveSample(String sampleKey) {
        if (!StringUtils.hasText(sampleKey)) {
            return null;
        }
        String key = sampleKey.trim();
        SampleRequest byNo = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, key)
                .last("LIMIT 1"));
        if (byNo != null) {
            return byNo;
        }
        try {
            UUID id = UUID.fromString(key);
            return sampleRequestMapper.selectById(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.param("请上传 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.param("文件大小不能超过 10MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase(Locale.ROOT).endsWith(".xlsx") || name.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw BusinessException.param("仅支持 .xlsx / .xls 文件");
        }
    }

    private void validateHeaders(Map<String, Integer> headerIndex) {
        boolean hasSample = headerIndex.containsKey("申请编号")
                || headerIndex.containsKey("sampleno")
                || headerIndex.containsKey("samplerequestid");
        if (!hasSample) {
            throw BusinessException.param("表头缺少申请编号列");
        }
        if (!headerIndex.containsKey("物流公司") && !headerIndex.containsKey("logisticscompany")) {
            throw BusinessException.param("表头缺少物流公司列");
        }
        if (!headerIndex.containsKey("物流单号") && !headerIndex.containsKey("trackingno")) {
            throw BusinessException.param("表头缺少物流单号列");
        }
    }

    private int resolveSampleColumn(Map<String, Integer> headerIndex) {
        if (headerIndex.containsKey("申请编号")) {
            return headerIndex.get("申请编号");
        }
        if (headerIndex.containsKey("sampleno")) {
            return headerIndex.get("sampleno");
        }
        return headerIndex.get("samplerequestid");
    }

    private int resolveRequiredColumn(Map<String, Integer> headerIndex, String cn, String en) {
        if (headerIndex.containsKey(cn)) {
            return headerIndex.get(cn);
        }
        return headerIndex.get(en);
    }

    private Map<String, Integer> readHeaderIndex(Row headerRow) {
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell);
            if (StringUtils.hasText(value)) {
                map.put(normalizeHeader(value), cell.getColumnIndex());
            }
        }
        return map;
    }

    private String normalizeHeader(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String readCell(Row row, int col, DataFormatter formatter) {
        if (col < 0) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (StringUtils.hasText(formatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private String validateOptionalProduct(SampleRequest sample, String productId) {
        if (sample.getProductId() == null || !StringUtils.hasText(productId)) {
            return null;
        }
        try {
            UUID importedProductId = UUID.fromString(productId.trim());
            if (!sample.getProductId().equals(importedProductId)) {
                return "商品ID与申请不匹配";
            }
            return null;
        } catch (IllegalArgumentException ex) {
            return "商品ID格式不正确";
        }
    }

    private String validateOptionalTalent(SampleRequest sample, String talentAccount) {
        if (sample.getTalentUid() != null && !sample.getTalentUid().equals(talentAccount.trim())) {
            return "达人账号与申请不匹配";
        }
        return null;
    }

    private void ensureImportPermission(Object roleCodes, boolean allowOverwrite) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅运营或管理员可导入物流单号");
        }
        if (allowOverwrite && !hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            throw new ForbiddenException("仅管理员可覆盖已有物流单号");
        }
    }

    private boolean canOperateSample(SampleRequest sample, Object roleCodes) {
        return hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
    }

    private boolean hasAnyRole(Object roleCodes, String... expected) {
        if (roleCodes == null) {
            return false;
        }
        Set<String> targets = Set.of(expected);
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString())
                    .anyMatch(targets::contains);
        }
        String raw = roleCodes.toString();
        for (String role : raw.replace("[", "").replace("]", "").split(",")) {
            if (targets.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private void putExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    private LogisticsImportResult.LogisticsImportItemResult itemResult(
            LogisticsImportRow row, UUID sampleId, boolean success, String message) {
        return LogisticsImportResult.LogisticsImportItemResult.builder()
                .rowNo(row.getRowNo())
                .sampleRequestId(sampleId)
                .sampleNo(row.getSampleNo())
                .success(success)
                .message(message)
                .build();
    }
}
