package com.colonel.saas.domain.sample.application.port;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.vo.sample.SampleBoardCard;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SampleBoardQueryPort {

    Map<String, List<SampleBoardCard>> getSampleBoard(
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);
}
