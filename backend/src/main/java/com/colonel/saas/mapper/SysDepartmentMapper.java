package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {

    @Select("SELECT * FROM sys_department WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort_order")
    List<SysDepartment> findByParentId(@Param("parentId") UUID parentId);

    @Select("SELECT * FROM sys_department WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Optional<SysDepartment> findById(@Param("id") UUID id);
}
