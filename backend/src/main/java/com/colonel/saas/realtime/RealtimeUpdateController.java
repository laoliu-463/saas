package com.colonel.saas.realtime;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 前端实时更新通知入口。
 */
@RestController
@RequestMapping({"/api/realtime", "/realtime"})
public class RealtimeUpdateController {

    private final RealtimeUpdateService realtimeUpdateService;

    public RealtimeUpdateController(RealtimeUpdateService realtimeUpdateService) {
        this.realtimeUpdateService = realtimeUpdateService;
    }

    @GetMapping(value = "/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter updates() {
        return realtimeUpdateService.subscribe();
    }
}
