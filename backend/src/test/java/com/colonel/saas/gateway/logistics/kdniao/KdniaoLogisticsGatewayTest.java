package com.colonel.saas.gateway.logistics.kdniao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KdniaoLogisticsGatewayTest {

    @Test
    void queryTrack_postsSingleEncodedFormAndMapsSignedTrace() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        KdniaoConfig config = config("https://sandbox.test/kdniao");
        KdniaoLogisticsGateway gateway = new KdniaoLogisticsGateway(restTemplate, config, new ObjectMapper());

        server.expect(requestTo("https://sandbox.test/kdniao"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    String body = httpRequest.getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).doesNotContain("%25");

                    Map<String, String> form = decodeForm(body);
                    String requestData = form.get("RequestData");
                    assertThat(requestData).isEqualTo("""
                            {"OrderCode":"","ShipperCode":"SF","LogisticCode":"118650888018"}""".trim());
                    assertThat(form).containsEntry("EBusinessID", "test-business");
                    assertThat(form).containsEntry("RequestType", "1002");
                    assertThat(form).containsEntry("DataType", "2");
                    assertThat(form).containsEntry("DataSign", expectedDataSign(requestData, "test-key"));
                })
                .andRespond(withSuccess("""
                        {
                          "EBusinessID": "test-business",
                          "OrderCode": "",
                          "ShipperCode": "SF",
                          "LogisticCode": "118650888018",
                          "Success": true,
                          "State": "3",
                          "Reason": null,
                          "Traces": [
                            {
                              "AcceptTime": "2026/05/14 10:23:03",
                              "AcceptStation": "派件已签收[深圳市]",
                              "Remark": null
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("SF", "118650888018");

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("SF");
        assertThat(result.trackingNo()).isEqualTo("118650888018");
        assertThat(result.externalState()).isEqualTo("3");
        assertThat(result.internalStatus()).isEqualTo("SIGNED");
        assertThat(result.signed()).isTrue();
        assertThat(result.signedAt()).isEqualTo(LocalDateTime.of(2026, 5, 14, 10, 23, 3));
        assertThat(result.traces()).hasSize(1);
        assertThat(result.traces().get(0).acceptStation()).contains("签收");
        assertThat(result.rawResponse()).containsEntry("Success", true);
        server.verify();
    }

    @Test
    void queryTrack_mapsFailedResponseWithoutAdvancingStatus() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        KdniaoConfig config = config("https://sandbox.test/kdniao");
        KdniaoLogisticsGateway gateway = new KdniaoLogisticsGateway(restTemplate, config, new ObjectMapper());

        server.expect(requestTo("https://sandbox.test/kdniao"))
                .andRespond(withSuccess("""
                        {
                          "EBusinessID": "test-business",
                          "ShipperCode": "SF",
                          "LogisticCode": "NO_TRACE_001",
                          "Success": false,
                          "Reason": "暂无轨迹",
                          "Traces": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.queryTrack("SF", "NO_TRACE_001");

        assertThat(result.success()).isFalse();
        assertThat(result.externalState()).isNull();
        assertThat(result.internalStatus()).isEqualTo("FAILED");
        assertThat(result.reason()).isEqualTo("暂无轨迹");
        assertThat(result.signed()).isFalse();
        assertThat(result.traces()).isEmpty();
        server.verify();
    }

    private static KdniaoConfig config(String requestUrl) {
        KdniaoConfig config = new KdniaoConfig();
        config.setEBusinessId("test-business");
        config.setAppKey("test-key");
        config.setRequestUrl(requestUrl);
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

    private static String expectedDataSign(String requestData, String appKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = md.digest((requestData + appKey).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md5Bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
