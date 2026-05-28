package com.colonel.saas.dto.performance;

import lombok.Data;

import java.util.List;

/**
 * 业绩分页查询响应 DTO。
 * <p>
 * 返回分页查询的业绩列表数据，包含分页元信息和当前页的业绩记录列表。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformancePageResponse {
    /** 当前页码 */
    private long page;
    /** 每页条数 */
    private long pageSize;
    /** 总记录数 */
    private long total;
    /** 当前页的业绩记录列表 */
    private List<PerformanceListItemDTO> items;
}
