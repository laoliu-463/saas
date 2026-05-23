package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.CommissionRule;
import com.colonel.saas.service.CommissionRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommissionRuleControllerTest {

    @Mock
    private CommissionRuleService commissionRuleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CommissionRuleController controller = new CommissionRuleController(commissionRuleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_returnsFilteredCommissionRules() throws Exception {
        CommissionRule rule = rule("activity", "A-1", "channel", "0.18");
        Page<CommissionRule> page = new Page<>(1, 20);
        page.setRecords(List.of(rule));
        page.setTotal(1);
        when(commissionRuleService.findPage("activity", "channel", 1, 20)).thenReturn(page);

        mockMvc.perform(get("/commission-rules")
                        .param("dimensionType", "activity")
                        .param("commissionType", "channel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].dimensionType").value("activity"))
                .andExpect(jsonPath("$.data.records[0].commissionType").value("channel"));
    }

    @Test
    void create_persistsCommissionRule() throws Exception {
        CommissionRule created = rule("product", "P-1", "recruiter", "0.25");
        created.setId(UUID.randomUUID());
        when(commissionRuleService.create(any(CommissionRule.class))).thenReturn(created);

        mockMvc.perform(post("/commission-rules")
                        .contentType("application/json")
                        .content("""
                                {"dimensionType":"product","dimensionId":"P-1","commissionType":"recruiter","ratio":0.25}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dimensionType").value("product"))
                .andExpect(jsonPath("$.data.dimensionId").value("P-1"))
                .andExpect(jsonPath("$.data.commissionType").value("recruiter"))
                .andExpect(jsonPath("$.data.ratio").value(0.25));

        verify(commissionRuleService).create(argThat(request ->
                "product".equals(request.getDimensionType())
                        && "P-1".equals(request.getDimensionId())
                        && "recruiter".equals(request.getCommissionType())
                        && request.getRatio().compareTo(new BigDecimal("0.25")) == 0));
    }

    @Test
    void delete_softDeletesCommissionRule() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/commission-rules/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(commissionRuleService).delete(id);
    }

    @Test
    void controller_shouldOnlyExposeAdminRole() {
        RequireRoles requireRoles = CommissionRuleController.class.getAnnotation(RequireRoles.class);

        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN);
    }

    private CommissionRule rule(String dimensionType, String dimensionId, String commissionType, String ratio) {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(dimensionType);
        rule.setDimensionId(dimensionId);
        rule.setCommissionType(commissionType);
        rule.setRatio(new BigDecimal(ratio));
        rule.setStatus(1);
        rule.setDeleted(0);
        return rule;
    }
}
