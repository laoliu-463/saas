package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 登录响应 DTO，包含 JWT 令牌与用户权限信息.
 *
 * <p>登录成功后返回给前端，用于：
 * <ul>
 *   <li>将 JWT token 放入后续请求的 Authorization header</li>
 *   <li>前端根据 dataScope/roleCodes 控制 UI 权限展示</li>
 *   <li>根据 userId/deptId 关联业务数据</li>
 * </ul>
 *
 * @see <a href="https://swagger.io/docs/specification/data-models/">OpenAPI Schema 文档</a>
 */
@Data
@Builder
@Schema(description = "登录响应数据")
public class LoginResponse {

    /**
     * JWT 访问令牌.
     * <p>
     * 使用 HS256 算法签名，Payload 中包含 sub(userId)、username、roleCodes、dataScope、iat、exp。
     * 前端需在后续请求的 {@code Authorization: Bearer <token>} 请求头中携带此令牌。
     *
     * @see #tokenType
     * @see #expiresIn
     */
    @Schema(description = "JWT 访问令牌")
    private String token;

    /**
     * 令牌类型，符合 OAuth 2.0 Bearer Token 规范.
     * <p>
     * 固定值 {@code "Bearer"}，与 Authorization header 格式对应：
     * {@code Authorization: Bearer <token>}
     */
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    /**
     * 令牌有效期，单位为秒.
     * <p>
     * 从签发时间(iat)到过期时间(exp)的持续时长，默认 7200 秒（2 小时）。
     * 前端可据此设置本地 token 刷新或过期提示。
     *
     * @see #token
     */
    @Schema(description = "令牌有效期（秒）", example = "7200")
    private Long expiresIn;

    /**
     * 用户唯一标识符(UUID)，对应 sys_user 表主键.
     * <p>
     * JWT 的 sub 字段即为此值，用于：
     * <ul>
     *   <li>用户相关业务查询（如 {@code WHERE user_id = ?}）</li>
     *   <li>关联其他业务表（如订单、资产等）</li>
     * </ul>
     */
    @Schema(description = "用户 ID")
    private UUID userId;

    /**
     * 部门 ID(UUID)，对应 sys_dept 表主键，可为空.
     * <p>
     * 用于数据权限隔离，用户只能看到归属部门的数据。
     * 为空时表示用户未归属任何部门，dataScope 仍生效。
     *
     * @see #dataScope
     */
    @Schema(description = "部门 ID")
    private UUID deptId;

    /**
     * 数据权限范围，控制用户可查询的数据范围.
     * <ul>
     *   <li>{@code 1} = 本人数据（仅能查看自己创建的数据）</li>
     *   <li>{@code 2} = 本组数据（可查看同部门/同角色下其他人的数据）</li>
     *   <li>{@code 3} = 全部数据（管理员，可查看所有数据）</li>
     * </ul>
     * <p>
     * 此值同时写入 JWT payload，后端 Security Filter 可直接解析使用，无需每次查库。
     *
     * @see #roleCodes
     */
    @Schema(description = "数据范围，1=本人，2=本组，3=全部", example = "3")
    private Integer dataScope;

    /**
     * 角色编码列表，用于接口权限判断.
     * <p>
     * 例：["admin"] 表示管理员角色，"admin" 是预定义系统管理员角色编码。
     * 此列表同时写入 JWT payload，Security Filter 解析后用于判断接口放行/拦截。
     *
     * @see #dataScope
     */
    @Schema(description = "角色编码列表")
    private List<String> roleCodes;

    /**
     * 登录用户名，对应 sys_user.username，唯一约束.
     * <p>
     * 用于：
     * <ul>
     *   <li>前端展示当前登录用户</li>
     *   <li>写入 JWT payload，方便后端直接获取而无需解析 token</li>
     * </ul>
     */
    @Schema(description = "用户名", example = "admin")
    private String username;

    /**
     * 真实姓名，对应 sys_user.real_name.
     * <p>
     * 用于前端 UI 展示，如"欢迎，XXX"。
     * 允许为空，部分用户可能未填写真实姓名。
     */
    @Schema(description = "真实姓名", example = "系统管理员")
    private String realName;
}
