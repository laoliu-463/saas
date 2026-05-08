package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.annotation.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ColonelsettlementOrderMapper extends BaseMapper<ColonelsettlementOrder> {

    ColonelsettlementOrder findByOrderId(@Param("orderId") String orderId);

    int insertIgnoreByOrderId(ColonelsettlementOrder order);

    int updateSyncedById(ColonelsettlementOrder order);

    @DataScope
    IPage<ColonelsettlementOrder> findPageWithScope(
            Page<ColonelsettlementOrder> page,
            @Param(Constants.WRAPPER) QueryWrapper<ColonelsettlementOrder> wrapper
    );
}
