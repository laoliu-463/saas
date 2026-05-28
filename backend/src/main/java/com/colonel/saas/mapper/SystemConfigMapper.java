package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;
import java.util.UUID;

/**
 * 系统配置数据访问层
 * <p>
 * 对应数据库表：system_config
 * 所属业务领域：配置域 - 系统配置管理
 * 主要操作：配置项的 CRUD 操作，按配置键查询，软删除
 * </p>
 *
 * @see com.colonel.saas.entity.SystemConfig
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    /**
     * 根据配置键查询配置项
     *
     * @param configKey 配置键名称
     * @return 包含配置信息的 Optional，不存在时为空
     */
    @Select("SELECT * FROM system_config WHERE config_key = #{configKey} AND deleted = 0 LIMIT 1")
    Optional<SystemConfig> findByConfigKey(@Param("configKey") String configKey);

    /**
     * 软删除配置项（逻辑删除）
     * <p>
     * 同时记录操作人 userId 到 update_by 字段，便于审计追溯。
     * </p>
     *
     * @param id     配置项主键 UUID
     * @param userId 执行删除操作的用户 UUID
     * @return 受影响行数
     */
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
