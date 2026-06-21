package com.colonel.saas.domain.colonel.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerAdminService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ColonelPartnerContactUpdateRouter {

    private final DddRefactorProperties dddRefactorProperties;
    private final ColonelPartnerAdminService legacyService;
    private final ColonelPartnerContactUpdateApplicationService dddService;

    public ColonelPartnerContactUpdateRouter(
            DddRefactorProperties dddRefactorProperties,
            ColonelPartnerAdminService legacyService,
            ColonelPartnerContactUpdateApplicationService dddService) {
        this.dddRefactorProperties = dddRefactorProperties;
        this.legacyService = legacyService;
        this.dddService = dddService;
    }

    public ColonelPartner updateContactInfo(
            UUID id,
            ColonelPartnerContactUpdateRequest request,
            UUID operatorId) {
        if (useDddPath()) {
            return dddService.updateContactInfo(id, request, operatorId);
        }
        return legacyService.updateContactInfo(id, request, operatorId);
    }

    boolean useDddPath() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getColonelPartnerContact().isEnabled();
    }
}
