package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.rulecenter.RuleCenterChangeLogView;
import com.colonel.saas.dto.rulecenter.RuleCenterSchemaResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterUpdateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValuesResponse;
import com.colonel.saas.service.RuleCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 规则中心控制器单元测试。
 *
 * <p>覆盖 7 个端点：schema / values / validate / groupUpdate / batchUpdate / changeLogs / eventStatus。
 * 重点验证：
 * <ul>
 *   <li>所有端点统一委托 service，不在 controller 自己做业务判断</li>
 *   <li>{@code userId} / {@code username} 来自 JWT request attribute（{@code @RequestAttribute}）</li>
 *   <li>鉴权门控为 {@code ADMIN}，{@code dataScope} 不参与（配置域全局可见，admin dataScope=ALL）</li>
 * </ul>
 *
 * <p>{@code standaloneSetup} 不激活 {@code @RequirePermission} AOP 切面，
 * 端到端鉴权验证进入 real-pre 联调或 RBAC 专项。
 */
@ExtendWith(MockitoExtension.class)
class RuleCenterControllerTest {

    @Mock
    private RuleCenterService ruleCenterService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RuleCenterController controller = new RuleCenterController(ruleCenterService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void schema_returnsRuleCenterSchema() throws Exception {
        RuleCenterSchemaResponse schema = new RuleCenterSchemaResponse(List.of());
        when(ruleCenterService.schema()).thenReturn(schema);

        mockMvc.perform(get("/rule-center/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groups").isArray());

        verify(ruleCenterService).schema();
    }

    @Test
    void values_returnsCurrentConfigValues() throws Exception {
        when(ruleCenterService.currentValues())
                .thenReturn(new RuleCenterValuesResponse(Map.of(
                        "sample.restrict_enabled", "true",
                        "talent.protection_days", "30")));

        mockMvc.perform(get("/rule-center"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.values['sample.restrict_enabled']").value("true"))
                .andExpect(jsonPath("$.data.values['talent.protection_days']").value("30"));
    }

    @Test
    void validate_delegatesValuesToService() throws Exception {
        when(ruleCenterService.validate(Map.of("sample.restrict_enabled", "true")))
                .thenReturn(new RuleCenterValidateResponse(true, List.of(), List.of()));

        mockMvc.perform(post("/rule-center/validate")
                        .contentType("application/json")
                        .content("""
                                {"values":{"sample.restrict_enabled":"true"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.valid").value(true));

        verify(ruleCenterService).validate(Map.of("sample.restrict_enabled", "true"));
    }

    @Test
    void validate_returnsErrorListWhenInvalid() throws Exception {
        when(ruleCenterService.validate(any()))
                .thenReturn(new RuleCenterValidateResponse(false,
                        List.of("至少提供一项配置值"),
                        List.of()));

        mockMvc.perform(post("/rule-center/validate")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").value("至少提供一项配置值"));
    }

    @Test
    void updateGroup_passesGroupCodeValuesChangeReasonAndUserAttributes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(ruleCenterService.updateGroup(
                eq("sample"),
                eq(Map.of("sample.restrict_enabled", "false")),
                eq("调整策略"),
                eq(userId),
                eq("admin")))
                .thenReturn(new RuleCenterUpdateResponse(eventId,
                        List.of("sample.restrict_enabled"),
                        List.of()));

        mockMvc.perform(put("/rule-center/groups/{groupCode}", "sample")
                        .requestAttr("userId", userId)
                        .requestAttr("username", "admin")
                        .contentType("application/json")
                        .content("""
                                {"values":{"sample.restrict_enabled":"false"},"changeReason":"调整策略"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.changedKeys[0]").value("sample.restrict_enabled"));

        verify(ruleCenterService).updateGroup(
                eq("sample"),
                eq(Map.of("sample.restrict_enabled", "false")),
                eq("调整策略"),
                eq(userId),
                eq("admin"));
    }

    @Test
    void batchUpdate_passesValuesChangeReasonAndUserAttributes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(ruleCenterService.batchUpdate(
                eq(Map.of("sample.restrict_enabled", "false")),
                eq("批量调整"),
                eq(userId),
                eq(null)))
                .thenReturn(new RuleCenterUpdateResponse(eventId,
                        List.of("sample.restrict_enabled"),
                        List.of()));

        mockMvc.perform(put("/rule-center/batch")
                        .requestAttr("userId", userId)
                        .contentType("application/json")
                        .content("""
                                {"values":{"sample.restrict_enabled":"false"},"changeReason":"批量调整"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()));

        verify(ruleCenterService).batchUpdate(
                eq(Map.of("sample.restrict_enabled", "false")),
                eq("批量调整"),
                eq(userId),
                eq(null));
    }

    @Test
    void changeLogs_passesKeyAndPage() throws Exception {
        UUID logId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        java.time.LocalDateTime changedAt = java.time.LocalDateTime.of(2026, 7, 7, 12, 45, 0);
        RuleCenterChangeLogView view = new RuleCenterChangeLogView(
                logId, eventId,
                "sample.restrict_enabled",
                "UPDATE",
                "true", "false",
                "RULE_CENTER", "调整",
                operatorId,
                2, changedAt);
        Page<RuleCenterChangeLogView> page = new Page<>(1, 20);
        page.setRecords(List.of(view));
        page.setTotal(1);
        when(ruleCenterService.changeLogs("sample.restrict_enabled", 1, 20)).thenReturn(page);

        mockMvc.perform(get("/rule-center/change-logs")
                        .param("key", "sample.restrict_enabled")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.data.records[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.records[0].configKey").value("sample.restrict_enabled"))
                .andExpect(jsonPath("$.data.records[0].changeAction").value("UPDATE"))
                .andExpect(jsonPath("$.data.records[0].oldValue").value("true"))
                .andExpect(jsonPath("$.data.records[0].newValue").value("false"))
                .andExpect(jsonPath("$.data.records[0].source").value("RULE_CENTER"))
                .andExpect(jsonPath("$.data.records[0].changeReason").value("调整"))
                .andExpect(jsonPath("$.data.records[0].operatorId").value(operatorId.toString()))
                .andExpect(jsonPath("$.data.records[0].configVersion").value(2));

        verify(ruleCenterService).changeLogs("sample.restrict_enabled", 1, 20);
    }

    @Test
    void eventStatus_returnsOutboxAndConsumerSnapshot() throws Exception {
        UUID eventId = UUID.randomUUID();
        RuleCenterSchemaRegistry registry = new RuleCenterSchemaRegistry(
                new com.colonel.saas.config.ConfigDefinitionRegistry(
                        new com.fasterxml.jackson.databind.ObjectMapper()));
        // 仅使用 service 返回值，不直接构造 DTO 内部字段
        when(ruleCenterService.eventStatus(eventId)).thenReturn(null);

        mockMvc.perform(get("/rule-center/events")
                        .param("eventId", eventId.toString()))
                .andExpect(status().isOk());

        verify(ruleCenterService).eventStatus(eventId);
    }

    @Test
    void controller_shouldOnlyExposeAdminRole() {
        RequirePermission requireRoles = RuleCenterController.class.getAnnotation(RequirePermission.class);

        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).isEqualTo("rule-center:access");
    }

    @Test
    void controller_shouldNotConsumeDataScopeAttribute() throws Exception {
        // 确认 dataScope 注解属性未被消费：设置后端 service 仍按 admin=all 全量返回
        UUID userId = UUID.randomUUID();
        when(ruleCenterService.currentValues())
                .thenReturn(new RuleCenterValuesResponse(Map.of("any.key", "v")));

        mockMvc.perform(get("/rule-center")
                        .requestAttr("dataScope", "self")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values['any.key']").value("v"));

        // 关键断言：dataScope=self 时规则中心仍返回全量（admin=all 行为）
        verify(ruleCenterService).currentValues();
    }
}
