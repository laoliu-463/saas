package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerMasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 团长主数据控制器单元测试。
 *
 * <p>覆盖 3 类场景：正常返回 / 空数据 / 鉴权失败。
 * <p>注意：{@code standaloneSetup} 不会激活 {@code @RequireRoles} 的 AOP 切面，
 * 因此鉴权失败实际由 Spring Security 过滤器链处理，本测试只能验证 controller 正常委托 service。
 * 鉴权失败的端到端验证进入 real-pre 联调或 {@code ColonelActivityControllerTest#assignee_returns403WhenNonAdmin}。
 */
@ExtendWith(MockitoExtension.class)
class ColonelPartnerMasterDataControllerTest {

    @Mock
    private ColonelPartnerMasterDataService colonelPartnerMasterDataService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ColonelPartnerMasterDataController controller = new ColonelPartnerMasterDataController(colonelPartnerMasterDataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsPagedResult() throws Exception {
        ColonelPartner partner = sample("王团长", "BUYIN");
        when(colonelPartnerMasterDataService.list("王", "BUYIN", Boolean.TRUE, 1L, 20L))
                .thenReturn(new PageResult<ColonelPartner>() {{
                    setTotal(1L);
                    setPage(1L);
                    setSize(20L);
                    setRecords(List.of(partner));
                }});

        mockMvc.perform(get("/api/colonel-partners")
                        .param("keyword", "王")
                        .param("source", "BUYIN")
                        .param("hasContact", "true")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].colonelName").value("王团长"));

        verify(colonelPartnerMasterDataService).list("王", "BUYIN", Boolean.TRUE, 1L, 20L);
    }

    @Test
    void list_returnsEmptyRecordsWhenNoMatch() throws Exception {
        when(colonelPartnerMasterDataService.list(any(), any(), any(), eq(1L), eq(20L)))
                .thenReturn(new PageResult<ColonelPartner>() {{
                    setTotal(0L);
                    setPage(1L);
                    setSize(20L);
                    setRecords(List.of());
                }});

        mockMvc.perform(get("/api/colonel-partners")
                        .param("keyword", "不存在的团长"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void detail_returnsPartnerWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        ColonelPartner partner = sample("李团长", "MANUAL");
        partner.setId(id);
        when(colonelPartnerMasterDataService.detail(id)).thenReturn(partner);

        mockMvc.perform(get("/api/colonel-partners/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colonelName").value("李团长"))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void sources_returnsDistinctSources() throws Exception {
        when(colonelPartnerMasterDataService.listSources()).thenReturn(List.of("BUYIN", "MANUAL"));

        mockMvc.perform(get("/api/colonel-partners/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("BUYIN"))
                .andExpect(jsonPath("$.data[1]").value("MANUAL"));
    }

    private static ColonelPartner sample(String name, String source) {
        ColonelPartner p = new ColonelPartner();
        p.setId(UUID.randomUUID());
        p.setColonelName(name);
        p.setSource(source);
        return p;
    }
}
