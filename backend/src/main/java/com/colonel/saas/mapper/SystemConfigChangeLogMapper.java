package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SystemConfigChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置变更日志数据访问层
 * <p>
 * 对应数据库表：system_config_change_log
 * 所属业务领域：配置域 - 配置变更审计
 * 主要操作：系统配置变更历史记录的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.SystemConfigChangeLog
 */
@Mapper
public interface SystemConfigChangeLogMapper extends BaseMapper<SystemConfigChangeLog> {
}
