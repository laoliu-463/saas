package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.doudian.open.api.token_create.TokenCreateRequest;
import com.doudian.open.api.token_create.TokenCreateResponse;
import com.doudian.open.api.token_create.data.TokenCreateData;
import com.doudian.open.api.token_create.param.TokenCreateParam;
import com.doudian.open.api.token_refresh.TokenRefreshRequest;
import com.doudian.open.api.token_refresh.TokenRefreshResponse;
import com.doudian.open.api.token_refresh.data.TokenRefreshData;
import com.doudian.open.api.token_refresh.param.TokenRefreshParam;
import com.doudian.open.core.AccessToken;
import com.doudian.open.core.GlobalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * 抖店 OAuth Token 网关。
 * <p>
 * 封装抖店开放平台的 Token 创建与刷新操作，作为所有 Token 相关外部调用的统一入口，
 * 在测试模式下自动阻止外部调用。
 *
 * <ul>
 *   <li>Token 创建 — 通过授权码或授权参数获取 access_token 与 refresh_token</li>
 *   <li>Token 刷新 — 使用 refresh_token 刷新获取新的 access_token</li>
 *   <li>Token 探测 — 以诊断模式执行 Token 创建请求并返回详细结果</li>
 *   <li>SDK 初始化 — 自动初始化抖店 SDK 的 appKey/appSecret 配置</li>
 *   <li>安全脱敏 — 日志中的 token 值自动脱敏为 ****</li>
 * </ul>
 *
 * 所属业务领域：抖音开放平台 / OAuth 授权
 *
 * @see DouyinConfig
 * @see DouyinApiException
 */
@Component
public class DoudianTokenGateway {

    private static final Logger log = LoggerFactory.getLogger(DoudianTokenGateway.class);

    private final DouyinConfig douyinConfig;
    @Value("${douyin.test.enabled:false}")
    private boolean testEnabled;

    public DoudianTokenGateway(DouyinConfig douyinConfig) {
        this.douyinConfig = douyinConfig;
    }

    /**
     * 创建 OAuth Token（正式模式）。
     * <p>
     * 通过授权码或授权参数调用抖店 Token 创建接口，解析响应后返回 Token 载荷。
     * 测试模式下直接抛出异常阻止外部调用。
     *
     * <ol>
     *   <li>检查测试模式标记，若启用则抛出异常</li>
     *   <li>调用 {@link #executeCreateToken} 执行 Token 创建请求</li>
     *   <li>校验响应码，非成功则抛出 {@link DouyinApiException}</li>
     *   <li>从响应数据中提取 Token 信息并构建 {@link TokenPayload}</li>
     * </ol>
     *
     * @param command Token 创建命令（包含授权码、授权类型等参数）
     * @return Token 载荷，包含 accessToken、refreshToken、过期时间等信息
     * @throws BusinessException  测试模式启用时抛出
     * @throws DouyinApiException 上游 API 返回非成功响应时抛出
     */
    public TokenPayload createToken(TokenCreateCommand command) {
        // 第一步：测试模式下阻止外部调用
        if (testEnabled) {
            throw BusinessException.param("test mode enabled: token gateway external call is blocked");
        }
        // 第二步：执行 Token 创建请求并获取原始响应
        TokenCreateProbeResult probeResult = executeCreateToken(command);
        TokenCreateResponse response = probeResult.rawResponse();
        log.info("TokenCreateResponse received: code={}, msg={}, subCode={}, subMsg={}",
                response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
        // 第三步：校验响应码，非成功则抛出 DouyinApiException
        ensureSuccess(response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());

        // 第四步：提取响应数据，空数据视为异常
        TokenCreateData data = response.getData();
        if (data == null) {
            throw BusinessException.param("token.create response missing data");
        }

        // 第五步：安全提取可选字段，构建 TokenPayload 返回
        String authorityId = safeText(data::getAuthorityId);
        String authSubjectType = safeText(data::getAuthSubjectType);
        Long tokenType = safeLong(data::getTokenType);
        log.info("TokenCreateData: accessToken={}, refreshToken={}, expiresIn={}, authorityId={}, authSubjectType={}, tokenType={}",
                maskSecret(data.getAccessToken()), maskSecret(data.getRefreshToken()), data.getExpiresIn(),
                authorityId, authSubjectType, tokenType);

        return new TokenPayload(
                data.getAccessToken(),
                data.getRefreshToken(),
                data.getExpiresIn(),
                authorityId,
                authSubjectType,
                tokenType
        );
    }

    /**
     * 探测模式执行 Token 创建请求（诊断用）。
     * <p>
     * 以诊断模式执行 Token 创建，返回包含原始响应和解析视图的完整探测结果，
     * 用于排查 Token 创建过程中的问题。
     *
     * @param command Token 创建命令
     * @return 探测结果，包含请求参数状态、原始响应和脱敏视图
     * @throws BusinessException  测试模式启用时抛出
     * @throws DouyinApiException 上游 API 返回非成功响应时抛出
     * @see #createToken(TokenCreateCommand)
     */
    public TokenCreateProbeResult probeCreateToken(TokenCreateCommand command) {
        // 第一步：测试模式下阻止外部调用
        if (testEnabled) {
            throw BusinessException.param("test mode enabled: token gateway external call is blocked");
        }
        // 第二步：以诊断模式执行 Token 创建请求
        TokenCreateProbeResult result = executeCreateToken(command);
        TokenCreateResponse response = result.rawResponse();
        log.info("TokenCreateProbe response: code={}, msg={}, subCode={}, subMsg={}",
                response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
        // 第三步：返回包含原始响应和脱敏视图的探测结果
        return result;
    }

    /**
     * 使用 refresh_token 刷新获取新的 access_token。
     * <p>
     * 调用抖店 Token 刷新接口，使用已有的 refresh_token 换取新的 Token 对。
     * 测试模式下直接抛出异常阻止外部调用。
     *
     * <ol>
     *   <li>检查测试模式标记，若启用则抛出异常</li>
     *   <li>初始化 SDK 配置</li>
     *   <li>构建 TokenRefreshRequest 并执行</li>
     *   <li>校验响应码，非成功则抛出 {@link DouyinApiException}</li>
     *   <li>从响应数据中提取新的 Token 信息</li>
     * </ol>
     *
     * @param refreshToken 用于刷新的 refresh_token 值
     * @return 新的 Token 载荷
     * @throws BusinessException  测试模式启用时或 refresh_token 为空时抛出
     * @throws DouyinApiException 上游 API 返回非成功响应时抛出
     */
    public TokenPayload refreshToken(String refreshToken) {
        // 第一步：测试模式下阻止外部调用
        if (testEnabled) {
            throw BusinessException.param("test mode enabled: token gateway external call is blocked");
        }
        // 第二步：初始化抖店 SDK 全局配置（appKey/appSecret）
        initSdkConfig();
        // 第三步：构建 Token 刷新请求，设置授权类型和 refresh_token
        TokenRefreshRequest request = new TokenRefreshRequest();
        TokenRefreshParam param = request.getParam();
        param.setGrantType("refresh_token");
        param.setRefreshToken(refreshToken);

        // 第四步：执行请求并记录响应日志
        log.info("Executing TokenRefreshRequest...");
        TokenRefreshResponse response = request.execute(AccessToken.wrap("", null));
        log.info("TokenRefreshResponse received: code={}, msg={}, subCode={}, subMsg={}",
                response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
        // 第五步：校验响应码，非成功则抛出异常
        ensureSuccess(response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());

        // 第六步：提取新 Token 数据，构建 TokenPayload 返回
        TokenRefreshData data = response.getData();
        if (data == null) {
            throw BusinessException.param("token.refresh response missing data");
        }

        String authorityId = safeText(data::getAuthorityId);
        String authSubjectType = safeText(data::getAuthSubjectType);
        log.info("TokenRefreshData: accessToken={}, refreshToken={}, expiresIn={}, authorityId={}, authSubjectType={}",
                maskSecret(data.getAccessToken()), maskSecret(data.getRefreshToken()), data.getExpiresIn(),
                authorityId, authSubjectType);

        return new TokenPayload(
                data.getAccessToken(),
                data.getRefreshToken(),
                data.getExpiresIn(),
                authorityId,
                authSubjectType,
                null
        );
    }

    /**
     * 初始化抖店 SDK 全局配置。
     * <p>
     * 从 {@link DouyinConfig} 中读取 appKey、appSecret 和可选的 baseUrl，
     * 写入 {@link GlobalConfig} 以供后续 SDK 请求使用。
     *
     * @throws BusinessException 当 appKey 或 appSecret 未配置时抛出
     */
    private void initSdkConfig() {
        // 第一步：读取应用凭据，缺失则抛出配置异常
        String appKey = douyinConfig.getClientKey();
        String appSecret = douyinConfig.getClientSecret();
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
            throw BusinessException.param("missing douyin.app.client-key/client-secret config");
        }
        // 第二步：初始化抖店 SDK 全局配置
        GlobalConfig.initAppKey(appKey);
        GlobalConfig.initAppSecret(appSecret);
        // 第三步：可选覆盖 SDK 请求地址（如沙箱或自定义域名）
        if (StringUtils.hasText(douyinConfig.getBaseUrl())) {
            GlobalConfig.getGlobalConfig().setOpenRequestUrl(douyinConfig.getBaseUrl());
        }
    }

    /**
     * 校验 API 响应码，非成功时抛出 {@link DouyinApiException}。
     * <p>
     * 当 code 为 {@code "10000"} 时表示成功；否则从 subCode 中提取业务错误码并抛出异常。
     *
     * @param code    API 响应主码
     * @param msg     API 响应主消息
     * @param subCode API 响应子码（可为空）
     * @param subMsg  API 响应子消息（可为空，优先级高于 msg）
     * @throws DouyinApiException 当 code 非 "10000" 时抛出
     */
    private void ensureSuccess(String code, String msg, String subCode, String subMsg) {
        // 第一步：code 为 "10000" 表示成功，直接返回
        if ("10000".equals(code)) {
            return;
        }
        // 第二步：从 subCode/subMsg 中解析业务错误码
        int errorCode = parseBusinessCode(subCode, subMsg, code);
        // 第三步：优先使用 subMsg 作为错误消息，回退到 msg
        String errorMessage = (subMsg == null || subMsg.isBlank()) ? msg : subMsg;
        // 第四步：抛出业务异常
        throw new DouyinApiException(errorCode, errorMessage == null ? "unknown error" : errorMessage);
    }

    /**
     * 从 API 响应中解析业务错误码。
     * <p>
     * 优先从 subCode 中提取 4-6 位数字作为错误码，
     * 其次从 subMsg 中提取，最后回退到 fallbackCode。
     *
     * @param subCode      子错误码（可为空）
     * @param subMsg       子错误消息（可为空）
     * @param fallbackCode 回退错误码字符串
     * @return 整数形式的业务错误码，解析失败时返回 -1
     */
    private int parseBusinessCode(String subCode, String subMsg, String fallbackCode) {
        // 第一步：优先从 subCode 提取，其次从 subMsg 提取
        String source = "";
        if (subCode != null && !subCode.isBlank()) {
            source = subCode;
        } else if (subMsg != null && !subMsg.isBlank()) {
            source = subMsg;
        }
        // 第二步：使用正则提取 4-6 位数字作为业务错误码
        if (!source.isBlank()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4,6})").matcher(source);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignore) {
                    // fallback
                }
            }
        }
        // 第三步：正则未命中时回退到 fallbackCode，解析失败返回 -1
        try {
            return Integer.parseInt(fallbackCode);
        } catch (Exception ignore) {
            return -1;
        }
    }

    /**
     * Token 创建请求命令。
     * <p>
     * 封装调用抖店 Token 创建接口所需的全部参数。
     *
     * @param authorizationCode 授权码（授权码模式时必填）
     * @param grantType         授权类型（如 "authorization_code"）
     * @param testShop          测试店铺标识（可选）
     * @param shopId            店铺 ID（可选）
     * @param authId            授权 ID（可选）
     * @param authSubjectType   授权主体类型（可选）
     */
    public record TokenCreateCommand(
            String authorizationCode,
            String grantType,
            String testShop,
            String shopId,
            String authId,
            String authSubjectType) {
    }

    /**
     * Token 创建探测结果。
     * <p>
     * 包含请求参数状态、原始响应和脱敏后的响应视图，用于诊断 Token 创建问题。
     *
     * @param grantType      请求中的授权类型
     * @param codeState      授权码状态（absent / empty / present）
     * @param testShop       测试店铺标识
     * @param shopId         店铺 ID
     * @param authIdPresent  是否携带 authId
     * @param authSubjectType 授权主体类型
     * @param rawResponse    原始 TokenCreateResponse
     * @param responseView   脱敏后的响应视图
     */
    public record TokenCreateProbeResult(
            String grantType,
            String codeState,
            String testShop,
            String shopId,
            boolean authIdPresent,
            String authSubjectType,
            TokenCreateResponse rawResponse,
            TokenCreateResponseView responseView) {
    }

    /**
     * Token 创建响应视图（脱敏版）。
     * <p>
     * 将原始响应中的敏感字段（accessToken、refreshToken）脱敏后展示，
     * 用于日志输出和诊断界面显示。
     *
     * @param code              API 响应主码
     * @param msg               API 响应主消息
     * @param subCode           API 响应子码
     * @param subMsg            API 响应子消息
     * @param maskedAccessToken 脱敏后的 access_token
     * @param maskedRefreshToken 脱敏后的 refresh_token
     * @param expiresIn         Token 有效期（秒）
     * @param authorityId       授权 ID
     * @param authSubjectType   授权主体类型
     * @param tokenType         Token 类型
     */
    public record TokenCreateResponseView(
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
     * Token 载荷。
     * <p>
     * 封装 Token 创建或刷新后返回的核心数据，供上层服务使用。
     *
     * @param accessToken     访问令牌
     * @param refreshToken    刷新令牌
     * @param expiresIn       Token 有效期（秒）
     * @param authorityId     授权 ID
     * @param authSubjectType 授权主体类型
     * @param tokenType       Token 类型
     */
    public record TokenPayload(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            String authorityId,
            String authSubjectType,
            Long tokenType) {
    }

    /**
     * 安全获取响应字段文本值。
     * <p>
     * 调用 getter 获取值，处理 null 和类型转换异常，空白字符串视为 null。
     *
     * @param getter 属性读取函数
     * @return 非空白的字符串值，或 null
     */
    private String safeText(Supplier<Object> getter) {
        Object value = safeValue(getter);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 安全获取响应字段长整型值。
     * <p>
     * 调用 getter 获取值，支持 Number 类型直接转换和字符串解析，解析失败返回 null。
     *
     * @param getter 属性读取函数
     * @return Long 值，或 null
     */
    private Long safeLong(Supplier<Object> getter) {
        Object value = safeValue(getter);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * 安全执行 getter 调用，捕获类型转换异常。
     *
     * @param getter 属性读取函数
     * @return getter 返回值，类型不匹配时返回 null
     */
    private Object safeValue(Supplier<Object> getter) {
        try {
            return getter.get();
        } catch (ClassCastException ex) {
            log.warn("Token response field type mismatch, ignoring incompatible value: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 将敏感信息脱敏为 {@code ****}。
     *
     * @param secret 原始敏感信息
     * @return 脱敏后的字符串，null 或空白时返回空字符串
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        return "****";
    }

    /**
     * 执行 Token 创建请求的核心逻辑。
     * <p>
     * 初始化 SDK 配置后，将命令参数映射到抖店 SDK 的 {@link TokenCreateParam}，
     * 执行请求并封装为 {@link TokenCreateProbeResult} 返回。
     *
     * <ol>
     *   <li>调用 {@link #initSdkConfig} 初始化 SDK</li>
     *   <li>将 command 各字段设置到 TokenCreateParam</li>
     *   <li>记录授权码状态和请求参数日志</li>
     *   <li>通过 SDK 执行请求并构建探测结果</li>
     * </ol>
     *
     * @param command Token 创建命令
     * @return 探测结果，包含原始响应和脱敏视图
     */
    private TokenCreateProbeResult executeCreateToken(TokenCreateCommand command) {
        // 第一步：初始化抖店 SDK 全局配置
        initSdkConfig();
        // 第二步：构建 Token 创建请求，将命令参数映射到 SDK 参数
        TokenCreateRequest request = new TokenCreateRequest();
        TokenCreateParam param = request.getParam();
        if (command.authorizationCode() != null) {
            param.setCode(command.authorizationCode());
        }
        param.setGrantType(command.grantType());
        if (StringUtils.hasText(command.testShop())) {
            param.setTestShop(command.testShop());
        }
        if (StringUtils.hasText(command.shopId())) {
            param.setShopId(command.shopId());
        }
        if (StringUtils.hasText(command.authId())) {
            param.setAuthId(command.authId());
        }
        if (StringUtils.hasText(command.authSubjectType())) {
            param.setAuthSubjectType(command.authSubjectType());
        }

        // 第三步：记录授权码状态和请求参数日志
        String codeState = param.getCode() == null ? "absent" : (param.getCode().isEmpty() ? "empty" : "present");
        log.info("Executing TokenCreateRequest with SDK params: grantType={}, shopId={}, testShop={}, authIdPresent={}, authSubjectType={}, codeState={}",
                param.getGrantType(),
                param.getShopId(),
                param.getTestShop(),
                StringUtils.hasText(param.getAuthId()),
                param.getAuthSubjectType(),
                codeState);

        // 第四步：执行 SDK 请求并封装探测结果
        log.info("Executing TokenCreateRequest...");
        TokenCreateResponse response = request.execute(AccessToken.wrap("", null));
        return new TokenCreateProbeResult(
                param.getGrantType(),
                codeState,
                param.getTestShop(),
                param.getShopId(),
                StringUtils.hasText(param.getAuthId()),
                param.getAuthSubjectType(),
                response,
                toResponseView(response)
        );
    }

    /**
     * 将原始 TokenCreateResponse 转换为脱敏视图。
     * <p>
     * Token 字段（accessToken、refreshToken）自动脱敏为 {@code ****}，
     * authorityId、authSubjectType、tokenType 通过 safe 系列方法安全提取。
     *
     * @param response 原始 Token 创建响应
     * @return 脱敏后的响应视图
     */
    private TokenCreateResponseView toResponseView(TokenCreateResponse response) {
        // 第一步：提取响应数据，data 为 null 时返回仅含头部的视图
        TokenCreateData data = response.getData();
        if (data == null) {
            return new TokenCreateResponseView(
                    response.getCode(),
                    response.getMsg(),
                    response.getSubCode(),
                    response.getSubMsg(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        // 第二步：Token 字段脱敏，可选字段安全提取，构建完整视图
        return new TokenCreateResponseView(
                response.getCode(),
                response.getMsg(),
                response.getSubCode(),
                response.getSubMsg(),
                maskSecret(data.getAccessToken()),
                maskSecret(data.getRefreshToken()),
                data.getExpiresIn(),
                safeText(data::getAuthorityId),
                safeText(data::getAuthSubjectType),
                safeLong(data::getTokenType)
        );
    }
}
