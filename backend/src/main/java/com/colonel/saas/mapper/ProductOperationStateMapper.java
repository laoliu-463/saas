package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductOperationState;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品运营状态数据访问层
 * <p>
 * 对应数据库表：product_operation_state
 * 所属业务领域：商品域 - 商品运营管理
 * 主要操作：商品运营状态（分配、展示状态、选品库等）的 CRUD 基础操作
 * </p>
 *
 * @see com.colonel.saas.entity.ProductOperationState
 */
@Mapper
public interface ProductOperationStateMapper extends BaseMapper<ProductOperationState> {
}

