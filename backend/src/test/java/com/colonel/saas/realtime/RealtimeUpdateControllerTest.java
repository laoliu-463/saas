package com.colonel.saas.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RealtimeUpdateControllerTest {

    @Mock
    private RealtimeUpdateService realtimeUpdateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RealtimeUpdateController(realtimeUpdateService))
                .build();
    }

    @Test
    void updates_shouldExposeApiPrefixedSseEndpoint() throws Exception {
        when(realtimeUpdateService.subscribe()).thenReturn(new SseEmitter(1L));

        mockMvc.perform(get("/api/realtime/updates")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(realtimeUpdateService).subscribe();
    }

    @Test
    void updates_shouldExposeProxyStrippedSseEndpoint() throws Exception {
        when(realtimeUpdateService.subscribe()).thenReturn(new SseEmitter(1L));

        mockMvc.perform(get("/realtime/updates")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(realtimeUpdateService).subscribe();
    }
}
