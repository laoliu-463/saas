package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ColonelOrderSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ColonelOrderSettlementMapper extends BaseMapper<ColonelOrderSettlement> {

    ColonelOrderSettlement findByUpstreamKey(@Param("upstreamKey") String upstreamKey);

    int insertIgnoreByUpstreamKey(ColonelOrderSettlement settlement);
}
