package com.colonel.saas.realtime;

/**
 * 前端列表实时失效通知消息。
 *
 * <p>该消息只表达“某类列表数据已变化，需要重新拉取”，不承载业务决策结果。
 * 前端收到后仍通过既有 REST 接口重新读取后端事实数据。</p>
 */
public record RealtimeUpdateMessage(
        String topic,
        String reason,
        String entityId,
        long occurredAt) {

    public static RealtimeUpdateMessage now(String topic, String reason, String entityId) {
        return new RealtimeUpdateMessage(topic, reason, entityId, System.currentTimeMillis());
    }
}
