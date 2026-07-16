package com.colonel.saas.domain.order.policy;

import com.colonel.saas.common.enums.DataScope;

import java.util.List;
import java.util.UUID;

/**
 * 当前用户在订单事实查询中的访问上下文。
 *
 * <p>角色事实由用户域规范化，订单域仅据此选择订单的渠道或招商归因字段，
 * 不从活动、商品或当前负责人重新推导订单归因。</p>
 */
public record OrderAccessContext(
        UUID userId,
        UUID deptId,
        DataScope dataScope,
        List<String> roleCodes) {

    public OrderAccessContext {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }
}
