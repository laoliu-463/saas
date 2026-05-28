package com.colonel.saas.vo;

import lombok.Data;

import java.util.UUID;

/**
 * 部门统计信息展示视图对象。
 * <p>
 * 用于组织架构管理页面中各部门的统计数据展示，包括部门成员数量和
 * 下属业务组数量。帮助管理者快速了解组织架构的人员分布情况。
 * </p>
 *
 * @see com.colonel.saas.mapper.SysDeptMapper
 */
@Data
public class DeptStatsVO {
    /** 部门唯一标识 */
    private UUID deptId;
    /** 部门成员总数 */
    private long memberCount;
    /** 下属招商组数量 */
    private long recruiterGroupCount;
    /** 下属渠道组数量 */
    private long channelGroupCount;
}
