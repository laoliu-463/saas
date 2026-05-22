package com.colonel.saas.gateway.logistics.kdniao;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 快递鸟物流查询实现
 * 对接文档：https://www.kdniao.com/api-track
 *
 * 接口指令：1002
 * 请求方式：HTTP POST (application/x-www-form-urlencoded)
 * 数据类型：JSON
 * 每日调用限制：500次/天
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kdniao.enabled", havingValue = "true")
public class KdniaoLogisticsGateway implements LogisticsGateway {

    private static final String DATA_TYPE = "2";
    private static final DateTimeFormatter SLASH_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter DASH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate kdniaoRestTemplate;
    private final KdniaoConfig kdniaoConfig;
    private final ObjectMapper objectMapper;

    public KdniaoLogisticsGateway(RestTemplate kdniaoRestTemplate, KdniaoConfig kdniaoConfig, ObjectMapper objectMapper) {
        this.kdniaoRestTemplate = kdniaoRestTemplate;
        this.kdniaoConfig = kdniaoConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        // 快递鸟即时查询API不支持下单发货，仅支持轨迹查询
        // 此方法暂不支持，如需发货功能请对接电子面单API
        throw new UnsupportedOperationException("快递鸟即时查询API不支持创建发货，请使用电子面单API");
    }

    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        throw BusinessException.param("快递公司编码不能为空，请调用 queryTrack(companyCode, trackingNo)");
    }

    /**
     * 查询物流状态
     *
     * @param trackingNo   物流单号
     * @param shipperCode  快递公司编码（如不传则自动识别）
     * @return 物流状态结果
     */
    public LogisticsStatusResult queryStatus(String trackingNo, String shipperCode) {
        LogisticsTrackResult track = queryTrack(shipperCode, trackingNo);
        return new LogisticsStatusResult(
                track.trackingNo(),
                track.companyCode(),
                track.internalStatus(),
                track.reason() != null ? track.reason() : buildStatusMessage(track),
                LocalDateTime.now()
        );
    }

    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        if (trackingNo == null || trackingNo.isBlank()) {
            throw BusinessException.param("物流单号不能为空");
        }
        if (companyCode == null || companyCode.isBlank()) {
            throw BusinessException.param("快递公司编码不能为空，请先识别快递公司");
        }

        try {
            Map<String, Object> requestData = new LinkedHashMap<>();
            requestData.put("OrderCode", "");
            requestData.put("ShipperCode", companyCode);
            requestData.put("LogisticCode", trackingNo);
            KdniaoResponse response = doRequest(requestData);
            return parseTrackResponse(response, trackingNo, companyCode);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询物流状态失败, trackingNo={}, shipperCode={}, error={}",
                    trackingNo, companyCode, e.getMessage(), e);
            throw BusinessException.external("查询物流状态失败: " + e.getMessage());
        }
    }

    /**
     * 发送请求到快递鸟API
     */
    private KdniaoResponse doRequest(Map<String, Object> requestData) {
        try {
            // 序列化为JSON
            String requestDataJson = toJson(requestData);

            // 生成签名
            String dataSign = generateDataSign(requestDataJson);

            // 构建表单参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("RequestData", requestDataJson);
            params.add("EBusinessID", kdniaoConfig.getEBusinessId());
            params.add("RequestType", kdniaoConfig.getRequestType());
            params.add("DataSign", dataSign);
            params.add("DataType", DATA_TYPE);

            // 发送请求
            String response = kdniaoRestTemplate.postForObject(
                    kdniaoConfig.getRequestUrl(),
                    params,
                    String.class
            );

            // 解析响应
            return parseJsonResponse(response);

        } catch (Exception e) {
            log.error("快递鸟API请求失败: {}", e.getMessage(), e);
            throw BusinessException.external("快递鸟API请求失败: " + e.getMessage());
        }
    }

    /**
     * 生成数据签名
     * 签名算法：把(请求内容(未编码)+AppKey)进行MD5加密，然后Base64编码。
     * RestTemplate 表单转换会负责最终的 URL 编码，避免二次编码。
     */
    private String generateDataSign(String requestData) {
        try {
            String data = requestData + kdniaoConfig.getAppKey();

            // MD5加密
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = md.digest(data.getBytes(StandardCharsets.UTF_8));

            // Base64编码
            return Base64.getEncoder().encodeToString(md5Bytes);

        } catch (Exception e) {
            throw BusinessException.external("生成签名失败", e);
        }
    }

    /**
     * 解析响应
     */
    private LogisticsStatusResult parseResponse(KdniaoResponse response, String trackingNo, String shipperCode) {
        LogisticsTrackResult track = parseTrackResponse(response, trackingNo, shipperCode);
        return new LogisticsStatusResult(
                track.trackingNo(),
                track.companyCode(),
                track.internalStatus(),
                track.reason() != null ? track.reason() : buildStatusMessage(track),
                LocalDateTime.now()
        );
    }

    private LogisticsTrackResult parseTrackResponse(KdniaoResponse response, String trackingNo, String shipperCode) {
        if (response == null) {
            return new LogisticsTrackResult(
                    shipperCode,
                    trackingNo,
                    false,
                    "物流查询返回空响应",
                    null,
                    "UNKNOWN",
                    false,
                    null,
                    List.of(),
                    Map.of());
        }
        List<LogisticsTraceNode> traces = toTraceNodes(response.getTraces());
        Map<String, Object> rawResponse = objectMapper.convertValue(response, Map.class);
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            return new LogisticsTrackResult(
                    response.getShipperCode() != null ? response.getShipperCode() : shipperCode,
                    trackingNo,
                    false,
                    response.getReason() != null ? response.getReason() : "物流查询失败",
                    response.getState(),
                    "FAILED",
                    false,
                    null,
                    traces,
                    rawResponse);
        }

        String status = mapState(response.getState());
        LocalDateTime signedAt = "SIGNED".equals(status) ? resolveSignedAt(traces) : null;

        return new LogisticsTrackResult(
                response.getShipperCode() != null ? response.getShipperCode() : shipperCode,
                trackingNo,
                true,
                null,
                response.getState(),
                status,
                "SIGNED".equals(status),
                signedAt,
                traces,
                rawResponse);
    }

    /**
     * 映射快递鸟状态到内部状态
     */
    private String mapState(String stateCode) {
        if (stateCode == null) {
            return "UNKNOWN";
        }
        return switch (stateCode) {
            case "2" -> "IN_TRANSIT";    // 在途中
            case "3" -> "SIGNED";        // 签收
            case "4" -> "EXCEPTION";      // 问题件
            default -> "UNKNOWN";
        };
    }

    /**
     * 构建状态消息
     */
    private String buildStatusMessage(LogisticsTrackResult track) {
        StringBuilder sb = new StringBuilder();

        KdniaoResponse.LogisticsState state = KdniaoResponse.LogisticsState.fromCode(track.externalState());
        if (state != null) {
            sb.append(switch (state) {
                case IN_TRANSIT -> "运输中";
                case SIGNED -> "已签收";
                case EXCEPTION -> "问题件";
            });
        }

        if (track.traces() != null && !track.traces().isEmpty()) {
            var latestTrace = track.traces().get(track.traces().size() - 1);
            sb.append(" - ").append(latestTrace.acceptTime())
              .append(" ").append(latestTrace.acceptStation());
        }

        return sb.toString();
    }

    /**
     * JSON序列化
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw BusinessException.param("JSON序列化失败", e);
        }
    }

    /**
     * 解析JSON响应
     */
    private KdniaoResponse parseJsonResponse(String json) {
        try {
            return objectMapper.readValue(json, KdniaoResponse.class);
        } catch (Exception e) {
            log.error("解析快递鸟响应失败: {}", json, e);
            throw BusinessException.external("解析物流查询响应失败: " + e.getMessage());
        }
    }

    private List<LogisticsTraceNode> toTraceNodes(List<KdniaoResponse.LogisticsTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }
        return traces.stream()
                .filter(Objects::nonNull)
                .map(trace -> new LogisticsTraceNode(
                        parseAcceptTime(trace.getAcceptTime()),
                        trace.getAcceptStation(),
                        trace.getRemark()))
                .toList();
    }

    private LocalDateTime resolveSignedAt(List<LogisticsTraceNode> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        return traces.stream()
                .filter(trace -> trace.acceptStation() != null && trace.acceptStation().contains("签收"))
                .map(LogisticsTraceNode::acceptTime)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElseGet(() -> traces.get(traces.size() - 1).acceptTime());
    }

    private LocalDateTime parseAcceptTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SLASH_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DASH_TIME);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}
