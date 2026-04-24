package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentEnrichTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface TalentEnrichTaskMapper extends BaseMapper<TalentEnrichTask> {

    @Select("SELECT * FROM talent_enrich_task WHERE talent_id = #{talentId} AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    TalentEnrichTask findLatestByTalentId(@Param("talentId") UUID talentId);
}
