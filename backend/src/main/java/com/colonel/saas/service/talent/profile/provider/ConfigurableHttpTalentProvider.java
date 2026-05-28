package com.colonel.saas.service.talent.profile.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.service.talent.profile.TalentProfileFieldNames;
import com.colonel.saas.service.talent.profile.TalentProfileProvider;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可配置的 HTTP 达人资料采集提供者。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>通过配置化 HTTP 端点（endpoint、method、token）向外部服务请求达人资料</li>
 *   <li>支持 GET / POST 两种 HTTP 方法，GET 携带查询参数，POST 携带 JSON 请求体</li>
 *   <li>解析外部服务返回的 JSON 响应，映射为统一的 {@link TalentProfileResult} 结构</li>
 *   <li>容错处理：HTTP 状态码非 2xx 时返回失败结果，JSON 解析异常时返回错误信息</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为 {@link TalentProfileProvider} 策略链中的一员（order=10），
 * 优先级介于抖音官方 API（order=5）和公开页面爬虫（order=20）之间。
 * 当配置属性 {@code talent.profile.http.enabled=true} 且端点已配置时生效。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentProfileProvider
 * @see TalentCollectProperties
 */
@Component
public class ConfigurableHttpTalentProvider implements TalentProfileProvider {

    /** Jackson JSON 序列化/反序列化器 */
    private final ObjectMapper objectMapper;

    /** 采集配置属性，控制是否允许 API 采集、是否为 mock 模式等 */
    private final TalentCollectProperties collectProperties;

    /** 是否启用本提供者（配置项：{@code talent.profile.http.enabled}，默认 false） */
    private final boolean enabled;

    /** 外部 HTTP 服务端点地址（配置项：{@code talent.profile.http.endpoint}） */
    private final String endpoint;

    /** HTTP 请求方法，GET 或 POST（配置项：{@code talent.profile.http.method}，默认 GET） */
    private final String method;

    /** Bearer Token，用于构造 Authorization 请求头 */
    private final String token;

    /** 自定义 HTTP 请求头集合，包含 Authorization 头 */
    private final Map<String, String> headers;

    /**
     * 构造方法 —— 通过 Spring 注入配置属性，初始化 HTTP 采集提供者。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>注入 Jackson ObjectMapper、采集配置属性和各配置项</li>
     *   <li>如果配置了自定义 Authorization 头（{@code header-authorization}），优先使用</li>
     *   <li>否则如果配置了 Token（{@code token}），自动拼接为 "Bearer {token}" 格式</li>
     * </ol>
     *
     * @param objectMapper       JSON 序列化器
     * @param collectProperties  采集配置属性
     * @param enabled            是否启用本提供者
     * @param endpoint           外部 HTTP 端点地址
     * @param method             HTTP 方法（GET/POST）
     * @param token              Bearer Token
     * @param authorizationHeader 自定义 Authorization 头（优先级高于 token）
     */
    public ConfigurableHttpTalentProvider(
            ObjectMapper objectMapper,
            TalentCollectProperties collectProperties,
            @Value("${talent.profile.http.enabled:false}") boolean enabled,
            @Value("${talent.profile.http.endpoint:}") String endpoint,
            @Value("${talent.profile.http.method:GET}") String method,
            @Value("${talent.profile.http.token:}") String token,
            @Value("${talent.profile.http.header-authorization:}") String authorizationHeader) {
        this.objectMapper = objectMapper;
        this.collectProperties = collectProperties;
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.method = method;
        this.token = token;
        // 第一步：构建自定义请求头集合
        this.headers = new LinkedHashMap<>();
        if (StringUtils.hasText(authorizationHeader)) {
            // 优先使用用户直接配置的完整 Authorization 头
            this.headers.put("Authorization", authorizationHeader);
        } else if (StringUtils.hasText(token)) {
            // 其次使用 Token 自动拼接为 Bearer 格式
            this.headers.put("Authorization", "Bearer " + token.trim());
        }
    }

    /** {@inheritDoc} 返回提供者唯一标识 "configurable_http" */
    @Override
    public String providerCode() {
        return "configurable_http";
    }

    /** {@inheritDoc} 返回优先级 10，介于抖音官方 API(5) 和公开页面爬虫(20) 之间 */
    @Override
    public int order() {
        return 10;
    }

    /**
     * {@inheritDoc}
     *
     * <p>判断是否支持当前查询，需同时满足以下条件：</p>
     * <ol>
     *   <li>本提供者已启用（{@code talent.profile.http.enabled=true}）</li>
     *   <li>采集配置允许 API 类采集</li>
     *   <li>不是仅 mock 模式</li>
     *   <li>外部端点地址已配置</li>
     *   <li>查询对象非空，且包含有效输入文本</li>
     *   <li>不是手动填写模式（手动填写由 {@code ManualTalentProvider} 处理）</li>
     * </ol>
     */
    @Override
    public boolean supports(TalentProfileQuery query) {
        return enabled
                && collectProperties.isApiAllowed()
                && !collectProperties.isMockOnly()
                && StringUtils.hasText(endpoint)
                && query != null
                && StringUtils.hasText(query.getInput())
                && !query.isManualFill();
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过配置化 HTTP 端点获取达人资料。</p>
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：构建 HTTP 请求，设置端点、方法、超时（15 秒）和自定义请求头</li>
     *   <li>第二步：根据请求方法填充参数
     *     <ul>
     *       <li>GET：将 input、secUid、douyinAccount 作为查询参数</li>
     *       <li>POST：将 input 和解析结果作为 JSON 请求体</li>
     *     </ul>
     *   </li>
     *   <li>第三步：执行请求并检查 HTTP 状态码（非 2xx 返回失败）</li>
     *   <li>第四步：解析响应 JSON 为 {@link TalentProfileResult} 结构</li>
     * </ol>
     *
     * @param query 达人资料查询请求
     * @return 采集结果（成功时包含达人资料字段，失败时包含错误码和错误信息）
     */
    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        try {
            // 第一步：构建 HTTP 请求对象，配置端点、方法和超时
            HttpRequest request = HttpRequest.of(endpoint.trim())
                    .method(parseMethod(method))
                    .timeout(15_000);
            // 注入自定义请求头（Authorization 等）
            headers.forEach(request::header);
            if (Method.GET.equals(parseMethod(method))) {
                // 第二步（GET 分支）：将达人输入作为查询参数传递
                request.form("input", query.getInput());
                if (query.getParsed() != null && StringUtils.hasText(query.getParsed().getSecUid())) {
                    request.form("secUid", query.getParsed().getSecUid());
                }
                if (query.getParsed() != null && StringUtils.hasText(query.getParsed().getDouyinNo())) {
                    request.form("douyinAccount", query.getParsed().getDouyinNo());
                }
            } else {
                // 第二步（POST 分支）：将达人输入和解析结果序列化为 JSON 请求体
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("input", query.getInput());
                if (query.getParsed() != null) {
                    body.put("parsed", query.getParsed());
                }
                request.body(objectMapper.writeValueAsString(body), "application/json");
            }
            // 第三步：执行 HTTP 请求
            HttpResponse response = request.execute();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return failed("HTTP_STATUS_" + response.getStatus(), "configurable http provider returned status " + response.getStatus());
            }
            // 第四步：解析响应 JSON 为统一的达人资料结果
            return mapResponse(response.body(), query.getInput());
        } catch (Exception ex) {
            return failed("HTTP_PROVIDER_ERROR", ex.getMessage());
        }
    }

    /**
     * 将外部 HTTP 服务返回的 JSON 响应体解析为统一的达人资料结果。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：解析 JSON 根节点，兼容 {@code root.data} 或 {@code root} 两种结构</li>
     *   <li>第二步：依次提取各达人资料字段（昵称、头像、粉丝数等），
     *       每个字段支持多种 JSON key 命名风格（驼峰和下划线）</li>
     *   <li>第三步：记录已成功获取的字段列表和不支持的字段列表</li>
     *   <li>第四步：构建并返回 {@link TalentProfileResult}，包含所有解析出的资料数据</li>
     * </ol>
     *
     * @param body  外部 HTTP 服务返回的原始 JSON 响应体
     * @param input 用户原始输入（用于兜底 rawPayload）
     * @return 解析后的达人资料结果
     * @throws Exception JSON 解析失败时抛出异常
     */
    private TalentProfileResult mapResponse(String body, String input) throws Exception {
        // 第一步：解析 JSON 根节点，兼容嵌套 data 结构
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data").isMissingNode() ? root : root.path("data");
        List<String> fetched = new ArrayList<>();
        List<String> unsupported = new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED);

        // 第二步：提取各达人资料字段，支持多种 JSON key 命名风格
        String nickname = text(data, "nickname");
        String avatarUrl = text(data, "avatarUrl", "avatar_url");
        Long fans = number(data, "fansCount", "fans_count");
        Long likes = number(data, "likeCount", "like_count", "likes_count");
        Long following = number(data, "followingCount", "following_count");
        Long works = number(data, "worksCount", "works_count");
        String ip = text(data, "ipLocation", "ip_location");
        String level = text(data, "talentLevel", "talent_level");
        Long sales = number(data, "sales30d", "sales_30d");

        // 第三步：记录已成功获取的字段，对于通常不支持的字段（达人等级、30天销售额），
        //         一旦成功获取则从 unsupported 列表中移除
        if (StringUtils.hasText(nickname)) {
            fetched.add(TalentProfileFieldNames.NICKNAME);
        }
        if (StringUtils.hasText(avatarUrl)) {
            fetched.add(TalentProfileFieldNames.AVATAR_URL);
        }
        if (fans != null) {
            fetched.add(TalentProfileFieldNames.FANS_COUNT);
        }
        if (likes != null) {
            fetched.add(TalentProfileFieldNames.LIKE_COUNT);
        }
        if (following != null) {
            fetched.add(TalentProfileFieldNames.FOLLOWING_COUNT);
        }
        if (works != null) {
            fetched.add(TalentProfileFieldNames.WORKS_COUNT);
        }
        if (StringUtils.hasText(ip)) {
            fetched.add(TalentProfileFieldNames.IP_LOCATION);
        }
        if (StringUtils.hasText(level)) {
            fetched.add(TalentProfileFieldNames.TALENT_LEVEL);
            unsupported.remove(TalentProfileFieldNames.TALENT_LEVEL);
        }
        if (sales != null) {
            fetched.add(TalentProfileFieldNames.SALES_30D);
            unsupported.remove(TalentProfileFieldNames.SALES_30D);
        }

        Map<String, Object> raw = objectMapper.convertValue(root, Map.class);
        if (fetched.isEmpty()) {
            return failed("HTTP_EMPTY_PROFILE", "configurable http provider returned no profile fields");
        }
        String syncStatus = unsupported.isEmpty() ? TalentProfileResult.STATUS_SUCCESS : TalentProfileResult.STATUS_PARTIAL_SUCCESS;
        return TalentProfileResult.builder()
                .success(true)
                .providerCode(providerCode())
                .syncStatus(syncStatus)
                .douyinAccount(text(data, "douyinAccount", "douyin_account"))
                .talentUid(text(data, "talentUid", "talent_uid", "uid"))
                .secUid(text(data, "secUid", "sec_uid"))
                .nickname(nickname)
                .avatarUrl(avatarUrl)
                .fansCount(fans)
                .likeCount(likes)
                .followingCount(following)
                .worksCount(works)
                .ipLocation(ip)
                .talentLevel(level)
                .sales30d(sales)
                .fetchedFields(fetched)
                .unsupportedFields(unsupported)
                .rawPayload(raw == null ? Map.of("input", input, "body", body) : raw)
                .build();
    }

    /**
     * 将字符串形式的 HTTP 方法名转换为 {@link Method} 枚举。
     *
     * @param raw HTTP 方法字符串（如 "GET"、"POST"），为空时默认 GET
     * @return 解析后的 Method 枚举值
     */
    private Method parseMethod(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Method.GET;
        }
        return Method.valueOf(raw.trim().toUpperCase());
    }

    /**
     * 从 JSON 节点中按优先级尝试提取文本字段值。
     * 依次尝试多个候选字段名（如驼峰 "avatarUrl" 和下划线 "avatar_url"），
     * 返回第一个非空非缺失的字段值。
     *
     * @param node   JSON 根节点或数据节点
     * @param fields 候选字段名列表（按优先级排列）
     * @return 第一个有效字段的文本值，全部无效时返回 null
     */
    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    /**
     * 从 JSON 节点中按优先级尝试提取数值字段值。
     * 支持 JSON 数值类型和字符串类型的数值，兼容不同 API 的返回格式。
     *
     * @param node   JSON 根节点或数据节点
     * @param fields 候选字段名列表（按优先级排列）
     * @return 第一个有效字段的 Long 值，全部无效时返回 null
     */
    private Long number(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asLong();
                }
                String text = value.asText("").trim();
                if (StringUtils.hasText(text)) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 构建失败的采集结果对象。
     *
     * @param code    错误码（如 "HTTP_STATUS_500"、"HTTP_PROVIDER_ERROR"）
     * @param message 错误描述信息
     * @return 包含错误信息的失败结果
     */
    private TalentProfileResult failed(String code, String message) {
        return TalentProfileResult.builder()
                .success(false)
                .providerCode(providerCode())
                .syncStatus(TalentProfileResult.STATUS_FAILED)
                .errorCode(code)
                .errorMessage(message)
                .unsupportedFields(new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED))
                .build();
    }
}
