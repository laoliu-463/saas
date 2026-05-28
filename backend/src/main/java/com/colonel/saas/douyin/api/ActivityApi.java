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

/**
 * 精选联盟活动管理 API 客户端。
 * <p>
 * 封装抖音精选联盟团长活动的查询、创建/更新和商品取消操作，
 * 支持 contract（合同模式）与真实上游两种调用路径。
 *
 * <ul>
 *   <li>活动列表查询 — 按状态、排序、分页条件列出团长活动</li>
 *   <li>活动详情查询 — 根据活动 ID 获取详情</li>
 *   <li>活动创建/更新 — 创建或更新团长活动（含参数校验）</li>
 *   <li>活动商品取消 — 取消指定活动中的商品</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 活动管理
 *
 * @see DouyinApiClient
 * @see DouyinUpstreamModeSupport
 * @see DouyinContractFixtureProvider
 */
@Service
public class ActivityApi {

    /** 合法活动类型集合：1-公开活动，2-指定商家活动 */
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

    /**
     * 查询默认分页的活动列表。
     *
     * @param appId 应用 ID（可为空，为空时使用全局默认 appId）
     * @return 活动列表响应，包含 data 和 total 字段
     */
    public Map<String, Object> list(String appId) {
        return listActivities(appId, 0, 0L, 1L, 1L, 20L, null);
    }

    /**
     * 按条件查询活动列表。
     * <p>
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     *
     * @param appId        应用 ID（可为空）
     * @param status       活动状态筛选，可选值：0/1/2/3/4/5/7，默认 0（全部）
     * @param searchType   搜索类型，可选值：0/1/2，默认 0
     * @param sortType     排序类型，0-降序，1-升序，默认 1
     * @param page         页码，从 1 开始，默认 1
     * @param pageSize     每页数量，范围 1~20，默认 20
     * @param activityInfo 活动名称关键词（模糊搜索）
     * @return 活动列表响应
     * @throws BusinessException 当参数校验不通过时抛出
     */
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

    /**
     * 查询活动详情。
     * <p>
     * 在 contract 模式下返回契约桩数据，否则调用真实上游 API。
     *
     * @param appId      应用 ID（可为空）
     * @param activityId 活动 ID（数字字符串）
     * @return 活动详情响应
     * @throws BusinessException 当 activityId 为空或非数字时抛出
     */
    public Map<String, Object> detail(String appId, String activityId) {
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityDetailResponse(appId, activityId);
        }
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        params.put("activity_id", parseActivityId(activityId));
        return douyinApiClient.post("buyin.colonelActivityDetail", params);
    }

    /**
     * 创建或更新活动。
     * <p>
     * 执行完整的参数校验后调用上游 API 创建或更新团长活动。
     *
     * <ol>
     *   <li>校验必填参数（名称、描述、时间、佣金率等）</li>
     *   <li>校验活动类型合法性及关联条件</li>
     *   <li>校验佣金率和服务费率上限</li>
     *   <li>调用上游 API 执行创建或更新</li>
     * </ol>
     *
     * @param command 活动创建/更新命令对象
     * @return 上游 API 响应
     * @throws BusinessException 当参数校验不通过时抛出
     * @see ActivityCreateOrUpdateCommand
     */
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

    /**
     * 取消活动中的商品。
     *
     * @param appId   应用 ID
     * @param payload 请求体参数（包含要取消的商品信息）
     * @return 上游 API 响应
     */
    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        Map<String, Object> params = new HashMap<>();
        putIfNotBlank(params, "appId", appId);
        if (payload != null && !payload.isEmpty()) {
            params.putAll(payload);
        }
        params.remove("appId");
        return douyinApiClient.post("alliance.colonelActivityProductCancel", params);
    }

    /**
     * 校验活动创建/更新命令的参数合法性。
     * <p>
     * 校验规则包括：必填字段非空、活动类型合法、指定商家活动必须提供商家 ID、
     * 佣金率和服务费率不超过上限。
     *
     * @param command 待校验的命令对象
     * @throws BusinessException 当任一参数校验不通过时抛出
     */
    private void validateCreateOrUpdate(ActivityCreateOrUpdateCommand command) {
        if (command == null) {
            throw BusinessException.param("createOrUpdate command cannot be null");
        }
        if (command.applicationLimited() == null) {
            throw BusinessException.param("application_limited cannot be null");
        }
        if (isBlank(command.activityName())) {
            throw BusinessException.param("activity_name cannot be blank");
        }
        if (isBlank(command.activityDesc())) {
            throw BusinessException.param("activity_desc cannot be blank");
        }
        if (isBlank(command.applyStartTime())) {
            throw BusinessException.param("apply_start_time cannot be blank");
        }
        if (isBlank(command.applyEndTime())) {
            throw BusinessException.param("apply_end_time cannot be blank");
        }
        if (isBlank(command.commissionRate())) {
            throw BusinessException.param("commission_rate cannot be blank");
        }
        if (isBlank(command.serviceRate())) {
            throw BusinessException.param("service_rate cannot be blank");
        }
        if (isBlank(command.estimatedSingleSale())) {
            throw BusinessException.param("estimated_single_sale cannot be blank");
        }
        if (command.activityType() == null) {
            throw BusinessException.param("activity_type cannot be null");
        }
        if (command.online() == null) {
            throw BusinessException.param("online cannot be null");
        }

        if (Boolean.TRUE.equals(command.applicationLimited())) {
            if (command.isNewShop() == null) {
                throw BusinessException.param("is_new_shop is required when application_limited=true");
            }
            if (isBlank(command.shopType())) {
                throw BusinessException.param("shop_type is required when application_limited=true");
            }
        }

        if (!VALID_ACTIVITY_TYPES.contains(command.activityType())) {
            throw BusinessException.param("activity_type must be 1 or 2");
        }
        if (command.activityType() == 2 && isBlank(command.specifiedShopIds())) {
            throw BusinessException.param("specified_shop_ids is required when activity_type=2");
        }

        if (command.minPromotionDays() != null && !VALID_MIN_PROMOTION_DAYS.contains(command.minPromotionDays())) {
            throw BusinessException.param("min_promotion_days must be one of 30/90/180/360");
        }
        if (command.cosLimitType() != null && !VALID_COS_LIMIT_TYPES.contains(command.cosLimitType())) {
            throw BusinessException.param("cos_limit_type must be one of 1/2/3");
        }

        BigDecimal commission = parseRate(command.commissionRate(), "commission_rate");
        BigDecimal service = parseRate(command.serviceRate(), "service_rate");
        if (commission.compareTo(MAX_COMMISSION_RATE) > 0) {
            throw BusinessException.param("commission_rate cannot exceed 50");
        }
        if (service.compareTo(MAX_SERVICE_RATE) > 0) {
            throw BusinessException.param("service_rate cannot exceed 40");
        }
    }

    /**
     * 解析佣金/服务费率字符串为 BigDecimal 并校验非负。
     *
     * @param rawRate   原始费率字符串
     * @param fieldName 字段名称（用于错误提示）
     * @return 解析后的 BigDecimal 值
     * @throws BusinessException 当费率非数字或为负数时抛出
     */
    private BigDecimal parseRate(String rawRate, String fieldName) {
        try {
            BigDecimal value = new BigDecimal(rawRate.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw BusinessException.param(fieldName + " cannot be negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw BusinessException.param(fieldName + " must be a numeric string", e);
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
            throw BusinessException.param("status invalid, expected one of 0/1/2/3/4/5/7");
        }
        return value;
    }

    private long normalizeSearchType(Long searchType) {
        long value = searchType == null ? 0L : searchType;
        List<Long> valid = List.of(0L, 1L, 2L);
        if (!valid.contains(value)) {
            throw BusinessException.param("search_type invalid, expected one of 0/1/2");
        }
        return value;
    }

    private long normalizeSortType(Long sortType) {
        long value = sortType == null ? 1L : sortType;
        if (value != 0L && value != 1L) {
            throw BusinessException.param("sort_type invalid, expected one of 0/1");
        }
        return value;
    }

    private long normalizePage(Long page) {
        long value = page == null ? 1L : page;
        if (value < 1L) {
            throw BusinessException.param("page must be >= 1");
        }
        return value;
    }

    private long normalizePageSize(Long pageSize) {
        long value = pageSize == null ? 20L : pageSize;
        if (value < 1L || value > 20L) {
            throw BusinessException.param("page_size must be between 1 and 20");
        }
        return value;
    }

    private long parseActivityId(String activityId) {
        if (isBlank(activityId)) {
            throw BusinessException.param("activity_id cannot be blank");
        }
        try {
            return Long.parseLong(activityId.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.param("activity_id must be numeric", e);
        }
    }

    /**
     * 活动创建/更新命令参数对象。
     *
     * @param appId               应用 ID
     * @param activityId          活动 ID（更新时必填）
     * @param applicationLimited  是否限制申请（true 时需提供 isNewShop 和 shopType）
     * @param isNewShop           是否仅限新店
     * @param shopType            店铺类型
     * @param activityName        活动名称（必填）
     * @param activityDesc        活动描述（必填）
     * @param applyStartTime      申请开始时间
     * @param applyEndTime        申请结束时间
     * @param commissionRate      佣金率（最高 50%）
     * @param serviceRate         服务费率（最高 40%）
     * @param wechatId            微信号
     * @param phoneNum            联系电话
     * @param estimatedSingleSale 预计单场销量
     * @param activityType        活动类型：1-公开活动，2-指定商家活动
     * @param specifiedShopIds    指定商家 ID 列表（activityType=2 时必填）
     * @param online              是否上线
     * @param categories          品类限制
     * @param shopScore           店铺评分要求
     * @param minPromotionDays    最低推广天数，可选值：30/90/180/360
     * @param thresholdCrossBorder 跨境门槛
     * @param minExclusionDuration 最低排他时长
     * @param adCommissionRate    广告佣金率
     * @param adServiceRate       广告服务费率
     * @param cosLimitType        COS 限制类型，可选值：1/2/3
     */
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
