package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.PickSourceMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 精选联盟选品来源映射数据访问层
 * <p>
 * 对应数据库表：pick_source_mapping
 * 所属业务领域：商品域 - 转链管理
 * 主要操作：商品转链后 pick_source 映射记录的 CRUD 基础操作
 * </p>
 *
 * @see com.colonel.saas.entity.PickSourceMapping
 */
@Mapper
public interface PickSourceMappingMapper extends BaseMapper<PickSourceMapping> {
}
