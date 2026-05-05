package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductSnapshotMapper extends BaseMapper<ProductSnapshot> {

    int upsert(@Param("snapshot") ProductSnapshot snapshot);
}
