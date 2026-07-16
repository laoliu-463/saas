package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;

import java.util.Optional;
import java.util.Set;

/** 根据创建链接时的有效角色和明确选择确定归属维度。 */
public final class PromotionAttributionOwnerPolicy {

    private static final Set<String> CHANNEL_ROLES = Set.of(
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF);
    private static final Set<String> RECRUITER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF);

    public Optional<AttributionOwnerType> resolve(Set<String> roleCodes) {
        return resolve(roleCodes, null);
    }

    /**
     * 双角色用户必须显式选择本次链接是渠道还是招商归属，避免角色集合顺序影响业绩事实。
     */
    public Optional<AttributionOwnerType> resolve(
            Set<String> roleCodes, AttributionOwnerType requestedOwnerType) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Optional.empty();
        }
        boolean channel = roleCodes.stream().anyMatch(CHANNEL_ROLES::contains);
        boolean recruiter = roleCodes.stream().anyMatch(RECRUITER_ROLES::contains);
        if (channel && recruiter) {
            if (requestedOwnerType != null) {
                return Optional.of(requestedOwnerType);
            }
            throw new BusinessException(
                    ResultCode.CONFLICT.getCode(),
                    "用户同时拥有渠道和招商角色，创建推广链接时必须明确选择归属维度",
                    "ATTRIBUTION_OWNER_TYPE_SELECTION_REQUIRED",
                    null);
        }
        if (channel) {
            assertRequestedMatches(requestedOwnerType, AttributionOwnerType.CHANNEL);
            return Optional.of(AttributionOwnerType.CHANNEL);
        }
        if (recruiter) {
            assertRequestedMatches(requestedOwnerType, AttributionOwnerType.RECRUITER);
            return Optional.of(AttributionOwnerType.RECRUITER);
        }
        return Optional.empty();
    }

    private static void assertRequestedMatches(
            AttributionOwnerType requestedOwnerType, AttributionOwnerType resolvedOwnerType) {
        if (requestedOwnerType != null && requestedOwnerType != resolvedOwnerType) {
            throw new BusinessException(
                    ResultCode.FORBIDDEN.getCode(),
                    "当前角色不能以所选归属维度创建推广链接",
                    "ATTRIBUTION_OWNER_TYPE_NOT_GRANTED",
                    null);
        }
    }
}
