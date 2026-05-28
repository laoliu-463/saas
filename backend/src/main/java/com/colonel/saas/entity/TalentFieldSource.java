package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 达人字段数据来源追踪实体。
 * <p>
 * 对应数据库表：{@code talent_field_source}，记录达人资料中每个字段的数据来源信息。
 * 用于追溯达人资料中各字段（如昵称、粉丝数、联系方式等）的数据来自哪个数据源，
 * 并支持人工审核确认（verified），确保数据可溯源、可审计。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Talent 达人主实体
 * @see TalentEnrichTask 达人资料补全任务
 * @see TalentProfileSyncLog 达人资料同步日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_field_source")
public class TalentFieldSource extends BaseEntity {

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联达人主表，标识该字段来源记录所属的达人</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 字段名称
     * <p>对应数据库列：{@code field_name}，标识被追踪的达人资料字段，
     * 如 "nickname"、"fansCount"、"contactPhone" 等</p>
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 数据来源类型
     * <p>对应数据库列：{@code source_type}，标识该字段数据来自哪种数据源，
     * 如 "CRAWLER"（爬虫抓取）、"API"（官方接口）、"MANUAL"（人工录入）、
     * "ENRICH_TASK"（补全任务）等</p>
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 来源值
     * <p>对应数据库列：{@code source_value}，数据来源提供的原始值</p>
     */
    @TableField("source_value")
    private String sourceValue;

    /**
     * 来源引用类型
     * <p>对应数据库列：{@code source_ref_type}，标识来源数据的引用载体类型，
     * 如 "SYNC_LOG"（同步日志）、"ENRICH_TASK"（补全任务）等</p>
     */
    @TableField("source_ref_type")
    private String sourceRefType;

    /**
     * 来源引用 ID
     * <p>对应数据库列：{@code source_ref_id}，关联具体来源记录的主键，
     * 结合 sourceRefType 可定位到具体的同步日志或补全任务</p>
     */
    @TableField("source_ref_id")
    private String sourceRefId;

    /**
     * 审核人 ID
     * <p>对应数据库列：{@code verified_by}，对该字段来源进行人工审核确认的操作人标识</p>
     */
    @TableField("verified_by")
    private UUID verifiedBy;

    /**
     * 审核时间
     * <p>对应数据库列：{@code verified_time}，人工审核确认的时间点</p>
     */
    @TableField("verified_time")
    private LocalDateTime verifiedTime;
}

