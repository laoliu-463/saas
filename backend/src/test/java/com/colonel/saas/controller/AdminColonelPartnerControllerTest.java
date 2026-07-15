package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.domain.colonel.application.ColonelPartnerContactUpdateRouter;
import com.colonel.saas.service.ColonelPartnerAdminService;
import com.colonel.saas.service.ColonelPartnerSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminColonelPartnerControllerTest {

    @Mock
    private ColonelPartnerAdminService colonelPartnerAdminService;
    @Mock
    private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock
    private ColonelPartnerContactUpdateRouter colonelPartnerContactUpdateRouter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminColonelPartnerController controller = new AdminColonelPartnerController(
                colonelPartnerAdminService,
                colonelPartnerSyncService,
                colonelPartnerContactUpdateRouter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sync_shouldBeAvailableBelowConfiguredApiContextPath() throws Exception {
        when(colonelPartnerSyncService.syncAll()).thenReturn(13);

        mockMvc.perform(post("/admin/colonel-partners/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.upserted").value(13));

        verify(colonelPartnerSyncService).syncAll();
    }
}
