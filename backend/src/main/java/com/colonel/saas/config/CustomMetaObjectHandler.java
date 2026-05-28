package com.colonel.saas.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MyBatis-Plus 自动填充处理器。
 * <p>
 * 实现 {@link MetaObjectHandler} 接口，在实体插入和更新时自动填充审计字段：
 * <ul>
 *   <li>{@code id} — 插入时若未设置则自动生成 UUID 主键</li>
 *   <li>{@code createTime} — 插入时自动填充当前时间</li>
 *   <li>{@code updateTime} — 插入和更新时自动填充当前时间</li>
 *   <li>{@code createBy} — 插入时自动填充当前操作用户 ID</li>
 *   <li>{@code updateBy} — 插入和更新时自动填充当前操作用户 ID</li>
 * </ul>
 *
 * <p>使用方式：在实体类的对应字段上添加 MyBatis-Plus 注解即可：</p>
 * <pre>
 * {@literal @}TableId(type = IdType.ASSIGN_UUID)
 * private UUID id;
 * {@literal @}TableField(fill = FieldFill.INSERT)
 * private LocalDateTime createTime;
 * {@literal @}TableField(fill = FieldFill.INSERT_UPDATE)
 * private LocalDateTime updateTime;
 * </pre>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>依赖 {@link SecurityConfig} 中的 JWT 过滤器设置 {@code userId} 请求属性</li>
 *   <li>配合 {@link MyBatisPlusConfig} 中的乐观锁拦截器使用</li>
 * </ul>
 */
@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入操作时自动填充审计字段。
     * <p>
     * 填充策略：
     * <ol>
     *   <li>若 {@code id} 字段为空，自动生成随机 UUID 作为主键</li>
     *   <li>自动填充 {@code createTime} 和 {@code updateTime} 为当前时间</li>
     *   <li>自动填充 {@code createBy} 和 {@code updateBy} 为当前请求的用户 ID</li>
     * </ol>
     * </p>
     *
     * @param metaObject MyBatis 元对象，包含实体的字段信息
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 若主键字段为空，则自动生成 UUID 作为主键
        if (getFieldValByName("id", metaObject) == null) {
            setFieldValByName("id", UUID.randomUUID(), metaObject);
        }
        // 填充创建时间和更新时间为当前时间
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        // 填充创建人和更新人为当前登录用户
        this.strictInsertFill(metaObject, "createBy", UUID.class, getCurrentUserId());
        this.strictInsertFill(metaObject, "updateBy", UUID.class, getCurrentUserId());
    }

    /**
     * 更新操作时自动填充审计字段。
     * <p>
     * 仅更新 {@code updateTime} 和 {@code updateBy}，不修改创建相关字段。
     * </p>
     *
     * @param metaObject MyBatis 元对象，包含实体的字段信息
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", UUID.class, getCurrentUserId());
    }

    /**
     * 从当前 HTTP 请求上下文中获取登录用户的 ID。
     * <p>
     * 用户 ID 由 {@link com.colonel.saas.security.JwtAuthenticationFilter}
     * 在 JWT 鉴权通过后写入请求属性 {@code "userId"}。
     * 本方法支持多种类型（UUID 对象、字符串），并做安全转换。
     * </p>
     *
     * @return 当前用户 UUID，若无法获取则返回 {@code null}（如非 HTTP 上下文、未登录等）
     */
    UUID getCurrentUserId() {
        // 从 Spring 请求上下文中获取当前请求属性
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        // 从请求属性中读取 JWT 过滤器设置的 userId
        Object raw = servletAttributes.getRequest().getAttribute("userId");
        if (raw == null) {
            return null;
        }
        // 直接是 UUID 类型则返回
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        // 字符串类型则尝试转换为 UUID
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
