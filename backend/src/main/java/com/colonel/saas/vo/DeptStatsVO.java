package com.colonel.saas.vo;

import lombok.Data;

import java.util.UUID;

@Data
public class DeptStatsVO {
    private UUID deptId;
    private long memberCount;
    private long recruiterGroupCount;
    private long channelGroupCount;
}
