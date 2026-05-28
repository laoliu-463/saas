package com.colonel.saas.vo.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 趋势图单点数据 VO。
 * <p>
 * 表示趋势图表中某一天的数据快照，通常用于 7 日趋势等时间序列展示。
 * 支持 {@code @AllArgsConstructor} 以便在 Service 层快速构造。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {
    /** 日期，格式通常为 {@code yyyy-MM-dd} */
    private String date;
    /** 当日订单数量 */
    private Long orderCount;
    /** 当日 GMV（商品交易总额），单位：元 */
    private BigDecimal gmv;
}
