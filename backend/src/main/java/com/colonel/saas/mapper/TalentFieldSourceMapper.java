package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentFieldSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 达人字段来源数据访问层
 * <p>
 * 对应数据库表：talent_field_source
 * 所属业务领域：达人域 - 达人数据管理
 * 主要操作：达人各字段数据来源追踪的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.TalentFieldSource
 */
@Mapper
public interface TalentFieldSourceMapper extends BaseMapper<TalentFieldSource> {
}

