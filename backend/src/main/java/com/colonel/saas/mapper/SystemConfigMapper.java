package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Select("SELECT * FROM system_config WHERE config_key = #{configKey} AND deleted = 0 LIMIT 1")
    Optional<SystemConfig> findByConfigKey(@Param("configKey") String configKey);

    @Update("""
            UPDATE system_config
            SET deleted = 1,
                update_by = #{userId},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDeleteById(@Param("id") UUID id, @Param("userId") UUID userId);
}
