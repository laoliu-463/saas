package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.dto.talent.ResolveTalentProfileRequest;
import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.dto.talent.TalentProfilePayload;
import com.colonel.saas.service.talent.profile.TalentProfileSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TalentProfileControllerTest {

    @Mock
    private TalentProfileSyncService talentProfileSyncService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TalentProfileController(talentProfileSyncService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resolveProfile_shouldPassFlagsAndManualPayload() throws Exception {
        ResolveTalentProfileRequest request = new ResolveTalentProfileRequest();
        request.setInput("https://www.douyin.com/user/MS4wLjABAAAA");
        request.setForceRefresh(true);
        request.setManualFill(true);
        request.setManualPayload(Map.of("nickname", "达人A"));

        ResolveTalentProfileResponse response = ResolveTalentProfileResponse.builder()
                .success(true)
                .profile(TalentProfilePayload.builder().nickname("达人A").build())
                .build();
        when(talentProfileSyncService.resolveProfile(
                eq(request.getInput()),
                eq(true),
                eq(true),
                eq(request.getManualPayload()))).thenReturn(response);

        mockMvc.perform(post("/talents/resolve-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.nickname").value("达人A"));
    }

    @Test
    void resolveProfile_shouldTreatNullFlagsAsFalse() throws Exception {
        ResolveTalentProfileRequest request = new ResolveTalentProfileRequest();
        request.setInput("douyin-id-1");

        ResolveTalentProfileResponse response = ResolveTalentProfileResponse.builder()
                .success(true)
                .profile(TalentProfilePayload.builder().nickname("默认达人").build())
                .build();
        when(talentProfileSyncService.resolveProfile(eq("douyin-id-1"), eq(false), eq(false), any()))
                .thenReturn(response);

        mockMvc.perform(post("/talents/resolve-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.nickname").value("默认达人"));
    }

    @Test
    void syncProfile_shouldPassIdAndForceRefresh() throws Exception {
        UUID talentId = UUID.randomUUID();
        ResolveTalentProfileResponse response = ResolveTalentProfileResponse.builder()
                .success(true)
                .profile(TalentProfilePayload.builder().talentUid(talentId.toString()).nickname("已刷新达人").build())
                .build();
        when(talentProfileSyncService.syncExistingProfile(talentId, true)).thenReturn(response);

        mockMvc.perform(post("/talents/{id}/sync-profile", talentId)
                        .param("forceRefresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.nickname").value("已刷新达人"));

        verify(talentProfileSyncService).syncExistingProfile(talentId, true);
    }
}
