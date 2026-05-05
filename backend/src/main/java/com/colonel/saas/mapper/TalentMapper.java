package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.Talent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TalentMapper extends BaseMapper<Talent> {

    @Select("""
        <script>
        SELECT *
        FROM talent
        WHERE deleted = 0
          AND status = 1
        <if test="keyword != null and keyword != ''">
          AND (
            nickname ILIKE CONCAT('%', #{keyword}, '%')
            OR douyin_uid ILIKE CONCAT('%', #{keyword}, '%')
          )
        </if>
        <if test="region != null and region != ''">
          AND ip_location ILIKE CONCAT('%', #{region}, '%')
        </if>
        <if test="minFans != null">
          AND fans_count <![CDATA[ >= ]]> #{minFans}
        </if>
        <if test="maxFans != null">
          AND fans_count <![CDATA[ <= ]]> #{maxFans}
        </if>
        ORDER BY fans_count DESC, updated_at DESC
        </script>
        """)
    IPage<Talent> searchActiveTalents(
            Page<Talent> page,
            @Param("keyword") String keyword,
            @Param("region") String region,
            @Param("minFans") Long minFans,
            @Param("maxFans") Long maxFans
    );
}
