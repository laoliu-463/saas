package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentFollowRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 达人跟进记录数据访问层
 * <p>
 * 对应数据库表：talent_follow_record
 * 所属业务领域：达人域 - 达人跟进管理
 * 主要操作：达人跟进记录的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.TalentFollowRecord
 */
@Mapper
public interface TalentFollowRecordMapper extends BaseMapper<TalentFollowRecord> {
}
