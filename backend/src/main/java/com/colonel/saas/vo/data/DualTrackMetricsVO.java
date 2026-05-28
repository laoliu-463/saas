package com.colonel.saas.vo.data;

import lombok.Data;

/**
 * 双轨指标聚合 VO。
 * <p>
 * 用于 Dashboard 等场景同时展示"已结算"与"预估"两套指标体系，
 * 便于前端在同一视图内对比结算口径与预估口径的差异。
 * </p>
 */
@Data
public class DualTrackMetricsVO {
    /** 已结算口径指标（基于已确认/已结算的订单数据） */
    private MetricsVO settle;
    /** 预估口径指标（包含待结算、预测性数据，用于前瞻分析） */
    private MetricsVO estimate;
}
