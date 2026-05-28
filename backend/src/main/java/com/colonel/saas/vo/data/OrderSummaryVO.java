package com.colonel.saas.vo.data;

import lombok.Data;

import java.util.List;

/**
 * 订单汇总报表 VO。
 * <p>
 * 封装订单汇总报表的返回数据，包含总计行和按日期拆分的明细行列表。
 * 通常用于数据概览页面的订单汇总表格展示。
 * </p>
 *
 * @see OrderSummaryRowVO 订单汇总行明细
 */
@Data
public class OrderSummaryVO {
    /** 汇总总计行（所有日期的聚合数据） */
    private OrderSummaryRowVO total;
    /** 按日期拆分的明细行列表 */
    private List<OrderSummaryRowVO> records;
}
