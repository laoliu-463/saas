package com.colonel.saas.service;

import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.LogisticsImportResult;
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

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsImportServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private SampleStatusLogService sampleStatusLogService;

    private SampleLogisticsImportService service;

    @BeforeEach
    void setUp() {
        service = new SampleLogisticsImportService(
                sampleRequestMapper, sampleStatusLogService,
                org.mockito.Mockito.mock(com.colonel.saas.domain.sample.event.SampleDomainEventPublisher.class));
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
}
