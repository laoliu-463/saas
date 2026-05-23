package com.colonel.saas.mapper;

import com.colonel.saas.entity.PerformanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PerformanceRecordMapper {

    PerformanceRecord findByOrderId(@Param("orderId") String orderId);

    int upsert(PerformanceRecord record);
}
