package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;

import java.util.Optional;
import java.util.Set;

/** 根据创建链接时的有效角色确定唯一归属维度。 */
public final class PromotionAttributionOwnerPolicy {

    private static final Set<String> CHANNEL_ROLES = Set.of(
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF);
    private static final Set<String> RECRUITER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF);

    public Optional<AttributionOwnerType> resolve(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Optional.empty();
        }
        boolean channel = roleCodes.stream().anyMatch(CHANNEL_ROLES::contains);
        boolean recruiter = roleCodes.stream().anyMatch(RECRUITER_ROLES::contains);
        if (channel && recruiter) {
            throw new BusinessException(
                    ResultCode.CONFLICT.getCode(),
                    "用户角色同时属于渠道和招商，无法固化推广链接归属类型",
                    "ATTRIBUTION_OWNER_TYPE_AMBIGUOUS",
                    null);
        }
        if (channel) {
            return Optional.of(AttributionOwnerType.CHANNEL);
        }
        if (recruiter) {
            return Optional.of(AttributionOwnerType.RECRUITER);
        }
        return Optional.empty();
    }
}
