package com.colonel.saas.domain.config.facade;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.dto.*;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@link ConfigDomainFacade} 遗留实现：委派 {@link BusinessRuleConfigService} 和 {@link SysConfigService}。
 * 保证向下兼容的只读能力（DDD-CONFIG-001 / DDD-CONFIG-002）。
 */
@Service
public class LegacyConfigDomainFacade implements ConfigDomainFacade {

    private final BusinessRuleConfigService businessRuleConfigService;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

    public LegacyConfigDomainFacade(
            BusinessRuleConfigService businessRuleConfigService,
            SysConfigService sysConfigService,
            ObjectMapper objectMapper) {
        this.businessRuleConfigService = businessRuleConfigService;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getSampleLimitDays() {
        return businessRuleConfigService.getSampleRestrictDays();
    }

    @Override
    public boolean isSampleLimitEnabled() {
        return businessRuleConfigService.isSampleRestrictEnabled();
    }

    @Override
    public int getSampleAutoCloseDays() {
        return businessRuleConfigService.getSampleTimeoutHomeworkDays();
    }

    @Override
    public int getTalentClaimProtectDays() {
        return businessRuleConfigService.getTalentProtectionDays();
    }

    @Override
    public BigDecimal getExclusiveTalentFeeRatio() {
        return businessRuleConfigService.getTalentExclusiveRatioThreshold();
    }

    @Override
    public int getExclusiveTalentMonthlySamples() {
        return businessRuleConfigService.getTalentExclusiveMonthlySamples();
    }

    @Override
    public String getConfig(String key) {
        return sysConfigService.getConfigValue(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        String val = getConfig(key);
        return StringUtils.hasText(val) ? val.trim() : defaultValue;
    }

    @Override
    public Integer getInt(String key, Integer defaultValue) {
        String val = getConfig(key);
        if (!StringUtils.hasText(val)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String val = getConfig(key);
        if (!StringUtils.hasText(val)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        String val = getConfig(key);
        if (!StringUtils.hasText(val)) {
            return defaultValue;
        }
        String normalized = val.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    @Override
    public <T> T getJson(String key, Class<T> type, T defaultValue) {
        String val = getConfig(key);
        if (!StringUtils.hasText(val)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(val.trim(), type);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public CommissionRatesDTO getCommissionRates() {
        BigDecimal businessDefault = getDecimal(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO, new BigDecimal("0.05"));
        BigDecimal channelDefault = getDecimal(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO, new BigDecimal("0.10"));
        return new CommissionRatesDTO(businessDefault, channelDefault);
    }

    @Override
    public SampleRulesDTO getSampleRules() {
        BusinessRuleConfigService.SampleDefaultStandardConfig standard = businessRuleConfigService.getSampleDefaultStandard();
        SampleDefaultStandardDTO standardDTO = new SampleDefaultStandardDTO(
                standard.min30DaySales(), standard.minLevel(), standard.raw());
        return new SampleRulesDTO(
                businessRuleConfigService.getSampleRestrictDays(),
                businessRuleConfigService.isSampleRestrictEnabled(),
                businessRuleConfigService.getSampleTimeoutHomeworkDays(),
                businessRuleConfigService.getSampleTimeoutPendingShipDays(),
                standardDTO
        );
    }

    @Override
    public TalentRulesDTO getTalentRules() {
        return new TalentRulesDTO(
                businessRuleConfigService.getTalentProtectionDays(),
                businessRuleConfigService.getTalentExclusiveRatioThreshold(),
                businessRuleConfigService.getTalentExclusiveMonthlySamples()
        );
    }

    @Override
    public PromotionTemplateDTO getPromotionTemplate() {
        BusinessRuleConfigService.PromotionPickExtraRuleConfig rule = businessRuleConfigService.getPromotionPickExtraRule();
        return new PromotionTemplateDTO(
                businessRuleConfigService.getPromotionCopyBriefTemplate(),
                rule.format(),
                rule.encode()
        );
    }

    @Override
    public ExclusiveRulesDTO getExclusiveRules() {
        BigDecimal merchantServiceFeeRatio = getDecimal(SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO, new BigDecimal("0.20"));
        return new ExclusiveRulesDTO(merchantServiceFeeRatio);
    }
}
