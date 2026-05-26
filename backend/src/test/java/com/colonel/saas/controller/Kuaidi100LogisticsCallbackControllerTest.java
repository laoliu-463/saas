package com.colonel.saas.controller;

import com.colonel.saas.service.Kuaidi100LogisticsCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Kuaidi100LogisticsCallbackControllerTest {

    private Kuaidi100LogisticsCallbackService callbackService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        callbackService = mock(Kuaidi100LogisticsCallbackService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Kuaidi100LogisticsCallbackController(callbackService))
                .build();
    }

    @Test
    void callback_shouldReturnKuaidi100JsonAndDelegateFormFields() throws Exception {
        when(callbackService.handleCallback("{json}", "SIGN"))
                .thenReturn(new Kuaidi100LogisticsCallbackService.CallbackAck(true, "200", "success"));

        mockMvc.perform(post("/public/logistics/kuaidi100/callback")
                        .param("param", "{json}")
                        .param("sign", "SIGN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.returnCode").value("200"))
                .andExpect(jsonPath("$.message").value("success"));

        verify(callbackService).handleCallback("{json}", "SIGN");
    }

    @Test
    void callback_shouldRejectMissingParamWithoutCallingService() throws Exception {
        mockMvc.perform(post("/public/logistics/kuaidi100/callback")
                        .param("sign", "SIGN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.returnCode").value("500"));
    }

    @Test
    void callback_shouldRejectMissingSignWithoutCallingService() throws Exception {
        mockMvc.perform(post("/public/logistics/kuaidi100/callback")
                        .param("param", "{json}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.returnCode").value("500"))
                .andExpect(jsonPath("$.message").value("缺少参数"));

        verify(callbackService, never()).handleCallback("{json}", null);
    }

    @Test
    void callback_shouldSupportApiPublicAliasPath() throws Exception {
        when(callbackService.handleCallback("{json}", "SIGN"))
                .thenReturn(new Kuaidi100LogisticsCallbackService.CallbackAck(true, "200", "success"));

        mockMvc.perform(post("/api/public/logistics/kuaidi100/callback")
                        .param("param", "{json}")
                        .param("sign", "SIGN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.returnCode").value("200"));

        verify(callbackService).handleCallback("{json}", "SIGN");
    }
}
