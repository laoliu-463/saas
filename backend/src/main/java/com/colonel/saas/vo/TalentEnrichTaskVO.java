package com.colonel.saas.vo;

import com.colonel.saas.entity.TalentEnrichTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TalentEnrichTaskVO {

    private String id;
    private String talentId;
    private String inputValue;
    private String inputType;
    private String sourceType;
    private String taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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
