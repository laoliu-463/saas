package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品数据访问层
 * <p>
 * 对应数据库表：product
 * 所属业务领域：商品域 - 商品管理
 * 主要操作：商品基础信息的 CRUD 操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.Product
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
