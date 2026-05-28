package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 爬虫抓取的达人信息快照表实体。
 * <p>
 * 对应数据库表：{@code crawler_talent_info}，用于存储通过爬虫从外部平台
 * 抓取的达人基础信息，作为达人资料补全（enrichment）和数据同步的数据来源。
 * 该实体不继承 BaseEntity，采用自增主键，独立管理时间戳字段。
 * </p>
 *
 * @see Talent 达人主实体，本表为其提供爬虫维度的补充信息
 */
@Data
@TableName("crawler_talent_info")
public class CrawlerTalentInfo {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 达人唯一标识（外部平台达人 ID）
     * <p>对应数据库列：{@code talent_id}，用于与达人主表建立关联</p>
     */
    @TableField("talent_id")
    private String talentId;

    /**
     * 达人昵称
     * <p>抓取时的达人昵称快照，可能与达人主表中的昵称不一致</p>
     */
    private String nickname;

    /**
     * 达人头像 URL
     * <p>对应数据库列：{@code avatar_url}，外部平台头像地址</p>
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 粉丝数
     * <p>对应数据库列：{@code fans_count}，抓取时的粉丝数量快照</p>
     */
    @TableField("fans_count")
    private Long fansCount;

    /**
     * 信用评分
     * <p>对应数据库列：{@code credit_score}，外部平台提供的达人信用分</p>
     */
    @TableField("credit_score")
    private BigDecimal creditScore;

    /**
     * 主营类目
     * <p>对应数据库列：{@code main_category}，达人主要经营的商品类目</p>
     */
    @TableField("main_category")
    private String mainCategory;

    /**
     * 所在地区
     * <p>达人所在地理区域</p>
     */
    private String region;

    /**
     * 最近一次爬取时间
     * <p>对应数据库列：{@code last_crawl_time}，记录该条数据最近一次被爬虫更新的时间</p>
     */
    @TableField("last_crawl_time")
    private LocalDateTime lastCrawlTime;

    /**
     * 记录创建时间
     * <p>对应数据库列：{@code created_at}，数据首次入库时间</p>
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录更新时间
     * <p>对应数据库列：{@code updated_at}，数据最近一次更新时间</p>
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
