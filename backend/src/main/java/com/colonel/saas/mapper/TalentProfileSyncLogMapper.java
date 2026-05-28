package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentProfileSyncLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 达人资料同步日志数据访问层
 * <p>
 * 对应数据库表：talent_profile_sync_log
 * 所属业务领域：达人域 - 达人数据同步
 * 主要操作：达人资料同步日志的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.TalentProfileSyncLog
 */
@Mapper
public interface TalentProfileSyncLogMapper extends BaseMapper<TalentProfileSyncLog> {
}
