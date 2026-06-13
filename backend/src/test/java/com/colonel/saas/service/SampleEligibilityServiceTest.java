package com.colonel.saas.service;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.domain.config.facade.dto.SampleRulesDTO;
import com.colonel.saas.entity.Talent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleEligibilityServiceTest {

    @Mock
    private ConfigDomainFacade configDomainFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private SampleEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new SampleEligibilityService(configDomainFacade, jdbcTemplate);
    }

    @Test
    void evaluate_shouldReadDefaultSampleRequirementsFromFacade() {
        when(configDomainFacade.getSampleRules()).thenReturn(new SampleRulesDTO(
                7, true, 30, 15,
                new SampleDefaultStandardDTO(5000L, "LV1", Map.of())));

        Talent talent = new Talent();
        talent.setSales30d(8000L);
        talent.setTalentLevel("LV2");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityService.EligibilityResult result = service.evaluate(talent, null);

        verify(configDomainFacade).getSampleRules();
        assertThat(result.eligible()).isTrue();
        assertThat(result.standard().min30DaySales()).isEqualTo(5000L);
        assertThat(result.standard().minLevel()).isEqualTo("LV1");
    }

    @Test
    void evaluate_shouldRejectWhenBelowConfiguredThreshold() {
        when(configDomainFacade.getSampleRules()).thenReturn(new SampleRulesDTO(
                7, true, 30, 15,
                new SampleDefaultStandardDTO(10000L, "LV2", Map.of())));

        Talent talent = new Talent();
        talent.setSales30d(2000L);
        talent.setTalentLevel("LV1");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityService.EligibilityResult result = service.evaluate(talent, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("10000"));
    }

    @Test
    void classifyFailureRules_shouldDelegateToPolicy() {
        assertThat(service.classifyFailureRules(List.of(
                "近30天销售额未达到 10000",
                "达人等级未达到 LV2",
                "人工规则")))
                .containsExactly("min30DaySales", "minLevel", "custom");
    }
}
