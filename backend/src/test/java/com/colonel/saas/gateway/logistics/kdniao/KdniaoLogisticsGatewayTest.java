package com.colonel.saas.gateway.logistics.kdniao;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * KdniaoLogisticsGateway 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KdniaoLogisticsGatewayTest {

    @Mock
    private RestTemplate kdniaoRestTemplate;

    @Mock
    private KdniaoConfig kdniaoConfig;

    private ObjectMapper objectMapper;
    private KdniaoLogisticsGateway gateway;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        gateway = new KdniaoLogisticsGateway(kdniaoRestTemplate, kdniaoConfig, objectMapper);
    }

    @Nested
    @DisplayName("queryTrack")
    class QueryTrackTest {

        @Test
        void shouldThrowWhenTrackingNoIsNull() {
            assertThatThrownBy(() -> gateway.queryTrack("SF", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("物流单号不能为空");
        }

        @Test
        void shouldThrowWhenTrackingNoIsBlank() {
            assertThatThrownBy(() -> gateway.queryTrack("SF", "  "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("物流单号不能为空");
        }

        @Test
        void shouldThrowWhenCompanyCodeIsNull() {
            assertThatThrownBy(() -> gateway.queryTrack(null, "SF1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("快递公司编码不能为空");
        }

        @Test
        void shouldThrowWhenCompanyCodeIsBlank() {
            assertThatThrownBy(() -> gateway.queryTrack("  ", "SF1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("快递公司编码不能为空");
        }

        @Test
        void shouldReturnSuccessResultForSignedPackage() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "OrderCode": "",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "3",
                    "Reason": "",
                    "Traces": [
                        {"AcceptTime": "2026/05/15 08:00:00", "AcceptStation": "【深圳市】已发出", "Remark": ""},
                        {"AcceptTime": "2026/05/16 14:30:00", "AcceptStation": "【北京市】已签收", "Remark": "签收"}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.success()).isTrue();
            assertThat(result.internalStatus()).isEqualTo("SIGNED");
            assertThat(result.signed()).isTrue();
            assertThat(result.companyCode()).isEqualTo("SF");
            assertThat(result.trackingNo()).isEqualTo("SF1234567890");
            assertThat(result.traces()).hasSize(2);
        }

        @Test
        void shouldReturnInTransitResult() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "ZTO",
                    "LogisticCode": "ZTO1234567890",
                    "Success": true,
                    "State": "2",
                    "Traces": [
                        {"AcceptTime": "2026/05/15 10:00:00", "AcceptStation": "【中转站】运输中", "Remark": ""}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("ZTO", "ZTO1234567890");

            assertThat(result.success()).isTrue();
            assertThat(result.internalStatus()).isEqualTo("IN_TRANSIT");
            assertThat(result.signed()).isFalse();
            assertThat(result.signedAt()).isNull();
        }

        @Test
        void shouldReturnExceptionResult() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "YTO",
                    "LogisticCode": "YTO1234567890",
                    "Success": true,
                    "State": "4",
                    "Reason": "问题件：地址不详",
                    "Traces": []
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("YTO", "YTO1234567890");

            assertThat(result.success()).isTrue();
            assertThat(result.internalStatus()).isEqualTo("EXCEPTION");
            assertThat(result.signed()).isFalse();
        }

        @Test
        void shouldReturnFailedResultWhenSuccessIsFalse() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "Success": false,
                    "Reason": "暂无物流信息"
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.success()).isFalse();
            assertThat(result.internalStatus()).isEqualTo("FAILED");
            assertThat(result.reason()).isEqualTo("暂无物流信息");
        }

        @Test
        void shouldThrowWhenResponseIsNull() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> gateway.queryTrack("SF", "SF1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("解析物流查询响应失败");
        }

        @Test
        void shouldThrowWhenApiReturnsInvalidJson() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn("invalid json");

            assertThatThrownBy(() -> gateway.queryTrack("SF", "SF1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("解析物流查询响应失败");
        }

        @Test
        void shouldThrowWhenRestTemplateThrowsException() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> gateway.queryTrack("SF", "SF1234567890"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("快递鸟API请求失败");
        }

        @Test
        void shouldHandleTraceWithDashDateFormat() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "2",
                    "Traces": [
                        {"AcceptTime": "2026-05-15 10:00:00", "AcceptStation": "【中转站】运输中", "Remark": ""}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.success()).isTrue();
            assertThat(result.traces()).hasSize(1);
        }

        @Test
        void shouldHandleEmptyTraces() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "2",
                    "Traces": []
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.success()).isTrue();
            assertThat(result.traces()).isEmpty();
        }

        @Test
        void shouldHandleNullTraces() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "2"
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.success()).isTrue();
            assertThat(result.traces()).isEmpty();
        }

        @Test
        void shouldUseResponseCompanyCodeWhenAvailable() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "2",
                    "Traces": []
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            // 请求时使用小写，但响应中返回的是大写
            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("sf", "SF1234567890");

            assertThat(result.companyCode()).isEqualTo("SF");
        }
    }

    @Nested
    @DisplayName("queryStatus")
    class QueryStatusTest {

        @Test
        void shouldReturnStatusResult() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "3",
                    "Traces": [
                        {"AcceptTime": "2026/05/16 14:30:00", "AcceptStation": "【北京市】已签收", "Remark": "签收"}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsStatusResult result = gateway.queryStatus("SF1234567890", "SF");

            assertThat(result.trackingNo()).isEqualTo("SF1234567890");
            assertThat(result.company()).isEqualTo("SF");
            assertThat(result.status()).isEqualTo("SIGNED");
        }
    }

    @Nested
    @DisplayName("createShipment")
    class CreateShipmentTest {

        @Test
        void shouldThrowUnsupportedOperationException() {
            LogisticsGateway.LogisticsCommand command = new LogisticsGateway.LogisticsCommand(
                    java.util.UUID.randomUUID(),
                    "P001",
                    "张三",
                    "13800138000",
                    "北京市朝阳区xxx"
            );

            assertThatThrownBy(() -> gateway.createShipment(command))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("快递鸟即时查询API不支持创建发货");
        }
    }

    @Nested
    @DisplayName("LogisticsTraceNode handling")
    class LogisticsTraceNodeTest {

        @Test
        void shouldFilterNullTraces() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "2",
                    "Traces": [
                        null,
                        {"AcceptTime": "2026/05/15 10:00:00", "AcceptStation": "【中转站】运输中", "Remark": ""},
                        null
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.traces()).hasSize(1);
            assertThat(result.traces().get(0).acceptStation()).isEqualTo("【中转站】运输中");
        }

        @Test
        void shouldResolveSignedAtFromTraces() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "3",
                    "Traces": [
                        {"AcceptTime": "2026/05/15 08:00:00", "AcceptStation": "【深圳市】已发出", "Remark": ""},
                        {"AcceptTime": "2026/05/15 12:00:00", "AcceptStation": "【中转站】分拨", "Remark": ""},
                        {"AcceptTime": "2026/05/16 14:30:00", "AcceptStation": "【北京市】已签收，签收人：本人", "Remark": "签收"}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.signed()).isTrue();
            assertThat(result.signedAt()).isNotNull();
        }

        @Test
        void shouldUseLastTraceTimeWhenNoSignedTrace() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "3",
                    "Traces": [
                        {"AcceptTime": "2026/05/15 08:00:00", "AcceptStation": "【深圳市】已发出", "Remark": ""},
                        {"AcceptTime": "2026/05/16 14:30:00", "AcceptStation": "【北京市】已签收", "Remark": ""}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.signed()).isTrue();
            assertThat(result.signedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Status message building")
    class StatusMessageTest {

        @Test
        void shouldBuildStatusMessageForInTransit() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "2",
                    "Traces": [
                        {"AcceptTime": "2026/05/15 10:00:00", "AcceptStation": "【深圳市】已发出", "Remark": ""}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.internalStatus()).isEqualTo("IN_TRANSIT");
        }

        @Test
        void shouldBuildStatusMessageForSigned() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "3",
                    "Traces": [
                        {"AcceptTime": "2026/05/16 14:30:00", "AcceptStation": "【北京市】已签收", "Remark": "签收"}
                    ]
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.internalStatus()).isEqualTo("SIGNED");
        }

        @Test
        void shouldBuildStatusMessageForException() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "YTO",
                    "LogisticCode": "YTO1234567890",
                    "Success": true,
                    "State": "4",
                    "Traces": []
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("YTO", "YTO1234567890");

            assertThat(result.internalStatus()).isEqualTo("EXCEPTION");
        }

        @Test
        void shouldReturnUnknownForUnmappedState() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "State": "0",
                    "Traces": []
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.internalStatus()).isEqualTo("UNKNOWN");
        }

        @Test
        void shouldReturnUnknownForNullState() throws Exception {
            String apiUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
            when(kdniaoConfig.getRequestUrl()).thenReturn(apiUrl);
            when(kdniaoConfig.getEBusinessId()).thenReturn("TEST_EB_ID");
            when(kdniaoConfig.getAppKey()).thenReturn("TEST_APP_KEY");
            when(kdniaoConfig.getRequestType()).thenReturn("1002");

            String responseJson = """
                {
                    "EBusinessID": "TEST_EB_ID",
                    "ShipperCode": "SF",
                    "LogisticCode": "SF1234567890",
                    "Success": true,
                    "Traces": []
                }
                """;
            when(kdniaoRestTemplate.postForObject(eq(apiUrl), any(), eq(String.class)))
                    .thenReturn(responseJson);

            LogisticsGateway.LogisticsTrackResult result = gateway.queryTrack("SF", "SF1234567890");

            assertThat(result.internalStatus()).isEqualTo("UNKNOWN");
        }
    }
}
