package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private UUID createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updateBy;

    @TableLogic
    private Integer deleted = 0;
}
