package com.colonel.saas.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 配置变更事件消费者接口，各业务域实现此接口以响应系统配置变更。
 *
 * <p>每个消费者通过 {@link #supports} 方法声明关心哪些 configKey，
 * 当 {@link ConfigChangedEventRouter} 分发事件时，只有 supports 返回 true
 * 的消费者才会被调用 {@link #consume} 方法。</p>
 *
 * <p>现有实现包括：
 * <ul>
 *   <li>{@link PerformanceConfigChangedConsumer} — 业绩域（提成比例等）</li>
 *   <li>{@link ProductConfigChangedConsumer} — 商品域（推广文案模板等）</li>
 *   <li>{@link SampleConfigChangedConsumer} — 寄样域（寄样限制天数等）</li>
 *   <li>{@link TalentConfigChangedConsumer} — 达人域（达人保护期等）</li>
 *   <li>{@link UserConfigChangedConsumer} — 用户域（登录失败锁定等）</li>
 * </ul>
 * </p>
 */
public interface ConfigChangedEventConsumer {

    /**
     * 返回消费者唯一名称，用于消费日志（{@link DomainEventConsumeLog}）的幂等性判断。
     *
     * @return 消费者名称，如 {@code product-config-consumer}
     */
    String consumerName();

    /**
     * 判断当前消费者是否关心此次配置变更事件。
     *
     * <p>通常通过检查事件中的 configKey 是否在本域关注的 key 集合中来判断。</p>
     *
     * @param payload 配置变更事件载荷
     * @return true 表示本消费者需要处理此事件
     */
    boolean supports(ConfigChangedEventPayload payload);

    /**
     * 执行配置变更的消费逻辑，通常为失效本地缓存或刷新业务规则。
     *
     * @param payload      配置变更事件载荷
     * @param objectMapper JSON 序列化器（按需使用）
     * @throws Exception 消费失败时抛出异常，由 Router 记录失败日志
     */
    void consume(ConfigChangedEventPayload payload, ObjectMapper objectMapper) throws Exception;
}
