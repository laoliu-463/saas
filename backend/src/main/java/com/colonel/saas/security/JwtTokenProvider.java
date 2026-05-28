package com.colonel.saas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 令牌提供者，负责 JWT 的生成、解析、验证和吊销辅助操作。
 *
 * <p>架构角色：
 * <ul>
 *   <li>作为安全层的核心组件，为 {@link JwtAuthenticationFilter} 提供统一的 Token 操作能力。</li>
 *   <li>所有涉及 JWT 的签发与校验逻辑均收口于此，避免 Token 操作散落在各处。</li>
 * </ul>
 *
 * <p>功能说明：
 * <ul>
 *   <li>生成 Access Token（短期有效，包含用户身份、部门、数据范围、角色等声明）</li>
 *   <li>生成 Refresh Token（长期有效，仅含用户 ID，用于刷新 Access Token）</li>
 *   <li>解析并验证 JWT 签名和过期时间</li>
 *   <li>计算 Token 的 SHA-256 哈希值，用于黑名单/吊销校验</li>
 *   <li>查询 Token 剩余有效期</li>
 * </ul>
 *
 * <p>配置项（来自 application.yml）：
 * <ul>
 *   <li>{@code security.jwt.secret} — HMAC 签名密钥，最少 32 字符</li>
 *   <li>{@code security.jwt.expire-seconds} — Access Token 过期秒数，默认 7200（2 小时）</li>
 *   <li>{@code security.jwt.refresh-expire-seconds} — Refresh Token 过期秒数，默认 604800（7 天）</li>
 * </ul>
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} — 在请求进入 Spring Security 链之前，调用本类解析和校验 Token</li>
 *   <li>{@link com.colonel.saas.auth.service.AuthService} — 通过 {@code isTokenBlacklisted} 方法配合本类的哈希能力实现 Token 吊销</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** 开发环境占位密钥，生产环境必须替换为随机字符串 */
    private static final String PLACEHOLDER_SECRET = "dev-secret-key-replace-in-production-with-random-64-char-string";

    /** 签名密钥的最小长度要求（字符数），不足时启动阶段直接拒绝 */
    private static final int MIN_SECRET_LENGTH = 32;

    /** HMAC-SHA 对称签名密钥，由配置项 secret 经 SHA-256 哈希后生成 */
    private final SecretKey secretKey;

    /** Access Token 过期时间（秒），默认 7200 秒 = 2 小时 */
    private final long expireSeconds;

    /** Refresh Token 过期时间（秒），默认 604800 秒 = 7 天 */
    private final long refreshExpireSeconds;

    /**
     * 构造函数，通过 Spring 依赖注入读取 JWT 配置并初始化签名密钥。
     *
     * @param secret            HMAC 签名密钥原始字符串，来自配置 {@code security.jwt.secret}
     * @param expireSeconds     Access Token 有效期（秒），默认 7200
     * @param refreshExpireSeconds Refresh Token 有效期（秒），默认 604800
     * @throws IllegalStateException 当密钥长度不足 {@value MIN_SECRET_LENGTH} 个字符时抛出，
     *                               应用将无法启动
     */
    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expire-seconds:7200}") long expireSeconds,
            @Value("${security.jwt.refresh-expire-seconds:604800}") long refreshExpireSeconds) {
        // 启动阶段校验密钥：过短直接拒绝，占位符仅打印警告
        validateSecret(secret);
        // 对原始密钥做 SHA-256 哈希后再生成 HMAC 密钥，确保无论输入长度如何都能得到固定 32 字节密钥
        this.secretKey = Keys.hmacShaKeyFor(sha256(secret));
        this.expireSeconds = expireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    /**
     * 校验签名密钥的合法性。
     * <p>
     * 若密钥仍为占位符默认值，仅打印警告日志（不阻断启动，方便本地开发）；
     * 若密钥长度不足最低要求，则直接抛出异常阻止应用启动，防止使用弱密钥。
     *
     * @param secret 待校验的原始密钥字符串
     * @throws IllegalStateException 当密钥长度少于 {@value MIN_SECRET_LENGTH} 个字符时
     */
    private void validateSecret(String secret) {
        // 占位符检测：提醒开发者在生产环境替换为真实密钥
        if (PLACEHOLDER_SECRET.equals(secret)) {
            log.warn("!!! JWT secret is using the default placeholder value. "
                    + "Set JWT_SECRET environment variable to a random string of at least "
                    + MIN_SECRET_LENGTH + " characters. !!!");
        }
        // 最小长度校验：不足则阻断启动
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + secret.length() + " chars). "
                    + "Minimum length is " + MIN_SECRET_LENGTH + " characters.");
        }
    }

    /**
     * 生成标准 Access Token（不含待激活标记，默认 pendingActivation = false）。
     *
     * @param userId    用户唯一标识
     * @param deptId    用户所属部门 ID，可为 {@code null}（无部门的用户）
     * @param dataScope 数据范围枚举编码（参见 {@link com.colonel.saas.common.enums.DataScope}）
     * @param roleCodes 用户角色编码列表，如 ["admin", "operator"]
     * @param username  用户登录名
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(
            UUID userId,
            UUID deptId,
            int dataScope,
            List<String> roleCodes,
            String username) {
        return generateAccessToken(userId, deptId, dataScope, roleCodes, username, false);
    }

    /**
     * 生成 Access Token，支持标记用户是否处于"待激活"状态。
     *
     * <p>Access Token 是短期令牌（默认 2 小时），用于接口鉴权。Token 内包含以下声明：
     * <ul>
     *   <li>{@code sub} — 用户 ID</li>
     *   <li>{@code type} — 固定为 "access"，与 Refresh Token 区分</li>
     *   <li>{@code deptId} — 部门 ID</li>
     *   <li>{@code dataScope} — 数据范围（1=个人，2=部门，3=全部，等）</li>
     *   <li>{@code roleCodes} — 角色编码列表</li>
     *   <li>{@code username} — 登录名</li>
     *   <li>{@code pendingActivation} — 是否待激活（新用户首次登录后需改密才可使用业务功能）</li>
     * </ul>
     *
     * @param userId            用户唯一标识
     * @param deptId            用户所属部门 ID，可为 {@code null}
     * @param dataScope         数据范围枚举编码
     * @param roleCodes         用户角色编码列表
     * @param username          用户登录名
     * @param pendingActivation {@code true} 表示用户处于待激活状态，仅允许访问改密等有限接口
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(
            UUID userId,
            UUID deptId,
            int dataScope,
            List<String> roleCodes,
            String username,
            boolean pendingActivation) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(expireSeconds);

        return Jwts.builder()
                .subject(userId.toString())                    // JWT 标准主题字段（sub），存放用户 ID
                .claim("type", "access")                       // 自定义声明：标记为 Access Token
                .claim("deptId", deptId == null ? null : deptId.toString())  // 部门 ID，null 表示无部门
                .claim("dataScope", dataScope)                 // 数据范围编码，下游用于行级数据权限过滤
                .claim("roleCodes", roleCodes)                 // 角色编码列表，下游用于功能权限判断
                .claim("username", username)                   // 登录名，方便日志和审计
                .claim("pendingActivation", pendingActivation) // 待激活标记，控制受限访问策略
                .issuedAt(Date.from(now))                      // 签发时间
                .expiration(Date.from(expireAt))               // 过期时间（签发时间 + expireSeconds）
                .signWith(secretKey)                           // 使用 HMAC-SHA 密钥签名
                .compact();
    }

    /**
     * 生成 Refresh Token。
     *
     * <p>Refresh Token 是长期令牌（默认 7 天），仅用于刷新 Access Token，不携带业务声明。
     * 内含唯一标识（jti），支持单 Token 级别的吊销。
     *
     * @param userId 用户唯一标识
     * @return 签名后的 Refresh Token JWT 字符串
     */
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(refreshExpireSeconds);
        // jti（JWT ID）用于唯一标识此 Refresh Token，便于精确吊销
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(userId.toString())        // 用户 ID
                .id(jti)                           // 唯一标识，用于吊销管理
                .claim("type", "refresh")          // 标记为 Refresh Token，与 Access Token 区分
                .issuedAt(Date.from(now))          // 签发时间
                .expiration(Date.from(expireAt))   // 过期时间（签发时间 + refreshExpireSeconds）
                .signWith(secretKey)               // HMAC-SHA 签名
                .compact();
    }

    /**
     * 解析并验证 JWT，返回其中包含的所有声明（Claims）。
     *
     * <p>验证过程包括：签名正确性、Token 是否过期。若验证失败将抛出异常。
     *
     * @param token JWT 字符串（不含 "Bearer " 前缀）
     * @return 解析后的 {@link Claims} 对象，包含所有声明字段
     * @throws io.jsonwebtoken.JwtException 当 Token 签名无效或格式错误时
     * @throws io.jsonwebtoken.ExpiredJwtException 当 Token 已过期时
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)         // 使用 HMAC 密钥验证签名
                .build()
                .parseSignedClaims(token)      // 解析并验证签名，返回 Jws<Claims>
                .getPayload();                 // 提取 Claims 负载
    }

    /**
     * 从 JWT 中提取 jti（JWT ID）声明。
     * <p>主要用于 Refresh Token，用于标识和吊销特定令牌。
     *
     * @param token JWT 字符串
     * @return jti 值，若未设置则返回 {@code null}
     */
    public String parseTokenId(String token) {
        return parseClaims(token).getId();
    }

    /**
     * 计算 Token 字符串的 SHA-256 哈希值（十六进制小写字符串）。
     *
     * <p>用途：将 Token 哈希后存入黑名单，避免直接存储完整 Token 字符串。
     * 黑名单校验时，对请求中的 Token 做同样的哈希运算，再查黑名单表即可判断是否已吊销。
     *
     * @param token 原始 JWT 字符串
     * @return 64 位十六进制小写哈希字符串
     */
    public String getTokenHash(String token) {
        byte[] hash = sha256(token);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));  // 将每个字节转为两位十六进制
        }
        return hex.toString();
    }

    /**
     * 获取 Access Token 的过期时间（秒）。
     *
     * @return Access Token 有效期秒数
     */
    public long getExpireSeconds() {
        return expireSeconds;
    }

    /**
     * 获取 Refresh Token 的过期时间（秒）。
     *
     * @return Refresh Token 有效期秒数
     */
    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    /**
     * 计算指定 Token 距过期还剩余的秒数。
     *
     * <p>最少返回 1 秒（即使已过期也返回 1，避免客户端收到 0 导致异常行为）。
     *
     * @param token JWT 字符串
     * @return 剩余有效秒数，最小值为 1
     * @throws io.jsonwebtoken.JwtException 当 Token 无效时
     */
    public long getRemainingSeconds(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 1);  // 保底返回 1，防止客户端拿到 0 秒
    }

    /**
     * 对输入字符串执行 SHA-256 哈希运算。
     *
     * <p>用于两个场景：
     * <ol>
     *   <li>将原始密钥哈希为固定 32 字节，再生成 HMAC 密钥（构造函数中）</li>
     *   <li>将完整 Token 哈希为摘要，存入吊销黑名单（{@link #getTokenHash} 中）</li>
     * </ol>
     *
     * @param value 待哈希的字符串
     * @return SHA-256 哈希结果的原始字节数组（32 字节）
     * @throws IllegalStateException 当 JVM 不支持 SHA-256 算法时（理论上不应发生）
     */
    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 规范要求的必须算法，正常不会触发此异常
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
