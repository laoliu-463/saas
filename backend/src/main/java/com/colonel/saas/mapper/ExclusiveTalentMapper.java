package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ExclusiveTalent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 独家达人数据访问层
 * <p>
 * 对应数据库表：exclusive_talent
 * 所属业务领域：达人域 - 独家达人管理
 * 主要操作：独家达人信息的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.ExclusiveTalent
 */
@Mapper
public interface ExclusiveTalentMapper extends BaseMapper<ExclusiveTalent> {
}
