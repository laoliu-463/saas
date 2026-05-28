package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 达人资料补全任务实体。
 * <p>
 * 对应数据库表：{@code talent_enrich_task}，管理达人资料从外部数据源补全（enrichment）
 * 的异步任务。任务支持重试机制，记录输入参数、数据源类型、执行状态和错误信息。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Talent 达人主实体
 * @see TalentProfileSyncLog 达人资料同步日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_enrich_task")
public class TalentEnrichTask extends BaseEntity {

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联达人主表，标识需要补全资料的达人</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 输入值
     * <p>对应数据库列：{@code input_value}，提交任务时使用的查询值，
     * 如达人 UID、手机号等</p>
     */
    @TableField("input_value")
    private String inputValue;

    /**
     * 输入类型
     * <p>对应数据库列：{@code input_type}，标识 inputValue 的含义，
     * 如 "UID"、"PHONE"、"SEC_UID" 等</p>
     */
    @TableField("input_type")
    private String inputType;

    /**
     * 数据源类型
     * <p>对应数据库列：{@code source_type}，指定从哪个数据源进行补全，
     * 如 "CRAWLER"（爬虫）、"API"（官方接口）等</p>
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 任务状态
     * <p>对应数据库列：{@code task_status}，如 "PENDING"（待执行）、"RUNNING"（执行中）、
     * "SUCCESS"（成功）、"FAILED"（失败）等</p>
     */
    @TableField("task_status")
    private String taskStatus;

    /**
     * 已重试次数
     * <p>对应数据库列：{@code retry_count}，任务执行失败后的重试次数累计</p>
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 下次重试时间
     * <p>对应数据库列：{@code next_retry_time}，任务失败后计划重试的时间点，
     * 通常采用指数退避策略</p>
     */
    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    /**
     * 错误信息
     * <p>对应数据库列：{@code errorMsg}，任务执行失败时的错误描述</p>
     */
    @TableField("error_msg")
    private String errorMsg;
}

