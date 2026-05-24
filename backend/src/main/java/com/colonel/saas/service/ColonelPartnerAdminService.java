package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ColonelPartnerAdminService {

    private final ColonelPartnerMapper colonelPartnerMapper;

    public ColonelPartnerAdminService(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ColonelPartner updateContactInfo(UUID id, ColonelPartnerContactUpdateRequest request, UUID operatorId) {
        ColonelPartner partner = colonelPartnerMapper.selectById(id);
        if (partner == null) {
            throw BusinessException.notFound("团长主数据不存在");
        }
        if (request.contactName() != null) {
            partner.setContactName(trimToNull(request.contactName()));
        }
        if (request.contactPhone() != null) {
            partner.setContactPhone(trimToNull(request.contactPhone()));
        }
        if (request.contactWechat() != null) {
            partner.setContactWechat(trimToNull(request.contactWechat()));
        }
        if (request.contactRemark() != null) {
            partner.setContactRemark(trimToNull(request.contactRemark()));
        }
        partner.setManualContactUpdatedAt(LocalDateTime.now());
        partner.setManualContactUpdatedBy(operatorId == null ? null : operatorId.toString());
        OptimisticLockSupport.requireUpdated(colonelPartnerMapper.updateById(partner));
        return partner;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
