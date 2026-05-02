package com.colonel.saas.douyin.api;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActivityApi {

    private static final Set<Integer> VALID_ACTIVITY_TYPES = Set.of(1, 2);
    private static final Set<Integer> VALID_MIN_PROMOTION_DAYS = Set.of(30, 90, 180, 360);
    private static final Set<Integer> VALID_COS_LIMIT_TYPES = Set.of(1, 2, 3);
    private static final BigDecimal MAX_COMMISSION_RATE = new BigDecimal("50");
    private static final BigDecimal MAX_SERVICE_RATE = new BigDecimal("40");

    private final DouyinApiClient douyinApiClient;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public ActivityApi(
            DouyinApiClient douyinApiClient,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.douyinApiClient = douyinApiClient;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    public Map<String, Object> list(String appId) {
        return listActivities(appId, 0, 0L, 1L, 1L, 20L, null);
    }

    public Map<String, Object> listActivities(
            String appId,
            Integer status,
            Long searchType,
            Long sortType,
            Long page,
            Long pageSize,
            String activityInfo) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityListResponse(appId, status, searchType, sortType, page, pageSize, activityInfo);
        }
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        params.put("status", normalizeStatus(status));
        params.put("search_type", normalizeSearchType(searchType));
        params.put("sort_type", normalizeSortType(sortType));
        params.put("page", normalizePage(page));
        params.put("page_size", normalizePageSize(pageSize));
        putIfNotBlank(params, "activity_info", activityInfo);
        return douyinApiClient.post("alliance.instituteColonelActivityList", params);
    }

    public Map<String, Object> detail(String appId, String activityId) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityDetailResponse(appId, activityId);
        }
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        params.put("activity_id", parseActivityId(activityId));
        return douyinApiClient.post("buyin.colonelActivityDetail", params);
    }

    public Map<String, Object> createOrUpdate(ActivityCreateOrUpdateCommand command) {
        validateCreateOrUpdate(command);
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", command.appId());
        putIfNotNull(params, "activity_id", command.activityId());
        putIfNotNull(params, "application_limited", command.applicationLimited());
        putIfNotNull(params, "is_new_shop", command.isNewShop());
        putIfNotBlank(params, "shop_type", command.shopType());
        putIfNotBlank(params, "activity_name", command.activityName());
        putIfNotBlank(params, "activity_desc", command.activityDesc());
        putIfNotBlank(params, "apply_start_time", command.applyStartTime());
        putIfNotBlank(params, "apply_end_time", command.applyEndTime());
        putIfNotBlank(params, "commission_rate", command.commissionRate());
        putIfNotBlank(params, "service_rate", command.serviceRate());
        putIfNotBlank(params, "wechat_id", command.wechatId());
        putIfNotBlank(params, "phone_num", command.phoneNum());
        putIfNotBlank(params, "estimated_single_sale", command.estimatedSingleSale());
        putIfNotNull(params, "activity_type", command.activityType());
        putIfNotBlank(params, "specified_shop_ids", command.specifiedShopIds());
        putIfNotNull(params, "online", command.online());
        putIfNotBlank(params, "categories", command.categories());
        putIfNotNull(params, "shop_score", command.shopScore());
        putIfNotNull(params, "min_promotion_days", command.minPromotionDays());
        putIfNotNull(params, "threshold_cross_border", command.thresholdCrossBorder());
        putIfNotNull(params, "min_exclusion_duration", command.minExclusionDuration());
        putIfNotBlank(params, "ad_commission_rate", command.adCommissionRate());
        putIfNotBlank(params, "ad_service_rate", command.adServiceRate());
        putIfNotNull(params, "cos_limit_type", command.cosLimitType());

        return douyinApiClient.post("alliance.colonelActivityCreateOrUpdate", params);
    }

    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        if (payload != null && !payload.isEmpty()) {
            params.putAll(payload);
        }
        params.remove("appId");
        return douyinApiClient.post("alliance.colonelActivityProductCancel", params);
    }

    private void validateCreateOrUpdate(ActivityCreateOrUpdateCommand command) {
        if (command == null) {
            throw new BusinessException("createOrUpdate command cannot be null");
        }
        if (command.applicationLimited() == null) {
            throw new BusinessException("application_limited cannot be null");
        }
        if (isBlank(command.activityName())) {
            throw new BusinessException("activity_name cannot be blank");
        }
        if (isBlank(command.activityDesc())) {
            throw new BusinessException("activity_desc cannot be blank");
        }
        if (isBlank(command.applyStartTime())) {
            throw new BusinessException("apply_start_time cannot be blank");
        }
        if (isBlank(command.applyEndTime())) {
            throw new BusinessException("apply_end_time cannot be blank");
        }
        if (isBlank(command.commissionRate())) {
            throw new BusinessException("commission_rate cannot be blank");
        }
        if (isBlank(command.serviceRate())) {
            throw new BusinessException("service_rate cannot be blank");
        }
        if (isBlank(command.estimatedSingleSale())) {
            throw new BusinessException("estimated_single_sale cannot be blank");
        }
        if (command.activityType() == null) {
            throw new BusinessException("activity_type cannot be null");
        }
        if (command.online() == null) {
            throw new BusinessException("online cannot be null");
        }

        if (Boolean.TRUE.equals(command.applicationLimited())) {
            if (command.isNewShop() == null) {
                throw new BusinessException("is_new_shop is required when application_limited=true");
            }
            if (isBlank(command.shopType())) {
                throw new BusinessException("shop_type is required when application_limited=true");
            }
        }

        if (!VALID_ACTIVITY_TYPES.contains(command.activityType())) {
            throw new BusinessException("activity_type must be 1 or 2");
        }
        if (command.activityType() == 2 && isBlank(command.specifiedShopIds())) {
            throw new BusinessException("specified_shop_ids is required when activity_type=2");
        }

        if (command.minPromotionDays() != null && !VALID_MIN_PROMOTION_DAYS.contains(command.minPromotionDays())) {
            throw new BusinessException("min_promotion_days must be one of 30/90/180/360");
        }
        if (command.cosLimitType() != null && !VALID_COS_LIMIT_TYPES.contains(command.cosLimitType())) {
            throw new BusinessException("cos_limit_type must be one of 1/2/3");
        }

        BigDecimal commission = parseRate(command.commissionRate(), "commission_rate");
        BigDecimal service = parseRate(command.serviceRate(), "service_rate");
        if (commission.compareTo(MAX_COMMISSION_RATE) > 0) {
            throw new BusinessException("commission_rate cannot exceed 50");
        }
        if (service.compareTo(MAX_SERVICE_RATE) > 0) {
            throw new BusinessException("service_rate cannot exceed 40");
        }
    }

    private BigDecimal parseRate(String rawRate, String fieldName) {
        try {
            BigDecimal value = new BigDecimal(rawRate.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(fieldName + " cannot be negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + " must be a numeric string", e);
        }
    }

    private void putIfNotBlank(Map<String, Object> params, String key, String value) {
        if (!isBlank(value)) {
            params.put(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> params, String key, Object value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int normalizeStatus(Integer status) {
        int value = status == null ? 0 : status;
        List<Integer> valid = List.of(0, 1, 2, 3, 4, 5, 7);
        if (!valid.contains(value)) {
            throw new BusinessException("status invalid, expected one of 0/1/2/3/4/5/7");
        }
        return value;
    }

    private long normalizeSearchType(Long searchType) {
        long value = searchType == null ? 0L : searchType;
        List<Long> valid = List.of(0L, 1L, 2L);
        if (!valid.contains(value)) {
            throw new BusinessException("search_type invalid, expected one of 0/1/2");
        }
        return value;
    }

    private long normalizeSortType(Long sortType) {
        long value = sortType == null ? 1L : sortType;
        if (value != 0L && value != 1L) {
            throw new BusinessException("sort_type invalid, expected one of 0/1");
        }
        return value;
    }

    private long normalizePage(Long page) {
        long value = page == null ? 1L : page;
        if (value < 1L) {
            throw new BusinessException("page must be >= 1");
        }
        return value;
    }

    private long normalizePageSize(Long pageSize) {
        long value = pageSize == null ? 20L : pageSize;
        if (value < 1L || value > 20L) {
            throw new BusinessException("page_size must be between 1 and 20");
        }
        return value;
    }

    private long parseActivityId(String activityId) {
        if (isBlank(activityId)) {
            throw new BusinessException("activity_id cannot be blank");
        }
        try {
            return Long.parseLong(activityId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("activity_id must be numeric", e);
        }
    }

    public record ActivityCreateOrUpdateCommand(
            String appId,
            Long activityId,
            Boolean applicationLimited,
            Boolean isNewShop,
            String shopType,
            String activityName,
            String activityDesc,
            String applyStartTime,
            String applyEndTime,
            String commissionRate,
            String serviceRate,
            String wechatId,
            String phoneNum,
            String estimatedSingleSale,
            Integer activityType,
            String specifiedShopIds,
            Boolean online,
            String categories,
            Integer shopScore,
            Integer minPromotionDays,
            Integer thresholdCrossBorder,
            Integer minExclusionDuration,
            String adCommissionRate,
            String adServiceRate,
            Integer cosLimitType) {
    }
}
