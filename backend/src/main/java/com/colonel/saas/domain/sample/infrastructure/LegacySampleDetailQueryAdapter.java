package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.sample.application.port.SampleDetailQueryPort;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 寄样详情查询的 Legacy 适配器。
 *
 * <p>先稳定应用层端口，再把旧 3,500+ 行服务中的详情物化逻辑逐步下沉到新实现。</p>
 */
@Service
public class LegacySampleDetailQueryAdapter implements SampleDetailQueryPort {

    private final SampleQueryService sampleQueryService;

    public LegacySampleDetailQueryAdapter(@Lazy SampleQueryService sampleQueryService) {
        this.sampleQueryService = sampleQueryService;
    }

    @Override
    public SampleVO getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryService.getSampleById(id, userId, deptId, dataScope, roleCodes);
    }
}
