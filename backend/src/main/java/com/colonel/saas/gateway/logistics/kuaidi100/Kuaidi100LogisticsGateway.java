package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 快递100物流查询实现
 * 对接文档：https://www.kuaidi100.com/openapi/
 *
 * 免费接口（基础查询免费），无需签名
 * POST https://poll.kuaidi100.com/poll/query
 * 参数：customer + param（JSON schema=2）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kuaidi100.enabled", havingValue = "true")
public class Kuaidi100LogisticsGateway implements LogisticsGateway {

    private static final String SCHEMA = "2";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final Kuaidi100Config config;
    private final ObjectMapper objectMapper;

    public Kuaidi100LogisticsGateway(
            RestTemplate kuaidi100RestTemplate,
            Kuaidi100Config config,
            ObjectMapper objectMapper) {
        this.restTemplate = kuaidi100RestTemplate;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        throw new UnsupportedOperationException("快递100即时查询API不支持创建发货");
    }

    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        throw BusinessException.param("请调用 queryTrack(companyCode, trackingNo)");
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
            String param = buildParam(companyCode, trackingNo);
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("customer", config.getCustomer());
            params.add("param", param);

            String response = restTemplate.postForObject(config.getRequestUrl(), params, String.class);
            return parseTrackResponse(response, companyCode, trackingNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("快递100查询失败, trackingNo={}, companyCode={}, error={}",
                    trackingNo, companyCode, e.getMessage(), e);
            throw BusinessException.external("快递100查询失败: " + e.getMessage());
        }
    }

    private String buildParam(String companyCode, String trackingNo) {
        try {
            Map<String, Object> param = Map.of(
                    "com", companyCode,
                    "num", trackingNo,
                    "from", "",
                    "to", ""
            );
            return objectMapper.writeValueAsString(param);
        } catch (Exception e) {
            throw BusinessException.param("构建查询参数失败", e);
        }
    }

    private LogisticsTrackResult parseTrackResponse(String json, String companyCode, String trackingNo) {
        if (json == null || json.isBlank()) {
            return failedResult(companyCode, trackingNo, "快递100返回空响应", null);
        }

        Kuaidi100Response resp;
        try {
            resp = objectMapper.readValue(json, Kuaidi100Response.class);
        } catch (Exception e) {
            log.error("解析快递100响应失败: {}", json, e);
            return failedResult(companyCode, trackingNo, "解析响应失败: " + e.getMessage(), null);
        }

        Map<String, Object> raw = objectMapper.convertValue(resp, Map.class);

        if (!"200".equals(resp.getStatus())) {
            return failedResult(
                    resp.getCompanyCode() != null ? resp.getCompanyCode() : companyCode,
                    resp.getTrackingNo() != null ? resp.getTrackingNo() : trackingNo,
                    resp.getMessage() != null ? resp.getMessage() : "查询失败",
                    raw);
        }

        String status = mapStatus(resp);
        List<LogisticsTraceNode> traces = toTraceNodes(resp.getData());
        LocalDateTime signedAt = "SIGNED".equals(status) ? resolveSignedAt(traces) : null;

        return new LogisticsTrackResult(
                resp.getCompanyCode() != null ? resp.getCompanyCode() : companyCode,
                resp.getTrackingNo() != null ? resp.getTrackingNo() : trackingNo,
                true,
                null,
                resp.getCondition(),
                status,
                "SIGNED".equals(status),
                signedAt,
                traces,
                raw);
    }

    private LogisticsTrackResult failedResult(String companyCode, String trackingNo, String reason, Map<String, Object> raw) {
        return new LogisticsTrackResult(
                companyCode, trackingNo, false, reason,
                null, "FAILED", false, null, List.of(), raw != null ? raw : Map.of());
    }

    private String mapStatus(Kuaidi100Response resp) {
        String isCheck = resp.getIsCheck();
        String condition = resp.getCondition();

        // ischeck: 0=未签收, 1=已签收
        if ("1".equals(isCheck)) {
            return "SIGNED";
        }
        if ("0".equals(isCheck)) {
            // 根据condition判断
            if ("F00".equals(condition)) {
                return "IN_TRANSIT";
            }
            if ("F01".equals(condition) || "F02".equals(condition)) {
                return "EXCEPTION";
            }
            if ("F03".equals(condition) || "F04".equals(condition)) {
                return "IN_TRANSIT";
            }
            return "IN_TRANSIT";
        }

        // fallback: 直接看data最后一条描述
        if (resp.getData() != null && !resp.getData().isEmpty()) {
            String lastContext = resp.getData().get(resp.getData().size() - 1).getContext();
            if (lastContext != null) {
                if (lastContext.contains("签收") || lastContext.contains("已取件") || lastContext.contains("已提取")) {
                    return "SIGNED";
                }
                if (lastContext.contains("退") || lastContext.contains("拒收") || lastContext.contains("退回")) {
                    return "EXCEPTION";
                }
            }
        }

        return "UNKNOWN";
    }

    private List<LogisticsTraceNode> toTraceNodes(List<Kuaidi100Response.TraceNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(Objects::nonNull)
                .map(n -> new LogisticsTraceNode(
                        parseTime(n.getTime()),
                        n.getContext(),
                        n.getLocation()))
                .toList();
    }

    private LocalDateTime resolveSignedAt(List<LogisticsTraceNode> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        return traces.stream()
                .filter(t -> t.acceptStation() != null &&
                        (t.acceptStation().contains("签收") || t.acceptStation().contains("已取件")))
                .map(LogisticsTraceNode::acceptTime)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElseGet(() -> traces.get(traces.size() - 1).acceptTime());
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
