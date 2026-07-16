package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentComplaint;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 达人投诉数据访问层。
 */
@Mapper
public interface TalentComplaintMapper extends BaseMapper<TalentComplaint> {

    /**
     * 达人风险批量摘要，不暴露投诉正文或举报人。
     */
    record TalentRiskSummary(
            UUID talentId,
            long complaintCount,
            LocalDateTime latestComplaintAt) {
    }

    @ConstructorArgs({
            @Arg(column = "talent_id", javaType = UUID.class),
            @Arg(column = "complaint_count", javaType = long.class),
            @Arg(column = "latest_complaint_at", javaType = LocalDateTime.class)
    })
    @Select("""
            <script>
            SELECT talent_id,
                   COUNT(*) AS complaint_count,
                   MAX(create_time) AS latest_complaint_at
            FROM talent_complaint
            WHERE deleted = 0
            <choose>
              <when test="talentIds != null and talentIds.size() > 0">
                AND talent_id IN
                <foreach collection="talentIds" item="talentId" open="(" separator="," close=")">
                  #{talentId}
                </foreach>
              </when>
              <otherwise>
                AND 1 = 0
              </otherwise>
            </choose>
            GROUP BY talent_id
            ORDER BY latest_complaint_at DESC, talent_id ASC
            </script>
            """)
    List<TalentRiskSummary> selectRiskSummariesByTalentIds(
            @Param("talentIds") Collection<UUID> talentIds);
}
