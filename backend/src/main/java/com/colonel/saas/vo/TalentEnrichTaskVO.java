package com.colonel.saas.vo;

import com.colonel.saas.entity.TalentEnrichTask;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 达人信息充实任务展示视图对象。
 * <p>
 * 用于达人信息充实任务管理页面的展示。达人信息充实（Enrich）是指通过爬虫或 API
 * 补全达人的详细信息（如粉丝数、类目、带货数据等），支持重试机制和错误追踪。
 * </p>
 * <p>
 * 任务生命周期：
 * <ol>
 *   <li>创建任务（指定输入值和类型）</li>
 *   <li>执行充实（由 Crawler 调度）</li>
 *   <li>成功完成 / 失败重试 / 最终失败</li>
 * </ol>
 * </p>
 *
 * @see com.colonel.saas.entity.TalentEnrichTask
 * @see com.colonel.saas.mapper.TalentEnrichTaskMapper
 */
@Data
public class TalentEnrichTaskVO {

    /** 任务唯一标识 */
    private String id;
    /** 关联的达人 ID */
    private String talentId;
    /** 输入值（如达人抖音号、UID 等） */
    private String inputValue;
    /** 输入类型，标识输入值的含义（如 douyin_uid、douyin_no） */
    private String inputType;
    /** 数据来源类型（如 crawler、api、manual） */
    private String sourceType;
    /** 任务状态（如 pending、processing、completed、failed） */
    private String taskStatus;
    /** 已重试次数 */
    private Integer retryCount;
    /** 下次重试时间，用于调度器判断是否执行 */
    private LocalDateTime nextRetryTime;
    /** 错误信息，记录最近一次失败的原因 */
    private String errorMsg;
    /** 任务创建时间 */
    private LocalDateTime createTime;
    /** 任务最后更新时间 */
    private LocalDateTime updateTime;

    /**
     * 从 {@code TalentEnrichTask} 实体转换为 VO。
     * <p>
     * 该方法处理 null 值的安全转换，UUID 类型字段转为 String。
     * </p>
     *
     * @param task 达人充实任务实体，可以为 null
     * @return 对应的 VO 对象，输入为 null 时返回 null
     */
    public static TalentEnrichTaskVO from(TalentEnrichTask task) {
        if (task == null) {
            return null;
        }
        TalentEnrichTaskVO vo = new TalentEnrichTaskVO();
        if (task.getId() != null) {
            vo.setId(task.getId().toString());
        }
        if (task.getTalentId() != null) {
            vo.setTalentId(task.getTalentId().toString());
        }
        vo.setInputValue(task.getInputValue());
        vo.setInputType(task.getInputType());
        vo.setSourceType(task.getSourceType());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setNextRetryTime(task.getNextRetryTime());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }
}
