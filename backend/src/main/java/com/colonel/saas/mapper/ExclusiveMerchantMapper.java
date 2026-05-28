package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ExclusiveMerchant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 独家商家数据访问层
 * <p>
 * 对应数据库表：exclusive_merchant
 * 所属业务领域：商品域 - 独家商家管理
 * 主要操作：独家商家信息的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.ExclusiveMerchant
 */
@Mapper
public interface ExclusiveMerchantMapper extends BaseMapper<ExclusiveMerchant> {
}
