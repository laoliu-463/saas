package com.colonel.saas.domain.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 配置变更事件载荷，描述一次系统配置变更的完整上下文。
 *
 * <p>当管理员通过后台修改系统配置（如提成比例、寄样规则等）时，
 * 系统生成本事件并写入 Outbox 表，随后由各域消费者（{@link ConfigChangedEventConsumer}）
 * 根据关心的 configKey 进行本地缓存失效或业务规则刷新。</p>
 *
 * <p>事件类型统一为 {@value EVENT_TYPE}，通过 {@link ConfigChangedEventRouter}
 * 路由到所有支持的消费者。</p>
 */
public record ConfigChangedEventPayload(
        /** 事件唯一标识（UUID），由事件产生方生成。 */
        UUID eventId,
        /** 事件类型，固定为 {@value EVENT_TYPE}。 */
        String eventType,
        /** 事件载荷版本号，用于消费者做兼容性判断。 */
        int eventVersion,
        /** 触发变更的操作人 ID。 */
        UUID operatorId,
        /** 触发变更的操作人姓名，用于审计日志展示。 */
        String operatorName,
        /** 变更发生时间。 */
        LocalDateTime changedAt,
        /** 变更原因说明（可选），用于审计和问题排查。 */
        String changeReason,
        /** 变更来源标识，如 {@code ADMIN_UI}、{@code API}、{@code SYSTEM}。 */
        String source,
        /** 本次变更涉及的配置项列表，每项包含 key、旧值、新值等详细信息。 */
        List<ConfigChangedItemPayload> items,
        /** 本次变更的影响评估（是否需要缓存刷新、是否仅影响新数据等）。 */
        ConfigChangedImpactPayload impact) {

    /** 事件类型常量，用于 eventType 字段和 Outbox 表的路由标识。 */
    public static final String EVENT_TYPE = "CONFIG_CHANGED";
}
