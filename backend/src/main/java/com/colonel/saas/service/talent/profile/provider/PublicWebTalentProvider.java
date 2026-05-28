package com.colonel.saas.service.talent.profile.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.profile.TalentProfileFieldNames;
import com.colonel.saas.service.talent.profile.TalentProfileProvider;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公开页面爬取模式的达人资料提供者 —— 通过访问抖音达人公开主页 HTML 提取资料。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>解析用户输入，定位达人公开主页 URL（支持直接 URL、secUid、抖音号等）</li>
 *   <li>使用模拟浏览器 User-Agent 请求达人主页 HTML</li>
 *   <li>从 HTML 中提取嵌入的 JSON 数据（RENDER_DATA 或内联 JSON 片段）</li>
 *   <li>通过正则表达式从 HTML 或 JSON 中提取达人资料字段（昵称、头像、粉丝数等）</li>
 *   <li>检测反爬机制（验证码/captcha）并返回相应错误码</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为 {@link TalentProfileProvider} 策略链中的第二优先级提供者（order=20），
 * 在抖音官方 API 不可用时作为主要的数据采集通道。
 * 需要配置 {@code talent.profile.public-web.enabled=true} 且采集属性允许爬虫模式。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域 / 公开页面爬取通道</p>
 *
 * @see TalentProfileProvider
 * @see TalentCollectProperties
 */
@Component
public class PublicWebTalentProvider implements TalentProfileProvider {

    /** HTML 中 JSON 片段的正则匹配模式，用于提取包含 nickname 的嵌入 JSON */
    private static final Pattern JSON_SNIPPET = Pattern.compile("\\{[^{}]{0,20000}?\"nickname\"[^{}]{0,20000}?\\}");

    /** 模拟 Chrome 浏览器的 User-Agent，降低被反爬机制拦截的概率 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /** Jackson JSON 序列化/反序列化器 */
    private final ObjectMapper objectMapper;

    /** 是否启用本提供者（配置项：{@code talent.profile.public-web.enabled}，默认 true） */
    private final boolean enabled;

    /** 采集配置属性，控制是否允许爬虫模式和是否为 mock 模式 */
    private final TalentCollectProperties collectProperties;

    /**
     * 构造方法 —— 通过 Spring 注入依赖。
     *
     * @param objectMapper      JSON 序列化器
     * @param collectProperties 采集配置属性
     * @param enabled           是否启用本提供者（默认 true）
     */
    public PublicWebTalentProvider(
            ObjectMapper objectMapper,
            TalentCollectProperties collectProperties,
            @Value("${talent.profile.public-web.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.collectProperties = collectProperties;
        this.enabled = enabled;
    }

    /** {@inheritDoc} 返回提供者唯一标识 "CRAWLER"，表示公开页面爬取通道 */
    @Override
    public String providerCode() {
        return "CRAWLER";
    }

    /** {@inheritDoc} 返回优先级 20，在抖音 API(5) 和可配置 HTTP(10) 之后 */
    @Override
    public int order() {
        return 20;
    }

    /**
     * {@inheritDoc}
     *
     * <p>判断是否支持当前查询，需同时满足：</p>
     * <ol>
     *   <li>本提供者已启用</li>
     *   <li>采集配置允许爬虫类采集</li>
     *   <li>不是仅 mock 模式</li>
     *   <li>查询对象非空且包含有效输入</li>
     *   <li>不是手动填写模式</li>
     * </ol>
     */
    @Override
    public boolean supports(TalentProfileQuery query) {
        return enabled
                && collectProperties.isCrawlerAllowed()
                && !collectProperties.isMockOnly()
                && query != null
                && StringUtils.hasText(query.getInput())
                && !query.isManualFill();
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过爬取达人公开主页获取资料。</p>
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：解析目标 URL（支持直接 URL、secUid 拼接、抖音号拼接）</li>
     *   <li>第二步：使用模拟浏览器 User-Agent 发起 HTTP GET 请求</li>
     *   <li>第三步：检查 HTTP 状态码和响应体有效性</li>
     *   <li>第四步：检测反爬机制（验证码/captcha）</li>
     *   <li>第五步：解析 HTML 提取达人资料字段</li>
     * </ol>
     *
     * @param query 达人资料查询请求
     * @return 采集结果（成功时包含达人资料，失败时包含错误信息）
     */
    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        // 第一步：解析目标达人主页 URL
        String targetUrl = resolveTargetUrl(query);
        if (!StringUtils.hasText(targetUrl)) {
            return failed("PUBLIC_WEB_NO_URL", "cannot resolve public profile url from input");
        }
        try {
            // 第二步：使用模拟浏览器 User-Agent 请求达人主页
            HttpResponse response = HttpRequest.get(targetUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/json")
                    .timeout(15_000)
                    .execute();
            // 第三步：检查 HTTP 状态码
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return failed("PUBLIC_WEB_HTTP_" + response.getStatus(),
                        "public page request failed with status " + response.getStatus());
            }
            String body = response.body();
            if (!StringUtils.hasText(body)) {
                return failed("PUBLIC_WEB_EMPTY_BODY", "public page response body is empty");
            }
            // 第四步：检测反爬机制（验证码/captcha）
            if (body.contains("验证码") || body.toLowerCase().contains("captcha")) {
                return failed("PUBLIC_WEB_BLOCKED", "public page blocked by anti-bot challenge");
            }
            // 第五步：解析 HTML 提取达人资料
            return parseHtml(body, targetUrl, query);
        } catch (Exception ex) {
            return failed("PUBLIC_WEB_ERROR", ex.getMessage());
        }
    }

    /**
     * 解析达人公开主页的目标 URL。
     * 按优先级尝试以下策略：
     * <ol>
     *   <li>已解析的 profileUrl 直接使用</li>
     *   <li>用户输入本身就是 URL（以 http:// 或 https:// 开头）</li>
     *   <li>使用已解析的 secUid 拼接抖音主页 URL</li>
     *   <li>使用原始输入文本拼接抖音主页 URL</li>
     * </ol>
     *
     * @param query 达人资料查询请求
     * @return 解析出的目标 URL，无法解析时返回 null
     */
    private String resolveTargetUrl(TalentProfileQuery query) {
        TalentInputParseResult parsed = query.getParsed();
        // 策略一：优先使用已解析的主页 URL
        if (parsed != null && StringUtils.hasText(parsed.getProfileUrl())) {
            return parsed.getProfileUrl().trim();
        }
        // 策略二：用户输入本身就是完整 URL
        String input = query.getInput().trim();
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        }
        // 策略三：使用 secUid 拼接抖音用户主页
        if (parsed != null && StringUtils.hasText(parsed.getSecUid())) {
            return "https://www.douyin.com/user/" + parsed.getSecUid();
        }
        // 策略四：使用原始输入文本拼接
        if (StringUtils.hasText(input)) {
            return "https://www.douyin.com/user/" + input;
        }
        return null;
    }

    /**
     * 解析达人主页 HTML 内容，提取达人资料字段。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：尝试从 HTML 中提取嵌入的 JSON 数据</li>
     *   <li>第二步：通过正则从 HTML 和嵌入 JSON 中提取各字段
     *       （昵称、头像、粉丝数、点赞数、关注数、作品数、IP 属地、secUid、抖音号）</li>
     *   <li>第三步：记录成功获取的字段列表</li>
     *   <li>第四步：无有效字段时返回失败，否则构建成功结果</li>
     * </ol>
     *
     * @param body  主页 HTML 原始内容
     * @param url   请求的目标 URL（记录到 rawPayload 中）
     * @param query 原始查询请求
     * @return 解析后的达人资料结果
     */
    private TalentProfileResult parseHtml(String body, String url, TalentProfileQuery query) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("sourceUrl", url);
        raw.put("htmlLength", body.length());

        JsonNode embedded = extractEmbeddedJson(body);
        if (embedded != null) {
            raw.put("embeddedJson", objectMapper.convertValue(embedded, Map.class));
        }

        String nickname = firstText(body, embedded, "\"nickname\"\\s*:\\s*\"([^\"]+)\"");
        String avatarUrl = firstText(body, embedded, "\"avatarUrl\"\\s*:\\s*\"([^\"]+)\"", "\"avatar_url\"\\s*:\\s*\"([^\"]+)\"");
        Long fans = firstLong(body, embedded, "\"follower_count\"\\s*:\\s*(\\d+)", "\"followerCount\"\\s*:\\s*(\\d+)", "\"fans_count\"\\s*:\\s*(\\d+)");
        Long likes = firstLong(body, embedded, "\"total_favorited\"\\s*:\\s*(\\d+)", "\"totalFavorited\"\\s*:\\s*(\\d+)", "\"like_count\"\\s*:\\s*(\\d+)");
        Long following = firstLong(body, embedded, "\"following_count\"\\s*:\\s*(\\d+)", "\"followingCount\"\\s*:\\s*(\\d+)");
        Long works = firstLong(body, embedded, "\"aweme_count\"\\s*:\\s*(\\d+)", "\"awemeCount\"\\s*:\\s*(\\d+)", "\"works_count\"\\s*:\\s*(\\d+)");
        String ip = firstText(body, embedded, "\"ip_location\"\\s*:\\s*\"([^\"]+)\"", "\"ipLocation\"\\s*:\\s*\"([^\"]+)\"");
        String secUid = firstText(body, embedded, "\"sec_uid\"\\s*:\\s*\"([^\"]+)\"", "\"secUid\"\\s*:\\s*\"([^\"]+)\"");
        String uniqueId = firstText(body, embedded, "\"unique_id\"\\s*:\\s*\"([^\"]+)\"", "\"uniqueId\"\\s*:\\s*\"([^\"]+)\"");

        List<String> fetched = new ArrayList<>();
        List<String> unsupported = new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED);
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
        if (StringUtils.hasText(uniqueId)) {
            fetched.add(TalentProfileFieldNames.DOUYIN_ACCOUNT);
        }
        if (StringUtils.hasText(secUid)) {
            fetched.add(TalentProfileFieldNames.SEC_UID);
        }

        if (fetched.isEmpty()) {
            return failed("PUBLIC_WEB_PARSE_EMPTY", "no public profile fields parsed from page");
        }

        String syncStatus = unsupported.isEmpty()
                ? TalentProfileResult.STATUS_SUCCESS
                : TalentProfileResult.STATUS_PARTIAL_SUCCESS;
        return TalentProfileResult.builder()
                .success(true)
                .providerCode(providerCode())
                .syncStatus(syncStatus)
                .douyinAccount(uniqueId)
                .talentUid(query.getParsed() == null ? null : query.getParsed().getUid())
                .secUid(secUid)
                .nickname(nickname)
                .avatarUrl(decodeUrl(avatarUrl))
                .fansCount(fans)
                .likeCount(likes)
                .followingCount(following)
                .worksCount(works)
                .ipLocation(ip)
                .fetchedFields(fetched)
                .unsupportedFields(unsupported)
                .rawPayload(raw)
                .build();
    }

    /**
     * 从 HTML 内容中提取嵌入的 JSON 数据。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：使用正则匹配包含 "nickname" 的 JSON 片段</li>
     *   <li>第二步：若正则未命中，尝试从 RENDER_DATA 标记中提取 URL 编码的 JSON</li>
     * </ol>
     *
     * @param body HTML 原始内容
     * @return 提取到的 JSON 根节点，未找到时返回 null
     */
    private JsonNode extractEmbeddedJson(String body) {
        Matcher matcher = JSON_SNIPPET.matcher(body);
        while (matcher.find()) {
            String snippet = matcher.group();
            try {
                return objectMapper.readTree(snippet);
            } catch (Exception ignored) {
                // try next
            }
        }
        int renderIdx = body.indexOf("RENDER_DATA");
        if (renderIdx >= 0) {
            int start = body.indexOf('{', renderIdx);
            int end = body.indexOf("</script>", start);
            if (start >= 0 && end > start) {
                String encoded = body.substring(start, end).trim();
                try {
                    String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                    return objectMapper.readTree(decoded);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 按优先级从 HTML 正文或嵌入 JSON 节点中提取第一个匹配的文本值。
     * 先尝试 HTML 正则匹配，再尝试从 JSON 节点中查找。
     *
     * @param body     HTML 原始内容
     * @param node     嵌入的 JSON 节点（可为 null）
     * @param patterns 正则模式列表（每个模式应包含一个捕获组）
     * @return 第一个匹配的文本值，全部未命中时返回 null
     */
    private String firstText(String body, JsonNode node, String... patterns) {
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (node != null) {
            String fromNode = textFromNode(node, "nickname");
            if (StringUtils.hasText(fromNode)) {
                return fromNode;
            }
        }
        return null;
    }

    /**
     * 从 HTML 正文中提取第一个匹配的数值字段。
     *
     * @param body     HTML 原始内容
     * @param node     嵌入的 JSON 节点（未使用，保留用于扩展）
     * @param patterns 正则模式列表（每个模式应包含一个数字捕获组）
     * @return 第一个匹配的 Long 值，全部未命中时返回 null
     */
    private Long firstLong(String body, JsonNode node, String... patterns) {
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(body);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        return null;
    }

    /**
     * 从 JSON 节点中按字段名提取文本值。
     *
     * @param node  JSON 根节点
     * @param field 字段名
     * @return 字段的文本值，字段不存在或为 null 时返回 null
     */
    private String textFromNode(JsonNode node, String field) {
        JsonNode value = node.findValue(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 解码 URL 中的 Unicode 转义字符（如 {@code /} → {@code /}）。
     *
     * @param url 原始 URL 字符串
     * @return 解码后的 URL，输入为空时原样返回
     */
    private String decodeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        return url.replace("\\u002F", "/").replace("\\/", "/");
    }

    /**
     * 构建失败的采集结果对象。
     *
     * @param code    错误码（如 "PUBLIC_WEB_BLOCKED"、"PUBLIC_WEB_ERROR"）
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
