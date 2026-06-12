package com.colonel.saas.domain.sample.facade;

import java.util.UUID;

/**
 * 寄样域只读门面（DDD-SAMPLE-007 Batch3）。
 *
 * <p>其他域与 Controller 读路径应通过本接口校验寄样单存在性，
 * 禁止新增跨域 {@code SampleRequestMapper} 注入。</p>
 */
public interface SampleDomainFacade {

    /** 寄样申请是否存在（按主键）。 */
    boolean existsById(UUID sampleRequestId);
}
