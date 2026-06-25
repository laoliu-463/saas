package com.colonel.saas.domain.colonel.domain;

import com.colonel.saas.entity.ColonelPartner;

import java.util.UUID;

public interface ColonelPartnerRepository {

    ColonelPartner findById(UUID id);

    void save(ColonelPartner partner);
}
