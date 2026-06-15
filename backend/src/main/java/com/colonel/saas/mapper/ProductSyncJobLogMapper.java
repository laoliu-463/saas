package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductSyncJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品同步任务日志 Mapper。
 */
@Mapper
public interface ProductSyncJobLogMapper extends BaseMapper<ProductSyncJobLog> {
}
