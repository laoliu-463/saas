package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.sample.application.port.SampleBoardQueryPort;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleBoardCard;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LegacySampleBoardQueryAdapter implements SampleBoardQueryPort {

    private final SampleQueryService sampleQueryService;

    public LegacySampleBoardQueryAdapter(@Lazy SampleQueryService sampleQueryService) {
        this.sampleQueryService = sampleQueryService;
    }

    @Override
    public Map<String, List<SampleBoardCard>> getSampleBoard(
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryService.getSampleBoard(userId, deptId, dataScope, roleCodes);
    }
}
