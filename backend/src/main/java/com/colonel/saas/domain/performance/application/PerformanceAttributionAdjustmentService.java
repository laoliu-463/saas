package com.colonel.saas.domain.performance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.performance.policy.PerformanceAttributionPolicy.ManualOwner;
import com.colonel.saas.entity.PerformanceAttributionAdjustment;
import com.colonel.saas.mapper.PerformanceAttributionAdjustmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 读取已审批且在订单发生时间有效的人工归属调整。 */
@Service
public class PerformanceAttributionAdjustmentService {

    private final PerformanceAttributionAdjustmentMapper mapper;

    public PerformanceAttributionAdjustmentService(PerformanceAttributionAdjustmentMapper mapper) {
        this.mapper = mapper;
    }

    public ManualOwner findEffectiveOwner(String orderId, LocalDateTime businessTime) {
        if (!StringUtils.hasText(orderId)) {
            return null;
        }
        LocalDateTime effectiveTime = businessTime == null ? LocalDateTime.now() : businessTime;
        PerformanceAttributionAdjustment adjustment = mapper.selectOne(
                new LambdaQueryWrapper<PerformanceAttributionAdjustment>()
                        .eq(PerformanceAttributionAdjustment::getOrderId, orderId.trim())
                        .eq(PerformanceAttributionAdjustment::getStatus, "APPROVED")
                        .and(wrapper -> wrapper.isNull(PerformanceAttributionAdjustment::getEffectiveFrom)
                                .or()
                                .le(PerformanceAttributionAdjustment::getEffectiveFrom, effectiveTime))
                        .and(wrapper -> wrapper.isNull(PerformanceAttributionAdjustment::getEffectiveUntil)
                                .or()
                                .ge(PerformanceAttributionAdjustment::getEffectiveUntil, effectiveTime))
                        .orderByDesc(PerformanceAttributionAdjustment::getApprovedAt)
                        .orderByDesc(PerformanceAttributionAdjustment::getCreatedAt)
                        .last("LIMIT 1"));
        if (adjustment == null) {
            return null;
        }
        return new ManualOwner(
                adjustment.getChannelUserId(),
                adjustment.getRecruiterUserId(),
                adjustment.getChannelDeptId(),
                adjustment.getRecruiterDeptId());
    }
}
