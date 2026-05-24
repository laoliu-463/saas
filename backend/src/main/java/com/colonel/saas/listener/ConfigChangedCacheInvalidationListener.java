package com.colonel.saas.listener;

import com.colonel.saas.event.ConfigChangedApplicationEvent;
import com.colonel.saas.service.BusinessRuleConfigService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
public class ConfigChangedCacheInvalidationListener {

    private final BusinessRuleConfigService businessRuleConfigService;

    public ConfigChangedCacheInvalidationListener(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterConfigChanged(ConfigChangedApplicationEvent event) {
        if (event == null || event.changedKeys() == null) {
            return;
        }
        for (String configKey : event.changedKeys()) {
            if (StringUtils.hasText(configKey)) {
                businessRuleConfigService.invalidate(configKey);
            }
        }
    }
}
