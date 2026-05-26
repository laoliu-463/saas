package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeCommand;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    void subscribeTrack_postsSchemaAndParamFormThenTreatsDuplicateAsSubscribed() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ObjectMapper objectMapper = new ObjectMapper();
        LogisticsProperties properties = properties("https://sandbox.test/query");
        properties.getKd100().setSubscribeEndpoint("https://sandbox.test/poll");
        properties.getKd100().setCallbackUrl("https://saas.test/api/public/logistics/kuaidi100/callback");
        properties.getKd100().setCallbackSalt("callback-salt");
        properties.getKd100().setResultV2("4");
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(restTemplate, properties, objectMapper);

        server.expect(requestTo("https://sandbox.test/poll"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    Map<String, String> form = decodeForm(httpRequest.getBodyAsString(StandardCharsets.UTF_8));
                    assertThat(form).containsEntry("schema", "json");
                    assertThat(form).containsKey("param");
                    Map<String, Object> param = objectMapper.readValue(form.get("param"), Map.class);
                    assertThat(param)
                            .containsEntry("company", "shunfeng")
                            .containsEntry("number", "SF123456789")
                            .containsEntry("key", "test-key");
                    assertThat((Map<String, Object>) param.get("parameters"))
                            .containsEntry("callbackurl", "https://saas.test/api/public/logistics/kuaidi100/callback")
                            .containsEntry("salt", "callback-salt")
                            .containsEntry("resultv2", "4")
                            .containsEntry("phone", "13800138000")
                            .containsEntry("from", "广东省深圳市南山区")
                            .containsEntry("to", "北京市朝阳区");
                })
                .andRespond(withSuccess("""
                        {
                          "result": false,
                          "returnCode": "501",
                          "message": "重复订阅"
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("SF")
                .trackingNo("SF123456789")
                .phone("13800138000")
                .from("广东省深圳市南山区")
                .to("北京市朝阳区")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReturnCode()).isEqualTo("501");
        assertThat(result.getMessage()).isEqualTo("重复订阅");
        assertThat(result.getRawResponse()).containsEntry("returnCode", "501");
        server.verify();
    }

    @Test
    void subscribeTrack_rejectsInvalidInputsAndIncompleteConfigBeforeRemoteApi() {
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                properties("https://sandbox.test/query"),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> gateway.subscribeTrack(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号不能为空");
        assertThatThrownBy(() -> gateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("YTO")
                .trackingNo(" ")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号不能为空");
        assertThatThrownBy(() -> gateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("auto")
                .trackingNo("YT001")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递公司编码不能为空");
        assertThatThrownBy(() -> gateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("YTO")
                .trackingNo("YT001")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递100订阅配置不完整");

        LogisticsProperties noKeyProperties = properties("https://sandbox.test/query");
        noKeyProperties.getKd100().setKey(" ");
        noKeyProperties.getKd100().setCallbackUrl("https://saas.test/callback");
        noKeyProperties.getKd100().setCallbackSalt("callback-salt");
        Kuaidi100LogisticsGateway noKeyGateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                noKeyProperties,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> noKeyGateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("YTO")
                .trackingNo("YT001")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递100订阅配置不完整");

        LogisticsProperties noSaltProperties = properties("https://sandbox.test/query");
        noSaltProperties.getKd100().setCallbackUrl("https://saas.test/callback");
        noSaltProperties.getKd100().setCallbackSalt(" ");
        Kuaidi100LogisticsGateway noSaltGateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                noSaltProperties,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> noSaltGateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("YTO")
                .trackingNo("YT001")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递100订阅配置不完整");
    }

    @Test
    void subscribeTrack_defaultsResultV2AndMessageForAcceptedResponse() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ObjectMapper objectMapper = new ObjectMapper();
        LogisticsProperties properties = properties("https://sandbox.test/query");
        properties.getKd100().setSubscribeEndpoint("https://sandbox.test/poll");
        properties.getKd100().setCallbackUrl(" https://saas.test/api/public/logistics/kuaidi100/callback ");
        properties.getKd100().setCallbackSalt(" callback-salt ");
        properties.getKd100().setResultV2(" ");
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(restTemplate, properties, objectMapper);

        server.expect(requestTo("https://sandbox.test/poll"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    Map<String, String> form = decodeForm(httpRequest.getBodyAsString(StandardCharsets.UTF_8));
                    Map<String, Object> param = objectMapper.readValue(form.get("param"), Map.class);
                    assertThat(param)
                            .containsEntry("company", "yuantong")
                            .containsEntry("number", "YT001")
                            .containsEntry("key", "test-key");
                    assertThat((Map<String, Object>) param.get("parameters"))
                            .containsEntry("callbackurl", "https://saas.test/api/public/logistics/kuaidi100/callback")
                            .containsEntry("salt", "callback-salt")
                            .containsEntry("resultv2", "4")
                            .doesNotContainKeys("phone", "from", "to");
                })
                .andRespond(withSuccess("""
                        {
                          "result": true,
                          "returnCode": "200",
                          "message": ""
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("YTO")
                .trackingNo(" YT001 ")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCompanyCode()).isEqualTo("yuantong");
        assertThat(result.getTrackingNo()).isEqualTo("YT001");
        assertThat(result.getReturnCode()).isEqualTo("200");
        assertThat(result.getMessage()).isEqualTo("订阅成功");
        server.verify();
    }

    @Test
    void subscribeTrack_mapsEmptyInvalidAndRejectedResponses() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LogisticsProperties properties = properties("https://sandbox.test/query");
        properties.getKd100().setSubscribeEndpoint("https://sandbox.test/poll");
        properties.getKd100().setCallbackUrl("https://saas.test/api/public/logistics/kuaidi100/callback");
        properties.getKd100().setCallbackSalt("callback-salt");
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(restTemplate, properties, new ObjectMapper());
        LogisticsSubscribeCommand command = LogisticsSubscribeCommand.builder()
                .companyCode("STO")
                .trackingNo("ST001")
                .build();

        server.expect(requestTo("https://sandbox.test/poll"))
                .andRespond(withSuccess(" ", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sandbox.test/poll"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sandbox.test/poll"))
                .andRespond(withSuccess("""
                        {
                          "returnCode": "700",
                          "message": ""
                        }
                        """, MediaType.APPLICATION_JSON));

        var emptyResult = gateway.subscribeTrack(command);
        assertThat(emptyResult.isSuccess()).isFalse();
        assertThat(emptyResult.getReturnCode()).isEqualTo("EMPTY_RESPONSE");
        assertThat(emptyResult.getMessage()).isEqualTo("快递100返回空响应");
        assertThat(emptyResult.getRawResponse()).isEmpty();

        var invalidResult = gateway.subscribeTrack(command);
        assertThat(invalidResult.isSuccess()).isFalse();
        assertThat(invalidResult.getReturnCode()).isEqualTo("PARSE_ERROR");
        assertThat(invalidResult.getMessage()).contains("解析响应失败");

        var rejectedResult = gateway.subscribeTrack(command);
        assertThat(rejectedResult.isSuccess()).isFalse();
        assertThat(rejectedResult.getReturnCode()).isEqualTo("700");
        assertThat(rejectedResult.getMessage()).isEqualTo("订阅失败");
        assertThat(rejectedResult.getRawResponse()).containsEntry("returnCode", "700");
        server.verify();
    }

    @Test
    void subscribeTrack_wrapsRemoteTransportFailureAsBusinessException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LogisticsProperties properties = properties("https://sandbox.test/query");
        properties.getKd100().setSubscribeEndpoint("https://sandbox.test/poll");
        properties.getKd100().setCallbackUrl("https://saas.test/api/public/logistics/kuaidi100/callback");
        properties.getKd100().setCallbackSalt("callback-salt");
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(restTemplate, properties, new ObjectMapper());

        server.expect(requestTo("https://sandbox.test/poll"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                .companyCode("YTO")
                .trackingNo("YT001")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递100订阅失败");
        server.verify();
    }

    @Test
    void queryTrack_postsCustomerAndParamFormThenMapsSignedTrace() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ObjectMapper objectMapper = new ObjectMapper();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                objectMapper
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    Map<String, String> form = decodeForm(httpRequest.getBodyAsString(StandardCharsets.UTF_8));
                    assertThat(form).containsEntry("customer", "test-customer");
                    assertThat(form).containsKey("sign");
                    assertThat(form.get("sign")).isEqualTo(sign(form.get("param"), "test-key", "test-customer"));
                    assertThat(objectMapper.readValue(form.get("param"), Map.class))
                            .containsEntry("com", "shunfeng")
                            .containsEntry("num", "118650888018")
                            .containsEntry("phone", "13800138000")
                            .containsEntry("to", "广东省深圳市南山区")
                            .containsEntry("resultv2", "4")
                            .containsEntry("show", "0")
                            .containsEntry("order", "desc")
                            .containsEntry("lang", "zh");
                })
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "message": "ok",
                          "com": "shunfeng",
                          "nu": "118650888018",
                          "state": "3",
                          "data": [
                            {
                              "time": "2026-05-14 10:23:03",
                              "context": "派件已签收",
                              "location": "深圳"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("118650888018")
                .phone("13800138000")
                .to("广东省深圳市南山区")
                .build());

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("shunfeng");
        assertThat(result.trackingNo()).isEqualTo("118650888018");
        assertThat(result.externalState()).isEqualTo("3");
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
                properties("https://sandbox.test/kuaidi100"),
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

        var result = gateway.queryTrack("YTO", "INPUT001");

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
    void queryTrack_mapsFailedResponseUsingInputFallbacksWhenRemoteIdentifiersAndMessageMissing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "500",
                          "message": null,
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("YTO", "INPUT001");

        assertThat(result.success()).isFalse();
        assertThat(result.companyCode()).isEqualTo("yuantong");
        assertThat(result.trackingNo()).isEqualTo("INPUT001");
        assertThat(result.reason()).isEqualTo("查询失败");
        assertThat(result.internalStatus()).isEqualTo("FAILED");
        server.verify();
    }

    @Test
    void queryTrack_mapsConditionExceptionAndLeavesBadTraceTimeNull() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "ZTO",
                          "nu": "ZTO001",
                          "state": "2",
                          "data": [
                            {
                              "time": "bad-time",
                              "context": "包裹异常",
                              "location": "杭州"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("ZTO")
                .trackingNo("ZTO001")
                .phone("13800138000")
                .build());

        assertThat(result.success()).isTrue();
        assertThat(result.internalStatus()).isEqualTo("EXCEPTION");
        assertThat(result.signed()).isFalse();
        assertThat(result.signedAt()).isNull();
        assertThat(result.traces()).singleElement().satisfies(trace -> assertThat(trace.acceptTime()).isNull());
        server.verify();
    }

    @Test
    void queryTrack_returnsUnknownWhenOfficialStateMissing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
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

        assertThat(result.internalStatus()).isEqualTo("UNKNOWN");
        assertThat(result.signed()).isFalse();
        assertThat(result.signedAt()).isNull();
        server.verify();
    }

    @Test
    void queryTrack_usesExplicitResultV2AndRejectsNullCommandOrAutoCompany() throws Exception {
        Kuaidi100LogisticsGateway validationGateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> validationGateway.queryTrack((LogisticsTrackCommand) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号不能为空");
        assertThatThrownBy(() -> validationGateway.queryTrack("auto", "AUTO001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递公司编码不能为空");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ObjectMapper objectMapper = new ObjectMapper();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                objectMapper
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    Map<String, String> form = decodeForm(httpRequest.getBodyAsString(StandardCharsets.UTF_8));
                    assertThat(objectMapper.readValue(form.get("param"), Map.class))
                            .containsEntry("com", "yunda")
                            .containsEntry("num", "YD001")
                            .containsEntry("resultv2", "1");
                })
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "yunda",
                          "nu": "YD001",
                          "state": "1",
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("YD")
                .trackingNo("YD001")
                .resultV2(" 1 ")
                .build());

        assertThat(result.internalStatus()).isEqualTo("IN_TRANSIT");
        server.verify();
    }

    @Test
    void queryTrack_usesInputFallbacksForSuccessfulResponseWithUnknownState() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "state": "99",
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("YD", "YD002");

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("yunda");
        assertThat(result.trackingNo()).isEqualTo("YD002");
        assertThat(result.internalStatus()).isEqualTo("UNKNOWN");
        assertThat(result.traces()).isEmpty();
        server.verify();
    }

    @Test
    void queryTrack_leavesSignedAtNullWhenSignedTraceHasNoParseableTime() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "ems",
                          "nu": "EMS002",
                          "state": "3",
                          "data": [
                            {
                              "time": " ",
                              "context": "本人已签收",
                              "location": "广州"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("EMS", "EMS002");

        assertThat(result.internalStatus()).isEqualTo("SIGNED");
        assertThat(result.signed()).isTrue();
        assertThat(result.signedAt()).isNull();
        assertThat(result.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.acceptTime()).isNull();
            assertThat(trace.acceptStation()).isEqualTo("本人已签收");
        });
        server.verify();
    }

    @Test
    void queryTrack_mapsOfficialStateDeliveringWithoutTextFallback() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "yuantong",
                          "nu": "YT001",
                          "state": "5",
                          "data": [
                            {
                              "time": "2026-05-14 09:00:00",
                              "context": "快件正在处理"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("YTO", "YT001");

        assertThat(result.success()).isTrue();
        assertThat(result.externalState()).isEqualTo("5");
        assertThat(result.internalStatus()).isEqualTo("DELIVERING");
        assertThat(result.signed()).isFalse();
        server.verify();
    }

    @Test
    void queryTrack_mapsInTransitStateAndSignedAtFromFirstTraceWhenNoSignedKeyword() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer inTransitServer = MockRestServiceServer.bindTo(restTemplate).build();
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                restTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        inTransitServer.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "ems",
                          "nu": "EMS001",
                          "state": "10",
                          "data": [
                            null,
                            {
                              "ftime": "2026-05-14 09:00:00",
                              "time": "bad-time",
                              "context": "快件到达转运中心",
                              "location": "上海"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var inTransit = gateway.queryTrack("EMS", "EMS001");

        assertThat(inTransit.internalStatus()).isEqualTo("IN_TRANSIT");
        assertThat(inTransit.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.acceptTime()).isEqualTo(LocalDateTime.of(2026, 5, 14, 9, 0));
            assertThat(trace.acceptStation()).isEqualTo("快件到达转运中心");
            assertThat(trace.remark()).isEqualTo("上海");
        });
        inTransitServer.verify();

        RestTemplate signedTemplate = new RestTemplate();
        MockRestServiceServer signedServer = MockRestServiceServer.bindTo(signedTemplate).build();
        Kuaidi100LogisticsGateway signedGateway = new Kuaidi100LogisticsGateway(
                signedTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        signedServer.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("""
                        {
                          "status": "200",
                          "com": "jd",
                          "nu": "JD001",
                          "state": "3",
                          "data": [
                            {
                              "time": "2026-05-15 18:45:10",
                              "context": "投递完成",
                              "location": "北京"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var signed = signedGateway.queryTrack("JD", "JD001");

        assertThat(signed.internalStatus()).isEqualTo("SIGNED");
        assertThat(signed.signedAt()).isEqualTo(LocalDateTime.of(2026, 5, 15, 18, 45, 10));
        signedServer.verify();
    }

    @Test
    void queryTrack_returnsFailedResultForEmptyOrInvalidJsonResponse() {
        RestTemplate emptyTemplate = new RestTemplate();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyTemplate).build();
        Kuaidi100LogisticsGateway emptyGateway = new Kuaidi100LogisticsGateway(
                emptyTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        emptyServer.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess(" ", MediaType.APPLICATION_JSON));

        var emptyResult = emptyGateway.queryTrack("YTO", "EMPTY001");
        assertThat(emptyResult.success()).isFalse();
        assertThat(emptyResult.reason()).isEqualTo("快递100返回空响应");
        assertThat(emptyResult.rawResponse()).isEmpty();
        emptyServer.verify();

        RestTemplate invalidTemplate = new RestTemplate();
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidTemplate).build();
        Kuaidi100LogisticsGateway invalidGateway = new Kuaidi100LogisticsGateway(
                invalidTemplate,
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        invalidServer.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        var invalidResult = invalidGateway.queryTrack("YTO", "BAD001");
        assertThat(invalidResult.success()).isFalse();
        assertThat(invalidResult.reason()).contains("解析响应失败");
        assertThat(invalidResult.internalStatus()).isEqualTo("FAILED");
        invalidServer.verify();
    }

    @Test
    void queryTrack_rejectsBlankInputsBeforeCallingRemoteApi() {
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                properties("https://sandbox.test/kuaidi100"),
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
    void queryTrack_requiresPhoneForShunfengAndZhongtong() {
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> gateway.queryTrack("SF", "118650888018"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号");
        assertThatThrownBy(() -> gateway.queryTrack("ZTO", "ZTO001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号");
    }

    @Test
    void queryStatusAndCreateShipmentDeclareUnsupportedEntryPoints() {
        Kuaidi100LogisticsGateway gateway = new Kuaidi100LogisticsGateway(
                new RestTemplate(),
                properties("https://sandbox.test/kuaidi100"),
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
                properties("https://sandbox.test/kuaidi100"),
                new ObjectMapper()
        );

        server.expect(requestTo("https://sandbox.test/kuaidi100"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.queryTrack(LogisticsTrackCommand.builder()
                .companyCode("SF")
                .trackingNo("118650888018")
                .phone("13800138000")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快递100查询失败");
        server.verify();
    }

    private static LogisticsProperties properties(String endpoint) {
        LogisticsProperties properties = new LogisticsProperties();
        properties.getKd100().setEnabled(true);
        properties.getKd100().setEndpoint(endpoint);
        properties.getKd100().setCustomer("test-customer");
        properties.getKd100().setKey("test-key");
        return properties;
    }

    private static String sign(String param, String key, String customer) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((param + key + customer).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
