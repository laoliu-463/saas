package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crawler_talent_info")
public class CrawlerTalentInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("talent_id")
    private String talentId;

    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("fans_count")
    private Long fansCount;

    @TableField("credit_score")
    private BigDecimal creditScore;

    @TableField("main_category")
    private String mainCategory;

    private String region;

    @TableField("last_crawl_time")
    private LocalDateTime lastCrawlTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
