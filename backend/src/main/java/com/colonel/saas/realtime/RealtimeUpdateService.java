package com.colonel.saas.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理前端 SSE 连接并广播列表失效通知。
 */
@Service
public class RealtimeUpdateService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeUpdateService.class);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, "connected", RealtimeUpdateMessage.now("system", "CONNECTED", null));
        return emitter;
    }

    public void publish(String topic, String reason, String entityId) {
        if (!StringUtils.hasText(topic)) {
            return;
        }
        RealtimeUpdateMessage message = RealtimeUpdateMessage.now(topic, reason, entityId);
        for (SseEmitter emitter : emitters) {
            if (!send(emitter, "update", message)) {
                emitters.remove(emitter);
            }
        }
    }

    int subscriberCount() {
        return emitters.size();
    }

    private boolean send(SseEmitter emitter, String eventName, RealtimeUpdateMessage message) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .reconnectTime(1000)
                    .data(message, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception ex) {
            log.debug("Realtime SSE client disconnected: topic={}, reason={}", message.topic(), message.reason(), ex);
            try {
                emitter.completeWithError(ex);
            } catch (Exception ignored) {
                // emitter 可能已经关闭，忽略二次关闭异常
            }
            return false;
        }
    }
}
