package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Kuaidi100LogisticsGatewayTest {

    @Test
    void queryTrack_postsCustomerAndParamFormThenMapsSignedTrace() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ObjectMapper objectMapper = new ObjectMapper();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                config("https://sandbox.test/kuaidi100"),
                objectMapper
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    Map<String, String> form = decodeForm(httpRequest.getBodyAsString(StandardCharsets.UTF_8));
                    assertThat(form).containsEntry("customer", "test-customer");
                    assertThat(objectMapper.readValue(form.get("param"), Map.class))
                            .containsEntry("com", "SF")
                            .containsEntry("num", "118650888018")
                            .containsEntry("from", "")
                            .containsEntry("to", "");
                })
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "message": "ok",
                          "com": "SF",
                          "nu": "118650888018",
                          "condition": "F00",
                          "ischeck": "1",
                          "data": [
                            {
                              "time": "2026-05-14 10:23:03",
                              "context": "派件已签收",
                              "location": "深圳"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("SF", "118650888018");

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("SF");
        assertThat(result.trackingNo()).isEqualTo("118650888018");
        assertThat(result.externalState()).isEqualTo("F00");
        assertThat(result.internalStatus()).isEqualTo("SIGNED");
        assertThat(result.signed()).isTrue();
        assertThat(result.signedAt()).isEqualTo(LocalDateTime.of(2026, 5, 14, 10, 23, 3));
        assertThat(result.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.acceptStation()).isEqualTo("派件已签收");
            assertThat(trace.remark()).isEqualTo("深圳");
        });
        assertThat(result.rawResponse()).containsEntry("status", "200");
        server.verify();
    }

    @Test
    void queryTrack_mapsFailedResponseUsingRemoteIdentifiersWhenPresent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "400",
                          "message": "暂无轨迹",
                          "com": "YTO",
                          "nu": "YT001",
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("SF", "INPUT001");

        assertThat(result.success()).isFalse();
        assertThat(result.companyCode()).isEqualTo("YTO");
        assertThat(result.trackingNo()).isEqualTo("YT001");
        assertThat(result.reason()).isEqualTo("暂无轨迹");
        assertThat(result.internalStatus()).isEqualTo("FAILED");
        assertThat(result.traces()).isEmpty();
        assertThat(result.rawResponse()).containsEntry("message", "暂无轨迹");
        server.verify();
    }

    @Test
    void queryTrack_mapsConditionExceptionAndLeavesBadTraceTimeNull() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "ZTO",
                          "nu": "ZTO001",
                          "condition": "F01",
                          "ischeck": "0",
                          "data": [
                            {
                              "time": "bad-time",
                              "context": "包裹异常",
                              "location": "杭州"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("ZTO", "ZTO001");

        assertThat(result.success()).isTrue();
        assertThat(result.internalStatus()).isEqualTo("EXCEPTION");
        assertThat(result.signed()).isFalse();
        assertThat(result.signedAt()).isNull();
        assertThat(result.traces()).singleElement().satisfies(trace -> assertThat(trace.acceptTime()).isNull());
        server.verify();
    }

    @Test
    void queryTrack_fallsBackToLastTraceTextWhenCheckFlagMissing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "JD",
                          "nu": "JD001",
                          "condition": null,
                          "data": [
                            {
                              "time": "2026-05-14 09:00:00",
                              "context": "快件离开发货仓"
                            },
                            {
                              "time": "2026-05-15 18:45:10",
                              "context": "客户已取件"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("JD", "JD001");

        assertThat(result.internalStatus()).isEqualTo("SIGNED");
        assertThat(result.signed()).isTrue();
        assertThat(result.signedAt()).isEqualTo(LocalDateTime.of(2026, 5, 15, 18, 45, 10));
        server.verify();
    }

    @Test
    void queryTrack_returnsFailedResultForEmptyOrInvalidJsonResponse() {
        RestTemplate emptyTemplate = new RestTemplate();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyTemplate).build();
        Kuaidi100LogisticsGateway emptyGateway = new Kuaidi100LogisticsGateway(
                emptyTemplate,
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        emptyServer.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess(" ", MediaType.APPLICATION_JSON));

        var emptyResult = emptyGateway.queryTrack("SF", "EMPTY001");
        assertThat(emptyResult.success()).isFalse();
        assertThat(emptyResult.reason()).isEqualTo("快递100返回空响应");
        assertThat(emptyResult.rawResponse()).isEmpty();
        emptyServer.verify();

        RestTemplate invalidTemplate = new RestTemplate();
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidTemplate).build();
        Kuaidi100LogisticsGateway invalidGateway = new Kuaidi100LogisticsGateway(
                invalidTemplate,
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        invalidServer.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        var invalidResult = invalidGateway.queryTrack("SF", "BAD001");
        assertThat(invalidResult.success()).isFalse();
        assertThat(invalidResult.reason()).contains("解析响应失败");
        assertThat(invalidResult.internalStatus()).isEqualTo("FAILED");
        invalidServer.verify();
    }

    @Test
    void queryTrack_rejectsBlankInputsBeforeCallingRemoteApi() {
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> gateway.queryTrack("SF", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号不能为空");
        assertThatThrownBy(() -> gateway.queryTrack("", "118650888018"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递公司编码不能为空");
    }

    @Test
    void queryStatusAndCreateShipmentDeclareUnsupportedEntryPoints() {
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> gateway.queryStatus("118650888018"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请调用 queryTrack");
        assertThatThrownBy(() -> gateway.createShipment(null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("不支持创建发货");
    }

    @Test
    void queryTrack_wrapsRemoteTransportFailureAsBusinessException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                config("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.queryTrack("SF", "118650888018"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递100查询失败");
        server.verify();
    }

    private static Kuaidi100Config config(String requestUrl) {
        Kuaidi100Config config = new Kuaidi100Config();
        config.setRequestUrl(requestUrl);
        config.setCustomer("test-customer");
        config.setSecret("test-secret");
        return config;
    }

    private static Map<String, String> decodeForm(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }
}
