package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;

import java.util.List;
import java.util.Map;

/**
 * 分析模块订单事实只读查询门面。
 * <p>
 * DataApplicationService 仍保留既有查询条件组装，订单域负责执行订单 Mapper 查询。
 * </p>
 */
public interface DataOrderQueryFacade {

    IPage<ColonelsettlementOrder> findPageWithScope(
            Page<ColonelsettlementOrder> page,
            QueryWrapper<ColonelsettlementOrder> wrapper);

    List<Map<String, Object>> selectMaps(QueryWrapper<ColonelsettlementOrder> wrapper);
}
