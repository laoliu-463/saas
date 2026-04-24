package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TalentClaimMapper extends BaseMapper<TalentClaim> {

    @Select("SELECT * FROM talent_claim WHERE talent_id = #{talentId} AND deleted = 0 ORDER BY apply_time DESC LIMIT 1")
    TalentClaim findLastClaim(@Param("talentId") UUID talentId);

    @Select("SELECT * FROM talent_claim WHERE user_id = #{userId} AND status = 1 AND deleted = 0")
    List<TalentClaim> findActiveByUserId(@Param("userId") UUID userId);

    @Select("SELECT * FROM talent_claim WHERE dept_id = #{deptId} AND status = 1 AND deleted = 0")
    List<TalentClaim> findActiveByDeptId(@Param("deptId") UUID deptId);

    @Select("SELECT * FROM talent_claim WHERE talent_id = #{talentId} AND user_id = #{userId} AND status = 1 AND deleted = 0 LIMIT 1")
    TalentClaim findActiveByTalentAndUser(@Param("talentId") UUID talentId, @Param("userId") UUID userId);

    @Select("SELECT * FROM talent_claim WHERE talent_id = #{talentId} AND status = 1 AND deleted = 0 ORDER BY apply_time DESC")
    List<TalentClaim> findActiveByTalentId(@Param("talentId") UUID talentId);
}
