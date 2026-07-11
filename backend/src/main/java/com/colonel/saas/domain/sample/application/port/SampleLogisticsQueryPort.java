package com.colonel.saas.domain.sample.application.port;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.vo.sample.SampleLogisticsVO;

import java.util.UUID;

public interface SampleLogisticsQueryPort {
    SampleLogisticsVO getSampleLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);
}
