package com.colonel.saas.domain.user.event;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 用户域事件头信息工具类。
 *
 * <p>提供用户域领域事件构建时所需的公共头信息生成方法，包括：
 * <ul>
 *   <li>事件 ID 生成（UUID）</li>
 *   <li>链路追踪 ID 提取（从 SLF4J MDC）</li>
 * </ul>
 *
 * <p>所有方法为静态工具方法，不可实例化。</p>
 */
public final class UserDomainEventHeaders {

    /** MDC 中 traceId 的键名，与日志框架的链路追踪配置一致。 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 工具类，禁止实例化。 */
    private UserDomainEventHeaders() {
    }

    /**
     * 生成新的事件唯一标识。
     *
     * @return 随机生成的 UUID
     */
    public static UUID newEventId() {
        return UUID.randomUUID();
    }

    /**
     * 从当前线程的 MDC 上下文中提取链路追踪 ID。
     *
     * <p>如果当前线程未设置 traceId（如非 HTTP 请求线程），返回空字符串。</p>
     *
     * @return 当前链路追踪 ID，未设置时返回空字符串
     */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }
}
