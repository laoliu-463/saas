package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 抖音推广网关的生产环境实现。
 *
 * <p>功能描述：通过 {@link PromotionApi} 和 {@link DouyinApiClient} 调用抖音精选联盟的真实推广 API，
 * 提供推广转链生成、原始上游请求透传等功能。
 * 当 {@link DouyinUpstreamModeSupport} 判定为 contract 模式时，委托给
 * {@link DouyinContractFixtureProvider} 返回契约夹具数据，不发起真实 HTTP 请求。</p>
 *
 * <ul>
 *   <li>推广转链生成（{@link #generateLink}）：将商品链接转换为带归因参数的推广链接</li>
 *   <li>原始上游透传（{@link #rawUpstreamPost}）：直接向抖音 API 发送自定义方法请求</li>
 *   <li>写操作安全门（{@link #ensurePromotionWriteAllowed}）：受 {@code douyin.real.promotion-write-enabled}
 *   与 {@code douyin.real.allow-promotion-write} 双开关控制</li>
 *   <li>契约模式支持：contract 模式下返回预置夹具数据，不发起真实 HTTP 请求</li>
 * </ul>
 *
 * <p>所属业务领域：抖音网关 / 推广适配层</p>
 *
 * @see DouyinPromotionGateway
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 * @see com.colonel.saas.gateway.douyin.test.TestDouyinPromotionGateway
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinPromotionGateway implements DouyinPromotionGateway {

    /**
     * 抖音机构转链方法标识，用于判断写操作是否为目标推广方法。
     */
    private static final String INST_PICK_SOURCE_CONVERT_METHOD = "buyin.instpicksourceconvert";

    /** 抖音推广 API 客户端，用于生成推广链接 */
    private final PromotionApi promotionApi;

    /** 抖音通用 API 客户端，用于透传自定义方法请求 */
    private final DouyinApiClient douyinApiClient;

    /** 上游模式判断：live（真实 API）或 contract（契约夹具） */
    private final DouyinUpstreamModeSupport upstreamModeSupport;

    /** 契约测试夹具数据提供者，contract 模式下使用 */
    private final DouyinContractFixtureProvider contractFixtureProvider;

    /** 推广写操作开关，由配置项 {@code douyin.real.promotion-write-enabled} 控制，默认关闭 */
    private final boolean promotionWriteEnabled;

    /** 真实推广写操作二次确认开关，由 {@code douyin.real.allow-promotion-write} 控制，默认关闭 */
    private final boolean allowPromotionWrite;

    /**
     * 构造函数（Spring 自动注入）。
     *
     * @param promotionApi            抖音推广 API 客户端
     * @param douyinApiClient         抖音通用 API 客户端
     * @param upstreamModeSupport     上游模式判断器
     * @param contractFixtureProvider 契约夹具数据提供者
     * @param promotionWriteEnabled   推广写操作主开关（由配置注入）
     * @param allowPromotionWrite     真实推广写操作二次确认开关（由配置注入）
     */
    public RealDouyinPromotionGateway(
            PromotionApi promotionApi,
            DouyinApiClient douyinApiClient,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider,
            @Value("${douyin.real.promotion-write-enabled:false}") boolean promotionWriteEnabled,
            @Value("${douyin.real.allow-promotion-write:false}") boolean allowPromotionWrite) {
        this.promotionApi = promotionApi;
        this.douyinApiClient = douyinApiClient;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
        this.promotionWriteEnabled = promotionWriteEnabled;
        this.allowPromotionWrite = allowPromotionWrite;
    }

    /**
     * 生成推广链接。
     *
     * <p>处理流程：
     * <ol>
     *   <li>记录网关日志</li>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>调用 {@link #ensurePromotionWriteAllowed} 检查写操作是否允许</li>
     *   <li>将命令中的上下文信息转换为 {@link PromotionApi.PromotionContext}</li>
     *   <li>调用 {@link PromotionApi#generateLink} 发起真实 API 请求</li>
     *   <li>将返回结果映射为 {@link PromotionLinkResult}</li>
     * </ol>
     *
     * @param command 推广转链命令（含唯一标识、推广场景、商品 ID 列表、上下文等）
     * @return 推广链接结果（含 pickSource、pickExtra、短链、推广链接等）
     */
    @Override
    public PromotionLinkResult generateLink(PromotionLinkCommand command) {
        // 第一步：记录网关访问日志
        logGateway();
        // 第二步：contract 模式直接返回夹具数据，不发起真实 HTTP 请求
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildPromotionLinkResult(command);
        }
        // 第三步：安全门检查——推广写操作需显式启用
        ensurePromotionWriteAllowed(INST_PICK_SOURCE_CONVERT_METHOD);
        // 第四步：构建上游 API 所需的 PromotionContext 上下文对象
        PromotionApi.PromotionContext context = command.context() == null ? null : new PromotionApi.PromotionContext(
                command.context().userId(),
                command.context().deptId(),
                command.context().productId(),
                command.context().activityId(),
                command.context().sourceUrl(),
                command.context().scene(),
                command.context().pickExtra()
        );
        // 第五步：调用抖音推广 API 生成推广链接
        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                command.externalUniqueId(),
                command.promotionScene(),
                command.productIds(),
                command.needShortLink(),
                context
        );
        // 第六步：将上游结果映射为网关层的 PromotionLinkResult
        return new PromotionLinkResult(
                result.pickSource(),
                result.pickExtra(),
                result.shortId(),
                result.shortLink(),
                result.promoteLink(),
                result.uuidSeed()
        );
    }

    /**
     * 向抖音上游 API 发送自定义方法的原始 POST 请求。
     *
     * <p>处理流程：
     * <ol>
     *   <li>记录网关访问日志</li>
     *   <li>若为 contract 模式，返回包含契约标识的固定响应</li>
     *   <li>调用 {@link #ensurePromotionWriteAllowed} 检查目标方法是否允许写操作</li>
     *   <li>构建请求 body：先填入 payload，再补充 appId（若未在 payload 中存在）</li>
     *   <li>调用 {@link DouyinApiClient#post} 发起真实 HTTP 请求并返回原始响应</li>
     * </ol>
     *
     * @param appId   抖音应用 ID（可为 null）
     * @param method  抖音 API 方法标识（如 {@code buyin.instpicksourceconvert}）
     * @param payload 请求参数 Map（可为 null）
     * @return 抖音 API 原始响应 Map
     */
    @Override
    public Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload) {
        // 第一步：记录网关访问日志
        logGateway();
        // 第二步：contract 模式返回固定响应
        if (upstreamModeSupport.isContract()) {
            return Map.of(
                    "code", "10000",
                    "msg", "success",
                    "data", Map.of("contract", true, "method", method == null ? "" : method));
        }
        // 第三步：安全门检查
        ensurePromotionWriteAllowed(method);
        // 第四步：构建请求 body
        Map<String, Object> body = new LinkedHashMap<>();
        if (payload != null) {
            body.putAll(payload);
        }
        if (org.springframework.util.StringUtils.hasText(appId)) {
            body.putIfAbsent("appId", appId);
        }
        // 第五步：调用通用 API 客户端发送 POST 请求
        return douyinApiClient.post(method, body);
    }

    /**
     * 安全门检查：确保推广写操作已被显式启用。
     *
     * <p>仅当目标方法属于推广写操作，且主开关与二次确认开关均为 true 时放行。
     * 非写操作方法直接放行。</p>
     *
     * @param method 抖音 API 方法标识
     * @throws BusinessException 当写操作未启用且目标方法为推广写操作时
     */
    private void ensurePromotionWriteAllowed(String method) {
        if (!isPromotionWriteMethod(method) || (promotionWriteEnabled && allowPromotionWrite)) {
            return;
        }
        throw BusinessException.stateInvalid("真实抖店推广写操作未开启，请同时配置 DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true 与 ALLOW_REAL_PROMOTION_WRITE=true 后再执行转链");
    }

    /**
     * 判断指定方法是否为推广写操作方法。
     *
     * <p>当前仅 {@code buyin.instpicksourceconvert} 被识别为写操作。</p>
     *
     * @param method 抖音 API 方法标识
     * @return 是推广写操作方法返回 true
     */
    private boolean isPromotionWriteMethod(String method) {
        return INST_PICK_SOURCE_CONVERT_METHOD.equals(normalizeMethod(method));
    }

    /**
     * 标准化方法标识：null 安全处理、trim、转小写，便于与常量比较。
     *
     * @param method 原始方法标识（可为 null）
     * @return 标准化后的小写方法标识；null 时返回空字符串
     */
    private String normalizeMethod(String method) {
        return method == null ? "" : method.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 记录网关调用日志，输出当前上游模式、脱敏后的 appKey、shopId 和 authId。
     *
     * <p>日志格式示例：
     * {@code gateway=RealDouyinPromotionGateway, upstreamMode=live, appKey=abcd****efgh, shopId=123, authId=456}</p>
     */
    private void logGateway() {
        log.info(
                "gateway=RealDouyinPromotionGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(contractFixtureProvider.appKey()),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    /**
     * 对字符串进行脱敏处理，保留前 4 位和后 4 位，中间用 {@code ****} 替换。
     *
     * <p>用于日志输出时隐藏 appKey 等敏感信息。
     * 长度不超过 8 的字符串不做脱敏直接返回。</p>
     *
     * @param value 待脱敏的字符串
     * @return 脱敏后的字符串；null 或空白时返回空字符串
     */
    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }
}
