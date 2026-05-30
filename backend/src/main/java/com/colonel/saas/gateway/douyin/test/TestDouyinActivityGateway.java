package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.domain.ActivityStatusResolver;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.asLong;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.formatPriceText;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockActivityEndDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockActivityStartDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockPromotionEndDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockPromotionStartDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.parseCursor;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.productStatusText;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.resolveMockProductStatus;

/**
 * 测试环境抖店活动网关适配器。
 * <p>
 * 实现 {@link DouyinActivityGateway} 接口，在 {@code douyin.test.enabled=true} 时替代真实的
 * {@code DouyinActivityGateway}（通常由 HTTP 客户端对接抖店开放平台），为本地开发和 test 环境提供
 * 不依赖真实上游的 Mock 活动数据。
 * </p>
 *
 * <ul>
 *   <li><b>活动列表</b>：生成 36 条 Mock 活动，覆盖 6 种状态循环，支持状态过滤、关键词搜索和排序</li>
 *   <li><b>活动商品列表</b>：根据 activityId 种子值确定性地生成 80 条商品，支持状态过滤、关键词搜索、游标/分页翻页</li>
 *   <li><b>活动创建/编辑</b>：直接返回成功响应，不执行实际写入</li>
 *   <li><b>活动详情</b>：根据 activityId 种子值生成完整的活动详情 Map</li>
 *   <li><b>取消活动商品</b>：直接返回成功响应，透传请求参数</li>
 * </ul>
 *
 * <p>架构角色：Gateway 测试适配器（Test Double），所属领域：活动域。
 * 与真实网关的关系：实现同一 {@link DouyinActivityGateway} 接口，通过 Spring Profile 条件注解切换，
 * 保证业务层代码不感知 Mock 与真实的差异。</p>
 *
 * @see DouyinActivityGateway
 * @see TestMockActivityProductSupport
 */
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinActivityGateway implements DouyinActivityGateway {

    /**
     * 查询 Mock 活动列表，支持状态过滤、关键词搜索、排序和分页。
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：调用 {@link #buildTestActivities()} 生成 36 条 Mock 活动数据</li>
     *   <li>第二步：按 status（活动状态）过滤，null 或 0 表示不过滤</li>
     *   <li>第三步：按 activityInfo（关键词）模糊匹配活动 ID 或名称</li>
     *   <li>第四步：按 searchType 和 sortType 排序（searchType=1 按报名开始时间，=2 按报名结束时间，默认按活动开始时间；sortType=0 升序，其他降序）</li>
     *   <li>第五步：按 page 和 pageSize 执行内存分页，返回分页结果</li>
     * </ol>
     *
     * @param query 活动列表查询条件，包含 status、activityInfo、searchType、sortType、page、pageSize
     * @return 包含成功标志、Mock 机构 ID（11111111）、总条数和当前页数据的结果对象
     */
    @Override
    public ActivityListResult listActivities(ActivityListQuery query) {
        // 第一步：生成 36 条 Mock 活动数据
        List<ActivityItem> source = buildTestActivities();

        // 第二步：按状态过滤 -> 第三步：按关键词过滤 -> 第四步：排序
        List<ActivityItem> filtered = source.stream()
                .filter(item -> query.status() == null || query.status() == 0 || item.status() == query.status())
                .filter(item -> matchesKeyword(item, query.activityInfo()))
                .sorted(buildComparator(query.searchType(), query.sortType()))
                .toList();

        // 第五步：计算分页参数并截取当前页数据
        long page = query.page() == null || query.page() < 1 ? 1 : query.page();
        long pageSize = query.pageSize() == null || query.pageSize() < 1 ? 20 : query.pageSize();
        int from = (int) ((page - 1) * pageSize);
        int to = Math.min(from + (int) pageSize, filtered.size());
        List<ActivityItem> pageList = from >= filtered.size() ? List.of() : filtered.subList(from, to);

        return new ActivityListResult(true, 11111111L, filtered.size(), pageList);
    }

    /**
     * 查询活动下的 Mock 商品列表，支持状态过滤、关键词搜索、游标/分页翻页。
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：将 activityId 解析为种子值（seed），计算 seedOffset 和 shopOffset 使数据确定性可重复</li>
     *   <li>第二步：循环生成 80 条 Mock 商品，每条包含价格、佣金、店铺、状态等完整字段</li>
     *   <li>第三步：按 status（商品状态）和 productInfo（关键词）过滤</li>
     *   <li>第四步：根据 retrieveMode 选择翻页模式（0=页码翻页，1=游标翻页），截取当前页数据</li>
     *   <li>第五步：返回结果，包含 nextCursor 用于前端连续加载</li>
     * </ol>
     *
     * @param query 活动商品查询条件，包含 activityId、status、productInfo、count、retrieveMode、page、cursor
     * @return 包含成功标志、活动种子值、总数、游标和当前页商品列表的结果对象
     */
    @Override
    public ActivityProductListResult listActivityProducts(ActivityProductListQuery query) {
        // 第一步：解析 activityId 为种子值，用于确定性生成数据
        long activitySeed = asLong(query.activityId(), 0L);
        int seedOffset = (int) (activitySeed % 37);
        int shopOffset = (int) (activitySeed % 9);
        // 第二步：循环生成 80 条确定性 Mock 商品数据
        List<ActivityProductItem> all = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            int rank = i + seedOffset;
            int itemStatus = resolveMockProductStatus(activitySeed, rank);
            long productId = 900000L + rank + activitySeed * 100L;
            long price = 9900L + rank * 13L + seedOffset * 17L;
            int shopIndex = (rank + shopOffset) % 9;
            all.add(new ActivityProductItem(
                    productId,
                    buildProductTitle(i, rank),
                    "https://example.com/test-product-" + i + ".png",
                    price,
                    formatPriceText(price),
                    10L + (rank % 20),
                    1000L + rank * 11L + seedOffset * 7L,
                    1000L + (rank % 20) * 100L,
                    (10 + (rank % 20)) + "%",
                    rank % 2,
                    rank % 2 == 1 ? "阶梯佣金" : "固定服务费率",
                    rank % 2 == 1 ? String.valueOf(8 + (seedOffset % 4)) : null,
                    rank % 2 == 1 ? Long.valueOf(6 + (seedOffset % 5)) : null,
                    rank % 3 == 0,
                    rank % 5 != 0,
                    200L + rank * 3L + seedOffset * 5L,
                    800000L + shopIndex,
                    buildShopName(shopIndex),
                    "4." + (70 + (rank % 20)),
                    itemStatus,
                    productStatusText(itemStatus),
                    rank % 2 == 0 ? "美妆个护" : "女装穿搭",
                    String.valueOf(Math.max(1000 - rank, 0)),
                    i % 2 == 0 ? "满199减20" : "满299减40",
                    mockActivityStartDate(),
                    mockActivityEndDate(),
                    mockPromotionStartDate(),
                    mockPromotionEndDate(),
                    "https://example.com/test-detail/" + productId,
                    String.valueOf(46128341673481000L + (productId % 1000)),
                    Map.of("origin_colonel_buyin_id", String.valueOf(46128341673481000L + (productId % 1000)))
            ));
        }
        // 第三步：按状态和关键词过滤商品
        List<ActivityProductItem> filtered = all.stream()
                .filter(item -> query.status() == null || item.status() == query.status())
                .filter(item -> {
                    if (query.productInfo() == null || query.productInfo().isBlank()) {
                        return true;
                    }
                    // 模糊匹配商品标题或商品 ID
                    String keyword = query.productInfo().trim().toLowerCase(java.util.Locale.ROOT);
                    return item.title().toLowerCase(java.util.Locale.ROOT).contains(keyword)
                            || String.valueOf(item.productId()).contains(keyword);
                })
                .toList();

        // 第四步：根据翻页模式计算起始位置
        int pageSize = Math.min(Math.max(query.count() == null ? 20 : query.count(), 1), 20);
        long mode = query.retrieveMode() == null ? 1L : query.retrieveMode();
        // mode=0 使用页码翻页，mode=1（默认）使用游标翻页
        int from = mode == 0L
                ? (int) (((query.page() == null || query.page() < 1 ? 1 : query.page()) - 1) * pageSize)
                : parseCursor(query.cursor());

        // 第五步：截取当前页数据，生成下一页游标
        int to = Math.min(from + pageSize, filtered.size());
        List<ActivityProductItem> items = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);

        // 返回结果：游标模式返回 nextCursor，页码模式返回 totalCount
        return new ActivityProductListResult(true, activitySeed, 111111L,
                mode == 0L ? Long.valueOf(filtered.size()) : null, nextCursor, items);
    }

    /**
     * Mock 创建或更新活动。
     * <p>直接返回成功响应，不执行实际写入。activityId 为 null 时使用默认值 900001。</p>
     *
     * @param command 活动创建/编辑命令，包含 activityId 和 activityName
     * @return 包含 code、msg 和 data（activity_id、activity_name）的 Mock 响应 Map
     */
    @Override
    public Map<String, Object> createOrUpdate(ActivityApi.ActivityCreateOrUpdateCommand command) {
        long activityId = command.activityId() == null ? 900001L : command.activityId();
        return Map.of(
                "code", 10000,
                "msg", "success",
                "data", Map.of(
                        "activity_id", activityId,
                        "activity_name", command.activityName() == null ? "Mock活动" : command.activityName()
                )
        );
    }

    /**
     * Mock 取消活动商品。
     * <p>直接返回成功响应，透传请求中的 appId 和 payload。</p>
     *
     * @param appId   应用 ID，null 时使用 "test-app"
     * @param payload 请求参数 Map，null 时使用空 Map
     * @return 包含 code、msg 和 data（app_id、payload）的 Mock 响应 Map
     */
    @Override
    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        return Map.of(
                "code", 10000,
                "msg", "success",
                "data", Map.of(
                        "app_id", appId == null ? "test-app" : appId,
                        "payload", payload == null ? Map.of() : payload
                )
        );
    }

    /**
     * Mock 查询活动详情。
     * <p>根据 activityId 种子值生成包含 colonel_buyin_id、活动名称、店铺信息、佣金率、
     * 推广时间等完整活动详情的 Map。</p>
     *
     * @param appId      应用 ID（未使用，保留接口兼容）
     * @param activityId 活动 ID 字符串，用于确定性生成 Mock 详情
     * @return 包含双层 data 嵌套结构的 Mock 活动详情 Map（{@code {data: {data: detail}}}
     */
    @Override
    public Map<String, Object> activityDetail(String appId, String activityId) {
        // 将 activityId 解析为种子值，用于确定性生成 colonel_buyin_id
        long seed = activityId == null || activityId.isBlank() ? 0L : asLong(activityId, Math.abs(activityId.hashCode()));
        long colonelBuyinId = 46128341673481000L + (Math.floorMod(seed, 100000));

        // 构造双层嵌套的活动详情（模拟抖店 API 的 data.data 结构）
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("colonel_buyin_id", colonelBuyinId);
        detail.put("colonelBuyinId", colonelBuyinId);
        detail.put("activity_name", "Mock活动-" + activityId);
        detail.put("activityName", "Mock活动-" + activityId);
        detail.put("shop_id", 800001L);
        detail.put("shop_name", "Mock店铺");
        detail.put("commission_rate", "10");
        detail.put("service_rate", "5");
        detail.put("activity_start_time", mockActivityStartDate() + " 00:00:00");
        detail.put("activity_end_time", mockActivityEndDate() + " 23:59:59");
        detail.put("status_text", "推广中");
        return Map.of("data", Map.of("data", detail));
    }

    /**
     * Mock 创建或更新活动（通过 ActivityMutateCommand）。
     * <p>直接返回成功响应，activityId 为 null 时使用默认值 12345。</p>
     *
     * @param command 活动变更命令
     * @return 包含 code、msg 和 data（activity_id）的 Mock 响应 Map
     */
    @Override
    public Map<String, Object> createOrUpdateActivity(ActivityMutateCommand command) {
        long id = command.activityId() == null ? 12345L : command.activityId();
        return Map.of("code", 10000, "msg", "success", "data", Map.of("activity_id", id));
    }

    /**
     * 构建 36 条 Mock 活动数据。
     * <p>每条活动的 ID 从 100001 开始递增，状态按 {1,2,3,4,5,7} 循环分配，
     * 类目和名称也分别按预设数组循环取值，确保数据分布均匀且可复现。</p>
     *
     * @return 36 条 Mock 活动项的列表
     */
    private List<ActivityItem> buildTestActivities() {
        List<ActivityItem> activities = new ArrayList<>();
        int[] statusCycle = {1, 2, 3, 4, 5, 7};
        String[] categoryCycle = {"美妆个护", "女装穿搭", "家用电器", "食品饮料", "母婴用品", "运动户外"};
        String[] nameCycle = {"春季上新", "夏季爆款", "秋季清仓", "双11预热", "年货节", "新品招商"};
        for (int i = 1; i <= 36; i++) {
            int status = statusCycle[(i - 1) % statusCycle.length];
            int month = ((i - 1) % 9) + 1;
            int day = ((i - 1) % 20) + 1;
            activities.add(new ActivityItem(
                    100000L + i,
                    nameCycle[(i - 1) % nameCycle.length] + "-渠道演示活动-" + i,
                    String.format("2026-%02d-%02d 00:00:00", month, day),
                    String.format("2026-%02d-%02d 23:59:59", month, Math.min(day + 10, 28)),
                    status,
                    ActivityStatusResolver.toText(status),
                    String.format("2026-%02d-%02d 00:00:00", Math.max(month - 1, 1), Math.max(day - 5, 1)),
                    String.format("2026-%02d-%02d 23:59:59", month, Math.max(day - 1, 1)),
                    Map.of("一级类目", categoryCycle[(i - 1) % categoryCycle.length]),
                    46128341673481000L + i
            ));
        }
        return activities;
    }

    /**
     * 判断活动项是否匹配关键词（模糊匹配活动 ID 或名称）。
     *
     * @param item         待匹配的活动项
     * @param activityInfo 搜索关键词
     * @return {@code true} 表示匹配，关键词为空时始终返回 true
     */
    private boolean matchesKeyword(ActivityItem item, String activityInfo) {
        if (activityInfo == null || activityInfo.isBlank()) {
            return true;
        }
        String keyword = activityInfo.trim().toLowerCase(java.util.Locale.ROOT);
        return String.valueOf(item.activityId()).toLowerCase(java.util.Locale.ROOT).contains(keyword)
                || item.activityName().toLowerCase(java.util.Locale.ROOT).contains(keyword);
    }

    /**
     * 构建活动列表的排序比较器。
     * <p>searchType=1 按报名开始时间排序，searchType=2 按报名结束时间排序，默认按活动开始时间排序。
     * sortType=0 升序，其他值降序。</p>
     *
     * @param searchType 搜索时间类型（1=报名开始, 2=报名结束, null=活动开始）
     * @param sortType   排序方向（0=升序, null 或其他=降序）
     * @return 活动项比较器
     */
    private Comparator<ActivityItem> buildComparator(Long searchType, Long sortType) {
        Comparator<ActivityItem> comparator;
        if (searchType != null && searchType == 1L) {
            comparator = Comparator.comparing(ActivityItem::applicationStartTime, Comparator.nullsLast(String::compareTo));
        } else if (searchType != null && searchType == 2L) {
            comparator = Comparator.comparing(ActivityItem::applicationEndTime, Comparator.nullsLast(String::compareTo));
        } else {
            comparator = Comparator.comparing(ActivityItem::activityStartTime, Comparator.nullsLast(String::compareTo));
        }
        return sortType != null && sortType == 0L ? comparator : comparator.reversed();
    }

    /**
     * 构建 Mock 商品标题，由前缀（商品类型）和后缀（款式类型）组合而成。
     *
     * @param index 商品序号（1~80）
     * @param rank  商品排名（含种子偏移）
     * @return 格式如 "活动演示商品-高佣款-1" 的商品标题
     */
    private String buildProductTitle(int index, int rank) {
        String[] prefixes = {"活动演示商品", "招商主推商品", "寄样联动商品", "订单归因商品"};
        String[] suffixes = {"高佣款", "基础款", "冲量款", "复购款"};
        return prefixes[index % prefixes.length] + "-" + suffixes[rank % suffixes.length] + "-" + index;
    }

    /**
     * 根据店铺索引返回 Mock 店铺名称（9 个品类店铺循环）。
     *
     * @param shopIndex 店铺索引
     * @return 店铺名称，如 "活动店铺-美妆馆"
     */
    private String buildShopName(int shopIndex) {
        String[] shopNames = {
                "活动店铺-美妆馆",
                "活动店铺-女装馆",
                "活动店铺-母婴馆",
                "活动店铺-数码馆",
                "活动店铺-食品馆",
                "活动店铺-家清馆",
                "活动店铺-家电馆",
                "活动店铺-日用馆",
                "活动店铺-运动馆"
        };
        return shopNames[Math.floorMod(shopIndex, shopNames.length)];
    }

    /**
     * 将活动状态码转换为中文状态文本。
     *
     * @param status 活动状态码
     * @return 中文状态描述，未知状态返回 "任意状态"
     */
}
