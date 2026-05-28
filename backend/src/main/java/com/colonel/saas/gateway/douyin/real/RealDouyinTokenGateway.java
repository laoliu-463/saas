package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinConfig;
import com.colonel.saas.douyin.api.InstitutionApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 抖音 Token 网关的生产环境实现，负责 OAuth2 Token 的缓存、刷新、创建和机构信息查询。
 *
 * <p>功能描述：通过 Redis 缓存 Token 信息，并委托 {@link DoudianTokenGateway} 执行实际的
 * Token 刷新与创建操作。当 {@link DouyinUpstreamModeSupport} 判定为 contract 模式时，
 * 委托给 {@link DouyinContractFixtureProvider} 返回契约夹具数据，不发起真实 HTTP 请求。</p>
 *
 * <ul>
 *   <li>Token 缓存查询（{@link #ensureToken}）：从 Redis 读取 accessToken/refreshToken/expireAt</li>
 *   <li>Token 刷新（{@link #refreshToken}）：委托 DoudianTokenGateway 执行刷新并返回新 Token</li>
 *   <li>Token 创建（{@link #createToken}）：使用授权码创建新 Token（OAuth2 授权码流程）</li>
 *   <li>机构信息查询（{@link #institutionInfo}）：查询机构详情（含店铺 ID、授权 ID 等）</li>
 *   <li>Token 创建探针（{@link #probeCreateToken}）：模拟 Token 创建流程，返回参数校验结果，不实际创建</li>
 * </ul>
 *
 * <p>环境说明：
 * <ul>
 *   <li>当 {@code douyin.test.enabled=false}（或未配置）时激活此实现（matchIfMissing=true）</li>
 *   <li>Token 缓存依赖 Redis，键前缀为 {@code douyin:token:} / {@code douyin:refresh:} / {@code douyin:token:expire_at:}</li>
 *   <li>与 {@link com.colonel.saas.gateway.douyin.test.TestDouyinTokenGateway} 互斥</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：抖音网关 / Token 适配层</p>
 *
 * @see DouyinTokenGateway
 * @see DoudianTokenGateway
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 * @see com.colonel.saas.gateway.douyin.test.TestDouyinTokenGateway
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinTokenGateway implements DouyinTokenGateway {

    /** Redis 键前缀：accessToken 存储 */
    private static final String TOKEN_KEY_PREFIX = "douyin:token:";

    /** Redis 键前缀：refreshToken 存储 */
    private static final String REFRESH_KEY_PREFIX = "douyin:refresh:";

    /** Redis 键前缀：Token 过期时间（Unix epoch 秒）存储 */
    private static final String EXPIRE_AT_KEY_PREFIX = "douyin:token:expire_at:";

    /** Redis 模板，用于读写 Token 缓存数据 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 抖音全局配置，提供 clientKey / appId 用于 Token 缓存键解析 */
    private final DouyinConfig douyinConfig;

    /** 店铺级 Token 网关，负责实际的 Token 刷新和创建操作 */
    private final DoudianTokenGateway doudianTokenGateway;

    /** 抖音机构 API 客户端，用于查询机构详情信息 */
    private final InstitutionApi institutionApi;

    /** 上游模式判断：live（真实 API）或 contract（契约夹具） */
    private final DouyinUpstreamModeSupport upstreamModeSupport;

    /** 契约测试夹具数据提供者，contract 模式下使用 */
    private final DouyinContractFixtureProvider contractFixtureProvider;

    /**
     * 构造函数（Spring 自动注入）。
     *
     * <p>注意：{@code institutionApi} 使用 {@link Lazy} 注解延迟注入，以打破循环依赖
     * （InstitutionApi 可能依赖 TokenGateway）。</p>
     *
     * @param redisTemplate           Redis 操作模板
     * @param douyinConfig            抖音全局配置
     * @param doudianTokenGateway     店铺级 Token 网关
     * @param institutionApi          抖音机构 API 客户端（延迟注入）
     * @param upstreamModeSupport     上游模式判断器
     * @param contractFixtureProvider 契约夹具数据提供者
     */
    public RealDouyinTokenGateway(
            RedisTemplate<String, Object> redisTemplate,
            DouyinConfig douyinConfig,
            DoudianTokenGateway doudianTokenGateway,
            @Lazy
            InstitutionApi institutionApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.redisTemplate = redisTemplate;
        this.douyinConfig = douyinConfig;
        this.doudianTokenGateway = doudianTokenGateway;
        this.institutionApi = institutionApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    /**
     * 确保 Token 存在且未过期，从 Redis 缓存中读取 Token 信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>通过 {@link #resolveAppId} 解析最终使用的 appId</li>
     *   <li>从 Redis 读取 accessToken、refreshToken 和过期时间戳（epoch 秒）</li>
     *   <li>若任一字段缺失或过期时间戳已过期，返回 null</li>
     *   <li>计算剩余有效期（expiresIn = expireAt - 当前时间），若已过期返回 null</li>
     *   <li>返回 TokenPayload（包含 accessToken、refreshToken、expiresIn）</li>
     * </ol>
     *
     * @param appId 抖音应用 ID（可为 null，此时从配置中解析）
     * @return Token 信息；缓存不存在或已过期时返回 null
     */
    @Override
    public TokenPayload ensureToken(String appId) {
        String finalAppId = resolveAppId(appId);
        String accessToken = asTrimmedString(redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + finalAppId));
        String refreshToken = asTrimmedString(redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + finalAppId));
        long expireAt = asLong(redisTemplate.opsForValue().get(EXPIRE_AT_KEY_PREFIX + finalAppId), 0L);
        if (accessToken == null || refreshToken == null || expireAt <= 0L) {
            return null;
        }

        long expiresIn = expireAt - Instant.now().getEpochSecond();
        if (expiresIn <= 0L) {
            return null;
        }
        return new TokenPayload(accessToken, refreshToken, expiresIn, null, null, null);
    }

    /**
     * 刷新 Token，使用 refreshToken 换取新的 accessToken。
     *
     * <p>处理流程：
     * <ol>
     *   <li>记录网关访问日志</li>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>委托 {@link DoudianTokenGateway#refreshToken} 执行真实 Token 刷新</li>
     *   <li>将店铺级 TokenPayload 映射为网关层 TokenPayload</li>
     * </ol>
     *
     * @param appId       抖音应用 ID
     * @param refreshToken 当前的 refreshToken
     * @return 新的 Token 信息（含 accessToken、refreshToken、expiresIn 等）
     */
    @Override
    public TokenPayload refreshToken(String appId, String refreshToken) {
        logGateway(appId);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildTokenPayload(appId, refreshToken);
        }
        DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.refreshToken(refreshToken);
        return new TokenPayload(
                payload.accessToken(),
                payload.refreshToken(),
                payload.expiresIn(),
                payload.authorityId(),
                payload.authSubjectType(),
                payload.tokenType()
        );
    }

    /**
     * 创建新 Token（OAuth2 授权码流程）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>记录网关访问日志</li>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>将网关层 {@link TokenCreateCommand} 映射为店铺级 {@link DoudianTokenGateway.TokenCreateCommand}</li>
     *   <li>委托 {@link DoudianTokenGateway#createToken} 执行真实 Token 创建</li>
     *   <li>将店铺级 TokenPayload 映射为网关层 TokenPayload</li>
     * </ol>
     *
     * @param command Token 创建命令（含授权码、grantType、shopId、authId 等）
     * @return 新创建的 Token 信息
     */
    @Override
    public TokenPayload createToken(TokenCreateCommand command) {
        logGateway(null);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildTokenPayload(null, null);
        }
        DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.createToken(
                new DoudianTokenGateway.TokenCreateCommand(
                        command.authorizationCode(),
                        command.grantType(),
                        command.testShop(),
                        command.shopId(),
                        command.authId(),
                        command.authSubjectType()
                )
        );
        return new TokenPayload(
                payload.accessToken(),
                payload.refreshToken(),
                payload.expiresIn(),
                payload.authorityId(),
                payload.authSubjectType(),
                payload.tokenType()
        );
    }

    /**
     * 查询抖音机构详情信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>记录网关访问日志</li>
     *   <li>若为 contract 模式，返回契约夹具数据</li>
     *   <li>委托 {@link InstitutionApi#info} 查询真实机构信息</li>
     * </ol>
     *
     * @param appId 抖音应用 ID
     * @return 机构详情 Map（含机构名称、关联店铺等信息）
     */
    @Override
    public Map<String, Object> institutionInfo(String appId) {
        logGateway(appId);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildInstitutionInfoResponse(appId);
        }
        return institutionApi.info(appId);
    }

    /**
     * 探测 Token 创建流程：模拟执行，返回参数校验结果，不实际创建 Token。
     *
     * <p>处理流程：
     * <ol>
     *   <li>记录网关访问日志</li>
     *   <li>若为 contract 模式，返回预置的探针结果（含脱敏 Token 和默认参数）</li>
     *   <li>委托 {@link DoudianTokenGateway#probeCreateToken} 执行真实探针请求</li>
     *   <li>将店铺级探针结果映射为网关层 {@link ProbeTokenCreateResult}，
     *       包含 TokenProbeResponseView（脱敏 accessToken/refreshToken、expiresIn、authorityId 等）</li>
     * </ol>
     *
     * @param command Token 创建命令（含授权码、grantType、shopId、authId 等）
     * @return 探针结果（含参数状态和脱敏的 Token 响应视图）
     */
    @Override
    public ProbeTokenCreateResult probeCreateToken(TokenCreateCommand command) {
        logGateway(null);
        if (upstreamModeSupport.isContract()) {
            return new ProbeTokenCreateResult(
                    command.grantType(),
                    command.authorizationCode() == null || command.authorizationCode().isBlank() ? "absent" : "present",
                    command.testShop(),
                    command.shopId(),
                    command.authId() != null && !command.authId().isBlank(),
                    command.authSubjectType(),
                    new TokenProbeResponseView(
                            "10000",
                            "success",
                            null,
                            null,
                            "****",
                            "****",
                            7200L,
                            contractFixtureProvider.authId(),
                            "Colonel",
                            1L
                    )
            );
        }
        DoudianTokenGateway.TokenCreateProbeResult probe = doudianTokenGateway.probeCreateToken(
                new DoudianTokenGateway.TokenCreateCommand(
                        command.authorizationCode(),
                        command.grantType(),
                        command.testShop(),
                        command.shopId(),
                        command.authId(),
                        command.authSubjectType()
                ));
        DoudianTokenGateway.TokenCreateResponseView v = probe.responseView();
        TokenProbeResponseView view = new TokenProbeResponseView(
                v.code(),
                v.msg(),
                v.subCode(),
                v.subMsg(),
                v.maskedAccessToken(),
                v.maskedRefreshToken(),
                v.expiresIn(),
                v.authorityId(),
                v.authSubjectType(),
                v.tokenType()
        );
        return new ProbeTokenCreateResult(
                probe.grantType(),
                probe.codeState(),
                probe.testShop(),
                probe.shopId(),
                probe.authIdPresent(),
                probe.authSubjectType(),
                view
        );
    }

    /**
     * 记录网关调用日志，输出当前上游模式、脱敏后的 appKey、shopId 和 authId。
     *
     * @param appId 调用方传入的 appId；若为 null 则使用契约夹具配置的默认 appKey
     */
    private void logGateway(String appId) {
        log.info(
                "gateway=RealDouyinTokenGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(appId == null ? contractFixtureProvider.appKey() : appId),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    /**
     * 对字符串进行脱敏处理，保留前 4 位和后 4 位，中间用 {@code ****} 替换。
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

    /**
     * 解析最终使用的 appId，按优先级回退。
     *
     * <p>回退优先级：
     * <ol>
     *   <li>显式传入的 appId 参数</li>
     *   <li>{@link DouyinConfig#getClientKey()}</li>
     *   <li>{@link DouyinConfig#getAppId()}</li>
     *   <li>契约夹具配置的默认 appKey</li>
     * </ol>
     *
     * @param appId 调用方显式传入的 appId（可为 null）
     * @return 解析后的 appId，保证非 null
     */
    private String resolveAppId(String appId) {
        String explicitAppId = asTrimmedString(appId);
        if (explicitAppId != null) {
            return explicitAppId;
        }
        String clientKey = asTrimmedString(douyinConfig.getClientKey());
        if (clientKey != null) {
            return clientKey;
        }
        String configuredAppId = asTrimmedString(douyinConfig.getAppId());
        if (configuredAppId != null) {
            return configuredAppId;
        }
        return contractFixtureProvider.appKey();
    }

    /**
     * 将任意类型的值安全转换为 trim 后的 String，null 或空白时返回 null。
     *
     * @param value 待转换的值
     * @return 非空白字符串；null 或空白时返回 null
     */
    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 将任意类型的值安全转换为 long，解析失败时返回默认值。
     *
     * <p>支持 Number（直接取 longValue）和 String（Long.parseLong）类型。</p>
     *
     * @param value        待转换的值
     * @param defaultValue 默认值
     * @return 转换后的 long 值
     */
    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
