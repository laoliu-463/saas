package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.job.TalentWeeklyRefreshJob;
import com.colonel.saas.service.TalentQueryService;
import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TalentControllerTest {

    @Mock
    private TalentService talentService;
    @Mock
    private TalentQueryService talentQueryService;
    @Mock
    private TalentWeeklyRefreshJob talentWeeklyRefreshJob;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TalentController controller = new TalentController(talentService, talentQueryService, talentWeeklyRefreshJob);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_returnsPagedTalents() throws Exception {
        UUID userId = UUID.randomUUID();
        IPage<Talent> page = new Page<>(1, 10);
        page.setRecords(List.of(new Talent()));
        page.setTotal(1);
        when(talentQueryService.page(any(TalentPageQuery.class)))
                .thenReturn(page);

        mockMvc.perform(get("/talents")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "alice")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(talentQueryService).page(argThat(query ->
                query.getPage() == 1
                        && query.getSize() == 10
                        && "alice".equals(query.getKeyword())
                        && query.getDataScope() == DataScope.PERSONAL
                        && userId.equals(query.getUserId())));
    }

    @Test
    void page_shouldAcceptViewAndMetricFilters() throws Exception {
        when(talentQueryService.page(any(TalentPageQuery.class))).thenReturn(new Page<>(1, 10, 0));

        mockMvc.perform(get("/talents")
                        .param("view", "TEAM_PUBLIC")
                        .param("category", "食品饮料")
                        .param("claimStatus", "UNCLAIMED")
                        .param("minFans", "10000")
                        .requestAttr("userId", UUID.randomUUID()))
                .andExpect(status().isOk());

        verify(talentQueryService).page(argThat(query ->
                "TEAM_PUBLIC".equals(query.getView())
                        && "食品饮料".equals(query.getCategory())
                        && "UNCLAIMED".equals(query.getClaimStatus())
                        && Long.valueOf(10000).equals(query.getMinFans())));
    }

    @Test
    void presetTags_returnsConfiguredPresetTags() throws Exception {
        when(talentService.listPresetTags()).thenReturn(List.of("美妆", "高转化"));

        mockMvc.perform(get("/talents/preset-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value("美妆"))
                .andExpect(jsonPath("$.data[1]").value("高转化"));
    }

    @Test
    void detail_existingTalent_returnsTalent() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        TalentDetailResponse response = new TalentDetailResponse();
        TalentDetailResponse.TalentInfo info = new TalentDetailResponse.TalentInfo();
        info.setId(id.toString());
        info.setNickname("tester");
        response.setTalent(info);
        when(talentQueryService.detail(id, userId, deptId, DataScope.ALL)).thenReturn(response);

        mockMvc.perform(get("/talents/{id}", id)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.talent.nickname").value("tester"));
    }

    @Test
    void create_validTalent_returnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        Talent created = new Talent();
        created.setId(id);
        created.setDouyinUid("uid_123");
        created.setNickname("new talent");
        created.setContactPhone("13800000000");
        created.setContactWechat("wx-secret");
        when(talentService.create(any(Talent.class))).thenReturn(created);

        String body = """
                {"douyinUid":"uid_123","nickname":"new talent","status":1,"ownerId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","blacklisted":true,"contactPhone":"13800000000","contactWechat":"wx-from-client","rawPayload":{"token":"secret"}}
                """;

        mockMvc.perform(post("/talents")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.douyinUid").value("uid_123"))
                .andExpect(jsonPath("$.data.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.data.contactWechat").doesNotExist())
                .andExpect(jsonPath("$.data.rawPayload").doesNotExist());

        verify(talentService).create(argThat(request ->
                "uid_123".equals(request.getDouyinUid())
                        && "new talent".equals(request.getNickname())
                        && "13800000000".equals(request.getContactPhone())
                        && request.getContactWechat() == null
                        && request.getStatus() == null
                        && request.getOwnerId() == null
                        && request.getBlacklisted() == null
                        && request.getRawPayload() == null));
    }

    @Test
    void create_shouldRejectBlankIdentityBeforeCallingService() throws Exception {
        mockMvc.perform(post("/talents")
                        .contentType("application/json")
                        .content("{\"nickname\":\"missing identity\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(talentService, never()).create(any(Talent.class));
    }

    @Test
    void update_validRequest_returnsUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent updated = new Talent();
        updated.setId(id);
        updated.setNickname("updated");
        updated.setContactPhone("13900000000");
        updated.setContactWechat("wx-secret");
        when(talentService.update(any(UUID.class), any(Talent.class))).thenReturn(updated);

        String body = """
                {"nickname":"updated","contactPhone":"13900000000","contactWechat":"wx-from-client","blacklisted":true,"ownerId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","rawPayload":{"token":"secret"}}
                """;

        mockMvc.perform(put("/talents/{id}", id)
                        .contentType("application/json")
                        .requestAttr("userId", userId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("updated"))
                .andExpect(jsonPath("$.data.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.data.contactWechat").doesNotExist())
                .andExpect(jsonPath("$.data.rawPayload").doesNotExist());

        verify(talentQueryService).assertCanOperate(id, userId, null, List.of(RoleCodes.CHANNEL_STAFF));
        verify(talentService).update(any(UUID.class), argThat(request ->
                "updated".equals(request.getNickname())
                        && "13900000000".equals(request.getContactPhone())
                        && request.getContactWechat() == null
                        && request.getOwnerId() == null
                        && request.getBlacklisted() == null
                        && request.getRawPayload() == null));
    }

    @Test
    void update_shouldRejectWhenTalentIsOutsideClaimScope() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        doThrow(new ForbiddenException("无权操作该达人"))
                .when(talentQueryService).assertCanOperate(id, userId, deptId, List.of(RoleCodes.CHANNEL_STAFF));

        mockMvc.perform(put("/talents/{id}", id)
                        .contentType("application/json")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF))
                        .content("{\"nickname\":\"bad-update\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(talentService, never()).update(any(UUID.class), any(Talent.class));
    }

    @Test
    void delete_existingTalent_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/talents/{id}", id)
                        .requestAttr("userId", userId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(talentQueryService).assertCanOperate(id, userId, null, List.of(RoleCodes.CHANNEL_STAFF));
        verify(talentService).delete(id);
    }

    @Test
    void delete_shouldRejectWhenTalentIsOutsideClaimScope() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        doThrow(new ForbiddenException("无权操作该达人"))
                .when(talentQueryService).assertCanOperate(id, userId, deptId, List.of(RoleCodes.CHANNEL_STAFF));

        mockMvc.perform(delete("/talents/{id}", id)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(talentService, never()).delete(any(UUID.class));
    }

    @Test
    void publicPool_returnsPublicTalents() throws Exception {
        when(talentService.getPublicPool()).thenReturn(List.of(new Talent()));

        mockMvc.perform(get("/talents/pools/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void privatePool_returnsPrivateTalents() throws Exception {
        UUID userId = UUID.randomUUID();
        when(talentService.getPrivatePool(userId)).thenReturn(List.of());

        mockMvc.perform(get("/talents/pools/private")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void statusTransitions_returnsClaimStateMatrix() throws Exception {
        mockMvc.perform(get("/talents/status-transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.protectionConfigKey").value("talent.protection_days"))
                .andExpect(jsonPath("$.data.allowMultiClaim").value(true))
                .andExpect(jsonPath("$.data.states[?(@.code=='PUBLIC' && @.canClaim==true)]").exists())
                .andExpect(jsonPath("$.data.states[?(@.code=='PRIVATE_SELF' && @.canRelease==true)]").exists())
                .andExpect(jsonPath("$.data.states[?(@.code=='PRIVATE_OTHERS' && @.canClaim==true)]").exists())
                .andExpect(jsonPath("$.data.states[?(@.code=='BLACKLIST' && @.canClaim==false)]").exists())
                .andExpect(jsonPath("$.data.transitions[?(@.action=='CLAIM' && @.toState=='PRIVATE_SELF')]").exists())
                .andExpect(jsonPath("$.data.transitions[?(@.action=='AUTO_RELEASE_EXPIRED' && @.toState=='RELEASED')]").exists());
    }

    @Test
    void claim_talent_returnsClaimedTalent() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent claimed = new Talent();
        claimed.setId(talentId);
        claimed.setOwnerId(userId);
        when(talentService.claim(talentId, userId, null)).thenReturn(claimed);

        mockMvc.perform(post("/talents/{id}/claims", talentId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void release_talent_returnsReleasedTalent() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent released = new Talent();
        released.setId(talentId);
        when(talentService.release(talentId, userId, null, null)).thenReturn(released);

        mockMvc.perform(post("/talents/{id}/release", talentId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void refresh_talent_returnsRefreshedTalent() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent refreshed = new Talent();
        refreshed.setId(talentId);
        refreshed.setNickname("crawler-updated");
        when(talentService.refresh(talentId)).thenReturn(refreshed);

        mockMvc.perform(post("/talents/{id}/refresh", talentId)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("crawler-updated"));

        verify(talentQueryService).assertCanOperate(talentId, userId, deptId, List.of(RoleCodes.CHANNEL_STAFF));
    }

    @Test
    void refresh_shouldRejectWhenTalentIsOutsideClaimScope() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        doThrow(new ForbiddenException("无权操作该达人"))
                .when(talentQueryService).assertCanOperate(talentId, userId, deptId, List.of(RoleCodes.CHANNEL_STAFF));

        mockMvc.perform(post("/talents/{id}/refresh", talentId)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(talentService, never()).refresh(any(UUID.class));
    }

    @Test
    void blacklist_talent_returnsUpdatedTalent() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent updated = new Talent();
        updated.setId(talentId);
        updated.setBlacklisted(true);
        updated.setBlacklistReason("重复违约");
        when(talentService.blacklist(talentId, "重复违约", userId, deptId, DataScope.DEPT)).thenReturn(updated);

        mockMvc.perform(post("/talents/{id}/blacklist", talentId)
                        .contentType("application/json")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT)
                        .content("{\"reason\":\"重复违约\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blacklisted").value(true))
                .andExpect(jsonPath("$.data.blacklistReason").value("重复违约"));
    }

    @Test
    void blacklist_shouldRejectTooLongReasonBeforeCallingService() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String tooLongReason = "违约".repeat(101);

        mockMvc.perform(post("/talents/{id}/blacklist", talentId)
                        .contentType("application/json")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT)
                        .content("{\"reason\":\"" + tooLongReason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(talentService, never()).blacklist(any(UUID.class), any(), any(), any(), any());
    }

    @Test
    void unblacklist_talent_returnsUpdatedTalent() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent updated = new Talent();
        updated.setId(talentId);
        updated.setBlacklisted(false);
        when(talentService.unblacklist(talentId, userId, deptId, DataScope.DEPT)).thenReturn(updated);

        mockMvc.perform(post("/talents/{id}/unblacklist", talentId)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blacklisted").value(false));
    }

    @Test
    void refreshWeekly_returnsOk() throws Exception {
        mockMvc.perform(post("/talents/refresh/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(talentWeeklyRefreshJob).weeklyRefreshActiveTalents();
    }

    @Test
    void manualFill_talent_returnsUpdatedTalent() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent updated = new Talent();
        updated.setId(talentId);
        updated.setNickname("manual-name");
        updated.setContactPhone("13700000000");
        updated.setContactWechat("wx-secret");
        when(talentService.manualFill(any(UUID.class), any(Talent.class))).thenReturn(updated);

        String body = """
                {"nickname":"manual-name","fansCount":1000,"contactPhone":"13700000000","contactWechat":"wx-from-client","blacklisted":true,"ownerId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","rawPayload":{"token":"secret"}}
                """;

        mockMvc.perform(put("/talents/{id}/manual-fill", talentId)
                        .contentType("application/json")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("manual-name"))
                .andExpect(jsonPath("$.data.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.data.contactWechat").doesNotExist())
                .andExpect(jsonPath("$.data.rawPayload").doesNotExist());

        verify(talentQueryService).assertCanOperate(talentId, userId, deptId, List.of(RoleCodes.CHANNEL_STAFF));
        verify(talentService).manualFill(any(UUID.class), argThat(request ->
                "manual-name".equals(request.getNickname())
                        && Long.valueOf(1000).equals(request.getFans())
                        && "13700000000".equals(request.getContactPhone())
                        && request.getContactWechat() == null
                        && request.getOwnerId() == null
                        && request.getBlacklisted() == null
                        && request.getRawPayload() == null));
    }

    @Test
    void manualFill_shouldRejectWhenTalentIsOutsideClaimScope() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        doThrow(new ForbiddenException("无权操作该达人"))
                .when(talentQueryService).assertCanOperate(talentId, userId, deptId, List.of(RoleCodes.CHANNEL_STAFF));

        mockMvc.perform(put("/talents/{id}/manual-fill", talentId)
                        .contentType("application/json")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF))
                        .content("{\"nickname\":\"blocked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(talentService, never()).manualFill(any(UUID.class), any(Talent.class));
    }

    @Test
    void manualFill_shouldRejectNegativeFansBeforeCallingService() throws Exception {
        UUID talentId = UUID.randomUUID();

        mockMvc.perform(put("/talents/{id}/manual-fill", talentId)
                        .contentType("application/json")
                        .content("{\"fansCount\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(talentService, never()).manualFill(any(UUID.class), any(Talent.class));
    }

    @Test
    void latestEnrichTask_returnsTask() throws Exception {
        UUID talentId = UUID.randomUUID();
        TalentEnrichTask task = new TalentEnrichTask();
        task.setTalentId(talentId);
        task.setTaskStatus("RUNNING");
        when(talentService.getLatestEnrichTask(talentId)).thenReturn(task);

        mockMvc.perform(get("/talents/{id}/enrich-task/latest", talentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskStatus").value("RUNNING"));
    }

    @Test
    void exclusiveCheck_talent_returnsCheckResult() throws Exception {
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TalentService.ExclusiveCheckResult result =
                new TalentService.ExclusiveCheckResult(true, 85, 12);
        when(talentService.evaluateExclusive(talentId, DataScope.ALL, userId, null))
                .thenReturn(result);

        mockMvc.perform(get("/talents/{id}/exclusive-status", talentId)
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.eligible").value(true))
                .andExpect(jsonPath("$.data.serviceFeeRatio").value(85))
                .andExpect(jsonPath("$.data.monthlySamples").value(12));
    }

    @Test
    void controller_shouldOnlyExposeChannelRoles() {
        RequireRoles requireRoles = TalentController.class.getAnnotation(RequireRoles.class);
        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF);
    }

    @Test
    void blacklistAndWeeklyRefresh_shouldRequireChannelLeader() throws Exception {
        Method blacklist = TalentController.class.getMethod(
                "blacklist",
                UUID.class,
                com.colonel.saas.dto.talent.TalentOperateRequest.class,
                UUID.class,
                UUID.class,
                DataScope.class
        );
        Method unblacklist = TalentController.class.getMethod(
                "unblacklist",
                UUID.class,
                UUID.class,
                UUID.class,
                DataScope.class
        );
        Method refreshWeekly = TalentController.class.getMethod("refreshWeekly");
        Method overrideAssignee = TalentController.class.getMethod(
                "overrideAssignee",
                UUID.class,
                com.colonel.saas.dto.talent.OverrideAssigneeRequest.class,
                UUID.class
        );

        assertThat(blacklist.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(blacklist.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.CHANNEL_LEADER);
        assertThat(unblacklist.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(unblacklist.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.CHANNEL_LEADER);
        assertThat(refreshWeekly.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(refreshWeekly.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.CHANNEL_LEADER);
        assertThat(overrideAssignee.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(overrideAssignee.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.ADMIN);
    }
}
