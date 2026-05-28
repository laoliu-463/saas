package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品操作日志数据访问层
 * <p>
 * 对应数据库表：product_operation_log
 * 所属业务领域：商品域 - 商品操作审计
 * 主要操作：商品操作日志的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.ProductOperationLog
 */
@Mapper
public interface ProductOperationLogMapper extends BaseMapper<ProductOperationLog> {
}

