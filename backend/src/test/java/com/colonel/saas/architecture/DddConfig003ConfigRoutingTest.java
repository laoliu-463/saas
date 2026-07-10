package com.colonel.saas.architecture;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.ExclusiveRulesDTO;
import com.colonel.saas.domain.config.facade.dto.PromotionTemplateDTO;
import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.domain.config.facade.dto.SampleRulesDTO;
import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.domain.event.ConfigChangedEventConsumer;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.service.CommissionRuleService;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.ExclusiveMerchantService;
import com.colonel.saas.service.PerformanceCalculationService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.PromotionLinkIdempotencyService;
import com.colonel.saas.service.SampleEligibilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * DDD-CONFIG-003：业绩域与商品域配置读取改走 {@link ConfigDomainFacade}。
 */
@ExtendWith(MockitoExtension.class)
class DddConfig003ConfigRoutingTest {

    @Mock private ConfigDomainFacade configDomainFacade;
    @Mock private CommissionRuleService commissionRuleService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ExclusiveMerchantMapper exclusiveMerchantMapper;

    @Test
    @DisplayName("业绩提成比例从 ConfigDomainFacade 读取")
    void commissionRates_shouldReadFromConfigDomainFacade() {
        when(configDomainFacade.getDecimal(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, new BigDecimal("0.15")))
                .thenReturn(new BigDecimal("0.08"));
        when(configDomainFacade.getDecimal(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, new BigDecimal("0.15")))
                .thenReturn(new BigDecimal("0.12"));
        when(commissionRuleService.resolveRule(anyString(), any(), any())).thenReturn(null);

        CommissionService service = new CommissionService(configDomainFacade, commissionRuleService, null);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);

        CommissionService.CommissionSummary summary = service.calculate(List.of(order));

        verify(configDomainFacade).getDecimal(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, new BigDecimal("0.15"));
        verify(configDomainFacade).getDecimal(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, new BigDecimal("0.15"));
        verify(configDomainFacade, never()).getConfig("commission.business_default_ratio");
        verify(configDomainFacade, never()).getConfig("commission.channel_default_ratio");
        assertThat(summary.bizRatio()).isEqualByComparingTo("0.08");
        assertThat(summary.channelRatio()).isEqualByComparingTo("0.12");
    }

    @Test
    @DisplayName("提成比例缺失时 fallback 到 0.15")
    void commissionRates_shouldFallbackWhenConfigMissing() {
        when(configDomainFacade.getDecimal(anyString(), any())).thenReturn(null);
        when(commissionRuleService.resolveRule(anyString(), any(), any())).thenReturn(null);

        CommissionService service = new CommissionService(configDomainFacade, commissionRuleService, null);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setSettleColonelCommission(10000L);

        CommissionService.CommissionSummary summary = service.calculate(List.of(order));

        assertThat(summary.bizRatio()).isEqualByComparingTo("0.15");
        assertThat(summary.channelRatio()).isEqualByComparingTo("0.15");
    }

    @Test
    @DisplayName("配置变更不自动重算历史业绩：CommissionService 不实现 ConfigChangedEventConsumer")
    void commissionService_shouldNotAutoRecalculateOnConfigChange() {
        assertThat(ConfigChangedEventConsumer.class.isAssignableFrom(CommissionService.class)).isFalse();
        assertThat(ConfigChangedEventConsumer.class.isAssignableFrom(PerformanceCalculationService.class)).isFalse();
    }

    @Test
    @DisplayName("独家商家阈值从 ConfigDomainFacade 读取")
    void exclusiveMerchant_shouldReadThresholdFromFacade() {
        when(configDomainFacade.getExclusiveRules()).thenReturn(new ExclusiveRulesDTO(new BigDecimal("82")));
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenReturn(List.of());

        ExclusiveMerchantService service =
                new ExclusiveMerchantService(configDomainFacade, jdbcTemplate, exclusiveMerchantMapper);
        service.evaluateMonth(java.time.YearMonth.of(2024, 6), java.time.YearMonth.of(2024, 7));

        verify(configDomainFacade).getExclusiveRules();
    }

    @Test
    @DisplayName("default_sample_requirements 从 ConfigDomainFacade 读取")
    void sampleEligibility_shouldReadDefaultRequirementsFromFacade() {
        when(configDomainFacade.getSampleRules()).thenReturn(new SampleRulesDTO(
                7, true, 30, 15,
                new SampleDefaultStandardDTO(30000L, "LV2", Map.of("min_30day_sales", 30000))));

        SampleEligibilityService service = new SampleEligibilityService(configDomainFacade, jdbcTemplate);
        Talent talent = new Talent();
        talent.setSales30d(40000L);
        talent.setTalentLevel("LV3");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityService.EligibilityResult result = service.evaluate(talent, new CrawlerTalentInfo());

        verify(configDomainFacade).getSampleRules();
        assertThat(result.eligible()).isTrue();
        assertThat(result.standard().min30DaySales()).isEqualTo(30000L);
        assertThat(result.standard().minLevel()).isEqualTo("LV2");
    }

    @Test
    @DisplayName("商品复制模板从 ConfigDomainFacade 读取")
    void productCopyTemplate_shouldReadFromFacade() {
        ProductService productService = minimalProductService();
        when(configDomainFacade.getPromotionTemplate()).thenReturn(new PromotionTemplateDTO(
                "【{productName}】佣金{commissionRate} 链接{shortLink}",
                "channel_{channel_code}",
                "none"));

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setTitle("测试商品");
        snapshot.setProductId("P-001");
        snapshot.setActivityCosRatioText("20%");

        String text = ReflectionTestUtils.invokeMethod(
                productService,
                "buildProductBriefCopyText",
                snapshot,
                null,
                "https://short.example/abc");

        verify(configDomainFacade).getPromotionTemplate();
        assertThat(text).contains("测试商品");
        assertThat(text).contains("20%");
        assertThat(text).contains("https://short.example/abc");
    }

    @Test
    @DisplayName("pick_extra_rule 从 ConfigDomainFacade 读取")
    void pickExtraRule_shouldReadFromFacade() {
        ProductService productService = minimalProductService();
        when(configDomainFacade.getPromotionTemplate()).thenReturn(new PromotionTemplateDTO(
                "template",
                "channel_{channel_code}_pid_{product_id}",
                "none"));

        String pickExtra = ReflectionTestUtils.invokeMethod(
                productService,
                "buildPickExtra",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "demo01",
                "9",
                "ACT-1");

        verify(configDomainFacade).getPromotionTemplate();
        assertThat(pickExtra).contains("demo01");
        assertThat(pickExtra).contains("pid_9");
    }

    private ProductService minimalProductService() {
        return new ProductService(
                (DouyinConvertPort) null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                new PromotionLinkIdempotencyService(new com.fasterxml.jackson.databind.ObjectMapper()),
                configDomainFacade,
                null, null, null, new com.colonel.saas.domain.product.policy.ProductDisplayPolicy(), null);
    }
}
