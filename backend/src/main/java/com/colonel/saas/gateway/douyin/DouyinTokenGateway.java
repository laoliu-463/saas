package com.colonel.saas.gateway.douyin;

import java.util.Map;

/**
 * 抖音令牌管理 Gateway 接口。
 * <p>
 * 封装抖店 OAuth 令牌的获取、刷新、创建和探测能力。
 * 所有需要调用抖店 API 的 Gateway 实现都依赖此接口获取有效令牌。
 * </p>
 *
 * <h3>实现切换</h3>
 * <p>
 * 通过配置 {@code douyin.test.enabled} 控制注入的实现：
 * <ul>
 *   <li>{@code true} - {@link com.colonel.saas.gateway.douyin.test.TestDouyinTokenGateway}，返回 Mock 令牌</li>
 *   <li>{@code false} - {@link com.colonel.saas.gateway.douyin.real.RealDouyinTokenGateway}，
 *       使用 Redis 缓存 + 抖店 SDK 获取真实令牌</li>
 * </ul>
 * </p>
 *
 * <h3>令牌生命周期</h3>
 * <p>
 * <ol>
 *   <li>{@link #createToken} - 首次授权，使用 authorization_code 换取令牌</li>
 *   <li>{@link #ensureToken} - 获取有效令牌（优先从 Redis 缓存读取，过期则自动刷新）</li>
 *   <li>{@link #refreshToken} - 使用 refresh_token 主动刷新令牌</li>
 *   <li>{@link #probeCreateToken} - 探测性创建（不写 Redis，仅用于联调验证）</li>
 * </ol>
 * </p>
 */
public interface DouyinTokenGateway {

    /**
     * 确保获取有效令牌。
     * <p>
     * 优先从缓存读取，若已过期则自动刷新。是其他 Gateway 调用抖店 API 时的首选方法。
     * </p>
     *
     * @param appId 应用 ID
     * @return 有效的令牌信息（含 access_token、refresh_token、过期时间等）
     * @throws RuntimeException 令牌获取失败时（如 refresh_token 也已过期）
     */
    TokenPayload ensureToken(String appId);

    /**
     * 使用 refresh_token 主动刷新令牌。
     *
     * @param appId        应用 ID
     * @param refreshToken 刷新令牌
     * @return 刷新后的令牌信息
     * @throws RuntimeException 刷新失败时
     */
    TokenPayload refreshToken(String appId, String refreshToken);

    /**
     * 创建新令牌（首次授权）。
     * <p>
     * 使用授权码（authorization_code）换取 access_token 和 refresh_token。
     * </p>
     *
     * @param command 令牌创建命令（含授权码、授权类型等）
     * @return 新创建的令牌信息
     * @throws RuntimeException 创建失败时（如授权码无效或过期）
     */
    TokenPayload createToken(TokenCreateCommand command);

    /**
     * 查询机构信息。
     *
     * @param appId 应用 ID
     * @return 机构信息 Map（抖店原始响应格式）
     */
    Map<String, Object> institutionInfo(String appId);

    /**
     * 探测性令牌创建（不写 Redis 缓存）。
     * <p>
     * 用于管理后台的联调探针功能，验证 SDK 令牌创建流程是否正常。
     * 与 {@link #createToken} 不同，此方法不持久化令牌。
     * Real 实现委托给 Doudian SDK 适配器执行。
     * </p>
     *
     * @param command 令牌创建命令
     * @return 探测结果（含 SDK 响应视图和诊断信息）
     */
    ProbeTokenCreateResult probeCreateToken(TokenCreateCommand command);

    /**
     * 令牌探测创建结果。
     * <p>
     * 由 {@link #probeCreateToken} 返回，包含 SDK 侧诊断信息，不写入缓存。
     * </p>
     *
     * @param grantType        实际使用的授权类型
     * @param codeState        授权码状态
     * @param testShop         测试店铺标识
     * @param shopId           店铺 ID
     * @param authIdPresent    SDK 响应中是否包含 auth_id
     * @param authSubjectType  授权主体类型
     * @param response         SDK 原始响应视图（脱敏后的令牌信息）
     */
    record ProbeTokenCreateResult(
            String grantType,
            String codeState,
            String testShop,
            String shopId,
            boolean authIdPresent,
            String authSubjectType,
            TokenProbeResponseView response) {
    }

    /**
     * 令牌探测响应视图（脱敏）。
     * <p>
     * SDK 返回的令牌信息经过脱敏处理，access_token 和 refresh_token 仅显示部分字符。
     * </p>
     *
     * @param code              SDK 响应码
     * @param msg               SDK 响应消息
     * @param subCode           SDK 子响应码
     * @param subMsg            SDK 子响应消息
     * @param maskedAccessToken 脱敏后的访问令牌
     * @param maskedRefreshToken 脱敏后的刷新令牌
     * @param expiresIn         令牌过期时间（秒）
     * @param authorityId       授权 ID
     * @param authSubjectType   授权主体类型
     * @param tokenType         令牌类型
     */
    record TokenProbeResponseView(
            String code,
            String msg,
            String subCode,
            String subMsg,
            String maskedAccessToken,
            String maskedRefreshToken,
            Long expiresIn,
            String authorityId,
            String authSubjectType,
            Long tokenType) {
    }

    /**
     * 令牌创建命令。
     * <p>
     * 封装令牌创建（授权码换令牌）所需的参数。
     * </p>
     *
     * @param authorizationCode OAuth 授权码（用户授权后回调获得）
     * @param grantType         授权类型（如 "authorization_code"）
     * @param testShop          测试店铺标识（可选，用于联调环境）
     * @param shopId            店铺 ID（可选）
     * @param authId            授权 ID（可选）
     * @param authSubjectType   授权主体类型（可选）
     */
    record TokenCreateCommand(
            String authorizationCode,
            String grantType,
            String testShop,
            String shopId,
            String authId,
            String authSubjectType) {
    }

    /**
     * 令牌信息。
     * <p>
     * 封装抖店 OAuth 令牌的核心字段，所有需要调用抖店 API 的 Gateway
     * 通过 {@link #ensureToken} 获取此对象中的 accessToken。
     * </p>
     *
     * @param accessToken      访问令牌（调用抖店 API 时使用）
     * @param refreshToken     刷新令牌（用于无感续期）
     * @param expiresIn        令牌有效期（秒）
     * @param authorityId      授权 ID（抖店侧标识）
     * @param authSubjectType  授权主体类型
     * @param tokenType        令牌类型
     */
    record TokenPayload(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            String authorityId,
            String authSubjectType,
            Long tokenType) {
    }
}
