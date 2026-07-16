package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentComplaint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 达人投诉数据访问层。
 */
@Mapper
public interface TalentComplaintMapper extends BaseMapper<TalentComplaint> {

    @Select("""
            <script>
            SELECT *
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
            ORDER BY create_time DESC
            </script>
            """)
    List<TalentComplaint> selectByTalentIds(@Param("talentIds") Collection<UUID> talentIds);
}
