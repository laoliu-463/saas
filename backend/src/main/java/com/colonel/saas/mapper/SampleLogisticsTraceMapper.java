package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SampleLogisticsTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 寄样物流轨迹数据访问层
 * <p>
 * 对应数据库表：sample_logistics_trace
 * 所属业务领域：寄样域 - 物流轨迹管理
 * 主要操作：寄样物流轨迹信息的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.SampleLogisticsTrace
 */
@Mapper
public interface SampleLogisticsTraceMapper extends BaseMapper<SampleLogisticsTrace> {
}
