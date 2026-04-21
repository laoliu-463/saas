package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.CrawlerTalentInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface CrawlerTalentInfoMapper extends BaseMapper<CrawlerTalentInfo> {

    @Insert("""
        INSERT INTO crawler_talent_info
            (talent_id, nickname, avatar_url, fans_count, credit_score,
             main_category, region, last_crawl_time, created_at, updated_at)
        VALUES
            (#{t.talentId}, #{t.nickname}, #{t.avatarUrl}, #{t.fansCount},
             #{t.creditScore}, #{t.mainCategory}, #{t.region},
             #{t.lastCrawlTime}, NOW(), NOW())
        ON CONFLICT (talent_id) DO UPDATE SET
            nickname = EXCLUDED.nickname,
            avatar_url = EXCLUDED.avatar_url,
            fans_count = EXCLUDED.fans_count,
            credit_score = EXCLUDED.credit_score,
            main_category = EXCLUDED.main_category,
            region = EXCLUDED.region,
            last_crawl_time = EXCLUDED.last_crawl_time,
            updated_at = NOW()
        """)
    void upsert(@Param("t") CrawlerTalentInfo info);

    @Select("""
        <script>
        SELECT talent_id, nickname, avatar_url, fans_count, credit_score, main_category, region
        FROM crawler_talent_info
        WHERE 1 = 1
        <if test="keyword != null and keyword != ''">
          AND (nickname ILIKE CONCAT('%', #{keyword}, '%') OR talent_id ILIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="region != null and region != ''">
          AND region = #{region}
        </if>
        <if test="minFans != null">
          AND fans_count <![CDATA[ >= ]]> #{minFans}
        </if>
        <if test="maxFans != null">
          AND fans_count <![CDATA[ <= ]]> #{maxFans}
        </if>
        <if test="minScore != null">
          AND credit_score <![CDATA[ >= ]]> #{minScore}
        </if>
        ORDER BY fans_count DESC, updated_at DESC
        </script>
        """)
    IPage<CrawlerTalentInfo> searchTalents(
            Page<CrawlerTalentInfo> page,
            @Param("keyword") String keyword,
            @Param("region") String region,
            @Param("minFans") Long minFans,
            @Param("maxFans") Long maxFans,
            @Param("minScore") BigDecimal minScore
    );
}
