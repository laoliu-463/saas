package com.colonel.saas.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class SysDeptVO {
    private UUID id;
    private UUID parentId;
    private String deptCode;
    private String deptName;
    private String leader;
    private String phone;
    private String email;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private List<SysDeptVO> children = new ArrayList<>();
}
