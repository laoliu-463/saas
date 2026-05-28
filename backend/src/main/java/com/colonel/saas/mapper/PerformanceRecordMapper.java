package com.colonel.saas.mapper;

import com.colonel.saas.entity.PerformanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 业绩记录数据访问层
 * <p>
 * 对应数据库表：performance_record
 * 所属业务领域：业绩域 - 业绩归属与提成管理
 * 主要操作：业绩记录的查询和 UPSERT，负责最终业绩归属、提成计算、冲正等金额处理
 * </p>
 *
 * @see com.colonel.saas.entity.PerformanceRecord
 */
@Mapper
public interface PerformanceRecordMapper {

    /**
     * 根据订单号查询业绩记录
     *
     * @param orderId 抖音订单号
     * @return 对应的业绩记录，不存在时返回 null
     */
    PerformanceRecord findByOrderId(@Param("orderId") String orderId);

    /**
     * 插入或更新业绩记录（UPSERT）
     * <p>
     * 基于业务主键做冲突更新，当业绩记录已存在时覆盖更新。
     * 用于业绩归属计算结果的落盘。
     * </p>
     *
     * @param record 业绩记录实体
     * @return 受影响行数
     */
    int upsert(PerformanceRecord record);
}
