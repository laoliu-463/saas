package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * {@link SampleDomainFacade} 遗留实现：委派 {@link SampleRequestMapper}，零行为变更。
 */
@Service
public class LegacySampleDomainFacade implements SampleDomainFacade {

    private final SampleRequestMapper sampleRequestMapper;

    public LegacySampleDomainFacade(SampleRequestMapper sampleRequestMapper) {
        this.sampleRequestMapper = sampleRequestMapper;
    }

    @Override
    public boolean existsById(UUID sampleRequestId) {
        if (sampleRequestId == null) {
            return false;
        }
        SampleRequest sample = sampleRequestMapper.selectById(sampleRequestId);
        return sample != null;
    }
}
