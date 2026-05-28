package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeCommand;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeResult;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 快递100物流查询网关实现。
 *
 * <p>功能描述：对接快递100实时查询 API 和轨迹订阅 API，提供物流轨迹查询与订阅能力。
 * 支持快递公司编码自动标准化、手机号校验（顺丰/中通强制要求）、轨迹订阅回调等。</p>
 *
 * <ul>
 *   <li>实时查询接口：POST https://poll.kuaidi100.com/poll/query.do
 *       参数：customer + sign + param（application/x-www-form-urlencoded）</li>
 *   <li>轨迹订阅接口：由 {@code logistics.kd100.subscribeEndpoint} 配置指定
 *       参数：schema=json + param（application/x-www-form-urlencoded）</li>
 *   <li>认证方式：customer（客户编号）+ sign（MD5 签名）</li>
 *   <li>签名算法：MD5(param + key + customer)，结果转大写十六进制</li>
 * </ul>
 *
 * <p>在架构中的角色：寄样域 / 物流适配层，实现 {@link LogisticsGateway} 统一接口，
 * 通过 {@code @ConditionalOnProperty(prefix = "logistics.kd100", name = "enabled", havingValue = "true")}
 * 按配置激活，与 {@link KdniaoLogisticsGateway} 互斥。</p>
 *
 * @see LogisticsGateway       物流网关统一接口
 * @see LogisticsProperties    物流配置属性（含快递100 customer、key、callbackUrl 等）
 * @see Kuaidi100Response      快递100 API 响应体映射
 * @see <a href="https://www.kuaidi100.com/openapi/">快递100 开放 API 文档</a>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "logistics.kd100", name = "enabled", havingValue = "true")
public class Kuaidi100LogisticsGateway implements LogisticsGateway {

    /** 快递100返回的时间格式：yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 快递公司编码别名映射表。
     * <p>
     * 将外部常用的快递公司编码（大写缩写或英文全称）映射为快递100标准编码（小写英文）。
     * 这样调用方可以使用 SF / SHUNFENG / ZTO / ZHONGTONG 等常见编码，内部自动标准化。
     * </p>
     */
    private static final Map<String, String> COMPANY_CODE_ALIASES = Map.ofEntries(
            Map.entry("SF", "shunfeng"),         // 顺丰
            Map.entry("SHUNFENG", "shunfeng"),
            Map.entry("ZTO", "zhongtong"),       // 中通
            Map.entry("ZHONGTONG", "zhongtong"),
            Map.entry("YTO", "yuantong"),        // 圆通
            Map.entry("YUANTONG", "yuantong"),
            Map.entry("STO", "shentong"),        // 申通
            Map.entry("SHENTONG", "shentong"),
            Map.entry("YD", "yunda"),            // 韵达
            Map.entry("YUNDA", "yunda"),
            Map.entry("EMS", "ems"),             // EMS
            Map.entry("JD", "jd"),               // 京东
            Map.entry("JINGDONG", "jd")
    );

    /** HTTP 请求客户端（由 Spring Bean 管理，已配置超时等参数） */
    private final RestTemplate restTemplate;

    /** 物流配置属性（包含快递100 customer、key、callbackUrl 等） */
    private final LogisticsProperties properties;

    /** Jackson JSON 序列化/反序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，通过 Spring 依赖注入获取所有必需组件。
     *
     * @param kuaidi100RestTemplate 快递100专用 RestTemplate Bean
     * @param properties            物流配置属性（customer、key、endpoint 等）
     * @param objectMapper          Jackson JSON 序列化/反序列化器
     */
    public Kuaidi100LogisticsGateway(
            RestTemplate kuaidi100RestTemplate,
            LogisticsProperties properties,
            ObjectMapper objectMapper) {
        this.restTemplate = kuaidi100RestTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建发货单（不支持）。
     * <p>快递100实时查询 API 仅提供物流轨迹查询能力，不支持创建发货单。</p>
     *
     * @param command 发货命令
     * @return 不会返回，直接抛出异常
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        throw new UnsupportedOperationException("快递100即时查询API不支持创建发货");
    }

    /**
     * 订阅物流轨迹推送（快递100主动推送）。
     * <p>
     * 向快递100发送轨迹订阅请求，快递100后续会在物流状态变更时通过回调 URL 主动推送轨迹信息。
     * 回调地址、盐值等配置在 {@link LogisticsProperties.Kd100} 中。
     * </p>
     *
     * <ol>
     *   <li>第一步：校验物流单号和快递公司编码</li>
     *   <li>第二步：校验快递100订阅配置完整性（key、callbackUrl、callbackSalt）</li>
     *   <li>第三步：调用 {@link #buildSubscribeParam} 构建订阅参数 JSON</li>
     *   <li>第四步：通过 RestTemplate 发送 POST 请求到快递100订阅端点</li>
     *   <li>第五步：调用 {@link #parseSubscribeResponse} 解析订阅响应</li>
     * </ol>
     *
     * @param command 订阅命令（包含物流单号、公司编码、手机号等）
     * @return 订阅结果（包含是否成功、返回码、消息等）
     * @throws BusinessException 当参数为空、配置不完整或订阅失败时抛出
     */
    @Override
    public LogisticsSubscribeResult subscribeTrack(LogisticsSubscribeCommand command) {
        // 第一步：校验物流单号
        if (command == null || !StringUtils.hasText(command.getTrackingNo())) {
            throw BusinessException.param("物流单号不能为空");
        }
        String trackingNo = command.getTrackingNo().trim();
        // 标准化快递公司编码（如 "SF" -> "shunfeng"）
        String companyCode = normalizeCompanyCode(command.getCompanyCode());
        if (!StringUtils.hasText(companyCode) || "auto".equals(companyCode)) {
            throw BusinessException.param("快递公司编码不能为空，请先识别快递公司");
        }
        // 第二步：校验订阅配置完整性
        LogisticsProperties.Kd100 kd100 = properties.getKd100();
        if (!StringUtils.hasText(kd100.getKey())
                || !StringUtils.hasText(kd100.getCallbackUrl())
                || !StringUtils.hasText(kd100.getCallbackSalt())) {
            throw BusinessException.param("快递100订阅配置不完整");
        }
        try {
            // 第三步：构建订阅参数 JSON
            String param = buildSubscribeParam(command, companyCode, trackingNo);
            // 构建表单参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("schema", "json");
            params.add("param", param);

            // 第四步：发送 POST 请求到快递100订阅端点
            String response = restTemplate.postForObject(kd100.getSubscribeEndpoint(), params, String.class);
            // 第五步：解析订阅响应
            return parseSubscribeResponse(response, companyCode, trackingNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("快递100订阅失败, trackingNo={}, companyCode={}, error={}",
                    trackingNo, companyCode, e.getMessage(), e);
            throw BusinessException.external("快递100订阅失败: " + e.getMessage());
        }
    }

    /**
     * 查询物流轨迹（简单参数版，委托给命令对象版）。
     *
     * @param companyCode 快递公司编码（如 SF、ZTO、YD 等）
     * @param trackingNo  物流运单号
     * @return 统一格式的物流轨迹结果
     */
    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        // 委托给接受 LogisticsTrackCommand 的重载方法
        return queryTrack(LogisticsTrackCommand.of(companyCode, trackingNo));
    }

    /**
     * 查询物流轨迹（核心方法，接受命令对象）。
     * <p>
     * 向快递100实时查询 API 发送请求，获取物流轨迹信息，并将响应转换为统一格式。
     * </p>
     *
     * <ol>
     *   <li>第一步：校验物流单号和快递公司编码</li>
     *   <li>第二步：标准化快递公司编码（通过 {@link #normalizeCompanyCode}）</li>
     *   <li>第三步：校验手机号（顺丰/中通强制要求，通过 {@link #requiresPhone}）</li>
     *   <li>第四步：调用 {@link #buildParam} 构建查询参数 JSON</li>
     *   <li>第五步：调用 {@link #generateSign} 生成 MD5 签名</li>
     *   <li>第六步：构建表单参数并发送 POST 请求到快递100查询端点</li>
     *   <li>第七步：调用 {@link #parseTrackResponse} 解析响应</li>
     * </ol>
     *
     * @param command 物流查询命令对象（包含公司编码、单号、手机号、出发地/目的地等）
     * @return 统一格式的物流轨迹结果
     * @throws BusinessException 当参数为空、校验失败或查询失败时抛出
     */
    @Override
    public LogisticsTrackResult queryTrack(LogisticsTrackCommand command) {
        // 第一步：校验物流单号
        if (command == null || !StringUtils.hasText(command.getTrackingNo())) {
            throw BusinessException.param("物流单号不能为空");
        }
        String trackingNo = command.getTrackingNo().trim();
        // 第二步：标准化快递公司编码
        String companyCode = normalizeCompanyCode(command.getCompanyCode());
        if (!StringUtils.hasText(companyCode) || "auto".equals(companyCode)) {
            throw BusinessException.param("快递公司编码不能为空，请先识别快递公司");
        }
        // 第三步：校验手机号（顺丰/中通查询快递100强制要求）
        String phone = trimToNull(command.getPhone());
        if (requiresPhone(companyCode) && !StringUtils.hasText(phone)) {
            throw BusinessException.param("顺丰/中通快递100查询需要收件人或寄件人手机号");
        }

        try {
            // 第四步：构建查询参数 JSON
            String param = buildParam(command, companyCode, trackingNo, phone);
            // 第五步：生成签名
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("customer", properties.getKd100().getCustomer());  // 客户编号
            params.add("sign", generateSign(param));                      // MD5 签名
            params.add("param", param);                                   // 查询参数 JSON

            // 第六步：发送 POST 请求到快递100实时查询端点
            String response = restTemplate.postForObject(properties.getKd100().getEndpoint(), params, String.class);
            // 第七步：解析响应
            return parseTrackResponse(response, companyCode, trackingNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("快递100查询失败, trackingNo={}, companyCode={}, error={}",
                    trackingNo, companyCode, e.getMessage(), e);
            throw BusinessException.external("快递100查询失败: " + e.getMessage());
        }
    }

    /**
     * 构建快递100实时查询 API 的查询参数 JSON。
     * <p>
     * 将查询命令中的各字段组装为快递100要求的 JSON 格式参数，
     * 包括快递公司编码、物流单号、手机号（可选）、出发地/目的地（可选）等。
     * </p>
     *
     * @param command     物流查询命令对象
     * @param companyCode 已标准化的快递公司编码（如 shunfeng、zhongtong）
     * @param trackingNo  物流单号
     * @param phone       手机号（可选，顺丰/中通必填）
     * @return 快递100 API 查询参数的 JSON 字符串
     * @throws BusinessException JSON 序列化失败时抛出
     */
    private String buildParam(LogisticsTrackCommand command, String companyCode, String trackingNo, String phone) {
        try {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("com", companyCode);
            param.put("num", trackingNo);
            if (StringUtils.hasText(phone)) {
                param.put("phone", phone);
            }
            putIfPresent(param, "from", command.getFrom());
            putIfPresent(param, "to", command.getTo());
            param.put("resultv2", StringUtils.hasText(command.getResultV2()) ? command.getResultV2().trim() : "4");
            param.put("show", "0");
            param.put("order", "desc");
            param.put("lang", "zh");
            return objectMapper.writeValueAsString(param);
        } catch (Exception e) {
            throw BusinessException.param("构建查询参数失败", e);
        }
    }

    /**
     * 构建快递100轨迹订阅 API 的订阅参数 JSON。
     * <p>
     * 将订阅命令与配置信息组装为快递100要求的嵌套 JSON 格式，
     * 外层包含快递公司编码、物流单号和 key，内层 parameters 包含回调地址、
     * 盐值、手机号（可选）、出发地/目的地（可选）等。
     * </p>
     *
     * @param command     订阅命令对象
     * @param companyCode 已标准化的快递公司编码
     * @param trackingNo  物流单号
     * @return 快递100订阅 API 参数的 JSON 字符串
     * @throws BusinessException JSON 序列化失败时抛出
     */
    private String buildSubscribeParam(LogisticsSubscribeCommand command, String companyCode, String trackingNo) {
        try {
            LogisticsProperties.Kd100 kd100 = properties.getKd100();
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("company", companyCode);
            param.put("number", trackingNo);
            param.put("key", kd100.getKey());

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("callbackurl", kd100.getCallbackUrl().trim());
            parameters.put("salt", kd100.getCallbackSalt().trim());
            parameters.put("resultv2", StringUtils.hasText(kd100.getResultV2()) ? kd100.getResultV2().trim() : "4");
            putIfPresent(parameters, "phone", command.getPhone());
            putIfPresent(parameters, "from", command.getFrom());
            putIfPresent(parameters, "to", command.getTo());
            param.put("parameters", parameters);
            return objectMapper.writeValueAsString(param);
        } catch (Exception e) {
            throw BusinessException.param("构建订阅参数失败", e);
        }
    }

    /**
     * 生成快递100 API 请求签名。
     * <p>签名算法（快递100官方规定）：</p>
     * <ol>
     *   <li>第一步：拼接原始数据 = param（查询参数 JSON）+ key（授权密钥）+ customer（客户编号）</li>
     *   <li>第二步：对拼接数据进行 UTF-8 编码后做 MD5 摘要</li>
     *   <li>第三步：将 MD5 字节数组转为大写十六进制字符串，得到最终签名</li>
     * </ol>
     *
     * @param param 已序列化的查询参数 JSON 字符串
     * @return 大写十六进制 MD5 签名字符串
     * @throws BusinessException 签名生成失败时抛出（如 MD5 算法不可用）
     */
    private String generateSign(String param) {
        try {
            String raw = param + properties.getKd100().getKey() + properties.getKd100().getCustomer();
            byte[] digest = MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw BusinessException.external("生成快递100签名失败", e);
        }
    }

    /**
     * 解析快递100轨迹查询响应，转换为统一的 {@link LogisticsTrackResult}。
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>第一步：如果响应为空，返回失败结果</li>
     *   <li>第二步：将 JSON 反序列化为 {@link Kuaidi100Response} 对象</li>
     *   <li>第三步：如果上游返回失败（status != 200），构建失败结果</li>
     *   <li>第四步：通过 {@link #mapStatus} 映射状态码为内部统一状态</li>
     *   <li>第五步：转换轨迹节点列表</li>
     *   <li>第六步：如果已签收，通过 {@link #resolveSignedAt} 提取签收时间</li>
     *   <li>第七步：组装并返回完整的轨迹结果</li>
     * </ol>
     *
     * @param json        快递100 API 返回的 JSON 响应字符串
     * @param companyCode 快递公司编码（用于响应中无公司编码时的兜底）
     * @param trackingNo  物流单号（用于响应中无单号时的兜底）
     * @return 统一格式的物流轨迹结果
     */
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

        String status = mapStatus(resp.getState());
        List<LogisticsTraceNode> traces = toTraceNodes(resp.getData());
        LocalDateTime signedAt = "SIGNED".equals(status) ? resolveSignedAt(traces) : null;

        return new LogisticsTrackResult(
                resp.getCompanyCode() != null ? resp.getCompanyCode() : companyCode,
                resp.getTrackingNo() != null ? resp.getTrackingNo() : trackingNo,
                true,
                null,
                resp.getState(),
                status,
                "SIGNED".equals(status),
                signedAt,
                traces,
                raw);
    }

    /**
     * 构建查询失败的物流轨迹结果。
     *
     * @param companyCode 快递公司编码
     * @param trackingNo  物流单号
     * @param reason      失败原因描述
     * @param raw         原始响应数据（可为 null，为 null 时使用空 Map）
     * @return 包含失败信息的物流轨迹结果
     */
    private LogisticsTrackResult failedResult(String companyCode, String trackingNo, String reason, Map<String, Object> raw) {
        return new LogisticsTrackResult(
                companyCode, trackingNo, false, reason,
                null, "FAILED", false, null, List.of(), raw != null ? raw : Map.of());
    }

    /**
     * 解析快递100订阅响应，转换为统一的 {@link LogisticsSubscribeResult}。
     * <p>
     * 快递100订阅接口返回码说明：
     * </p>
     * <ul>
     *   <li>"200" - 订阅成功</li>
     *   <li>"501" - 已订阅（重复订阅也视为成功）</li>
     *   <li>其他 - 订阅失败</li>
     * </ul>
     *
     * @param json        快递100订阅 API 返回的 JSON 响应字符串
     * @param companyCode 快递公司编码
     * @param trackingNo  物流单号
     * @return 订阅结果
     */
    private LogisticsSubscribeResult parseSubscribeResponse(String json, String companyCode, String trackingNo) {
        if (json == null || json.isBlank()) {
            return failedSubscribeResult(companyCode, trackingNo, "EMPTY_RESPONSE", "快递100返回空响应", Map.of());
        }
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("解析快递100订阅响应失败: {}", json, e);
            return failedSubscribeResult(companyCode, trackingNo, "PARSE_ERROR", "解析响应失败: " + e.getMessage(), Map.of());
        }
        String returnCode = valueAsString(raw.get("returnCode"));
        String message = valueAsString(raw.get("message"));
        boolean accepted = "200".equals(returnCode) || "501".equals(returnCode);
        return LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .success(accepted)
                .returnCode(returnCode)
                .message(StringUtils.hasText(message) ? message : (accepted ? "订阅成功" : "订阅失败"))
                .rawResponse(raw)
                .build();
    }

    /**
     * 构建订阅失败的订阅结果。
     *
     * @param companyCode 快递公司编码
     * @param trackingNo  物流单号
     * @param returnCode  返回码（如 "EMPTY_RESPONSE"、"PARSE_ERROR"）
     * @param message     失败原因描述
     * @param raw         原始响应数据
     * @return 包含失败信息的订阅结果
     */
    private LogisticsSubscribeResult failedSubscribeResult(
            String companyCode,
            String trackingNo,
            String returnCode,
            String message,
            Map<String, Object> raw) {
        return LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .success(false)
                .returnCode(returnCode)
                .message(message)
                .rawResponse(raw)
                .build();
    }

    /**
     * 映射快递100状态码到内部统一状态。
     * <p>快递100 API 的 state 字段取值：</p>
     * <ul>
     *   <li>"0" - 无轨迹 / "1" - 已揽收 / "7" - 待揽收 / "8" - 疑难件
     *       / "10" / "11" / "12" / "13" - 其他在途状态 → IN_TRANSIT</li>
     *   <li>"3" - 已签收 → SIGNED</li>
     *   <li>"5" - 派件中 → DELIVERING</li>
     *   <li>"2" - 揽收失败 / "4" - 问题件 / "6" - 退件 / "14" - 退回中 → EXCEPTION</li>
     *   <li>其他 → UNKNOWN</li>
     * </ul>
     *
     * @param state 快递100原始状态码
     * @return 内部统一状态字符串（IN_TRANSIT / SIGNED / DELIVERING / EXCEPTION / UNKNOWN）
     */
    private String mapStatus(String state) {
        if (!StringUtils.hasText(state)) {
            return "UNKNOWN";
        }
        return switch (state.trim()) {
            case "3" -> "SIGNED";
            case "5" -> "DELIVERING";
            case "2", "4", "6", "14" -> "EXCEPTION";
            case "0", "1", "7", "8", "10", "11", "12", "13" -> "IN_TRANSIT";
            default -> "UNKNOWN";
        };
    }

    /**
     * 将快递100轨迹节点列表转换为内部统一的 {@link LogisticsTraceNode} 列表。
     * <p>
     * 过滤 null 节点，优先使用 formattedTime（格式化后的时间），
     * 若不存在则回退到 time（原始时间），并解析为 LocalDateTime。
     * </p>
     *
     * @param nodes 快递100轨迹节点列表（可能为 null 或空）
     * @return 内部统一格式的轨迹节点列表
     */
    private List<LogisticsTraceNode> toTraceNodes(List<Kuaidi100Response.TraceNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(Objects::nonNull)
                .map(n -> new LogisticsTraceNode(
                        parseTime(StringUtils.hasText(n.getFormattedTime()) ? n.getFormattedTime() : n.getTime()),
                        n.getContext(),
                        n.getLocation()))
                .toList();
    }

    /**
     * 从轨迹节点列表中提取签收时间。
     * <p>
     * 查找轨迹描述中包含"签收"或"已取件"关键字的节点，取第一个匹配节点的时间作为签收时间。
     * 如果没有找到匹配节点，则使用第一条轨迹的时间作为兜底。
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
                .filter(t -> t.acceptStation() != null &&
                        (t.acceptStation().contains("签收") || t.acceptStation().contains("已取件")))
                .map(LogisticsTraceNode::acceptTime)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> traces.get(0).acceptTime());
    }

    /**
     * 解析快递100轨迹时间字符串为 LocalDateTime。
     * <p>快递100返回的时间格式为 yyyy-MM-dd HH:mm:ss，解析失败时返回 null。</p>
     *
     * @param value 时间字符串（可能为 null 或空）
     * @return 解析后的 LocalDateTime，解析失败时返回 null
     */
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

    /**
     * 标准化快递公司编码。
     * <p>
     * 先在 {@link #COMPANY_CODE_ALIASES} 别名表中查找（忽略大小写），
     * 如果找到映射则返回标准编码（如 "SF" -> "shunfeng"），
     * 否则将输入转为小写作为默认值（如 "Deppon" -> "deppon"）。
     * </p>
     *
     * @param companyCode 原始快递公司编码（如 SF、ZTO、deppon 等）
     * @return 标准化后的快递公司编码，输入为空时返回 null
     */
    private String normalizeCompanyCode(String companyCode) {
        if (!StringUtils.hasText(companyCode)) {
            return null;
        }
        String trimmed = companyCode.trim();
        String mapped = COMPANY_CODE_ALIASES.get(trimmed.toUpperCase(Locale.ROOT));
        return mapped != null ? mapped : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 判断指定快递公司是否强制要求手机号查询。
     * <p>快递100 API 要求顺丰（shunfeng）和中通（zhongtong）查询时必须提供收件人或寄件人手机号。</p>
     *
     * @param companyCode 已标准化的快递公司编码
     * @return true 表示需要手机号，false 表示不需要
     */
    private boolean requiresPhone(String companyCode) {
        return "shunfeng".equals(companyCode) || "zhongtong".equals(companyCode);
    }

    /**
     * 将非空的键值对放入参数 Map。
     * <p>当 value 非空且非空白时，trim 后放入 Map；否则忽略。</p>
     *
     * @param param 目标参数 Map
     * @param key   参数键名
     * @param value 参数值（可为 null 或空白）
     */
    private void putIfPresent(Map<String, Object> param, String key, String value) {
        if (StringUtils.hasText(value)) {
            param.put(key, value.trim());
        }
    }

    /**
     * 将字符串 trim 后返回，空白字符串返回 null。
     *
     * @param value 原始字符串
     * @return trim 后的字符串，空白时返回 null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 将对象转换为字符串表示，null 值直接返回 null。
     *
     * @param value 任意对象
     * @return 字符串表示，null 输入返回 null
     */
    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
