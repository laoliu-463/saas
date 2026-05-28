package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家数据访问层
 * <p>
 * 对应数据库表：merchant
 * 所属业务领域：商品域 - 商家管理
 * 主要操作：商家基础信息的 CRUD 操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.Merchant
 */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
