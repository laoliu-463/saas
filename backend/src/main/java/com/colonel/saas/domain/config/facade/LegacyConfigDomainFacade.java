package com.colonel.saas.domain.config.facade;

import com.colonel.saas.service.BusinessRuleConfigService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * {@link ConfigDomainFacade} 遗留实现：委派 {@link BusinessRuleConfigService}，零行为变更（DDD-CONFIG-002）。
 */
@Service
public class LegacyConfigDomainFacade implements ConfigDomainFacade {

    private final BusinessRuleConfigService businessRuleConfigService;

    public LegacyConfigDomainFacade(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
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
}
