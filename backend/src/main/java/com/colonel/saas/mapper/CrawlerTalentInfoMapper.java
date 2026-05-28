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

/**
 * 爬虫达人信息数据访问层
 * <p>
 * 对应数据库表：crawler_talent_info
 * 所属业务领域：达人域 - 达人数据采集
 * 主要操作：爬虫采集的达人信息的 UPSERT 和多条件搜索，支持关键词、地域、粉丝数、信用分筛选
 * </p>
 *
 * @see com.colonel.saas.entity.CrawlerTalentInfo
 */
@Mapper
public interface CrawlerTalentInfoMapper extends BaseMapper<CrawlerTalentInfo> {

    /**
     * 插入或更新爬虫达人信息（UPSERT）
     * <p>
     * 基于 talent_id 做冲突更新，当达人已存在时覆盖所有字段并刷新 updated_at。
     * 使用 PostgreSQL 的 ON CONFLICT DO UPDATE 语法。
     * </p>
     *
     * @param info 爬虫达人信息实体
     */
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

    /**
     * 多条件搜索达人信息（分页）
     * <p>
     * 支持以下可选筛选条件的动态组合：
     * - keyword：按昵称或达人 ID 模糊搜索（ILIKE）
     * - region：按地区精确匹配
     * - minFans / maxFans：粉丝数范围筛选
     * - minScore：最低信用分筛选
     * 结果按粉丝数降序、更新时间降序排列。
     * </p>
     *
     * @param page     分页参数
     * @param keyword  搜索关键词（昵称或达人 ID），为 null 时不过滤
     * @param region   地区筛选，为 null 时不过滤
     * @param minFans  最小粉丝数，为 null 时不过滤
     * @param maxFans  最大粉丝数，为 null 时不过滤
     * @param minScore 最低信用分，为 null 时不过滤
     * @return 分页结果，包含达人基本信息（不含内部 ID 和时间戳）
     */
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
