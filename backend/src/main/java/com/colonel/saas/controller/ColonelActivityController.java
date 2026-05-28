package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 团长活动管理控制器。
 * <p>
 * 负责团长活动列表查询及活动下商品列表查询。本控制器对接抖店开放平台的活动 API，
 * 并提供本地快照机制以减少对上游接口的依赖，提升查询性能。
 * </p>
 *
 * <ul>
 *   <li>查询机构创建的团长活动列表（带 60 秒短缓存）</li>
 *   <li>查询指定活动下的商品列表（支持本地快照优先、强制刷新上游、游标分页等多种模式）</li>
 *   <li>将抖店 API 错误码映射为业务友好的中文提示</li>
 * </ul>
 *
 * <p><strong>API 路径前缀：</strong>{@code /colonel/activities}</p>
 * <p><strong>架构角色：</strong>表现层（Controller），负责活动管理的 HTTP 入口处理，
 * 委托 {@link DouyinActivityGateway}、{@link DouyinProductGateway} 与抖店上游交互，
 * 委托 {@link ProductService} 管理本地商品快照。</p>
 * <p><strong>访问控制：</strong>类级别限制为 {@code BIZ_LEADER}、{@code ADMIN}、{@code COLONEL_LEADER}。</p>
 *
 * @see DouyinActivityGateway
 * @see DouyinProductGateway
 * @see ProductService
 * @see ShortTtlCacheService
 */
@Validated
@RestController
@Tag(name = "团长活动管理", description = "团长活动列表及活动下商品查询接口。")
@RequestMapping("/colonel/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.ADMIN, RoleCodes.COLONEL_LEADER})
public class ColonelActivityController extends BaseController {

    /** 活动列表缓存 TTL，60 秒过期，避免频繁调用上游接口 */
    private static final Duration ACTIVITY_LIST_CACHE_TTL = Duration.ofSeconds(60);
    /** 活动列表 Redis 缓存键前缀 */
    private static final String ACTIVITY_LIST_CACHE_PREFIX = "activities:list:";

    /** 抖店活动网关，负责调用抖店活动相关 API */
    private final DouyinActivityGateway douyinActivityGateway;
    /** 抖店商品网关，负责调用抖店商品相关 API */
    private final DouyinProductGateway douyinProductGateway;
    /** 商品服务，负责本地快照的维护和业务视图构建 */
    private final ProductService productService;
    /** 短 TTL 缓存服务，提供基于 Redis 的短期缓存能力 */
    private final ShortTtlCacheService shortTtlCacheService;

    /**
     * 构造注入所有依赖。
     *
     * @param douyinActivityGateway 抖店活动网关
     * @param douyinProductGateway  抖店商品网关
     * @param productService        商品服务
     * @param shortTtlCacheService  短 TTL 缓存服务
     */
    public ColonelActivityController(
            DouyinActivityGateway douyinActivityGateway,
            DouyinProductGateway douyinProductGateway,
            ProductService productService,
            ShortTtlCacheService shortTtlCacheService) {
        this.douyinActivityGateway = douyinActivityGateway;
        this.douyinProductGateway = douyinProductGateway;
        this.productService = productService;
        this.shortTtlCacheService = shortTtlCacheService;
    }

    /**
     * 查询团长活动列表。
     * <p>
     * 该接口服务于业务页面的活动筛选，不是原始的抖店联调接口。
     * 使用 60 秒短缓存以减少对上游 API 的调用频率。
     * </p>
     * <ol>
     *   <li>根据查询参数拼接缓存键</li>
     *   <li>先尝试从 Redis 短缓存获取结果</li>
     *   <li>缓存未命中时，调用 {@link DouyinActivityGateway#listActivities} 获取上游数据并缓存</li>
     *   <li>捕获 {@link DouyinApiException}，通过 {@link #mapActivityError} 映射为业务异常</li>
     * </ol>
     *
     * @param status       活动状态（上游 SDK 定义的枚举值）
     * @param searchType   搜索类型
     * @param sortType     排序类型
     * @param page         页码，从 1 开始
     * @param pageSize     每页条数，最大 20
     * @param activityInfo 活动信息关键字（可选）
     * @param appId        抖音应用 appId（可选，不传使用默认配置）
     * @return 包含活动列表的分页结果
     * @throws BusinessException 当抖店 API 调用失败或参数不合法时
     */
    @Operation(summary = "团长活动列表", description = "查询机构创建的团长活动列表。该接口服务于业务页活动筛选，不是原始联调接口。")
    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @Parameter(description = "活动状态。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "status", defaultValue = "0") Integer status,
            @Parameter(description = "搜索类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "searchType", defaultValue = "0") Long searchType,
            @Parameter(description = "排序类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "sortType", defaultValue = "1") Long sortType,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(name = "page", defaultValue = "1") @Min(1) Long page,
            @Parameter(description = "每页条数。当前仍保留 pageSize 命名，后续是否统一为 size 需单独决策。") @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(20) Long pageSize,
            @Parameter(description = "活动信息关键字。") @RequestParam(name = "activityInfo", required = false) String activityInfo,
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId) {
        try {
            // Step 1: 根据所有查询参数拼接唯一缓存键
            String cacheKey = ACTIVITY_LIST_CACHE_PREFIX + cacheKey(status, searchType, sortType, page, pageSize, activityInfo, appId);
            // Step 2: 使用短 TTL 缓存包装上游调用，缓存未命中时执行 lambda 查询
            return ok(shortTtlCacheService.get(cacheKey, ACTIVITY_LIST_CACHE_TTL, () -> douyinActivityGateway.listActivities(
                    new DouyinActivityGateway.ActivityListQuery(
                            appId, status, searchType, sortType, page, pageSize, activityInfo
                    )
            ).toMap()));
        } catch (DouyinApiException e) {
            // Step 3: 将抖店 API 错误映射为业务异常并抛出
            throw mapActivityError(e);
        }
    }

    /**
     * 查询指定活动下的商品列表。
     * <p>
     * 采用"本地快照优先"策略：优先从数据库快照构建业务视图，
     * 当本地无快照数据时回退到上游 API 查询并同步落库。
     * 支持 refresh=true 强制刷新上游数据。
     * </p>
     * <ol>
     *   <li>判断 refresh 参数：若非强制刷新且本地已有快照，直接从数据库构建视图返回</li>
     *   <li>若强制刷新（refresh=true），调用 {@link ProductService#refreshActivitySnapshots} 从上游拉取并更新快照</li>
     *   <li>若本地无快照，调用 {@link DouyinProductGateway#queryActivityProducts} 查询上游后落库</li>
     *   <li>最后从数据库快照构建业务视图并返回</li>
     *   <li>捕获 {@link DouyinApiException}，通过 {@link #mapProductError} 映射为业务异常</li>
     * </ol>
     *
     * @param activityId      团长活动 ID
     * @param searchType      搜索类型（上游 SDK 定义）
     * @param sortType        排序类型（上游 SDK 定义）
     * @param count           每次查询条数，最大 20
     * @param cooperationInfo 合作信息关键字（可选）
     * @param cooperationType 合作类型
     * @param productInfo     商品信息关键字（可选）
     * @param bizStatus       业务状态筛选，如 PENDING_AUDIT、APPROVED（可选）
     * @param status          商品状态（上游 SDK 定义，可选）
     * @param retrieveMode    拉取模式
     * @param cursor          游标，继续翻页时使用（可选）
     * @param page            页码（仅部分上游模式下使用）
     * @param appId           抖音应用 appId（可选）
     * @param refresh         是否强制刷新上游数据
     * @param sortBy          本地视图排序方式：default（置顶优先）/ latest（同步时间倒序）
     * @param goodsTags       货品标签，多选用逗号分隔（可选）
     * @param productTags     商品标签，多选用逗号分隔（可选）
     * @return 包含活动商品列表的结果
     * @throws BusinessException 当抖店 API 调用失败或参数不合法时
     */
    @Operation(summary = "活动商品列表", description = "查询团长活动下的商品列表。优先使用本地快照构造业务视图，未命中时回退到上游接口后再落库。")
    @GetMapping("/{activityId}/products")
    public ApiResult<Map<String, Object>> listProducts(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId,
            @Parameter(description = "搜索类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "searchType", defaultValue = "4") Long searchType,
            @Parameter(description = "排序类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "sortType", defaultValue = "1") Long sortType,
            @Parameter(description = "每次查询条数，最大 20。") @RequestParam(name = "count", defaultValue = "20") @Min(1) @Max(20) Integer count,
            @Parameter(description = "合作信息关键字。") @RequestParam(name = "cooperationInfo", required = false) String cooperationInfo,
            @Parameter(description = "合作类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "cooperationType", defaultValue = "0") Integer cooperationType,
            @Parameter(description = "商品信息关键字。") @RequestParam(name = "productInfo", required = false) String productInfo,
            @Parameter(description = "业务状态筛选，如 PENDING_AUDIT、APPROVED、ASSIGNED。") @RequestParam(name = "bizStatus", required = false) String bizStatus,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "status", required = false) Integer status,
            @Parameter(description = "拉取模式。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(name = "retrieveMode", defaultValue = "1") Long retrieveMode,
            @Parameter(description = "游标，继续翻页时使用。") @RequestParam(name = "cursor", required = false) String cursor,
            @Parameter(description = "页码。当前仅在部分上游模式下使用。") @RequestParam(name = "page", required = false) @Min(1) Long page,
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "是否绕过本地快照并强制刷新上游数据。") @RequestParam(name = "refresh", defaultValue = "false") Boolean refresh,
            @Parameter(description = "本地视图排序：default（置顶/转链/佣金优先）/ latest（同步时间倒序）。") @RequestParam(name = "sortBy", required = false) String sortBy,
            @Parameter(description = "货品标签，多选时用英文逗号分隔。") @RequestParam(name = "goodsTags", required = false) String goodsTags,
            @Parameter(description = "商品标签，多选时用英文逗号分隔。") @RequestParam(name = "productTags", required = false) String productTags) {
        try {
            // Step 1: 非强制刷新且本地已有快照时，直接从数据库构建视图
            if (!Boolean.TRUE.equals(refresh) && productService.hasActivitySnapshots(activityId)) {
                return ok(productService.buildActivityProductListViewFromDb(
                        activityId,
                        count,
                        cursor,
                        productInfo,
                        bizStatus,
                        status,
                        sortBy,
                        goodsTags,
                        productTags
                ));
            }
            // Step 2: 构建上游查询请求对象
            DouyinProductGateway.ActivityProductQueryRequest queryRequest =
                    new DouyinProductGateway.ActivityProductQueryRequest(
                            appId,
                            activityId,
                            searchType,
                            sortType,
                            count,
                            cooperationInfo,
                            cooperationType,
                            productInfo,
                            status,
                            retrieveMode,
                            cursor,
                            page
                    );
            if (Boolean.TRUE.equals(refresh)) {
                // Step 3a: 强制刷新模式，从上游拉取全量数据更新本地快照
                productService.refreshActivitySnapshots(queryRequest);
            } else {
                // Step 3b: 本地无快照，首次从上游查询并落库
                DouyinProductGateway.ActivityProductListResult result =
                        douyinProductGateway.queryActivityProducts(queryRequest);
                productService.upsertSnapshots(activityId, result.items());
            }
            // Step 4: 从更新后的本地快照构建业务视图并返回
            return ok(productService.buildActivityProductListViewFromDb(
                    activityId,
                    count,
                    cursor,
                    productInfo,
                    bizStatus,
                    status,
                    sortBy,
                    goodsTags,
                    productTags
            ));
        } catch (DouyinApiException e) {
            // Step 5: 将抖店 API 错误映射为业务异常
            throw mapProductError(e);
        }
    }

    /**
     * 将活动查询的抖店 API 异常映射为业务异常。
     * <p>
     * 根据抖店错误码和子码（subCode）组合，提供中文友好的错误提示。
     * 常见映射：50002-4197（未授权）、50002-4200（账号异常）、40004-257（参数不合法）等。
     * </p>
     *
     * @param e 抖店 API 异常
     * @return 包含中文提示的业务异常
     */
    private BusinessException mapActivityError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return BusinessException.stateInvalid("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return BusinessException.stateInvalid("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 40004 && subCode.contains("257")) {
            return BusinessException.param("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return BusinessException.external("抖店服务异常，请稍后重试");
        }
        return BusinessException.external("团长活动查询失败: " + e.getErrorMsg());
    }

    /**
     * 将活动商品查询的抖店 API 异常映射为业务异常。
     * <p>
     * 与 {@link #mapActivityError} 类似，但额外处理商品特有的错误码，
     * 如 50002-4097（每页最多 20 条）、50002-8197（不允许继续翻页）。
     * </p>
     *
     * @param e 抖店 API 异常
     * @return 包含中文提示的业务异常
     */
    private BusinessException mapProductError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4097")) {
            return BusinessException.param("每页最多查询 20 条商品");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("8197")) {
            return BusinessException.stateInvalid("不允许继续翻页，请使用游标模式加载更多");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return BusinessException.stateInvalid("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return BusinessException.stateInvalid("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("257")) {
            return BusinessException.param("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return BusinessException.external("抖店服务异常，请稍后重试");
        }
        return BusinessException.external("活动商品查询失败: " + e.getErrorMsg());
    }

    /**
     * 将多个参数值拼接为缓存键字符串。
     * <p>
     * 各值之间用 {@code |} 分隔，null 值视为空字符串。
     * </p>
     *
     * @param values 需要拼接的参数值
     * @return 拼接后的缓存键
     */
    private String cacheKey(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }
}
