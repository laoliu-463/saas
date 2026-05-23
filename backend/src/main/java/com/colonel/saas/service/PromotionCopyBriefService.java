package com.colonel.saas.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class PromotionCopyBriefService {

    private final BusinessRuleConfigService businessRuleConfigService;

    public PromotionCopyBriefService(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    public String render(String productName, String commissionRate, String shortLink, String pickSource) {
        String template = businessRuleConfigService.getPromotionCopyBriefTemplate();
        return template
                .replace("{productName}", safe(productName))
                .replace("{commissionRate}", safe(commissionRate))
                .replace("{shortLink}", safe(shortLink))
                .replace("{pickSource}", safe(pickSource));
    }

    public String render(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return businessRuleConfigService.getPromotionCopyBriefTemplate();
        }
        return render(
                placeholders.get("productName"),
                placeholders.get("commissionRate"),
                placeholders.get("shortLink"),
                placeholders.get("pickSource"));
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
