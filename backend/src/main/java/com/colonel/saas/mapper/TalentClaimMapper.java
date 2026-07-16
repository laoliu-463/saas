package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 达人归属认领数据访问层
 * <p>
 * 对应数据库表：talent_claim
 * 所属业务领域：达人域 - 达人归属管理
 * 主要操作：达人认领关系的查询，支持按用户/部门/达人维度查询有效认领记录，
 * 查询最新认领记录
 * </p>
 *
 * @see com.colonel.saas.entity.TalentClaim
 */
@Mapper
public interface TalentClaimMapper extends BaseMapper<TalentClaim> {

    /**
     * 查询指定达人的最新一条认领记录（不论状态）
     * <p>
     * 按申请时间倒序排列，取最近一条，用于判断达人的最新认领情况。
     * </p>
     *
     * @param talentId 达人主键 UUID
     * @return 最新的认领记录，不存在时为 null
     */
    @Select("SELECT * FROM talent_claim WHERE talent_id = #{talentId} AND deleted = 0 ORDER BY apply_time DESC LIMIT 1")
    TalentClaim findLastClaim(@Param("talentId") UUID talentId);

    /**
     * 查询指定用户所有有效的认领记录
     * <p>
     * 有效认领指 status = 1 且未软删除，用于获取某用户当前认领的所有达人。
     * </p>
     *
     * @param userId 用户主键 UUID
     * @return 该用户的有效认领记录列表
     */
    @Select("SELECT * FROM talent_claim WHERE user_id = #{userId} AND status = 1 AND deleted = 0")
    List<TalentClaim> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * 查询指定部门所有有效的认领记录
     * <p>
     * 有效认领指 status = 1 且未软删除，用于获取某部门当前认领的所有达人。
     * </p>
     *
     * @param deptId 部门主键 UUID
     * @return 该部门的有效认领记录列表
     */
    @Select("SELECT * FROM talent_claim WHERE dept_id = #{deptId} AND status = 1 AND deleted = 0")
    List<TalentClaim> findActiveByDeptId(@Param("deptId") UUID deptId);

    /**
     * 查询指定达人和指定用户之间的有效认领关系
     *
     * @param talentId 达人主键 UUID
     * @param userId   用户主键 UUID
     * @return 有效认领记录，不存在时为 null
     */
    @Select("SELECT * FROM talent_claim WHERE talent_id = #{talentId} AND user_id = #{userId} AND status = 1 AND deleted = 0 LIMIT 1")
    TalentClaim findActiveByTalentAndUser(@Param("talentId") UUID talentId, @Param("userId") UUID userId);

    /**
     * 查询指定达人的所有有效认领记录
     * <p>
     * 按申请时间倒序排列，返回该达人当前所有生效的认领关系。
     * </p>
     *
     * @param talentId 达人主键 UUID
     * @return 该达人的有效认领记录列表
     */
    @Select("SELECT * FROM talent_claim WHERE talent_id = #{talentId} AND status = 1 AND deleted = 0 ORDER BY apply_time DESC")
    List<TalentClaim> findActiveByTalentId(@Param("talentId") UUID talentId);

    @Select("""
            <script>
            SELECT DISTINCT talent_id
            FROM talent_claim
            WHERE user_id = #{userId}
              AND status = 1
              AND deleted = 0
              AND talent_id IN
              <foreach collection="talentIds" item="talentId" open="(" separator="," close=")">
                #{talentId}
              </foreach>
            </script>
            """)
    List<UUID> selectActiveTalentIdsByUserAndTalentIds(
            @Param("userId") UUID userId,
            @Param("talentIds") Collection<UUID> talentIds);

    @Select("""
            <script>
            SELECT DISTINCT talent_id
            FROM talent_claim
            WHERE dept_id = #{deptId}
              AND status = 1
              AND deleted = 0
              AND talent_id IN
              <foreach collection="talentIds" item="talentId" open="(" separator="," close=")">
                #{talentId}
              </foreach>
            </script>
            """)
    List<UUID> selectActiveTalentIdsByDeptAndTalentIds(
            @Param("deptId") UUID deptId,
            @Param("talentIds") Collection<UUID> talentIds);
}
