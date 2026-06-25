package com.colonel.saas.service;

import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.domain.sample.policy.SampleActionPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.LogisticsImportRow;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsImportServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private SampleStatusLogService sampleStatusLogService;
    @Mock private SampleDomainEventPublisher sampleDomainEventPublisher;
    @Mock private SampleLogisticsSubscriptionService sampleLogisticsSubscriptionService;

    private SampleLogisticsImportService service;

    @BeforeEach
    void setUp() {
        service = new SampleLogisticsImportService(
                sampleRequestMapper, sampleStatusLogService,
                sampleDomainEventPublisher,
                sampleLogisticsSubscriptionService,
                new SampleActionPermissionPolicy(new CurrentUserPermissionPolicy()));
    }

    @Test
    void generateTemplate_shouldReturnNonEmptyBytes() throws Exception {
        byte[] bytes = service.generateTemplate();
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void import_validRow_shouldSucceed() throws Exception {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SM20260523001");
        sample.setStatus(2);
        sample.setVersion(0);
        when(sampleRequestMapper.selectOne(any())).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook("SM20260523001", "SF", "SF1234567890"));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(RoleCodes.OPS_STAFF), false);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        org.mockito.Mockito.verify(sampleLogisticsSubscriptionService).subscribeAfterShipment(sample);
    }

    @Test
    void import_missingSample_shouldFailRow() throws Exception {
        when(sampleRequestMapper.selectOne(any())).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook("NOT-EXIST", "SF", "SF999"));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(RoleCodes.OPS_STAFF), false);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMessage()).contains("申请不存在");
    }

    @Test
    void import_existingTrackingNo_shouldRejectWithoutOverwrite() throws Exception {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SM20260523002");
        sample.setStatus(2);
        sample.setTrackingNo("OLD-TRACK");
        sample.setVersion(0);
        when(sampleRequestMapper.selectOne(any())).thenReturn(sample);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook("SM20260523002", "SF", "SF1234567890"));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(RoleCodes.OPS_STAFF), false);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMessage()).contains("已存在物流单号");
    }

    @Test
    void import_productIdMismatch_shouldFailRow() throws Exception {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SM20260523003");
        sample.setProductId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        sample.setStatus(2);
        sample.setVersion(0);
        when(sampleRequestMapper.selectOne(any())).thenReturn(sample);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook("SM20260523003", "22222222-2222-2222-2222-222222222222", "", "SF", "SF1234567890"));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(RoleCodes.OPS_STAFF), false);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMessage()).contains("商品ID与申请不匹配");
    }

    @Test
    void import_shouldRejectUnauthorizedRolesAndOverwriteByNonAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook("SM20260523004", "SF", "SF1234567890"));

        assertThatThrownBy(() -> service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of("TALENT"), false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅运营或管理员可导入物流单号");

        assertThatThrownBy(() -> service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(RoleCodes.OPS_STAFF), true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员可覆盖已有物流单号");
    }

    @Test
    void import_shouldNormalizeRoleCodesViaSamplePermissionPolicy() throws Exception {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SM20260523014");
        sample.setStatus(2);
        sample.setVersion(0);
        when(sampleRequestMapper.selectOne(any())).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook("SM20260523014", "SF", "SF1234567890"));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(" OPS_STAFF "), false);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
    }

    @Test
    void import_shouldRejectInvalidFilesBeforeParsingRows() {
        assertThatThrownBy(() -> service.importTrackingNumbers(
                null, UUID.randomUUID(), List.of(RoleCodes.ADMIN), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 Excel 文件");

        MockMultipartFile empty = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        assertThatThrownBy(() -> service.importTrackingNumbers(
                empty, UUID.randomUUID(), List.of(RoleCodes.ADMIN), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 Excel 文件");

        MockMultipartFile txt = new MockMultipartFile("file", "import.txt", "text/plain", "bad".getBytes());
        assertThatThrownBy(() -> service.importTrackingNumbers(
                txt, UUID.randomUUID(), List.of(RoleCodes.ADMIN), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 .xlsx / .xls 文件");

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.importTrackingNumbers(
                oversized, UUID.randomUUID(), List.of(RoleCodes.ADMIN), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小不能超过 10MB");
    }

    @Test
    void import_shouldReturnRowFailuresForRequiredCellValidation() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook(
                        List.of("申请编号", "物流公司", "物流单号"),
                        List.of(
                                List.of("", "SF", "SF001"),
                                List.of("SM20260523005", "SF", ""),
                                List.of("SM20260523006", "", "SF003"))));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), List.of(RoleCodes.ADMIN), false);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(3);
        assertThat(result.getItems()).extracting(LogisticsImportResult.LogisticsImportItemResult::getMessage)
                .containsExactly("申请编号不能为空", "物流单号不能为空", "物流公司不能为空");
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void import_shouldAcceptEnglishHeadersSkipEmptyRowsAndResolveById() throws Exception {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setRequestNo("SM20260523007");
        sample.setStatus(2);
        sample.setVersion(0);
        when(sampleRequestMapper.selectOne(any())).thenReturn(null);
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook(
                        List.of("sampleRequestId", "productId", "talentAccount", "logisticsCompany", "trackingNo", "remark"),
                        List.of(
                                List.of("", "", "", "", "", ""),
                                List.of(sampleId.toString(), "", "", "shunfeng", "SF1234567890", " urgent ")) ));

        LogisticsImportResult result = service.importTrackingNumbers(
                file, UUID.randomUUID(), "[" + RoleCodes.OPS_STAFF + "]", false);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(sample.getShipperCode()).isEqualTo("shunfeng");
        assertThat(sample.getTrackingNo()).isEqualTo("SF1234567890");
        assertThat(sample.getRemark()).isEqualTo("urgent");
        verify(sampleLogisticsSubscriptionService).subscribeAfterShipment(sample);
    }

    @Test
    void parseRows_shouldRejectEmptyWorkbookMissingHeaderAndMissingRequiredHeaders() throws Exception {
        MockMultipartFile noSheet = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildWorkbookWithoutSheets());
        assertThatThrownBy(() -> service.parseRows(noSheet))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Excel 工作表为空");

        MockMultipartFile noHeader = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildWorkbookWithNoHeader());
        assertThatThrownBy(() -> service.parseRows(noHeader))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少表头行");

        MockMultipartFile missingSample = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook(List.of("物流公司", "物流单号"), List.of(List.of("SF", "SF001"))));
        assertThatThrownBy(() -> service.parseRows(missingSample))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("表头缺少申请编号列");

        MockMultipartFile missingCompany = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook(List.of("申请编号", "物流单号"), List.of(List.of("SM1", "SF001"))));
        assertThatThrownBy(() -> service.parseRows(missingCompany))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("表头缺少物流公司列");

        MockMultipartFile missingTracking = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook(List.of("申请编号", "物流公司"), List.of(List.of("SM1", "SF"))));
        assertThatThrownBy(() -> service.parseRows(missingTracking))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("表头缺少物流单号列");
    }

    @Test
    void applyRow_shouldRejectOptionalMismatchInvalidStatusAndTalentMismatch() {
        SampleRequest productSample = sample("SM20260523008", 2);
        productSample.setProductId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        SampleRequest talentSample = sample("SM20260523009", 2);
        talentSample.setTalentUid("talent-a");
        SampleRequest statusNullSample = sample("SM20260523010", null);
        SampleRequest completedSample = sample("SM20260523011", 5);
        when(sampleRequestMapper.selectOne(any()))
                .thenReturn(productSample, talentSample, statusNullSample, completedSample);

        LogisticsImportResult.LogisticsImportItemResult productResult = service.applyRow(
                row("SM20260523008", "bad-uuid", null, "SF", "SF001", null),
                UUID.randomUUID(), List.of(RoleCodes.ADMIN), false);
        LogisticsImportResult.LogisticsImportItemResult talentResult = service.applyRow(
                row("SM20260523009", null, "talent-b", "SF", "SF002", null),
                UUID.randomUUID(), List.of(RoleCodes.ADMIN), false);
        LogisticsImportResult.LogisticsImportItemResult nullStatusResult = service.applyRow(
                row("SM20260523010", null, null, "SF", "SF003", null),
                UUID.randomUUID(), List.of(RoleCodes.ADMIN), false);
        LogisticsImportResult.LogisticsImportItemResult completedResult = service.applyRow(
                row("SM20260523011", null, null, "SF", "SF004", null),
                UUID.randomUUID(), List.of(RoleCodes.ADMIN), false);

        assertThat(productResult.getMessage()).isEqualTo("商品ID格式不正确");
        assertThat(talentResult.getMessage()).isEqualTo("达人账号与申请不匹配");
        assertThat(nullStatusResult.getMessage()).isEqualTo("申请状态不允许录入物流");
        assertThat(completedResult.getMessage()).isEqualTo("申请状态不允许录入物流");
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void applyRow_shouldOverwriteExistingTrackingWhenAdminAndKeepExtraData() {
        UUID productId = UUID.randomUUID();
        SampleRequest sample = sample("SM20260523012", 3);
        sample.setProductId(productId);
        sample.setTalentUid("talent-a");
        sample.setTrackingNo("OLD-TRACK");
        sample.setExtraData(new LinkedHashMap<>(Map.of("old", "value")));
        when(sampleRequestMapper.selectOne(any())).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);
        UUID operatorId = UUID.randomUUID();

        LogisticsImportResult.LogisticsImportItemResult result = service.applyRow(
                row("SM20260523012", productId.toString(), "talent-a", " yunda ", " YD001 ", " memo "),
                operatorId, List.of(RoleCodes.ADMIN), true);

        assertThat(result.isSuccess()).isTrue();
        assertThat(sample.getTrackingNo()).isEqualTo("YD001");
        assertThat(sample.getShipperCode()).isEqualTo("yunda");
        assertThat(sample.getRemark()).isEqualTo("memo");
        assertThat(sample.getExtraData()).containsEntry("old", "value").containsEntry("logisticsSource", "EXCEL_IMPORT");
        verify(sampleStatusLogService).log(sample.getId(), 3, 3, operatorId, "Excel 批量导入物流:  YD001 ");
        verify(sampleDomainEventPublisher).publishSampleShipped(any(), any(), any());
        verify(sampleLogisticsSubscriptionService).subscribeAfterShipment(sample);
    }

    @Test
    void applyRow_shouldThrowWhenOptimisticUpdateFails() {
        SampleRequest sample = sample("SM20260523013", 2);
        when(sampleRequestMapper.selectOne(any())).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(0);

        assertThatThrownBy(() -> service.applyRow(
                row("SM20260523013", null, null, "SF", "SF001", null),
                UUID.randomUUID(), List.of(RoleCodes.ADMIN), false))
                .isInstanceOf(BusinessException.class);
    }

    private byte[] buildWorkbook(String requestNo, String company, String trackingNo) throws Exception {
        return buildWorkbook(requestNo, "", "", company, trackingNo);
    }

    private byte[] buildWorkbook(String requestNo, String productId, String talentAccount, String company, String trackingNo) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("申请编号");
            header.createCell(1).setCellValue("商品ID");
            header.createCell(2).setCellValue("达人账号");
            header.createCell(3).setCellValue("物流公司");
            header.createCell(4).setCellValue("物流单号");
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue(requestNo);
            row.createCell(1).setCellValue(productId);
            row.createCell(2).setCellValue(talentAccount);
            row.createCell(3).setCellValue(company);
            row.createCell(4).setCellValue(trackingNo);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildWorkbook(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                XSSFRow row = sheet.createRow(rowIndex + 1);
                List<String> values = rows.get(rowIndex);
                for (int col = 0; col < values.size(); col++) {
                    String value = values.get(col);
                    if (value != null) {
                        row.createCell(col).setCellValue(value);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildWorkbookWithoutSheets() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildWorkbookWithNoHeader() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private SampleRequest sample(String requestNo, Integer status) {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo(requestNo);
        sample.setStatus(status);
        sample.setVersion(0);
        return sample;
    }

    private LogisticsImportRow row(
            String sampleNo,
            String productId,
            String talentAccount,
            String company,
            String trackingNo,
            String remark) {
        return LogisticsImportRow.builder()
                .rowNo(2)
                .sampleNo(sampleNo)
                .sampleRequestId(sampleNo)
                .productId(productId)
                .talentAccount(talentAccount)
                .logisticsCompany(company)
                .trackingNo(trackingNo)
                .remark(remark)
                .build();
    }
}
