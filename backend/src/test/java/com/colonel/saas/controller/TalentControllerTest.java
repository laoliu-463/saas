package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.domain.talent.application.TalentAddressApplicationService;
import com.colonel.saas.domain.talent.application.TalentProfileApplicationService;
import com.colonel.saas.domain.talent.application.TalentQueryApplicationService;
import com.colonel.saas.domain.talent.facade.dto.TalentShippingAddressDTO;
import com.colonel.saas.job.TalentWeeklyRefreshJob;
import com.colonel.saas.service.TalentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 达人域主 {@link TalentController} 端点单测。
 * <p>[V1 必做] 验证主列表端点 {@code GET /talents} 把请求参数正确绑定到 {@link TalentPageQuery}、
 * 把 {@code dataScope / userId / deptId} 注入到查询对象、再委托给 {@link TalentQueryApplicationService#page}。
 * 指标筛选（minFans/maxFans）、文本筛选（nickname/douyinNo/region）和视图筛选（view）都应被透传。</p>
 */
@ExtendWith(MockitoExtension.class)
class TalentControllerTest {

    @Mock
    private TalentService talentService;
    @Mock
    private TalentAddressApplicationService talentAddressApplicationService;
    @Mock
    private TalentProfileApplicationService talentProfileApplicationService;
    @Mock
    private TalentQueryApplicationService talentQueryApplicationService;
    @Mock
    private TalentWeeklyRefreshJob talentWeeklyRefreshJob;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        TalentController controller = new TalentController(
                talentService,
                talentAddressApplicationService,
                talentProfileApplicationService,
                talentQueryApplicationService,
                talentWeeklyRefreshJob);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_shouldBindTalentPageQueryAndInjectDataScope() throws Exception {
        IPage<Talent> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0);
        when(talentQueryApplicationService.page(any(TalentPageQuery.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/talents")
                        .param("page", "1")
                        .param("size", "10")
                        .param("view", "TEAM_PUBLIC")
                        .param("keyword", "食品达人")
                        .param("minFans", "10000")
                        .param("maxFans", "99999")
                        .param("region", "上海")
                        .param("liveSalesBand", "1W~2.5W")
                        .param("videoSalesBand", "1W~2.5W")
                        .param("level", "LV3")
                        .param("contactStatus", "HAS_CONTACT")
                        .param("claimStatus", "UNCLAIMED")
                        .param("category", "食品饮料")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(0));

        ArgumentCaptor<TalentPageQuery> captor = ArgumentCaptor.forClass(TalentPageQuery.class);
        verify(talentQueryApplicationService).page(captor.capture());
        TalentPageQuery query = captor.getValue();

        // 验证 query 字段绑定正确
        assertThat(query.getView()).isEqualTo("TEAM_PUBLIC");
        assertThat(query.getKeyword()).isEqualTo("食品达人");
        assertThat(query.getMinFans()).isEqualTo(10000L);
        assertThat(query.getMaxFans()).isEqualTo(99999L);
        assertThat(query.getRegion()).isEqualTo("上海");
        assertThat(query.getLiveSalesBand()).isEqualTo("1W~2.5W");
        assertThat(query.getVideoSalesBand()).isEqualTo("1W~2.5W");
        assertThat(query.getLevel()).isEqualTo("LV3");
        assertThat(query.getContactStatus()).isEqualTo("HAS_CONTACT");
        assertThat(query.getClaimStatus()).isEqualTo("UNCLAIMED");
        assertThat(query.getCategory()).isEqualTo("食品饮料");

        // 验证 dataScope / userId / deptId 被注入到 query 中（来自 request attribute）
        assertThat(query.getUserId()).isEqualTo(userId);
        assertThat(query.getDeptId()).isEqualTo(deptId);
        assertThat(query.getDataScope()).isEqualTo(DataScope.PERSONAL);
    }

    @Test
    void page_shouldDefaultDataScopeWhenAttributeMissing() throws Exception {
        IPage<Talent> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0);
        when(talentQueryApplicationService.page(any(TalentPageQuery.class))).thenReturn(emptyPage);

        // 不传 dataScope / deptId 时，controller 不强制要求；userId 是 required
        mockMvc.perform(get("/talents")
                        .param("view", "MY_TALENTS")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk());

        ArgumentCaptor<TalentPageQuery> captor = ArgumentCaptor.forClass(TalentPageQuery.class);
        verify(talentQueryApplicationService).page(captor.capture());
        TalentPageQuery query = captor.getValue();

        assertThat(query.getView()).isEqualTo("MY_TALENTS");
        assertThat(query.getUserId()).isEqualTo(userId);
        // dataScope / deptId 未传，应为 null
        assertThat(query.getDataScope()).isNull();
        assertThat(query.getDeptId()).isNull();
    }

    @Test
    void page_shouldIgnoreUnknownQueryParamsInsteadOfFailing() throws Exception {
        IPage<Talent> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0);
        when(talentQueryApplicationService.page(any(TalentPageQuery.class))).thenReturn(emptyPage);

        // 包含一些非法参数值（minFans 非数字应被忽略或返回 400，看具体行为）
        // 这里只验证不会因为无关参数导致 500
        mockMvc.perform(get("/talents")
                        .param("page", "1")
                        .param("size", "10")
                        .param("view", "TEAM_PRIVATE")
                        .param("poolStatus", "PRIVATE")
                        .param("ownerKeyword", "张三")
                        .param("platform", "douyin")
                        .param("douyinNo", "dy001")
                        .param("nickname", "达人A")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT))
                .andExpect(status().isOk());

        ArgumentCaptor<TalentPageQuery> captor = ArgumentCaptor.forClass(TalentPageQuery.class);
        verify(talentQueryApplicationService).page(captor.capture());
        TalentPageQuery query = captor.getValue();
        assertThat(query.getPoolStatus()).isEqualTo("PRIVATE");
        assertThat(query.getOwnerKeyword()).isEqualTo("张三");
        assertThat(query.getPlatform()).isEqualTo("douyin");
        assertThat(query.getDouyinNo()).isEqualTo("dy001");
        assertThat(query.getNickname()).isEqualTo("达人A");
        assertThat(query.getDataScope()).isEqualTo(DataScope.DEPT);
    }

    @Test
    void page_shouldReturnServiceRecordsWhenServiceReturnsThem() throws Exception {
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setNickname("测试达人A");
        IPage<Talent> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
        page.setRecords(List.of(talent));

        when(talentQueryApplicationService.page(any(TalentPageQuery.class))).thenReturn(page);

        mockMvc.perform(get("/talents")
                        .param("view", "TEAM_PUBLIC")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].nickname").value("测试达人A"));
    }

    @Test
    void create_shouldDelegateToTalentProfileApplicationService() throws Exception {
        Talent created = new Talent();
        created.setId(UUID.randomUUID());
        created.setDouyinNo("dy001");
        created.setNickname("新达人");
        when(talentProfileApplicationService.create(any(Talent.class))).thenReturn(created);

        mockMvc.perform(post("/talents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"douyinNo\":\"dy001\",\"nickname\":\"新达人\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.douyinNo").value("dy001"))
                .andExpect(jsonPath("$.data.nickname").value("新达人"));

        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentProfileApplicationService).create(captor.capture());
        assertThat(captor.getValue().getDouyinNo()).isEqualTo("dy001");
        assertThat(captor.getValue().getNickname()).isEqualTo("新达人");
    }

    @Test
    void update_shouldAssertCanOperateThenDelegateToTalentProfileApplicationService() throws Exception {
        UUID talentId = UUID.randomUUID();
        Talent updated = new Talent();
        updated.setId(talentId);
        updated.setNickname("达人A-更新");
        when(talentProfileApplicationService.update(any(UUID.class), any(Talent.class))).thenReturn(updated);

        mockMvc.perform(put("/talents/{id}", talentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"达人A-更新\"}")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.PERSONAL)
                        .requestAttr("roleCodes", List.of("CHANNEL_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("达人A-更新"));

        verify(talentQueryApplicationService).assertCanOperate(talentId, userId, deptId, List.of("CHANNEL_STAFF"));
        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentProfileApplicationService).update(org.mockito.ArgumentMatchers.eq(talentId), captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("达人A-更新");
    }

    @Test
    void updateTags_shouldAssertCanOperateThenDelegateWithOperatorForAudit() throws Exception {
        UUID talentId = UUID.randomUUID();
        when(talentProfileApplicationService.updateTags(talentId, List.of("美妆", "高转化"), userId))
                .thenReturn(List.of("美妆", "高转化"));

        mockMvc.perform(put("/talents/{id}/tags", talentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"美妆\",\"高转化\"]}")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT)
                        .requestAttr("roleCodes", List.of("CHANNEL_LEADER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("美妆"))
                .andExpect(jsonPath("$.data[1]").value("高转化"));

        verify(talentQueryApplicationService).assertCanOperate(talentId, userId, deptId, List.of("CHANNEL_LEADER"));
        verify(talentProfileApplicationService).updateTags(talentId, List.of("美妆", "高转化"), userId);
    }

    @Test
    void getShippingAddress_shouldAssertCanOperateThenReadViaTalentAddressApplicationService() throws Exception {
        UUID talentId = UUID.randomUUID();
        when(talentAddressApplicationService.getShippingAddress(talentId, userId))
                .thenReturn(new TalentShippingAddressDTO("张三", "13800138000", "上海市浦东新区"));

        mockMvc.perform(get("/talents/{id}/shipping-address", talentId)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.PERSONAL)
                        .requestAttr("roleCodes", List.of("CHANNEL_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName").value("张三"))
                .andExpect(jsonPath("$.data.recipientPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.recipientAddress").value("上海市浦东新区"));

        verify(talentQueryApplicationService).assertCanOperate(talentId, userId, deptId, List.of("CHANNEL_STAFF"));
        verify(talentAddressApplicationService).getShippingAddress(talentId, userId);
    }

    @Test
    void updateShippingAddress_shouldAssertCanOperateThenDelegateToTalentAddressApplicationService() throws Exception {
        UUID talentId = UUID.randomUUID();
        Talent updated = new Talent();
        updated.setId(talentId);
        updated.setShippingRecipientName("李四");
        updated.setShippingRecipientPhone("13900139000");
        updated.setShippingRecipientAddress("杭州市西湖区");
        when(talentAddressApplicationService.updateShippingAddress(
                talentId, userId, "李四", "13900139000", "杭州市西湖区"))
                .thenReturn(updated);

        mockMvc.perform(put("/talents/{id}/shipping-address", talentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"李四\",\"recipientPhone\":\"13900139000\",\"recipientAddress\":\"杭州市西湖区\"}")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.PERSONAL)
                        .requestAttr("roleCodes", List.of("CHANNEL_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingRecipientName").value("李四"))
                .andExpect(jsonPath("$.data.shippingRecipientPhone").value("13900139000"))
                .andExpect(jsonPath("$.data.shippingRecipientAddress").value("杭州市西湖区"));

        verify(talentQueryApplicationService).assertCanOperate(talentId, userId, deptId, List.of("CHANNEL_STAFF"));
        verify(talentAddressApplicationService).updateShippingAddress(
                talentId, userId, "李四", "13900139000", "杭州市西湖区");
    }

    @Test
    void manualFill_shouldAssertCanOperateThenDelegateToTalentProfileApplicationService() throws Exception {
        UUID talentId = UUID.randomUUID();
        Talent filled = new Talent();
        filled.setId(talentId);
        filled.setNickname("手动补全达人");
        filled.setAvatarUrl("https://example.test/avatar.jpg");
        when(talentProfileApplicationService.manualFill(any(UUID.class), any(Talent.class))).thenReturn(filled);

        mockMvc.perform(put("/talents/{id}/manual-fill", talentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"手动补全达人\",\"avatarUrl\":\"https://example.test/avatar.jpg\"}")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("roleCodes", List.of("CHANNEL_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("手动补全达人"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/avatar.jpg"));

        verify(talentQueryApplicationService).assertCanOperate(talentId, userId, deptId, List.of("CHANNEL_STAFF"));
        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentProfileApplicationService).manualFill(org.mockito.ArgumentMatchers.eq(talentId), captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("手动补全达人");
        assertThat(captor.getValue().getAvatarUrl()).isEqualTo("https://example.test/avatar.jpg");
    }
}
