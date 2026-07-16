package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.talent.facade.TalentComplaintFacade;
import com.colonel.saas.domain.sample.application.SampleApplicationService;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.dto.talent.TalentComplaintCreateRequest;
import com.colonel.saas.vo.talent.TalentComplaintReminderVO;
import com.colonel.saas.vo.talent.TalentComplaintVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TalentComplaintControllerTest {

    @Mock
    private TalentComplaintFacade facade;

    private MockMvc mockMvc;
    private UUID userId;
    private UUID deptId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        deptId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(new TalentComplaintController(facade))
                .defaultRequest(get("/")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT)
                        .requestAttr("roleCodes", List.of(RoleCodes.BIZ_LEADER)))
                .build();
    }

    @Test
    void risks_shouldBindAtMostOneHundredTalentIdsAndReturnMinimalSummary() throws Exception {
        UUID talentId = UUID.randomUUID();
        LocalDateTime latest = LocalDateTime.of(2026, 7, 16, 12, 0);
        when(facade.loadRisks(
                any(), eq(userId), eq(deptId), eq(DataScope.DEPT),
                eq(List.of(RoleCodes.BIZ_LEADER)))).thenReturn(List.of(
                new TalentComplaintRiskDTO(talentId, 2, latest)));

        mockMvc.perform(post("/talent-complaints/risks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentIds\":[\"" + talentId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].talentId").value(talentId.toString()))
                .andExpect(jsonPath("$.data[0].complaintCount").value(2))
                .andExpect(jsonPath("$.data[0].lastComplaintAt").exists())
                .andExpect(jsonPath("$.data[0].reasonCode").doesNotExist())
                .andExpect(jsonPath("$.data[0].content").doesNotExist())
                .andExpect(jsonPath("$.data[0].attachments").doesNotExist())
                .andExpect(jsonPath("$.data[0].reporterUserId").doesNotExist());

        verify(facade).loadRisks(
                any(), eq(userId), eq(deptId), eq(DataScope.DEPT),
                eq(List.of(RoleCodes.BIZ_LEADER)));
    }

    @Test
    void risks_shouldRejectMoreThanOneHundredIdsAtBindingBoundary() throws Exception {
        StringBuilder json = new StringBuilder("{\"talentIds\":[");
        for (int index = 0; index < 101; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('\"').append(UUID.randomUUID()).append('\"');
        }
        json.append("]}");

        mockMvc.perform(post("/talent-complaints/risks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isBadRequest());

        verify(facade, never()).loadRisks(any(), any(), any(), any(), any());
    }

    @Test
    void reminderRead_shouldForwardCurrentRecipientAndRoles() throws Exception {
        UUID reminderId = UUID.randomUUID();
        TalentComplaintReminderVO reminder = new TalentComplaintReminderVO(
                reminderId, UUID.randomUUID(), UUID.randomUUID(),
                "LOW_PRICE_RESALE", false, LocalDateTime.now(), null);
        when(facade.markReminderRead(
                eq(reminderId), eq(userId), eq(List.of(RoleCodes.BIZ_LEADER))))
                .thenReturn(reminder);

        mockMvc.perform(put("/talent-complaints/reminders/{id}/read", reminderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reminderId.toString()));
    }

    @Test
    void attachment_shouldReturnProtectedBytesWithoutStoragePath() throws Exception {
        UUID complaintId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(facade.downloadAttachment(
                eq(complaintId), eq(attachmentId), eq(List.of(RoleCodes.BIZ_LEADER))))
                .thenReturn(new TalentComplaintFacade.AttachmentDownload(
                        "proof.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        mockMvc.perform(get(
                        "/talent-complaints/{complaintId}/attachments/{attachmentId}",
                        complaintId, attachmentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void leaderOnlyEndpoints_shouldDeclareExactRoleBoundary() throws Exception {
        for (String methodName : List.of(
                "getDetail", "unreadCount", "listReminders", "markReminderRead", "downloadAttachment")) {
            Method method = java.util.Arrays.stream(TalentComplaintController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            RequireRoles roles = method.getAnnotation(RequireRoles.class);
            assertThat(roles).isNotNull();
            assertThat(roles.value()).containsExactlyInAnyOrder(
                    RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);
        }
    }

    @Test
    void sampleComplaintCreate_shouldBindMultipartAndDeriveAssociationsInApplicationLayer() throws Exception {
        SampleApplicationService sampleService = mock(SampleApplicationService.class);
        MockMvc sampleMvc = MockMvcBuilders.standaloneSetup(new SampleController(sampleService))
                .defaultRequest(get("/")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", com.colonel.saas.common.enums.DataScope.PERSONAL)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF)))
                .build();
        UUID sampleId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TalentComplaintVO created = new TalentComplaintVO(
                UUID.randomUUID(), sampleId, talentId, productId, userId,
                "OTHER", "其他投诉", "SUBMITTED", List.of(), null);
        when(sampleService.createComplaint(
                eq(sampleId),
                eq(new TalentComplaintCreateRequest("OTHER", "其他投诉")),
                any(),
                eq(userId),
                eq(null),
                eq(com.colonel.saas.common.enums.DataScope.PERSONAL),
                eq(List.of(RoleCodes.CHANNEL_STAFF))))
                .thenReturn(created);

        sampleMvc.perform(multipart("/samples/{id}/complaints", sampleId)
                        .file("files", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                        .param("reason", "OTHER")
                        .param("content", "其他投诉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sampleRequestId").value(sampleId.toString()))
                .andExpect(jsonPath("$.data.talentId").value(talentId.toString()))
                .andExpect(jsonPath("$.data.productId").value(productId.toString()));

        verify(sampleService).createComplaint(
                eq(sampleId),
                eq(new TalentComplaintCreateRequest("OTHER", "其他投诉")),
                any(),
                eq(userId),
                eq(null),
                eq(com.colonel.saas.common.enums.DataScope.PERSONAL),
                eq(List.of(RoleCodes.CHANNEL_STAFF)));
    }
}
