package com.colonel.saas.service;

import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleEligibilityServiceTest {

    @Mock
    private BusinessRuleConfigService businessRuleConfigService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private SampleEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new SampleEligibilityService(businessRuleConfigService, jdbcTemplate);
    }

    @Test
    void shouldPassWhenTalentMeetsStandard() {
        when(businessRuleConfigService.getSampleDefaultStandard())
                .thenReturn(new BusinessRuleConfigService.SampleDefaultStandardConfig(30000L, "LV1", Map.of()));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(), any()))
                .thenReturn(50000L);

        Talent talent = new Talent();
        talent.setDouyinUid("talent_ok");
        talent.setTalentLevel("LV2");
        talent.setUnsupportedFields(java.util.List.of());

        var result = service.evaluate(talent, null);

        assertThat(result.eligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void shouldFailWhenTalentBelowStandard() {
        when(businessRuleConfigService.getSampleDefaultStandard())
                .thenReturn(new BusinessRuleConfigService.SampleDefaultStandardConfig(30000L, "LV1", Map.of()));

        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId("talent_low");
        info.setFansCount(5000L);

        var result = service.evaluate(null, info);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("销售额") || reason.contains("等级"));
        assertThat(result.actual().level()).isNull();
    }

    @Test
    void shouldRequireReasonWhenTalentLevelUnsupported() {
        when(businessRuleConfigService.getSampleDefaultStandard())
                .thenReturn(new BusinessRuleConfigService.SampleDefaultStandardConfig(null, "LV1", Map.of()));
        Talent talent = new Talent();
        talent.setDouyinUid("talent_unsupported");
        talent.setUnsupportedFields(java.util.List.of("talentLevel", "sales30d"));

        var result = service.evaluate(talent, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("达人等级未同步"));
    }

    @Test
    void shouldUseTalentSalesAndNormalizeLegacyLevelsWithoutQueryingOrders() {
        when(businessRuleConfigService.getSampleDefaultStandard())
                .thenReturn(new BusinessRuleConfigService.SampleDefaultStandardConfig(1000L, "LV2", Map.of()));
        Talent talent = new Talent();
        talent.setSales30d(1500L);
        talent.setLevel("S");
        talent.setUnsupportedFields(java.util.List.of());

        var result = service.evaluate(talent, null);

        assertThat(result.eligible()).isTrue();
        assertThat(result.actual().monthlySales()).isEqualTo(1500L);
        assertThat(result.actual().level()).isEqualTo("LV2");
    }

    @Test
    void shouldTreatEmptyUnsupportedFieldsAsAllFieldsSupported() {
        when(businessRuleConfigService.getSampleDefaultStandard())
                .thenReturn(new BusinessRuleConfigService.SampleDefaultStandardConfig(1000L, "LV1", Map.of()));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(), any()))
                .thenReturn(null);
        Talent talent = new Talent();
        talent.setDouyinUid("talent_uid");
        talent.setUnsupportedFields(java.util.List.of());

        var result = service.evaluate(talent, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.actual().monthlySales()).isEqualTo(0L);
        assertThat(result.actual().level()).isNull();
    }
}
