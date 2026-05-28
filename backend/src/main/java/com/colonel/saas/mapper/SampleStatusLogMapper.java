package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SampleStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 寄样状态变更日志数据访问层
 * <p>
 * 对应数据库表：sample_status_log
 * 所属业务领域：寄样域 - 寄样状态审计
 * 主要操作：寄样申请状态变更日志的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.SampleStatusLog
 */
@Mapper
public interface SampleStatusLogMapper extends BaseMapper<SampleStatusLog> {
}
