package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.service.UserMasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserMasterDataControllerTest {

    @Mock
    private UserMasterDataService userMasterDataService;

    private MockMvc mockMvc;

    private final UUID deptId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        UserMasterDataController controller = new UserMasterDataController(userMasterDataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void channels_returnsChannelUserOptions() throws Exception {
        UUID channelId = UUID.randomUUID();
        when(userMasterDataService.listChannels("渠", 20)).thenReturn(List.of(
                new UserOptionResponse(channelId, "channel_staff", "渠道专员", deptId, List.of(RoleCodes.CHANNEL_STAFF))
        ));

        mockMvc.perform(get("/users/master-data/channels")
                        .param("keyword", "渠")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(channelId.toString()))
                .andExpect(jsonPath("$.data[0].roleCodes[0]").value(RoleCodes.CHANNEL_STAFF));
    }

    @Test
    void recruiters_returnsRecruiterUserOptions() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        when(userMasterDataService.listRecruiters("招", 20)).thenReturn(List.of(
                new UserOptionResponse(recruiterId, "biz_staff", "招商专员", deptId, List.of(RoleCodes.BIZ_STAFF))
        ));

        mockMvc.perform(get("/users/master-data/recruiters")
                        .param("keyword", "招")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("biz_staff"))
                .andExpect(jsonPath("$.data[0].roleCodes[0]").value(RoleCodes.BIZ_STAFF));
    }

    @Test
    void groupMembers_usesCurrentDeptWhenDeptNotProvided() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(userMasterDataService.listGroupMembers(null, deptId, List.of(RoleCodes.CHANNEL_LEADER), "组", 50))
                .thenReturn(List.of(new UserOptionResponse(
                        memberId,
                        "channel_member",
                        "组员",
                        deptId,
                        List.of(RoleCodes.CHANNEL_STAFF)
                )));

        mockMvc.perform(get("/users/master-data/group-members")
                        .param("keyword", "组")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_LEADER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].realName").value("组员"));
    }
}
