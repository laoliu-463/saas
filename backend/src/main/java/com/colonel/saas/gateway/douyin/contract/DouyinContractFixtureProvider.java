package com.colonel.saas.gateway.douyin.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 抖音契约测试夹具数据提供者。
 *
 * <p>功能描述：在 {@link DouyinUpstreamModeSupport} 判定为 contract 模式时，
 * 为各 RealDouyin*Gateway 实现提供硬编码的夹具（fixture）数据，覆盖抖音 API 的全部响应场景。
 * 包含活动列表/详情、活动商品列表、商品 SKU、推广链接、订单结算、Token、机构信息等模拟数据。
 * 数据规模与结构对齐真实抖音 API 响应格式，用于契约测试（contract test）验证下游业务逻辑的正确性。</p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>仅当 {@code douyin.real.upstream-mode=contract} 时被各 RealGateway 调用</li>
 *   <li>夹具数据固定（硬编码），不依赖外部 API 或数据库状态（订单数据除外，会查询最新 PickSourceMapping）</li>
 *   <li>DEFAULT_APP_KEY、DEFAULT_SHOP_ID、DEFAULT_AUTH_ID 等使用固定的测试商户数据</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：抖音网关 / 契约测试基础设施</p>
 *
 * @see DouyinUpstreamModeSupport
 * @see com.colonel.saas.gateway.douyin.real.RealDouyinActivityGateway
 * @see com.colonel.saas.gateway.douyin.real.RealDouyinOrderGateway
 */
@Component
public class DouyinContractFixtureProvider {

    /** 默认应用密钥（模拟的 app_key） */
    private static final String DEFAULT_APP_KEY = "7623665273727387199";

    /** 默认店铺 ID（模拟的 shop_id） */
    private static final String DEFAULT_SHOP_ID = "56591058";

    /** 默认机构/团长 ID（模拟的 buyin_id / auth_id） */
    private static final String DEFAULT_AUTH_ID = "7351155267604218149";

    /** 默认角色名称 */
    private static final String DEFAULT_ROLE_NAME = "招商团长";

    /** 默认机构名称 */
    private static final String DEFAULT_INSTITUTION_NAME = "星链达客";

    /** 默认店铺名称 */
    private static final String DEFAULT_SHOP_NAME = "星链达客测试店";

    /** 默认店铺评分 */
    private static final String DEFAULT_SHOP_SCORE = "4.90";

    /** 日期时间格式化器，用于将 epoch 秒数转为可读字符串 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** PickSourceMapping 数据访问层，用于查询最新的活跃映射以构造订单夹具数据 */
    private final PickSourceMappingMapper pickSourceMappingMapper;

    /**
     * 构造函数。
     *
     * @param pickSourceMappingMapper PickSourceMapping Mapper，用于查询最新活跃映射
     */
    public DouyinContractFixtureProvider(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    /**
     * 返回默认应用密钥。
     *
     * @return 默认 app_key
     */
    public String appKey() {
        return DEFAULT_APP_KEY;
    }

    /**
     * 返回默认店铺 ID。
     *
     * @return 默认 shop_id
     */
    public String shopId() {
        return DEFAULT_SHOP_ID;
    }

    /**
     * 返回默认机构/团长 ID。
     *
     * @return 默认 auth_id / buyin_id
     */
    public String authId() {
        return DEFAULT_AUTH_ID;
    }

    /**
     * 构造契约模式下的 Token 夹具数据。
     *
     * <p>处理流程：
     * <ol>
     *   <li>确定最终 appId（使用传入值或回退到 DEFAULT_APP_KEY）</li>
     *   <li>取 appId 末尾 6 位字符作为后缀，生成 access_token 格式为 "contract_access_{suffix}_{epoch}"</li>
     *   <li>生成 refresh_token 格式为 "contract_refresh_{suffix}"（若未提供 refreshTokenHint）</li>
     *   <li>返回有效期 7200 秒（2 小时）的 TokenPayload</li>
     * </ol>
     *
     * @param appId            应用 ID（可为空，为空时使用 DEFAULT_APP_KEY）
     * @param refreshTokenHint 刷新令牌提示（可为空，为空时自动生成）
     * @return 契约模式的 TokenPayload 实例
     */
    public DouyinTokenGateway.TokenPayload buildTokenPayload(String appId, String refreshTokenHint) {
        String finalAppId = hasText(appId) ? appId.trim() : DEFAULT_APP_KEY;
        String suffix = finalAppId.length() <= 6 ? finalAppId : finalAppId.substring(finalAppId.length() - 6);
        String accessToken = "contract_access_" + suffix + "_" + Instant.now().getEpochSecond();
        String refreshToken = hasText(refreshTokenHint)
                ? refreshTokenHint.trim()
                : "contract_refresh_" + suffix;
        return new DouyinTokenGateway.TokenPayload(
                accessToken,
                refreshToken,
                7200L,
                DEFAULT_AUTH_ID,
                "institution",
                1L
        );
    }

    /**
     * 构造机构信息查询的契约夹具响应（模拟 buyin.institutionInfo 接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>组装 data 节点，包含 app_key、shop_id、auth_id、institution_name 等固定值</li>
     *   <li>auth_subject_type 固定为 "self_use"（自用型机构）</li>
     *   <li>包装为标准 success 响应格式（err_no=0, upstream_mode=contract）</li>
     * </ol>
     *
     * @param appId 应用 ID（可为空，为空时使用 DEFAULT_APP_KEY）
     * @return 模拟的 buyin.institutionInfo 响应 Map
     */
    public Map<String, Object> buildInstitutionInfoResponse(String appId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        data.put("shop_id", DEFAULT_SHOP_ID);
        data.put("auth_id", DEFAULT_AUTH_ID);
        data.put("buyin_id", DEFAULT_AUTH_ID);
        data.put("institution_id", DEFAULT_AUTH_ID);
        data.put("role_name", DEFAULT_ROLE_NAME);
        data.put("institution_name", DEFAULT_INSTITUTION_NAME);
        data.put("auth_subject_type", "self_use");
        return success("buyin.institutionInfo", data);
    }

    /**
     * 构造活动列表的契约夹具结果（内部结构化格式）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>获取固定的契约活动列表（2 条活动）</li>
     *   <li>按 status、activityInfo、searchType、sortType 进行过滤与排序</li>
     *   <li>基于 page 和 pageSize 计算分页偏移量，截取当前页数据</li>
     *   <li>返回 ActivityListResult，包含分页后的活动列表与总数</li>
     * </ol>
     *
     * @param query 活动列表查询条件（包含分页、状态筛选、关键词等）
     * @return 分页后的活动列表结果
     */
    public DouyinActivityGateway.ActivityListResult buildActivityListResult(
            DouyinActivityGateway.ActivityListQuery query) {
        List<DouyinActivityGateway.ActivityItem> filtered = filterActivities(
                contractActivities(),
                query.status(),
                query.activityInfo(),
                query.searchType(),
                query.sortType()
        );
        long page = query.page() == null || query.page() < 1 ? 1 : query.page();
        long pageSize = query.pageSize() == null || query.pageSize() < 1 ? 20 : query.pageSize();
        int from = (int) ((page - 1) * pageSize);
        int to = Math.min(from + (int) pageSize, filtered.size());
        List<DouyinActivityGateway.ActivityItem> pageList = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to);
        return new DouyinActivityGateway.ActivityListResult(
                false,
                Long.parseLong(DEFAULT_AUTH_ID),
                filtered.size(),
                pageList
        );
    }

    /**
     * 构造活动列表的原始 Map 格式响应（模拟 alliance.instituteColonelActivityList 接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>获取契约活动列表并执行过滤/排序</li>
     *   <li>分页截取后，将每条 ActivityItem 转为原始 Map 格式</li>
     *   <li>包装为包含 activity_list、total、institution_id、app_key 的标准响应</li>
     * </ol>
     *
     * @param appId        应用 ID
     * @param status       活动状态筛选（null 表示全部）
     * @param searchType   搜索类型（1=按报名开始时间，2=按报名结束时间，其他=按活动开始时间）
     * @param sortType     排序方向（0=正序，null 或其他=倒序）
     * @param page         页码（从 1 开始）
     * @param pageSize     每页大小
     * @param activityInfo 活动名称/ID 关键词搜索
     * @return 模拟的 alliance.instituteColonelActivityList 响应 Map
     */
    public Map<String, Object> buildActivityListResponse(
            String appId,
            Integer status,
            Long searchType,
            Long sortType,
            Long page,
            Long pageSize,
            String activityInfo) {
        List<DouyinActivityGateway.ActivityItem> filtered = filterActivities(
                contractActivities(),
                status,
                activityInfo,
                searchType,
                sortType
        );
        long finalPage = page == null || page < 1 ? 1 : page;
        long finalPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int from = (int) ((finalPage - 1) * finalPageSize);
        int to = Math.min(from + (int) finalPageSize, filtered.size());
        List<Map<String, Object>> activityList = (from >= filtered.size() ? List.<DouyinActivityGateway.ActivityItem>of() : filtered.subList(from, to))
                .stream()
                .map(this::toActivityRaw)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activity_list", activityList);
        data.put("total", filtered.size());
        data.put("institution_id", DEFAULT_AUTH_ID);
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("alliance.instituteColonelActivityList", data);
    }

    /**
     * 构造活动详情的原始 Map 格式响应（模拟 buyin.colonelActivityDetail 接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从契约活动列表中匹配指定 activityId</li>
     *   <li>若未匹配到，回退到第一条活动</li>
     *   <li>转为原始 Map 格式并包装为标准 success 响应</li>
     * </ol>
     *
     * @param appId      应用 ID
     * @param activityId 目标活动 ID
     * @return 模拟的 buyin.colonelActivityDetail 响应 Map
     */
    public Map<String, Object> buildActivityDetailResponse(String appId, String activityId) {
        DouyinActivityGateway.ActivityItem item = contractActivities().stream()
                .filter(activity -> String.valueOf(activity.activityId()).equals(activityId))
                .findFirst()
                .orElse(contractActivities().get(0));
        Map<String, Object> data = toActivityRaw(item);
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("buyin.colonelActivityDetail", data);
    }

    /**
     * 构造活动商品列表的契约夹具结果（ActivityGateway 版本，返回 ActivityProductItem）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 activityId 获取对应的契约商品列表（每活动 3 条商品）</li>
     *   <li>按 status 和 productInfo 过滤</li>
     *   <li>根据 retrieveMode（0=分页，1=游标）计算偏移量，截取当前页</li>
     *   <li>将 DouyinProductGateway.ActivityProductItem 转为 DouyinActivityGateway.ActivityProductItem</li>
     *   <li>返回包含 nextCursor 的分页结果</li>
     * </ol>
     *
     * @param query 活动商品列表查询条件
     * @return 分页后的活动商品列表结果
     */
    public DouyinActivityGateway.ActivityProductListResult buildActivityProductListResult(
            DouyinActivityGateway.ActivityProductListQuery query) {
        List<DouyinProductGateway.ActivityProductItem> filtered = filterProducts(
                contractProducts(query.activityId()),
                query.status(),
                query.productInfo()
        );
        int pageSize = Math.min(Math.max(query.count() == null ? 20 : query.count(), 1), 20);
        long mode = query.retrieveMode() == null ? 1L : query.retrieveMode();
        int from = mode == 0L
                ? (int) (((query.page() == null || query.page() < 1 ? 1 : query.page()) - 1) * pageSize)
                : parseCursor(query.cursor());
        int to = Math.min(from + pageSize, filtered.size());
        List<DouyinActivityGateway.ActivityProductItem> items = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to).stream().map(this::toColonelActivityProductItem).toList();
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);
        return new DouyinActivityGateway.ActivityProductListResult(
                false,
                asLong(query.activityId(), 0L),
                Long.parseLong(DEFAULT_AUTH_ID),
                mode == 0L ? Long.valueOf(filtered.size()) : null,
                nextCursor,
                items
        );
    }

    /**
     * 构造活动商品列表的契约夹具结果（ProductGateway 版本，返回原生 ActivityProductItem）。
     *
     * <p>处理流程：与 {@link #buildActivityProductListResult} 类似，但不执行类型转换，
     * 直接返回 {@link DouyinProductGateway.ActivityProductItem} 列表。</p>
     *
     * @param request 活动商品查询请求
     * @return 分页后的活动商品列表结果
     */
    public DouyinProductGateway.ActivityProductListResult buildProductListResult(
            DouyinProductGateway.ActivityProductQueryRequest request) {
        List<DouyinProductGateway.ActivityProductItem> filtered = filterProducts(
                contractProducts(request.activityId()),
                request.status(),
                request.productInfo()
        );
        int pageSize = Math.min(Math.max(request.count() == null ? 20 : request.count(), 1), 20);
        long mode = request.retrieveMode() == null ? 1L : request.retrieveMode();
        int from = mode == 0L
                ? (int) (((request.page() == null || request.page() < 1 ? 1 : request.page()) - 1) * pageSize)
                : parseCursor(request.cursor());
        int to = Math.min(from + pageSize, filtered.size());
        List<DouyinProductGateway.ActivityProductItem> items = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to);
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);
        return new DouyinProductGateway.ActivityProductListResult(
                false,
                asLong(request.activityId(), 0L),
                Long.parseLong(DEFAULT_AUTH_ID),
                mode == 0L ? Long.valueOf(filtered.size()) : null,
                nextCursor,
                items
        );
    }

    /**
     * 构造活动商品列表的原始 Map 格式响应（模拟 alliance.colonelActivityProduct 接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>构建 ActivityProductQueryRequest 并调用 buildProductListResult 获取分页结果</li>
     *   <li>将每条 ActivityProductItem 转为原始 Map 格式</li>
     *   <li>包装为包含 data、total、next_cursor、institution_id 的标准响应</li>
     * </ol>
     *
     * @param appId        应用 ID
     * @param activityId   活动 ID
     * @param count        每页大小
     * @param cursor       游标（分页偏移量）
     * @param productInfo  商品名称/ID 关键词搜索
     * @param status       商品状态筛选
     * @param retrieveMode 检索模式（0=分页，1=游标）
     * @param page         页码
     * @return 模拟的 alliance.colonelActivityProduct 响应 Map
     */
    public Map<String, Object> buildProductListResponse(
            String appId,
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            Integer status,
            Long retrieveMode,
            Long page) {
        DouyinProductGateway.ActivityProductListResult result = buildProductListResult(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        appId,
                        activityId,
                        4L,
                        1L,
                        count,
                        null,
                        null,
                        productInfo,
                        status,
                        retrieveMode,
                        cursor,
                        page
                )
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("institution_id", result.institutionId());
        data.put("total", result.total());
        data.put("next_cursor", result.nextCursor());
        data.put("data", result.items().stream().map(this::toProductRaw).toList());
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("alliance.colonelActivityProduct", data);
    }

    /**
     * 构造商品 SKU 列表的契约夹具数据。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 productId 生成种子值（seed），用于确定性地生成价格偏移</li>
     *   <li>返回 2 个固定 SKU：标准装（基础价 + seed % 100）和加量装（基础价 + seed % 100）</li>
     *   <li>SKU ID 格式为 "{productId}-SKU1" 和 "{productId}-SKU2"</li>
     * </ol>
     *
     * @param productId 商品 ID
     * @return 包含 2 个 SKU 的列表
     */
    public List<DouyinProductGateway.ProductSkuResult> buildProductSkus(String productId) {
        long seed = asLong(productId, 0L);
        return List.of(
                new DouyinProductGateway.ProductSkuResult(
                        productId + "-SKU1",
                        "标准装",
                        3990L + (seed % 100),
                        120,
                        "https://cdn.contract.local/sku/" + productId + "-1.png"
                ),
                new DouyinProductGateway.ProductSkuResult(
                        productId + "-SKU2",
                        "加量装",
                        4590L + (seed % 100),
                        48,
                        "https://cdn.contract.local/sku/" + productId + "-2.png"
                )
        );
    }

    /**
     * 构造推广链接的契约夹具结果。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从 command 中提取 activityId 和 productId，缺失时使用默认值</li>
     *   <li>取 productId 末尾 3 位数字生成短链 ID（shortId），格式 "PS{tail}7351"</li>
     *   <li>生成 pick_source 格式为 "ps_{DEFAULT_AUTH_ID}_{tail}"</li>
     *   <li>若需要短链（needShortLink），生成 "https://contract.short.link/{shortId}"</li>
     *   <li>组装标准推广链接，格式为 "https://haohuo.jinritemai.com/views/product/item2?id=...&activity_id=...&pick_source=..."</li>
     *   <li>使用 UUID.nameUUIDFromBytes 生成确定性的 promotionId</li>
     * </ol>
     *
     * @param command 推广链接生成命令（包含商品 ID、活动上下文、是否需要短链等）
     * @return 契约模式的推广链接结果
     */
    public DouyinPromotionGateway.PromotionLinkResult buildPromotionLinkResult(
            DouyinPromotionGateway.PromotionLinkCommand command) {
        String activityId = command.context() == null || !hasText(command.context().activityId())
                ? "20260428001"
                : command.context().activityId();
        String productId = command.productIds() == null || command.productIds().isEmpty()
                ? "910001"
                : String.valueOf(command.productIds().get(0));
        String tail = digitsOnly(productId);
        if (tail.length() > 3) {
            tail = tail.substring(tail.length() - 3);
        }
        String shortId = ("PS" + tail + "7351").toUpperCase(Locale.ROOT);
        String pickSource = "ps_" + DEFAULT_AUTH_ID + "_" + (tail.isEmpty() ? "001" : tail);
        String shortLink = command.needShortLink()
                ? "https://contract.short.link/" + shortId
                : null;
        String promoteLink = "https://haohuo.jinritemai.com/views/product/item2?id="
                + productId
                + "&activity_id="
                + activityId
                + "&pick_source="
                + pickSource;
        String uuidSeed = UUID.nameUUIDFromBytes((activityId + ":" + productId + ":" + pickSource)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new DouyinPromotionGateway.PromotionLinkResult(
                pickSource,
                shortId,
                shortId,
                shortLink,
                promoteLink,
                uuidSeed
        );
    }

    /**
     * 构造订单列表的契约夹具结果。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询数据库中最新的活跃 PickSourceMapping（status=1，按更新时间倒序取第一条）</li>
     *   <li>若存在映射，构造一条"已归因"订单：使用映射中的活动 ID、pick_source、达人信息</li>
     *   <li>构造一条"映射缺失"订单：使用未知的 pick_source "ps_unknown_mapping_001"，模拟未命中映射场景</li>
     *   <li>构造一条"未带推广参数"订单：pick_source 为空，模拟无推广参数的订单场景</li>
     *   <li>订单时间基于 request.startTime 偏移（+1h、+1.5h、+2h）</li>
     *   <li>返回 3 条订单的列表结果，标记 upstream_mode=contract</li>
     * </ol>
     *
     * @param request 订单查询请求（包含时间范围、分页参数）
     * @return 契约模式的订单列表结果（固定 3 条订单，覆盖已归因/未归因/无参数三种场景）
     */
    public DouyinOrderGateway.OrderListResult buildOrderListResult(DouyinOrderGateway.DouyinOrderQueryRequest request) {
        List<DouyinOrderGateway.DouyinOrderItem> orders = new ArrayList<>();
        PickSourceMapping latestMapping = latestActiveMapping();
        long baseTime = request.startTime() > 0 ? request.startTime() : Instant.now().getEpochSecond() - 3600;

        if (latestMapping != null) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("product_name", "契约联调商品-已归因");
            raw.put("colonel_activity_id", latestMapping.getActivityId());
            raw.put("pick_source", latestMapping.getPickSource());
            raw.put("pick_extra", latestMapping.getShortId());
            raw.put("merchant_id", DEFAULT_SHOP_ID);
            raw.put("shop_id", DEFAULT_SHOP_ID);
            raw.put("talent_uid", hasText(latestMapping.getTalentId()) ? latestMapping.getTalentId() : DEFAULT_AUTH_ID);
            raw.put("author_id", hasText(latestMapping.getTalentId()) ? latestMapping.getTalentId() : DEFAULT_AUTH_ID);
            orders.add(new DouyinOrderGateway.DouyinOrderItem(
                    "CONTRACT_ORD_ATTR_" + latestMapping.getShortId(),
                    latestMapping.getProductId(),
                    latestMapping.getProductId(),
                    DEFAULT_SHOP_ID,
                    DEFAULT_SHOP_NAME,
                    latestMapping.getTalentId(),
                    latestMapping.getTalentName(),
                    latestMapping.getPickSource(),
                    3990L,
                    399L,
                    1,
                    baseTime + 3600,
                    baseTime + 7200,
                    raw
            ));
        }

        Map<String, Object> unknownRaw = new LinkedHashMap<>();
        unknownRaw.put("product_name", "契约联调商品-映射缺失");
        unknownRaw.put("colonel_activity_id", "20260428001");
        unknownRaw.put("pick_source", "ps_unknown_mapping_001");
        unknownRaw.put("pick_extra", "PSUNK001");
        unknownRaw.put("merchant_id", DEFAULT_SHOP_ID);
        unknownRaw.put("shop_id", DEFAULT_SHOP_ID);
        unknownRaw.put("talent_uid", DEFAULT_AUTH_ID);
        orders.add(new DouyinOrderGateway.DouyinOrderItem(
                "CONTRACT_ORD_UNATTR_1",
                "910002",
                "910002",
                DEFAULT_SHOP_ID,
                DEFAULT_SHOP_NAME,
                DEFAULT_AUTH_ID,
                "未命中映射达人",
                "ps_unknown_mapping_001",
                4590L,
                459L,
                1,
                baseTime + 5400,
                null,
                unknownRaw
        ));

        Map<String, Object> noPickRaw = new LinkedHashMap<>();
        noPickRaw.put("product_name", "契约联调商品-未带推广参数");
        noPickRaw.put("colonel_activity_id", "20260428001");
        noPickRaw.put("merchant_id", DEFAULT_SHOP_ID);
        noPickRaw.put("shop_id", DEFAULT_SHOP_ID);
        noPickRaw.put("talent_uid", DEFAULT_AUTH_ID);
        orders.add(new DouyinOrderGateway.DouyinOrderItem(
                "CONTRACT_ORD_UNATTR_2",
                "910003",
                "910003",
                DEFAULT_SHOP_ID,
                DEFAULT_SHOP_NAME,
                DEFAULT_AUTH_ID,
                "未带推广参数达人",
                null,
                2990L,
                299L,
                1,
                baseTime + 7200,
                null,
                noPickRaw
        ));

        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("order_count", orders.size());
        rawResponse.put("has_more", false);
        rawResponse.put("next_cursor", "0");
        rawResponse.put("upstream_mode", "contract");
        return new DouyinOrderGateway.OrderListResult(orders, false, "0", rawResponse);
    }

    /**
     * 构造订单结算查询的原始 Map 格式响应（模拟 buyin.colonelMultiSettlementOrders 接口）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析 startTime/endTime 为 epoch 秒数，缺失时回退到最近 2 小时范围</li>
     *   <li>调用 buildOrderListResult 获取订单列表</li>
     *   <li>将每条 DouyinOrderItem 转为原始 Map 格式</li>
     *   <li>包装为包含 data、has_more、next_cursor、time_type、order_ids 的标准响应</li>
     * </ol>
     *
     * @param appId     应用 ID
     * @param size      每页大小
     * @param cursor    游标
     * @param timeType  时间类型（"create" 或 "update"）
     * @param startTime 开始时间（yyyy-MM-dd HH:mm:ss 格式）
     * @param endTime   结束时间（yyyy-MM-dd HH:mm:ss 格式）
     * @param orderIds  订单 ID 列表（逗号分隔）
     * @return 模拟的 buyin.colonelMultiSettlementOrders 响应 Map
     */
    public Map<String, Object> buildOrderSettlementResponse(
            String appId,
            Integer size,
            String cursor,
            String timeType,
            String startTime,
            String endTime,
            String orderIds) {
        long startEpoch = parseEpochSecond(startTime, Instant.now().getEpochSecond() - 7200);
        long endEpoch = parseEpochSecond(endTime, Instant.now().getEpochSecond());
        DouyinOrderGateway.OrderListResult result = buildOrderListResult(
                new DouyinOrderGateway.DouyinOrderQueryRequest(
                        startEpoch,
                        endEpoch,
                        size == null ? 20 : size,
                        cursor
                )
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", result.orders().stream().map(this::toOrderRaw).toList());
        data.put("has_more", result.hasMore());
        data.put("next_cursor", result.nextCursor());
        data.put("time_type", timeType == null ? "update" : timeType);
        data.put("order_ids", orderIds);
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("buyin.colonelMultiSettlementOrders", data);
    }


    /**
     * 返回固定的契约活动列表（2 条活动：4月精选联盟活动[推广中] 和 高佣爆品活动[报名未开始]）。
     *
     * @return 不可变的活动列表
     */
    private List<DouyinActivityGateway.ActivityItem> contractActivities() {
        return List.of(
                new DouyinActivityGateway.ActivityItem(
                        20260428001L,
                        "4月精选联盟活动",
                        "2026-04-01 00:00:00",
                        "2026-05-01 00:00:00",
                        5,
                        "推广中",
                        "2026-03-25 00:00:00",
                        "2026-03-31 23:59:59",
                        Map.of("一级类目", "美妆个护"),
                        7351155267604218149L
                ),
                new DouyinActivityGateway.ActivityItem(
                        20260501002L,
                        "高佣爆品活动",
                        "2026-05-01 00:00:00",
                        "2026-06-01 00:00:00",
                        2,
                        "报名未开始",
                        "2026-04-25 00:00:00",
                        "2026-04-30 23:59:59",
                        Map.of("一级类目", "日用百货"),
                        7351155267604218149L
                )
        );
    }

    /**
     * 返回指定活动的契约商品列表。不同活动返回不同商品集（3 条/活动）。
     *
     * @param activityId 活动 ID（为空时默认为 "20260428001"）
     * @return 包含 3 个商品的不可变列表，覆盖可推广/待审核/申请未通过三种状态
     */
    private List<DouyinProductGateway.ActivityProductItem> contractProducts(String activityId) {
        String finalActivityId = hasText(activityId) ? activityId.trim() : "20260428001";
        if ("20260501002".equals(finalActivityId)) {
            return List.of(
                    buildProduct(920001L, "待开始活动-可推广商品", 3990L, 1, "可推广", "日用百货", finalActivityId),
                    buildProduct(920002L, "待开始活动-待审核商品", 4590L, 0, "待审核", "日用百货", finalActivityId),
                    buildProduct(920003L, "待开始活动-合作终止商品", 2990L, 3, "合作已终止", "日用百货", finalActivityId)
            );
        }
        return List.of(
                buildProduct(910001L, "测试爆品洗脸巾", 3990L, 1, "可推广", "美妆个护", finalActivityId),
                buildProduct(910002L, "测试爆品棉柔巾", 4590L, 0, "待审核", "美妆个护", finalActivityId),
                buildProduct(910003L, "测试爆品卸妆巾", 2990L, 2, "申请未通过", "美妆个护", finalActivityId)
        );
    }

    /**
     * 构造单个契约商品。
     *
     * @param productId    商品 ID
     * @param title        商品标题
     * @param price        价格（分）
     * @param status       商品状态码（0=待审核，1=可推广，2=申请未通过，3=合作已终止）
     * @param statusText   状态中文描述
     * @param categoryName 类目名称
     * @param activityId   关联的活动 ID
     * @return 构造好的 ActivityProductItem 实例
     */
    private DouyinProductGateway.ActivityProductItem buildProduct(
            long productId,
            String title,
            long price,
            int status,
            String statusText,
            String categoryName,
            String activityId) {
        long activityCosRatio = 2000L;
        Long activityAdCosRatio = 1000L;
        return new DouyinProductGateway.ActivityProductItem(
                productId,
                title,
                "https://cdn.contract.local/product/" + productId + ".png",
                price,
                String.format(Locale.ROOT, "%.2f", price / 100.0),
                2000L,
                Math.round(price * 0.2d),
                activityCosRatio,
                "20.00%",
                1,
                "双佣金",
                "10",
                activityAdCosRatio,
                true,
                status == 1,
                320L,
                Long.parseLong(DEFAULT_SHOP_ID),
                DEFAULT_SHOP_NAME,
                DEFAULT_SHOP_SCORE,
                status,
                statusText,
                categoryName,
                status == 1 ? "128" : "0",
                "满199减20",
                "2026-04-01 00:00:00",
                "2026-05-01 00:00:00",
                "2026-04-01 00:00:00",
                "2026-05-01 00:00:00",
                "https://haohuo.jinritemai.com/views/product/item2?id=" + productId + "&activity_id=" + activityId,
                "7293293346398011698",
                Map.of("origin_colonel_buyin_id", "7293293346398011698")
        );
    }

    /**
     * 按条件过滤活动列表并排序。
     *
     * <p>处理流程：
     * <ol>
     *   <li>按状态过滤：status 为 null 或 0 时保留全部，否则按 status 精确匹配</li>
     *   <li>按关键词过滤：匹配活动名称（大小写不敏感）或活动 ID（精确包含）</li>
     *   <li>按 searchType 选择排序字段：1=报名开始时间、2=报名结束时间、其他=活动开始时间</li>
     *   <li>按 sortType 决定排序方向：0=升序，null 或其他值=降序</li>
     * </ol>
     *
     * @param activities 待过滤的活动列表
     * @param status     状态过滤条件（null/0=不过滤）
     * @param keyword    搜索关键词（匹配活动名称或 ID）
     * @param searchType 排序字段选择（1=报名开始，2=报名结束，其他=活动开始）
     * @param sortType   排序方向（0=升序，其他=降序）
     * @return 过滤并排序后的活动列表
     */
    private List<DouyinActivityGateway.ActivityItem> filterActivities(
            List<DouyinActivityGateway.ActivityItem> activities,
            Integer status,
            String keyword,
            Long searchType,
            Long sortType) {
        List<DouyinActivityGateway.ActivityItem> filtered = activities.stream()
                .filter(item -> status == null || status == 0 || item.status() == status)
                .filter(item -> {
                    if (!hasText(keyword)) {
                        return true;
                    }
                    String normalized = keyword.trim().toLowerCase(Locale.ROOT);
                    return item.activityName().toLowerCase(Locale.ROOT).contains(normalized)
                            || String.valueOf(item.activityId()).contains(normalized);
                })
                .toList();
        Comparator<DouyinActivityGateway.ActivityItem> comparator;
        if (searchType != null && searchType == 1L) {
            comparator = Comparator.comparing(DouyinActivityGateway.ActivityItem::applicationStartTime, Comparator.nullsLast(String::compareTo));
        } else if (searchType != null && searchType == 2L) {
            comparator = Comparator.comparing(DouyinActivityGateway.ActivityItem::applicationEndTime, Comparator.nullsLast(String::compareTo));
        } else {
            comparator = Comparator.comparing(DouyinActivityGateway.ActivityItem::activityStartTime, Comparator.nullsLast(String::compareTo));
        }
        if (sortType == null || sortType != 0L) {
            comparator = comparator.reversed();
        }
        return filtered.stream().sorted(comparator).toList();
    }

    /**
     * 按条件过滤活动商品列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>按状态过滤：status 为 null 时保留全部，否则按 status 精确匹配</li>
     *   <li>按关键词过滤：匹配商品标题（大小写不敏感）或商品 ID（精确包含）</li>
     * </ol>
     *
     * @param items   待过滤的商品列表
     * @param status  状态过滤条件（null=不过滤）
     * @param keyword 搜索关键词（匹配商品标题或 ID）
     * @return 过滤后的商品列表
     */
    private List<DouyinProductGateway.ActivityProductItem> filterProducts(
            List<DouyinProductGateway.ActivityProductItem> items,
            Integer status,
            String keyword) {
        return items.stream()
                .filter(item -> status == null || item.status() == status)
                .filter(item -> {
                    if (!hasText(keyword)) {
                        return true;
                    }
                    String normalized = keyword.trim().toLowerCase(Locale.ROOT);
                    return item.title().toLowerCase(Locale.ROOT).contains(normalized)
                            || String.valueOf(item.productId()).contains(normalized);
                })
                .toList();
    }

    /**
     * 将活动项转换为原始 Map 格式，用于模拟抖音 API 响应结构。
     *
     * <p>映射字段：activity_id、activity_name、activity_start_time、activity_end_time、
     * status、application_start_time、application_end_time、categories_limit、colonel_buyin_id</p>
     *
     * @param item 活动项
     * @return 包含所有活动字段的 LinkedHashMap
     */
    private Map<String, Object> toActivityRaw(DouyinActivityGateway.ActivityItem item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("activity_id", item.activityId());
        raw.put("activity_name", item.activityName());
        raw.put("activity_start_time", item.activityStartTime());
        raw.put("activity_end_time", item.activityEndTime());
        raw.put("status", item.status());
        raw.put("application_start_time", item.applicationStartTime());
        raw.put("application_end_time", item.applicationEndTime());
        raw.put("categories_limit", item.categoriesLimit());
        raw.put("colonel_buyin_id", item.colonelBuyinId());
        return raw;
    }

    /**
     * 将活动商品项转换为原始 Map 格式，用于模拟抖音 API 响应结构。
     *
     * <p>映射字段：product_id、title、cover、price、cos_ratio、cos_fee、activity_cos_ratio、
     * cos_type、ad_service_ratio、activity_ad_cos_ratio、has_douin_goods_tag、in_stock、
     * sales、shop_id、shop_name、shop_score、status、category_name、product_stock、
     * colonel_coupon_info、activity_start_time、activity_end_time、promotion_start_time、
     * promotion_end_time、detail_url</p>
     *
     * @param item 活动商品项
     * @return 包含所有商品字段的 LinkedHashMap
     */
    private Map<String, Object> toProductRaw(DouyinProductGateway.ActivityProductItem item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("product_id", item.productId());
        raw.put("title", item.title());
        raw.put("cover", item.cover());
        raw.put("price", item.price());
        raw.put("cos_ratio", item.cosRatio());
        raw.put("cos_fee", item.cosFee());
        raw.put("activity_cos_ratio", item.activityCosRatio());
        raw.put("cos_type", item.cosType());
        raw.put("ad_service_ratio", item.adServiceRatio());
        raw.put("activity_ad_cos_ratio", item.activityAdCosRatio());
        raw.put("has_douin_goods_tag", item.hasDouinGoodsTag());
        raw.put("in_stock", item.inStock());
        raw.put("sales", item.sales());
        raw.put("shop_id", item.shopId());
        raw.put("shop_name", item.shopName());
        raw.put("shop_score", item.shopScore());
        raw.put("status", item.status());
        raw.put("category_name", item.categoryName());
        raw.put("product_stock", item.productStock());
        raw.put("colonel_coupon_info", item.colonelCouponInfo());
        raw.put("activity_start_time", item.activityStartTime());
        raw.put("activity_end_time", item.activityEndTime());
        raw.put("promotion_start_time", item.promotionStartTime());
        raw.put("promotion_end_time", item.promotionEndTime());
        raw.put("detail_url", item.detailUrl());
        return raw;
    }

    /**
     * 将订单项转换为原始 Map 格式，用于模拟抖音结算 API 响应结构。
     *
     * <p>映射字段：order_id、product_id、shop_id、shop_name、talent_uid、talent_name、
     * pick_source、order_amount、service_fee、order_status、create_time、settle_time。
     * 若 item 携带 rawPayload，其内容会追加到结果 Map 中。</p>
     *
     * @param item 订单项
     * @return 包含订单字段与原始负载的 LinkedHashMap
     */
    private Map<String, Object> toOrderRaw(DouyinOrderGateway.DouyinOrderItem item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("order_id", item.externalOrderId());
        raw.put("product_id", item.productId());
        raw.put("shop_id", item.merchantId());
        raw.put("shop_name", item.merchantName());
        raw.put("talent_uid", item.talentId());
        raw.put("talent_name", item.talentName());
        raw.put("pick_source", item.pickSource());
        raw.put("order_amount", item.orderAmount());
        raw.put("service_fee", item.serviceFee());
        raw.put("order_status", item.orderStatus());
        raw.put("create_time", formatEpoch(item.createTime()));
        raw.put("settle_time", item.settleTime() == null ? null : formatEpoch(item.settleTime()));
        if (item.rawPayload() != null) {
            raw.putAll(item.rawPayload());
        }
        return raw;
    }

    /**
     * 将 DouyinProductGateway 的商品项转换为 DouyinActivityGateway 的活动商品项。
     *
     * <p>两个 Gateway 使用不同的 ActivityProductItem 记录类型（字段名一致但包路径不同），
     * 此方法完成跨包的字段映射。rawPayload 为空时返回不可变空 Map。</p>
     *
     * @param item 来源商品项（DouyinProductGateway.ActivityProductItem）
     * @return 目标活动商品项（DouyinActivityGateway.ActivityProductItem），字段逐一复制
     */
    private DouyinActivityGateway.ActivityProductItem toColonelActivityProductItem(
            DouyinProductGateway.ActivityProductItem item) {
        return new DouyinActivityGateway.ActivityProductItem(
                item.productId(),
                item.title(),
                item.cover(),
                item.price(),
                item.priceText(),
                item.cosRatio(),
                item.cosFee(),
                item.activityCosRatio(),
                item.activityCosRatioText(),
                item.cosType(),
                item.cosTypeText(),
                item.adServiceRatio(),
                item.activityAdCosRatio(),
                item.hasDouinGoodsTag(),
                item.inStock(),
                item.sales(),
                item.shopId(),
                item.shopName(),
                item.shopScore(),
                item.status(),
                item.statusText(),
                item.categoryName(),
                item.productStock(),
                item.colonelCouponInfo(),
                item.activityStartTime(),
                item.activityEndTime(),
                item.promotionStartTime(),
                item.promotionEndTime(),
                item.detailUrl(),
                item.originColonelBuyinId(),
                item.rawPayload() == null ? Map.of() : new LinkedHashMap<>(item.rawPayload())
        );
    }

    /**
     * 构造模拟抖音 API 成功响应的包装 Map。
     *
     * <p>结构与抖音开放平台真实响应对齐：err_no=0、err_msg="success"、
     * log_id（由 method 与时间戳拼接）、data（实际业务数据）、upstream_mode="contract"</p>
     *
     * @param method API 方法名（如 "buyin.activityList"），用于生成 log_id
     * @param data   业务数据 Map
     * @return 包含 err_no/err_msg/log_id/data/upstream_mode 的标准响应 Map
     */
    private Map<String, Object> success(String method, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("err_no", 0);
        result.put("err_msg", "success");
        result.put("log_id", "contract-" + method.replace('.', '-') + "-" + Instant.now().toEpochMilli());
        result.put("data", data);
        result.put("upstream_mode", "contract");
        return result;
    }

    /**
     * 查询数据库中最新的活跃 PickSourceMapping 记录。
     *
     * <p>查询条件：status=1（活跃），按 update_time 降序取第一条。
     * 用于构造已归因的契约订单夹具数据。若数据库无记录则返回 null。</p>
     *
     * @return 最新的活跃 PickSourceMapping，无记录时返回 null
     */
    private PickSourceMapping latestActiveMapping() {
        return pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)
                .last("limit 1"));
    }

    /**
     * 判断字符串是否有实际内容（非 null 且非空白）。
     *
     * @param value 待判断的字符串
     * @return true 表示有内容，false 表示为 null 或空白
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 将字符串安全转换为 long，解析失败时返回默认值。
     *
     * @param value        待转换的字符串（null 或空白时返回默认值）
     * @param defaultValue 默认值
     * @return 转换后的 long 值，解析失败时返回 defaultValue
     */
    private long asLong(String value, long defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    /**
     * 将游标字符串安全转换为非负 int，解析失败时返回 0。
     *
     * <p>用于契约模式的分页游标解析，游标值为 null/空白/非数字时均回退到 0（首页）。</p>
     *
     * @param cursor 游标字符串
     * @return 解析后的游标值，始终 >= 0
     */
    private int parseCursor(String cursor) {
        if (!hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    /**
     * 将日期时间字符串解析为 epoch 秒数，解析失败时返回默认值。
     *
     * <p>使用 {@code yyyy-MM-dd HH:mm:ss} 格式解析，并通过 AppZone 转换为 epoch 秒。
     * null/空白/格式错误时均回退到 defaultValue。</p>
     *
     * @param value        日期时间字符串（如 "2026-04-28 10:00:00"）
     * @param defaultValue 默认 epoch 秒数
     * @return 解析后的 epoch 秒数，解析失败时返回 defaultValue
     */
    private long parseEpochSecond(String value, long defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return com.colonel.saas.common.time.AppZone.toEpochSecond(
                    LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER));
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    /**
     * 将 epoch 秒数格式化为 {@code yyyy-MM-dd HH:mm:ss} 字符串。
     *
     * @param epochSecond epoch 秒数（null 时返回 null）
     * @return 格式化后的日期时间字符串，输入为 null 时返回 null
     */
    private String formatEpoch(Long epochSecond) {
        if (epochSecond == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(com.colonel.saas.common.time.AppZone.fromEpochSecond(epochSecond));
    }

    /**
     * 从字符串中提取纯数字字符，忽略所有非数字字符。
     *
     * <p>用于从可能包含前缀/分隔符的 token 字符串中提取纯数字部分。
     * null 输入返回空字符串。</p>
     *
     * @param value 待提取的字符串
     * @return 仅包含数字字符的字符串，null 输入返回空字符串
     */
    private String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
