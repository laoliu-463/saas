package com.colonel.saas.domain.talent.application;

import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentShippingAddressDTO;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.TalentService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 达人地址应用层。
 *
 * <p>读路径向调用方暴露地址事实；写路径继续委托 Legacy 服务保留当前认领人校验。</p>
 */
@Service
public class TalentAddressApplicationService {

    private final TalentService talentService;
    private final TalentDomainFacade talentDomainFacade;

    public TalentAddressApplicationService(TalentService talentService, TalentDomainFacade talentDomainFacade) {
        this.talentService = talentService;
        this.talentDomainFacade = talentDomainFacade;
    }

    public TalentShippingAddressDTO getShippingAddress(UUID talentId, UUID userId) {
        if (userId != null) {
            return talentDomainFacade.findClaimShippingAddress(userId, talentId);
        }
        Talent talent = talentService.getShippingAddress(talentId, null);
        if (talent == null) {
            return TalentShippingAddressDTO.empty();
        }
        return new TalentShippingAddressDTO(
                talent.getShippingRecipientName(),
                talent.getShippingRecipientPhone(),
                talent.getShippingRecipientAddress());
    }

    public Talent updateShippingAddress(
            UUID talentId,
            UUID userId,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        return talentService.updateShippingAddress(talentId, userId, recipientName, recipientPhone, recipientAddress);
    }
}
