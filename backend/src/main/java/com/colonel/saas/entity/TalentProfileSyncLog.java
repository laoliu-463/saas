package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import com.colonel.saas.common.typehandler.JsonbListTypeHandler;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 达人资料同步日志实体。
 * <p>
 * 对应数据库表：{@code talent_profile_sync_log}，记录每次从外部数据源同步达人资料的执行日志。
 * 每次同步生成一条日志记录，包含同步状态、成功获取的字段列表、不支持的字段列表、
 * 原始响应载荷以及错误信息，用于同步过程追溯和问题排查。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see Talent 达人主实体
 * @see TalentEnrichTask 达人资料补全任务
 * @see TalentFieldSource 达人字段来源追踪
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "talent_profile_sync_log", autoResultMap = true)
public class TalentProfileSyncLog extends BaseEntity {

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联达人主表，标识本次同步的目标达人</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 输入查询值
     * <p>对应数据库列：{@code input_value}，提交同步任务时使用的查询值，
     * 如达人 UID、手机号等</p>
     */
    @TableField("input_value")
    private String inputValue;

    /**
     * 数据提供方编码
     * <p>对应数据库列：{@code provider_code}，标识同步所使用的数据源供应商，
     * 如 "CRAWLER"（爬虫）、"DOUYIN_API"（抖音官方接口）等</p>
     */
    @TableField("provider_code")
    private String providerCode;

    /**
     * 同步状态
     * <p>对应数据库列：{@code sync_status}，本次同步的执行结果，
     * 如 "SUCCESS"（成功）、"PARTIAL"（部分成功）、"FAILED"（失败）等</p>
     */
    @TableField("sync_status")
    private String syncStatus;

    /**
     * 成功获取的字段列表
     * <p>JSON 数组格式，对应数据库列：{@code fetched_fields}，
     * 记录本次同步成功拉取并更新的达人资料字段名称列表</p>
     */
    @TableField(value = "fetched_fields", typeHandler = JsonbListTypeHandler.class)
    private List<String> fetchedFields;

    /**
     * 不支持的字段列表
     * <p>JSON 数组格式，对应数据库列：{@code unsupported_fields}，
     * 记录数据源不支持或返回异常的字段名称列表</p>
     */
    @TableField(value = "unsupported_fields", typeHandler = JsonbListTypeHandler.class)
    private List<String> unsupportedFields;

    /**
     * 原始响应载荷
     * <p>JSON 对象格式，对应数据库列：{@code raw_payload}，
     * 记录数据源返回的完整原始数据，用于问题排查和数据回放</p>
     */
    @TableField(value = "raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    /**
     * 错误码
     * <p>对应数据库列：{@code error_code}，同步失败时的错误编码，成功时为 null</p>
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 错误信息
     * <p>对应数据库列：{@code error_message}，同步失败时的错误描述，成功时为 null</p>
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 同步开始时间
     * <p>对应数据库列：{@code started_at}，本次同步任务的开始执行时间</p>
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 同步完成时间
     * <p>对应数据库列：{@code finished_at}，本次同步任务的结束时间，
     * 执行中时为 null</p>
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
