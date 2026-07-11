package com.colonel.saas.domain.colonel.facade;

import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.domain.colonel.query.ColonelPartnerListQuery;
import com.colonel.saas.entity.ColonelPartner;

import java.util.UUID;

/**
 * Colonel 域跨域只读门面接口（DDD-COLONEL-002 Wave 1.5 补全）。
 *
 * <p>提供 colonel 域对外的只读查询能力，供 order / sample / talent 等域调用，
 * 避免跨域直接访问 colonel 域 Application Service 内部实现。</p>
 *
 * <p><b>实现：</b>{@code ColonelPartnerReadFacadeImpl}（后续 Wave 2 补全）</p>
 *
 * <p><b>已有：</b>{@code com.colonel.saas.service.ColonelPartnerMasterDataService}
 * 提供同名能力，facade 模式封装为其薄包装。</p>
 */
public interface ColonelPartnerReadFacade {

    /**
     * 分页查询团长主数据。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ColonelPartner> findPage(ColonelPartnerListQuery query);

    /**
     * 按 ID 查询团长详情。
     *
     * @param id 团长主键
     * @return 团长实体（不存在时返回 null）
     */
    ColonelPartner findById(UUID id);
}
