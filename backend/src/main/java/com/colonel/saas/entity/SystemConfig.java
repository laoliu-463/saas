package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends com.colonel.saas.common.base.BaseEntity {

    private String configKey;

    private String configValue;

    private String configType;

    private String configGroup;

    private String configName;

    @TableField("sort_order")
    private Integer sortOrder = 0;

    @TableField("status")
    private Integer status = 1;

    private String remark;

    private Integer configVersion = 1;

    private Boolean enabled = true;

    private Boolean visibleInRuleCenter = true;
}
