package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.sample.application.port.SampleLogisticsQueryPort;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LegacySampleLogisticsQueryAdapter implements SampleLogisticsQueryPort {
    private final SampleQueryService sampleQueryService;

    public LegacySampleLogisticsQueryAdapter(@Lazy SampleQueryService sampleQueryService) {
        this.sampleQueryService = sampleQueryService;
    }

    @Override
    public SampleLogisticsVO getSampleLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryService.getSampleLogistics(id, userId, deptId, dataScope, roleCodes);
    }
}
