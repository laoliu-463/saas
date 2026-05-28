package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ColonelPartner;
import org.apache.ibatis.annotations.Mapper;

/**
 * 团长合伙人数据访问层
 * <p>
 * 对应数据库表：colonel_partner
 * 所属业务领域：业绩域 - 团长合伙人管理
 * 主要操作：团长合伙人信息的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.ColonelPartner
 */
@Mapper
public interface ColonelPartnerMapper extends BaseMapper<ColonelPartner> {
}
