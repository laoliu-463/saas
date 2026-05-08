package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    /**
     * 技术上沿用 dept 命名，业务上承载“招商组 / 渠道组 / 运营组”等组织单元。
     */

    @TableField("parent_id")
    private UUID parentId;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    private String leader;

    private String phone;

    private String email;

    @TableField("sort_order")
    private Integer sortOrder = 0;

    private Integer status = 1;

    private String remark;
}
