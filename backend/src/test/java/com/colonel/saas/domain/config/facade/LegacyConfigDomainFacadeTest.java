package com.colonel.saas.domain.config.facade;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.dto.CommissionRatesDTO;
import com.colonel.saas.domain.config.facade.dto.ExclusiveRulesDTO;
import com.colonel.saas.domain.config.facade.dto.PromotionTemplateDTO;
import com.colonel.saas.domain.config.facade.dto.SampleRulesDTO;
import com.colonel.saas.domain.config.facade.dto.TalentRulesDTO;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.colonel.saas.domain.config.infrastructure.BusinessRuleConfigService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.domain.config.application.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ConfigDomainFacade 契约测试。
 * <ul>
 *   <li>DDD-CONFIG-002：寄样/达人核心阈值（{@link #sampleLimitDays_shouldBeSevenWhenConfigured} 等6个用例）</li>
 *   <li>DDD-CONFIG-001：通用只读入口（{@link #rawAccessors}） + 聚合 DTO（{@link #aggregateDtos}）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyConfigDomainFacadeTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @Mock
    private SysConfigService sysConfigService;

    private ConfigDomainFacade facade;

    @BeforeEach
    void setUp() {
        BusinessRuleConfigService businessRuleConfigService =
                new BusinessRuleConfigService(systemConfigMapper, new ObjectMapper(), new ShortTtlCacheService());
        lenient().when(sysConfigService.getConfigValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return systemConfigMapper.findByConfigKey(key)
                    .filter(config -> config.getDeleted() == null || config.getDeleted() == 0)
                    .map(SystemConfig::getConfigValue)
                    .orElse(null);
        });
        facade = new LegacyConfigDomainFacade(businessRuleConfigService, sysConfigService, new ObjectMapper());
    }

    // ==================== DDD-CONFIG-002：寄样/达人核心阈值 ====================

    @Test
    void sampleLimitDays_shouldBeSevenWhenConfigured() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                .thenReturn(Optional.of(config("7")));

        assertThat(facade.getSampleLimitDays()).isEqualTo(7);
    }

    @Test
    void sampleLimitEnabled_falseShouldAllowRepeatApplyPath() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED))
                .thenReturn(Optional.of(config("false")));

        assertThat(facade.isSampleLimitEnabled()).isFalse();
    }

    @Test
    void sampleAutoCloseDays_shouldReadHomeworkTimeout() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS))
                .thenReturn(Optional.of(config("25")));

        assertThat(facade.getSampleAutoCloseDays()).isEqualTo(25);
    }

    @Test
    void talentClaimProtectDays_shouldBeThirtyWhenConfigured() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS))
                .thenReturn(Optional.of(config("30")));

        assertThat(facade.getTalentClaimProtectDays()).isEqualTo(30);
    }

    @Test
    void exclusiveTalentThresholds_shouldReadRatioAndMonthlySamples() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO))
                .thenReturn(Optional.of(config("82")));
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES))
                .thenReturn(Optional.of(config("12")));

        assertThat(facade.getExclusiveTalentFeeRatio()).isEqualByComparingTo("82");
        assertThat(facade.getExclusiveTalentMonthlySamples()).isEqualTo(12);
    }

    @Test
    void missingConfig_shouldFallbackToLegacyDefaults() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO))
                .thenReturn(Optional.empty());
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES))
                .thenReturn(Optional.empty());

        assertThat(facade.getSampleLimitDays()).isEqualTo(7);
        assertThat(facade.isSampleLimitEnabled()).isTrue();
        assertThat(facade.getSampleAutoCloseDays()).isEqualTo(30);
        assertThat(facade.getTalentClaimProtectDays()).isEqualTo(30);
        assertThat(facade.getExclusiveTalentFeeRatio()).isEqualByComparingTo("70");
        assertThat(facade.getExclusiveTalentMonthlySamples()).isEqualTo(10);
    }

    // ==================== DDD-CONFIG-001：通用只读入口 ====================

    @Nested
    @DisplayName("通用只读入口（DDD-CONFIG-001）")
    class rawAccessors {

        @Test
        @DisplayName("getConfig: 命中时返回原始字符串；缺失时返回 null")
        void getConfig_returnsRawOrNull() {
            when(systemConfigMapper.findByConfigKey("any.key"))
                    .thenReturn(Optional.of(config("  raw-value  ")))
                    .thenReturn(Optional.empty());

            assertThat(facade.getConfig("any.key")).isEqualTo("  raw-value  ");
            assertThat(facade.getConfig("missing.key")).isNull();
        }

        @Test
        @DisplayName("getString: 命中时 trim；空白/缺失时回退默认")
        void getString_trimOrDefault() {
            when(systemConfigMapper.findByConfigKey("k.trim"))
                    .thenReturn(Optional.of(config("  hello  ")));
            when(systemConfigMapper.findByConfigKey("k.blank"))
                    .thenReturn(Optional.of(config("   ")));
            when(systemConfigMapper.findByConfigKey("k.missing"))
                    .thenReturn(Optional.empty());

            assertThat(facade.getString("k.trim", "fallback")).isEqualTo("hello");
            assertThat(facade.getString("k.blank", "fallback")).isEqualTo("fallback");
            assertThat(facade.getString("k.missing", "fallback")).isEqualTo("fallback");
        }

        @Test
        @DisplayName("getInt: 命中解析为 int；非数字/缺失回退默认")
        void getInt_parseOrDefault() {
            when(systemConfigMapper.findByConfigKey("k.int.42"))
                    .thenReturn(Optional.of(config("42")));
            when(systemConfigMapper.findByConfigKey("k.int.bad"))
                    .thenReturn(Optional.of(config("not-a-number")));
            when(systemConfigMapper.findByConfigKey("k.int.missing"))
                    .thenReturn(Optional.empty());

            assertThat(facade.getInt("k.int.42", 0)).isEqualTo(42);
            assertThat(facade.getInt("k.int.bad", 99)).isEqualTo(99);
            assertThat(facade.getInt("k.int.missing", 7)).isEqualTo(7);
        }

        @Test
        @DisplayName("getDecimal: 命中解析为 BigDecimal；非数字/缺失回退默认")
        void getDecimal_parseOrDefault() {
            when(systemConfigMapper.findByConfigKey("k.dec.0.05"))
                    .thenReturn(Optional.of(config("0.05")));
            when(systemConfigMapper.findByConfigKey("k.dec.bad"))
                    .thenReturn(Optional.of(config("abc")));
            when(systemConfigMapper.findByConfigKey("k.dec.missing"))
                    .thenReturn(Optional.empty());

            assertThat(facade.getDecimal("k.dec.0.05", BigDecimal.ZERO))
                    .isEqualByComparingTo("0.05");
            assertThat(facade.getDecimal("k.dec.bad", new BigDecimal("9.99")))
                    .isEqualByComparingTo("9.99");
            assertThat(facade.getDecimal("k.dec.missing", new BigDecimal("3.14")))
                    .isEqualByComparingTo("3.14");
        }

        @Test
        @DisplayName("getBoolean: 识别 true/false/1/0（大小写不敏感）；其余回退默认")
        void getBoolean_parseOrDefault() {
            when(systemConfigMapper.findByConfigKey("k.bool.true"))
                    .thenReturn(Optional.of(config("true")));
            when(systemConfigMapper.findByConfigKey("k.bool.false"))
                    .thenReturn(Optional.of(config("FALSE")));
            when(systemConfigMapper.findByConfigKey("k.bool.one"))
                    .thenReturn(Optional.of(config("1")));
            when(systemConfigMapper.findByConfigKey("k.bool.zero"))
                    .thenReturn(Optional.of(config("0")));
            when(systemConfigMapper.findByConfigKey("k.bool.garbage"))
                    .thenReturn(Optional.of(config("yes-please")));
            when(systemConfigMapper.findByConfigKey("k.bool.missing"))
                    .thenReturn(Optional.empty());

            assertThat(facade.getBoolean("k.bool.true", false)).isTrue();
            assertThat(facade.getBoolean("k.bool.false", true)).isFalse();
            assertThat(facade.getBoolean("k.bool.one", false)).isTrue();
            assertThat(facade.getBoolean("k.bool.zero", true)).isFalse();
            assertThat(facade.getBoolean("k.bool.garbage", false)).isFalse();
            assertThat(facade.getBoolean("k.bool.garbage", true)).isTrue();
            assertThat(facade.getBoolean("k.bool.missing", false)).isFalse();
            assertThat(facade.getBoolean("k.bool.missing", true)).isTrue();
        }

        @Test
        @DisplayName("getJson: 命中反序列化为目标类型；非法 JSON/缺失回退默认")
        void getJson_parseOrDefault() {
            when(systemConfigMapper.findByConfigKey("k.json.ok"))
                    .thenReturn(Optional.of(config("{\"name\":\"alice\",\"age\":18}")));
            when(systemConfigMapper.findByConfigKey("k.json.bad"))
                    .thenReturn(Optional.of(config("{not-json")));
            when(systemConfigMapper.findByConfigKey("k.json.missing"))
                    .thenReturn(Optional.empty());

            TestPerson fallback = new TestPerson("fallback", 0);

            TestPerson ok = facade.getJson("k.json.ok", TestPerson.class, fallback);
            assertThat(ok).isEqualTo(new TestPerson("alice", 18));

            TestPerson bad = facade.getJson("k.json.bad", TestPerson.class, fallback);
            assertThat(bad).isSameAs(fallback);

            TestPerson missing = facade.getJson("k.json.missing", TestPerson.class, fallback);
            assertThat(missing).isSameAs(fallback);
        }

        @Test
        @DisplayName("named key: recruiter_commission_rate → commission.business_default_ratio")
        void namedKey_recruiterCommissionRate() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO))
                    .thenReturn(Optional.of(config("0.07")));

            assertThat(facade.getDecimal(
                    SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, new BigDecimal("0.05")))
                    .isEqualByComparingTo("0.07");
        }

        @Test
        @DisplayName("named key: channel_commission_rate → commission.channel_default_ratio")
        void namedKey_channelCommissionRate() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO))
                    .thenReturn(Optional.of(config("0.12")));

            assertThat(facade.getDecimal(
                    SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, new BigDecimal("0.10")))
                    .isEqualByComparingTo("0.12");
        }

        @Test
        @DisplayName("named key: sample_limit_days → sample.restrict_days")
        void namedKey_sampleLimitDays() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                    .thenReturn(Optional.of(config("14")));

            assertThat(facade.getInt(SystemConfigKeys.SAMPLE_RESTRICT_DAYS, 7)).isEqualTo(14);
        }

        @Test
        @DisplayName("named key: sample_limit_enabled → sample.restrict_enabled")
        void namedKey_sampleLimitEnabled() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED))
                    .thenReturn(Optional.of(config("1")));

            assertThat(facade.getBoolean(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, false)).isTrue();
        }

        @Test
        @DisplayName("named key: talent_claim_protect_days → talent.protection_days")
        void namedKey_talentClaimProtectDays() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS))
                    .thenReturn(Optional.of(config("45")));

            assertThat(facade.getInt(SystemConfigKeys.TALENT_PROTECTION_DAYS, 30)).isEqualTo(45);
        }
    }

    // ==================== DDD-CONFIG-001：聚合 DTO ====================

    @Nested
    @DisplayName("聚合 DTO（DDD-CONFIG-001）")
    class aggregateDtos {

        @Test
        @DisplayName("getCommissionRates: 命中合并为 DTO；缺失回退各自默认")
        void commissionRates() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO))
                    .thenReturn(Optional.of(config("0.08")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO))
                    .thenReturn(Optional.empty());

            CommissionRatesDTO rates = facade.getCommissionRates();

            assertThat(rates.businessRatio()).isEqualByComparingTo("0.08");
            assertThat(rates.channelRatio()).isEqualByComparingTo("0.10");
        }

        @Test
        @DisplayName("getSampleRules: 寄样规则聚合（含 defaultStandard）")
        void sampleRules() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_DAYS))
                    .thenReturn(Optional.of(config("9")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED))
                    .thenReturn(Optional.of(config("false")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS))
                    .thenReturn(Optional.of(config("21")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS))
                    .thenReturn(Optional.of(config("11")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD))
                    .thenReturn(Optional.of(config(
                            "{\"min_30day_sales\":5000,\"min_level\":\"L3\",\"foo\":\"bar\"}")));

            SampleRulesDTO rules = facade.getSampleRules();

            assertThat(rules.restrictDays()).isEqualTo(9);
            assertThat(rules.restrictEnabled()).isFalse();
            assertThat(rules.timeoutHomeworkDays()).isEqualTo(21);
            assertThat(rules.timeoutPendingShipDays()).isEqualTo(11);
            assertThat(rules.defaultStandard().min30DaySales()).isEqualTo(5000);
            assertThat(rules.defaultStandard().minLevel()).isEqualTo("L3");
            assertThat(rules.defaultStandard().raw()).containsEntry("foo", "bar");
        }

        @Test
        @DisplayName("getTalentRules: 达人规则聚合")
        void talentRules() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_PROTECTION_DAYS))
                    .thenReturn(Optional.of(config("45")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO))
                    .thenReturn(Optional.of(config("85")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES))
                    .thenReturn(Optional.of(config("15")));

            TalentRulesDTO rules = facade.getTalentRules();

            assertThat(rules.protectionDays()).isEqualTo(45);
            assertThat(rules.exclusiveRatioThreshold()).isEqualByComparingTo("85");
            assertThat(rules.exclusiveMonthlySamples()).isEqualTo(15);
        }

        @Test
        @DisplayName("getPromotionTemplate: 推广模板聚合")
        void promotionTemplate() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PROMOTION_COPY_BRIEF_TEMPLATE))
                    .thenReturn(Optional.of(config("【{productName}】模板")));
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE))
                    .thenReturn(Optional.of(config(
                            "{\"format\":\"channel_{channel_code}\",\"encode\":\"none\",\"raw\":{}}")));

            PromotionTemplateDTO template = facade.getPromotionTemplate();

            assertThat(template.copyBriefTemplate()).isEqualTo("【{productName}】模板");
            assertThat(template.pickExtraFormat()).isEqualTo("channel_{channel_code}");
            assertThat(template.pickExtraEncode()).isEqualTo("none");
        }

        @Test
        @DisplayName("getExclusiveRules: 商家规则聚合（缺失回退默认 70）")
        void exclusiveRules() {
            when(systemConfigMapper.findByConfigKey(SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO))
                    .thenReturn(Optional.empty());

            ExclusiveRulesDTO rules = facade.getExclusiveRules();

            assertThat(rules.merchantServiceFeeRatio()).isEqualByComparingTo("70");
        }
    }

    // ==================== 辅助 ====================

    private static SystemConfig config(String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigValue(value);
        config.setDeleted(0);
        return config;
    }

    /** 测试用 JSON 目标类型。 */
    public record TestPerson(String name, int age) {}
}
