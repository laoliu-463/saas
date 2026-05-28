package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.CommissionRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 佣金规则数据访问层
 * <p>
 * 对应数据库表：commission_rule
 * 所属业务领域：配置域 - 佣金规则管理
 * 主要操作：佣金规则配置信息的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.CommissionRule
 */
@Mapper
public interface CommissionRuleMapper extends BaseMapper<CommissionRule> {
}
