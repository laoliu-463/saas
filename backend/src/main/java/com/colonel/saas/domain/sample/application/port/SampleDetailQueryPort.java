package com.colonel.saas.domain.sample.application.port;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.vo.sample.SampleVO;

import java.util.UUID;

/**
 * 寄样详情查询端口。
 *
 * <p>只承载详情读模型，不包含状态流转、物流同步或订单事实处理。</p>
 */
public interface SampleDetailQueryPort {

    SampleVO getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);
}
