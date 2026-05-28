package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentEnrichTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * 达人信息补全任务数据访问层
 * <p>
 * 对应数据库表：talent_enrich_task
 * 所属业务领域：达人域 - 达人资料管理
 * 主要操作：达人信息补全任务的 CRUD 操作，查询最新补全任务
 * </p>
 *
 * @see com.colonel.saas.entity.TalentEnrichTask
 */
@Mapper
public interface TalentEnrichTaskMapper extends BaseMapper<TalentEnrichTask> {

    /**
     * 查询指定达人的最新一条信息补全任务
     * <p>
     * 按创建时间倒序排列，取最近一条，用于判断达人资料补全的最新状态。
     * </p>
     *
     * @param talentId 达人主键 UUID
     * @return 最新的补全任务记录，不存在时为 null
     */
    @Select("SELECT * FROM talent_enrich_task WHERE talent_id = #{talentId} AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    TalentEnrichTask findLatestByTalentId(@Param("talentId") UUID talentId);
}
