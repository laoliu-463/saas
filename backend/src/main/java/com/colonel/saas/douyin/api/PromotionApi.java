package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.util.ShortCodeGenerator;
import com.colonel.saas.service.PickSourceMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * 精选联盟推广链接生成 API 客户端。
 * <p>
 * 封装抖音精选联盟推广链接的生成与转换逻辑，支持多种 API 方法的自动降级，
 * 并在生成链接后自动保存 pick_source 映射关系。
 *
 * <ul>
 *   <li>推广链接生成 — 通过商品 ID 或 sourceUrl 生成推广链接</li>
 *   <li>多方法降级 — 主方法不可用时自动尝试备选 API 方法</li>
 *   <li>pick_source 管理 — 生成短码作为 pick_source 并持久化映射</li>
 *   <li>pick_extra 标准化 — 对推广附加标识进行格式规范化</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 推广管理
 *
 * @see DouyinApiClient
 * @see PickSourceMappingService
 * @see ShortCodeGenerator
 */
@Service
@Slf4j
public class PromotionApi {

    /** 机构精选联盟转链接接口方法名 */
    private static final String INST_PICK_SOURCE_CONVERT_METHOD = "buyin.instPickSourceConvert";
    private static final String LEGACY_METHOD = "buyin.promotion.link.generate";
    private static final String FALLBACK_METHOD_1 = "buyin.kolProductShare";
    private static final String FALLBACK_METHOD_2 = "buyin.getProductShareMaterial";
    private static final int MAX_PICK_EXTRA_LENGTH = 20;

    private final DouyinApiClient douyinApiClient;
    private final PickSourceMappingService pickSourceMappingService;

    public PromotionApi(DouyinApiClient douyinApiClient, PickSourceMappingService pickSourceMappingService) {
        this.douyinApiClient = douyinApiClient;
        this.pickSourceMappingService = pickSourceMappingService;
    }

    /**
     * 生成推广链接（简化参数，无上下文）。
     * <p>
     * 委托至完整参数版本 {@link #generateLink(String, int, List, boolean, PromotionContext)}，
     * 上下文传 null 表示不需要保存 pick_source 映射。
     *
     * @param externalUniqueId  外部唯一标识
     * @param promotionScene    推广场景
     * @param productIds        商品 ID 列表
     * @param needShortLink     是否需要短链接
     * @return 推广链接结果
     */
    public PromotionLinkResult generateLink(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink
    ) {
        return generateLink(externalUniqueId, promotionScene, productIds, needShortLink, null);
    }

    /**
     * 生成推广链接（完整参数，支持 sourceUrl 转链）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>生成 UUID 种子并编码为 8 位 Base36 短码作为 pick_source</li>
     *   <li>标准化 pick_extra 推广附加标识</li>
     *   <li>若上下文中包含 sourceUrl，走机构转链接接口（{@code buyin.instPickSourceConvert}）</li>
     *   <li>否则走推广链接生成接口（含多方法降级策略）</li>
     *   <li>解析响应并保存 pick_source 映射关系</li>
     * </ol>
     *
     * @param externalUniqueId 外部唯一标识
     * @param promotionScene   推广场景
     * @param productIds       商品 ID 列表
     * @param needShortLink    是否需要短链接
     * @param context          推广上下文（含用户、部门、商品、sourceUrl 等信息，可为空）
     * @return 推广链接结果，包含 pick_source、推广链接等信息
     */
    public PromotionLinkResult generateLink(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink,
            PromotionContext context
    ) {
        // 第一步：生成 UUID 种子并编码为 8 位 Base36 短码作为 pick_source
        UUID uuidSeed = UUID.randomUUID();
        String shortId = ShortCodeGenerator.generate(uuidSeed);
        // 第二步：标准化 pick_extra 推广附加标识
        String normalizedPickExtra = normalizePickExtra(context == null ? null : context.pickExtra());

        // 第三步：若上下文包含 sourceUrl，走机构转链接接口
        if (context != null && StringUtils.hasText(context.sourceUrl())) {
            Map<String, Object> response = convertBySourceUrl(context.sourceUrl(), normalizedPickExtra);
            PromotionLinkResult result = PromotionLinkResult.from(
                    response,
                    shortId,
                    uuidSeed.toString(),
                    normalizedPickExtra
            );
            // 第四步：条件性保存 pick_source 映射关系
            saveMappingIfNecessary(result, uuidSeed, context);
            return result;
        }

        // 第五步：组装推广链接生成请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("external_unique_id", externalUniqueId);
        params.put("promotion_scene", promotionScene);
        params.put("product_ids", productIds);
        params.put("need_short_link", needShortLink);

        // 第六步：附加 UUID 种子和 pick_source 到 extra 字段
        Map<String, Object> extra = new HashMap<>();
        extra.put("uuid_seed", uuidSeed.toString());
        extra.put("pick_source", shortId);
        params.put("extra", extra);

        // 第七步：带降级策略的推广链接生成请求
        Map<String, Object> response = postWithFallback(params);
        // 第八步：解析上游响应，构建结果并保存映射
        PromotionLinkResult result = PromotionLinkResult.from(
                response,
                shortId,
                uuidSeed.toString(),
                normalizedPickExtra
        );
        saveMappingIfNecessary(result, uuidSeed, context);
        return result;
    }

    /**
     * 通过 sourceUrl 调用机构转链接接口。
     * <p>
     * 使用 {@code buyin.instPickSourceConvert} 方法将商品原始链接转换为带推广标识的链接。
     *
     * @param productUrl 商品原始链接（sourceUrl）
     * @param pickExtra  标准化后的推广附加标识
     * @return 上游 API 响应
     */
    private Map<String, Object> convertBySourceUrl(String productUrl, String pickExtra) {
        Map<String, Object> params = new HashMap<>();
        params.put("product_url", productUrl);
        params.put("pick_extra", pickExtra);
        return douyinApiClient.post(INST_PICK_SOURCE_CONVERT_METHOD, params);
    }

    /**
     * 条件性保存 pick_source 映射关系。
     * <p>
     * 当上下文中包含 userId 且结果中包含有效 pick_source 时，
     * 调用 {@link PickSourceMappingService#saveOrUpdate} 持久化映射记录。
     *
     * @param result   推广链接结果
     * @param uuidSeed UUID 种子
     * @param context  推广上下文（可为空，为空时跳过保存）
     */
    private void saveMappingIfNecessary(PromotionLinkResult result, UUID uuidSeed, PromotionContext context) {
        if (context == null || context.userId() == null || result == null || !StringUtils.hasText(result.pickSource())) {
            return;
        }
        pickSourceMappingService.saveOrUpdate(
                context.userId(),
                null,
                context.deptId(),
                null,
                null,
                result.shortId(),
                uuidSeed,
                result.pickSource(),
                context.productId(),
                context.activityId(),
                context.sourceUrl(),
                result.promoteLink(),
                null,
                context.scene(),
                result.pickExtra()
        );
    }

    /**
     * 标准化推广附加标识（pick_extra）。
     * <p>
     * 处理规则：
     * <ol>
     *   <li>空白字符串返回 null</li>
     *   <li>非字母数字下划线字符替换为下划线</li>
     *   <li>长度不超过 {@value MAX_PICK_EXTRA_LENGTH} 时直接返回</li>
     *   <li>以 "channel_" 开头时保留前缀并截断尾部</li>
     *   <li>其他情况截断至 {@value MAX_PICK_EXTRA_LENGTH} 字符并转小写</li>
     * </ol>
     *
     * @param pickExtra 原始推广附加标识
     * @return 标准化后的 pick_extra，或 null
     */
    private String normalizePickExtra(String pickExtra) {
        // 第一步：空白字符串直接返回 null
        if (!StringUtils.hasText(pickExtra)) {
            return null;
        }
        // 第二步：去除首尾空白，非字母数字下划线字符替换为下划线
        String normalized = pickExtra.trim()
                .replaceAll("[^A-Za-z0-9_]", "_");
        // 第三步：长度不超过 20 字符时直接返回
        if (normalized.length() <= MAX_PICK_EXTRA_LENGTH) {
            return normalized;
        }
        // 第四步：以 "channel_" 开头时保留前缀，截断尾部适配长度限制
        if (normalized.startsWith("channel_")) {
            String tail = normalized.substring("channel_".length());
            int allowedTailLength = MAX_PICK_EXTRA_LENGTH - "channel_".length();
            if (tail.length() > allowedTailLength) {
                tail = tail.substring(0, allowedTailLength);
            }
            return "channel_" + tail;
        }
        // 第五步：其他情况截断至 20 字符并转小写
        return normalized.substring(0, MAX_PICK_EXTRA_LENGTH).toLowerCase(Locale.ROOT);
    }

    /**
     * 带降级策略的推广链接生成请求。
     * <p>
     * 按以下顺序尝试调用：
     * <ol>
     *   <li>主方法 {@value LEGACY_METHOD}（buyin.promotion.link.generate）</li>
     *   <li>备选方法 1 {@value FALLBACK_METHOD_1}（buyin.kolProductShare）</li>
     *   <li>备选方法 2 {@value FALLBACK_METHOD_2}（buyin.getProductShareMaterial）</li>
     * </ol>
     * 仅当遇到 API 服务下线错误时才降级，其他错误直接抛出。
     *
     * @param params 请求参数
     * @return 推广链接生成响应
     * @throws DouyinApiException 当所有方法均失败时抛出最后一个异常
     */
    private Map<String, Object> postWithFallback(Map<String, Object> params) {
        // 第一步：尝试主方法 buyin.promotion.link.generate
        try {
            return douyinApiClient.post(LEGACY_METHOD, params);
        } catch (DouyinApiException ex) {
            // 第二步：仅当 API 服务下线时降级，其他错误直接抛出
            if (!isApiServiceOff(ex)) {
                throw ex;
            }
            // 第三步：尝试备选方法 1 buyin.kolProductShare
            try {
                return douyinApiClient.post(FALLBACK_METHOD_1, params);
            } catch (DouyinApiException ex2) {
                if (!isApiServiceOff(ex2)) {
                    throw ex2;
                }
                // 第四步：尝试备选方法 2 buyin.getProductShareMaterial
                return douyinApiClient.post(FALLBACK_METHOD_2, params);
            }
        }
    }

    /**
     * 判断异常是否为 API 服务下线（应触发降级）。
     * <p>
     * 判定条件（满足其一即可）：
     * <ul>
     *   <li>错误码为 70000</li>
     *   <li>sub_code 包含 "api-service-off"</li>
     *   <li>errorMsg 包含 "API不存在" 或 "API已下线"</li>
     * </ul>
     *
     * @param ex 抖音 API 异常
     * @return true 表示 API 已下线，应尝试备选方法
     */
    private boolean isApiServiceOff(DouyinApiException ex) {
        if (ex == null) {
            return false;
        }
        String subCode = ex.getSubCode();
        String errorMsg = ex.getErrorMsg();
        return ex.getErrorCode() == 70000
                || containsIgnoreCase(subCode, "api-service-off")
                || containsIgnoreCase(errorMsg, "API不存在")
                || containsIgnoreCase(errorMsg, "API已下线");
    }

    /**
     * 不区分大小写的字符串包含检查。
     *
     * @param source 源字符串
     * @param needle 待查找子串
     * @return true 表示 source 包含 needle（忽略大小写）
     */
    private boolean containsIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return false;
        }
        return source.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * 推广链接生成结果。
     *
     * @param pickSource  推广标识（从响应或链接 URL 中提取）
     * @param pickExtra   推广附加标识（标准化后）
     * @param shortId     8 位 Base36 短码（内部生成）
     * @param shortLink   短链接（来自上游响应）
     * @param promoteLink 完整推广链接
     * @param uuidSeed    UUID 种子（字符串形式）
     */
    public record PromotionLinkResult(
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed
    ) {
        /**
         * 从上游响应构建结果（无 pick_extra 偏好）。
         *
         * @param response 上游 API 响应
         * @param shortId  内部生成的短码
         * @param uuidSeed UUID 种子字符串
         * @return 推广链接结果
         */
        public static PromotionLinkResult from(Map<String, Object> response, String shortId, String uuidSeed) {
            return from(response, shortId, uuidSeed, null);
        }

        /**
         * 从上游响应构建结果（完整参数）。
         * <p>
         * 按优先级从响应中提取 promoteLink（兼容多个字段名），
         * 从链接 URL query 参数中提取 pick_source 和 pick_extra。
         *
         * @param response        上游 API 响应
         * @param shortId         内部生成的短码
         * @param uuidSeed        UUID 种子字符串
         * @param desiredPickExtra 偏好的 pick_extra（响应中无值时的兜底）
         * @return 推广链接结果
         */
        public static PromotionLinkResult from(
                Map<String, Object> response,
                String shortId,
                String uuidSeed,
                String desiredPickExtra) {
            // 第一步：安全提取 data 子 Map，处理类型转换
            Map<String, Object> data = null;
            if (response != null) {
                Object dataObj = response.get("data");
                if (dataObj instanceof Map<?, ?> mapData) {
                    data = new HashMap<>();
                    for (Map.Entry<?, ?> entry : mapData.entrySet()) {
                        if (entry.getKey() != null) {
                            data.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                }
            }
            // 第二步：按优先级从 7 个可能的键中提取推广链接
            String promoteLink = firstNonBlank(
                    data != null ? asStringOrNull(data.get("promote_link")) : null,
                    data != null ? asStringOrNull(data.get("promotion_link")) : null,
                    data != null ? asStringOrNull(data.get("converted_link")) : null,
                    data != null ? asStringOrNull(data.get("converted_url")) : null,
                    data != null ? asStringOrNull(data.get("product_url")) : null,
                    data != null ? asStringOrNull(data.get("share_link")) : null,
                    data != null ? asStringOrNull(data.get("url")) : null
            );
            // 第三步：从 2 个可能的键中提取短链接
            String shortLink = firstNonBlank(
                    data != null ? asStringOrNull(data.get("short_link")) : null,
                    data != null ? asStringOrNull(data.get("short_url")) : null
            );
            // 第四步：提取 pick_source（优先响应字段，回退到 URL query 参数）
            String responsePickSource = data != null ? asStringOrNull(data.get("pick_source")) : null;
            String responsePickExtra = data != null ? asStringOrNull(data.get("pick_extra")) : null;
            String finalPickSource = firstNonBlank(
                    responsePickSource,
                    extractPickSource(promoteLink)
            );
            // 第五步：提取 pick_extra（优先响应字段 → URL 参数 → 上下文偏好值）
            String finalPickExtra = firstNonBlank(
                    responsePickExtra,
                    extractPickExtra(promoteLink),
                    desiredPickExtra
            );
            // 第六步：构建最终结果
            return new PromotionLinkResult(
                    finalPickSource,
                    finalPickExtra,
                    shortId,
                    shortLink,
                    promoteLink,
                    uuidSeed
                );
        }

        /**
         * 返回参数中第一个非空白字符串。
         *
         * @param values 候选字符串数组
         * @return 第一个非空白值，或全部为空时返回 null
         */
        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
        }

        /**
         * 将对象安全转换为字符串。
         *
         * @param value 原始值
         * @return 字符串形式，或 null
         */
        private static String asStringOrNull(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        /**
         * 从推广链接 URL 的 query 参数中提取 pick_source。
         *
         * @param promoteLink 完整推广链接 URL
         * @return pick_source 值，或提取失败时返回 null
         */
        private static String extractPickSource(String promoteLink) {
            if (StringUtils.hasText(promoteLink)) {
                try {
                    String query = URI.create(promoteLink).getQuery();
                    if (query != null) {
                        String[] parts = query.split("&");
                        for (String part : parts) {
                            String[] kv = part.split("=", 2);
                            if (kv.length == 2 && "pick_source".equals(kv[0])) {
                                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                } catch (Exception ex) {
                    logMalformedPromotionLink("pick_source", promoteLink, ex);
                    return null;
                }
            }
            return null;
        }

        /**
         * 从推广链接 URL 的 query 参数中提取 pick_extra。
         *
         * @param promoteLink 完整推广链接 URL
         * @return pick_extra 值，或提取失败时返回 null
         */
        private static String extractPickExtra(String promoteLink) {
            if (!StringUtils.hasText(promoteLink)) {
                return null;
            }
            try {
                String query = URI.create(promoteLink).getQuery();
                if (query == null) {
                    return null;
                }
                String[] parts = query.split("&");
                for (String part : parts) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2 && "pick_extra".equals(kv[0])) {
                        return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception ex) {
                logMalformedPromotionLink("pick_extra", promoteLink, ex);
                return null;
            }
            return null;
        }

        /**
         * 记录推广链接解析失败的警告日志。
         *
         * @param field       解析失败的字段名（pick_source 或 pick_extra）
         * @param promoteLink 原始推广链接（日志中脱敏）
         * @param ex          解析异常
         */
        private static void logMalformedPromotionLink(String field, String promoteLink, Exception ex) {
            PromotionApi.log.warn(
                    "Failed to parse {} from promotion link: {}",
                    field,
                    describePromotionLink(promoteLink),
                    ex
            );
        }

        /**
         * 生成推广链接的脱敏描述（仅含长度和哈希）。
         *
         * @param promoteLink 推广链接
         * @return 脱敏描述字符串
         */
        private static String describePromotionLink(String promoteLink) {
            if (!StringUtils.hasText(promoteLink)) {
                return "blank";
            }
            return "length=" + promoteLink.length() + ", hash=" + Integer.toHexString(promoteLink.hashCode());
        }
    }

    /**
     * 推广上下文信息，用于生成链接时携带业务参数。
     *
     * @param userId     用户 ID（保存映射关系时使用）
     * @param deptId     部门 ID
     * @param productId  商品 ID
     * @param activityId 活动 ID
     * @param sourceUrl  商品原始链接（转链接模式时使用）
     * @param scene      推广场景标识
     * @param pickExtra  推广附加标识
     */
    public record PromotionContext(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String sourceUrl,
            String scene,
            String pickExtra
    ) {
    }
}
