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
 * 快递鸟物流查询网关实现。
 *
 * <p>功能描述：对接快递鸟即时查询 API（接口指令 1002），提供物流轨迹查询能力。
 * 仅支持物流轨迹查询，不支持创建发货单（需要对接电子面单 API）。</p>
 *
 * <ul>
 *   <li>请求方式：HTTP POST (application/x-www-form-urlencoded)</li>
 *   <li>数据格式：请求体为 JSON，响应体为 JSON</li>
 *   <li>认证方式：EBusinessID + AppKey 签名（MD5 + Base64）</li>
 *   <li>每日调用限制：500 次/天</li>
 * </ul>
 *
 * <p>在架构中的角色：寄样域 / 物流适配层，实现 {@link LogisticsGateway} 统一接口，
 * 通过 {@code @ConditionalOnProperty(name = "kdniao.enabled", havingValue = "true")}
 * 按配置激活，与 {@link Kuaidi100LogisticsGateway} 互斥。</p>
 *
 * @see LogisticsGateway      物流网关统一接口
 * @see KdniaoConfig           快递鸟配置类（含 API 密钥、请求地址等）
 * @see KdniaoResponse         快递鸟 API 响应体映射
 * @see <a href="https://www.kdniao.com/api-track">快递鸟即时查询 API 文档</a>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kdniao.enabled", havingValue = "true")
public class KdniaoLogisticsGateway implements LogisticsGateway {

    /** 数据类型：2 表示 JSON 格式（快递鸟规定） */
    private static final String DATA_TYPE = "2";

    /** 快递鸟返回的时间格式：yyyy/MM/dd HH:mm:ss（斜杠分隔） */
    private static final DateTimeFormatter SLASH_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /** 备选时间格式：yyyy-MM-dd HH:mm:ss（横线分隔，兼容不同响应） */
    private static final DateTimeFormatter DASH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 快递鸟专用 RestTemplate（由 {@link KdniaoConfig} 创建并配置超时时间） */
    private final RestTemplate kdniaoRestTemplate;

    /** 快递鸟配置项（包含 EBusinessId、AppKey、请求地址等） */
    private final KdniaoConfig kdniaoConfig;

    /** Jackson JSON 序列化/反序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，通过 Spring 依赖注入获取所有必需组件。
     *
     * @param kdniaoRestTemplate 快递鸟专用的 RestTemplate（由 {@link KdniaoConfig#kdniaoRestTemplate} Bean 提供）
     * @param kdniaoConfig       快递鸟配置项（包含 API 密钥、请求地址等）
     * @param objectMapper       Jackson JSON 序列化/反序列化器
     */
    public KdniaoLogisticsGateway(RestTemplate kdniaoRestTemplate, KdniaoConfig kdniaoConfig, ObjectMapper objectMapper) {
        this.kdniaoRestTemplate = kdniaoRestTemplate;
        this.kdniaoConfig = kdniaoConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建发货单（不支持）。
     * <p>
     * 快递鸟即时查询 API（指令 1002）仅提供物流轨迹查询能力，不支持下单发货。
     * 如需发货功能，需额外对接快递鸟电子面单 API。
     * </p>
     *
     * @param command 发货命令
     * @return 不会返回，直接抛出异常
     * @throws UnsupportedOperationException 始终抛出，快递鸟即时查询不支持发货
     */
    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        // 快递鸟即时查询API不支持下单发货，仅支持轨迹查询
        // 此方法暂不支持，如需发货功能请对接电子面单API
        throw new UnsupportedOperationException("快递鸟即时查询API不支持创建发货，请使用电子面单API");
    }

    /**
     * 仅通过单号查询物流状态（已废弃，不推荐）。
     * <p>
     * 快递鸟 API 要求同时传入快递公司编码（ShipperCode）和物流单号（LogisticCode），
     * 仅凭单号无法查询。此方法始终抛出异常，引导调用方使用带公司编码的重载方法。
     * </p>
     *
     * @param trackingNo 物流单号（此参数不使用）
     * @return 不会返回，直接抛出异常
     * @throws BusinessException 参数错误，提示需要快递公司编码
     * @deprecated 请使用 {@link #queryTrack(String, String)} 或 {@link #queryStatus(String, String)}
     */
    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        throw BusinessException.param("快递公司编码不能为空，请调用 queryTrack(companyCode, trackingNo)");
    }

    /**
     * 查询物流状态（带快递公司编码）。
     * <p>
     * 内部委托 {@link #queryTrack(String, String)} 获取完整轨迹，再转换为状态结果。
     * 如果上游返回了失败原因（reason），直接使用；否则通过 {@link #buildStatusMessage} 构建可读消息。
     * </p>
     *
     * <ol>
     *   <li>第一步：调用 queryTrack 获取物流轨迹结果</li>
     *   <li>第二步：从轨迹结果中提取状态、原因等字段</li>
     *   <li>第三步：构建 LogisticsStatusResult 返回</li>
     * </ol>
     *
     * @param trackingNo  物流单号
     * @param shipperCode 快递公司编码（如 SF、ZTO 等）
     * @return 物流状态结果，包含单号、公司编码、内部状态、状态消息和查询时间
     */
    public LogisticsStatusResult queryStatus(String trackingNo, String shipperCode) {
        // 第一步：委托 queryTrack 获取完整轨迹信息
        LogisticsTrackResult track = queryTrack(shipperCode, trackingNo);
        // 第二步：将轨迹结果转换为状态结果，优先使用上游 reason，否则构建状态消息
        return new LogisticsStatusResult(
                track.trackingNo(),
                track.companyCode(),
                track.internalStatus(),
                track.reason() != null ? track.reason() : buildStatusMessage(track),
                LocalDateTime.now()
        );
    }

    /**
     * 查询物流轨迹（核心方法）。
     * <p>
     * 向快递鸟即时查询 API 发送请求，获取物流轨迹信息，并将响应转换为统一的
     * {@link LogisticsTrackResult} 格式。
     * </p>
     *
     * <ol>
     *   <li>第一步：校验物流单号和快递公司编码不能为空</li>
     *   <li>第二步：构建快递鸟请求参数（OrderCode、ShipperCode、LogisticCode）</li>
     *   <li>第三步：调用 {@link #doRequest} 发送 HTTP 请求到快递鸟 API</li>
     *   <li>第四步：调用 {@link #parseTrackResponse} 解析响应为统一格式</li>
     * </ol>
     *
     * @param companyCode 快递公司编码（如 SF 表示顺丰、ZTO 表示中通等）
     * @param trackingNo  物流运单号
     * @return 物流轨迹结果，包含签收状态、轨迹节点列表、原始响应等
     * @throws BusinessException 当参数为空或查询失败时抛出
     */
    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        // 第一步：校验参数
        if (trackingNo == null || trackingNo.isBlank()) {
            throw BusinessException.param("物流单号不能为空");
        }
        if (companyCode == null || companyCode.isBlank()) {
            throw BusinessException.param("快递公司编码不能为空，请先识别快递公司");
        }

        try {
            // 第二步：构建快递鸟请求参数
            Map<String, Object> requestData = new LinkedHashMap<>();
            requestData.put("OrderCode", "");          // 订单编号（可选，留空）
            requestData.put("ShipperCode", companyCode); // 快递公司编码
            requestData.put("LogisticCode", trackingNo); // 物流单号
            // 第三步：发送 HTTP 请求
            KdniaoResponse response = doRequest(requestData);
            // 第四步：解析响应为统一格式
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
     * 发送 HTTP POST 请求到快递鸟即时查询 API。
     * <p>
     * 完整的请求流程如下：
     * </p>
     * <ol>
     *   <li>第一步：将请求参数 Map 序列化为 JSON 字符串（RequestData）</li>
     *   <li>第二步：使用 MD5 + Base64 生成签名（DataSign）</li>
     *   <li>第三步：构建 application/x-www-form-urlencoded 表单参数，
     *       包含 RequestData、EBusinessID、RequestType、DataSign、DataType</li>
     *   <li>第四步：通过 RestTemplate 发送 POST 请求到快递鸟 API 地址</li>
     *   <li>第五步：将 JSON 响应反序列化为 {@link KdniaoResponse} 对象</li>
     * </ol>
     *
     * @param requestData 请求参数 Map（包含 OrderCode、ShipperCode、LogisticCode）
     * @return 快递鸟 API 响应对象
     * @throws BusinessException 当请求失败或响应解析失败时抛出
     */
    private KdniaoResponse doRequest(Map<String, Object> requestData) {
        try {
            // 第一步：将请求参数序列化为 JSON 字符串
            String requestDataJson = toJson(requestData);

            // 第二步：生成签名（MD5 加密后 Base64 编码）
            String dataSign = generateDataSign(requestDataJson);

            // 第三步：构建表单参数（application/x-www-form-urlencoded）
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("RequestData", requestDataJson);                  // 请求内容（JSON 格式）
            params.add("EBusinessID", kdniaoConfig.getEBusinessId());    // 商户 ID
            params.add("RequestType", kdniaoConfig.getRequestType());   // 请求类型：1002 = 即时查询
            params.add("DataSign", dataSign);                            // 签名（MD5 + Base64）
            params.add("DataType", DATA_TYPE);                           // 数据类型：2 = JSON

            // 第四步：发送 POST 请求到快递鸟 API
            String response = kdniaoRestTemplate.postForObject(
                    kdniaoConfig.getRequestUrl(),
                    params,
                    String.class
            );

            // 第五步：解析 JSON 响应
            return parseJsonResponse(response);

        } catch (Exception e) {
            log.error("快递鸟API请求失败: {}", e.getMessage(), e);
            throw BusinessException.external("快递鸟API请求失败: " + e.getMessage());
        }
    }

    /**
     * 生成快递鸟 API 请求签名。
     * <p>
     * 签名算法（快递鸟官方规定）：
     * </p>
     * <ol>
     *   <li>第一步：拼接原始数据 = RequestData（JSON 字符串，未 URL 编码）+ AppKey</li>
     *   <li>第二步：对拼接数据进行 UTF-8 编码后做 MD5 摘要</li>
     *   <li>第三步：将 MD5 字节数组进行 Base64 编码，得到最终签名</li>
     * </ol>
     * <p>
     * 注意：RestTemplate 的表单转换器会负责最终的 URL 编码，此处签名使用未编码的原始数据，
     * 避免二次编码导致签名不匹配。
     * </p>
     *
     * @param requestData 已序列化的请求 JSON 字符串（未 URL 编码）
     * @return Base64 编码后的 MD5 签名字符串
     * @throws BusinessException 签名生成失败时抛出（如 MD5 算法不可用）
     */
    private String generateDataSign(String requestData) {
        try {
            // 第一步：拼接请求内容和 AppKey
            String data = requestData + kdniaoConfig.getAppKey();

            // 第二步：MD5 摘要
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = md.digest(data.getBytes(StandardCharsets.UTF_8));

            // 第三步：Base64 编码
            return Base64.getEncoder().encodeToString(md5Bytes);

        } catch (Exception e) {
            throw BusinessException.external("生成签名失败", e);
        }
    }

    /**
     * 解析快递鸟响应并转换为物流状态结果。
     * <p>
     * 委托 {@link #parseTrackResponse} 获取完整轨迹信息后，提取关键字段构建状态结果。
     * </p>
     *
     * @param response    快递鸟 API 响应对象
     * @param trackingNo  物流单号（响应中可能不包含，作为兜底使用）
     * @param shipperCode 快递公司编码（响应中可能不包含，作为兜底使用）
     * @return 物流状态结果
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

    /**
     * 解析快递鸟轨迹响应，转换为统一的 {@link LogisticsTrackResult}。
     * <p>
     * 处理逻辑：
     * </p>
     * <ol>
     *   <li>第一步：如果响应为 null，返回未知状态的结果</li>
     *   <li>第二步：将快递鸟轨迹节点转换为内部 {@link LogisticsTraceNode} 列表</li>
     *   <li>第三步：如果上游返回失败（Success != true），返回 FAILED 状态</li>
     *   <li>第四步：成功时，通过 {@link #mapState} 映射状态码为内部统一状态</li>
     *   <li>第五步：如果已签收，通过 {@link #resolveSignedAt} 提取签收时间</li>
     *   <li>第六步：组装并返回完整的轨迹结果</li>
     * </ol>
     *
     * @param response    快递鸟 API 响应对象（可能为 null）
     * @param trackingNo  物流单号（用于响应为 null 时的兜底）
     * @param shipperCode 快递公司编码（用于响应中无公司编码时的兜底）
     * @return 统一格式的物流轨迹结果
     */
    private LogisticsTrackResult parseTrackResponse(KdniaoResponse response, String trackingNo, String shipperCode) {
        // 第一步：响应为 null 时返回未知状态（空响应兜底）
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
        // 第二步：转换快递鸟轨迹节点为内部统一格式
        List<LogisticsTraceNode> traces = toTraceNodes(response.getTraces());
        // 保留原始响应，用于问题排查
        Map<String, Object> rawResponse = objectMapper.convertValue(response, Map.class);
        // 第三步：如果上游返回失败（Success != true），构建失败结果
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

        // 第四步：映射快递鸟状态码到内部统一状态（如 "2" -> "IN_TRANSIT"）
        String status = mapState(response.getState());
        // 第五步：已签收时提取签收时间
        LocalDateTime signedAt = "SIGNED".equals(status) ? resolveSignedAt(traces) : null;

        // 第六步：组装完整的轨迹结果
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
     * 映射快递鸟状态码到内部统一状态。
     * <p>
     * 快递鸟 API 的 State 字段取值：
     * </p>
     * <ul>
     *   <li>"0" - 无轨迹（未映射，返回 UNKNOWN）</li>
     *   <li>"1" - 已揽收（未映射，返回 UNKNOWN）</li>
     *   <li>"2" - 在途中（IN_TRANSIT）</li>
     *   <li>"3" - 已签收（SIGNED）</li>
     *   <li>"4" - 问题件（EXCEPTION）</li>
     *   <li>"5" - 疑难件（未映射，返回 UNKNOWN）</li>
     *   <li>"6" - 退件签收（未映射，返回 UNKNOWN）</li>
     * </ul>
     *
     * @param stateCode 快递鸟原始状态码（如 "2"、"3"、"4"）
     * @return 内部统一状态字符串（IN_TRANSIT / SIGNED / EXCEPTION / UNKNOWN）
     */
    private String mapState(String stateCode) {
        if (stateCode == null) {
            return "UNKNOWN";
        }
        return switch (stateCode) {
            case "2" -> "IN_TRANSIT";    // 在途中
            case "3" -> "SIGNED";        // 签收
            case "4" -> "EXCEPTION";     // 问题件
            default -> "UNKNOWN";
        };
    }

    /**
     * 根据轨迹结果构建人类可读的状态消息。
     * <p>
     * 消息格式示例："运输中 - 2025/01/15 10:30:00 快件已到达XX中转站"
     * </p>
     * <ol>
     *   <li>第一步：根据外部状态码翻译为中文状态名称（运输中 / 已签收 / 问题件）</li>
     *   <li>第二步：如果存在轨迹节点，追加最新一条轨迹的时间和站点信息</li>
     * </ol>
     *
     * @param track 物流轨迹结果
     * @return 格式化的状态描述消息
     */
    private String buildStatusMessage(LogisticsTrackResult track) {
        StringBuilder sb = new StringBuilder();

        KdniaoResponse.LogisticsState state = KdniaoResponse.LogisticsState.fromCode(track.externalState());
        if (state != null) {
            if (state == KdniaoResponse.LogisticsState.IN_TRANSIT) {
                sb.append("运输中");
            } else if (state == KdniaoResponse.LogisticsState.SIGNED) {
                sb.append("已签收");
            } else if (state == KdniaoResponse.LogisticsState.EXCEPTION) {
                sb.append("问题件");
            }
        }

        if (track.traces() != null && !track.traces().isEmpty()) {
            var latestTrace = track.traces().get(track.traces().size() - 1);
            sb.append(" - ").append(latestTrace.acceptTime())
              .append(" ").append(latestTrace.acceptStation());
        }

        return sb.toString();
    }

    /**
     * 将对象序列化为 JSON 字符串。
     * <p>
     * 用于将请求参数 Map 序列化为快递鸟 API 所需的 JSON 格式。
     * </p>
     *
     * @param obj 待序列化的对象（通常为 Map<String, Object>）
     * @return JSON 字符串
     * @throws BusinessException 序列化失败时抛出
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw BusinessException.param("JSON序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为 {@link KdniaoResponse} 对象。
     *
     * @param json 快递鸟 API 返回的 JSON 响应字符串
     * @return 反序列化后的响应对象
     * @throws BusinessException JSON 解析失败时抛出（如格式异常、字段映射失败等）
     */
    private KdniaoResponse parseJsonResponse(String json) {
        try {
            return objectMapper.readValue(json, KdniaoResponse.class);
        } catch (Exception e) {
            log.error("解析快递鸟响应失败: {}", json, e);
            throw BusinessException.external("解析物流查询响应失败: " + e.getMessage());
        }
    }

    /**
     * 将快递鸟轨迹节点列表转换为内部统一的 {@link LogisticsTraceNode} 列表。
     * <p>
     * 过滤 null 节点，并将快递鸟的 AcceptTime（字符串）解析为 LocalDateTime。
     * </p>
     *
     * @param traces 快递鸟轨迹节点列表（可能为 null 或空）
     * @return 内部统一格式的轨迹节点列表，按时间升序排列
     */
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

    /**
     * 从轨迹节点列表中提取签收时间。
     * <p>
     * 查找轨迹描述中包含"签收"关键字的节点，取最后一个匹配节点的时间作为签收时间。
     * 如果没有找到包含"签收"的节点，则使用最后一条轨迹的时间作为兜底。
     * </p>
     *
     * @param traces 轨迹节点列表（可能为 null 或空）
     * @return 签收时间，无法确定时返回 null
     */
    private LocalDateTime resolveSignedAt(List<LogisticsTraceNode> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        return traces.stream()
                .filter(trace -> trace.acceptStation() != null && trace.acceptStation().contains("签收"))
                .map(LogisticsTraceNode::acceptTime)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)  // 取最后一个匹配的签收节点
                .orElseGet(() -> traces.get(traces.size() - 1).acceptTime()); // 兜底：使用最后一条轨迹时间
    }

    /**
     * 解析快递鸟轨迹时间字符串为 LocalDateTime。
     * <p>
     * 快递鸟返回的时间格式不完全统一，支持两种格式的兼容解析：
     * </p>
     * <ul>
     *   <li>格式一：yyyy/MM/dd HH:mm:ss（斜杠分隔，快递鸟标准格式）</li>
     *   <li>格式二：yyyy-MM-dd HH:mm:ss（横线分隔，兼容格式）</li>
     * </ul>
     *
     * @param value 时间字符串（可能为 null 或空）
     * @return 解析后的 LocalDateTime，解析失败时返回 null
     */
    private LocalDateTime parseAcceptTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // 优先尝试斜杠分隔格式（快递鸟标准格式）
            return LocalDateTime.parse(value, SLASH_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                // 回退到横线分隔格式（兼容格式）
                return LocalDateTime.parse(value, DASH_TIME);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}
