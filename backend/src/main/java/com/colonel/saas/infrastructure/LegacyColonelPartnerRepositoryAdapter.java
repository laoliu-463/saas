package com.colonel.saas.infrastructure;

import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.colonel.domain.ColonelPartnerRepository;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LegacyColonelPartnerRepositoryAdapter implements ColonelPartnerRepository {

    private final ColonelPartnerMapper colonelPartnerMapper;

    public LegacyColonelPartnerRepositoryAdapter(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    @Override
    public ColonelPartner findById(UUID id) {
        return colonelPartnerMapper.selectById(id);
    }

    @Override
    public void save(ColonelPartner partner) {
        OptimisticLockSupport.requireUpdated(colonelPartnerMapper.updateById(partner));
    }
}
