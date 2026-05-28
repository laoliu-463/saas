package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志数据访问层
 * <p>
 * 对应数据库表：operation_log
 * 所属业务领域：系统域 - 操作审计
 * 主要操作：用户操作日志的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.OperationLog
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
