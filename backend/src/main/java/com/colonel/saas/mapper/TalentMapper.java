package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.Talent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 达人信息数据访问层
 * <p>
 * 对应数据库表：talent
 * 所属业务领域：达人域 - 达人管理
 * 主要操作：达人的 CRUD 操作，带多条件筛选的达人搜索（关键词/地区/粉丝数范围）
 * </p>
 *
 * @see com.colonel.saas.entity.Talent
 */
@Mapper
public interface TalentMapper extends BaseMapper<Talent> {

    /**
     * 多条件搜索有效达人（分页）
     * <p>
     * 仅查询 status = 1 且未软删除的达人。支持以下可选筛选条件：
     * <ul>
     *   <li>keyword：按达人昵称或抖音 UID 模糊匹配（ILIKE，PostgreSQL 不区分大小写）</li>
     *   <li>region：按 IP 属地模糊匹配</li>
     *   <li>minFans / maxFans：粉丝数范围筛选</li>
     * </ul>
     * 所有条件均为可选，未传则不过滤。结果按粉丝数降序、更新时间降序排列。
     * </p>
     *
     * @param page    分页参数
     * @param keyword 搜索关键词（昵称或抖音 UID），为 null 时不过滤
     * @param region  IP 属地关键词，为 null 时不过滤
     * @param minFans 最低粉丝数，为 null 时不限下限
     * @param maxFans 最高粉丝数，为 null 时不限上限
     * @return 分页结果
     */
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
