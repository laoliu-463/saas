package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.Product;
import com.colonel.saas.dto.product.ProductFilterOptionItem;
import com.colonel.saas.dto.product.ProductFilterOptionsDTO;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.dto.product.QuickSampleApplyResponse;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.domain.product.application.ProductLibraryPageQueryService;
import com.colonel.saas.domain.product.application.ProductQuickSampleApplicationService;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ProductSampleSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品管理控制器 (god controller - @Deprecated, 不再 DDD 切片).
 *
 * <p><strong>当前状态 (2026-07-14):</strong></p>
 * <ul>
 *   <li>1236 行 / 21 endpoint / 29 内部引用, 已标 @Deprecated (since 2026-04-24)</li>
 *   <li>不切理由 (与 ColonelActivityProductController / DouyinController / OrderController 一致处置):
 *     <ol>
 *       <li>@Deprecated 兼容过渡层: 新前端已迁移至 /colonel/ 命名空间, 本 controller 仅平滑过渡</li>
 *       <li>21 endpoint 数量大, 跨 6+ 业务簇 (商品库 / 类目 / 选品 / 详情 / 转链 / 跟进)</li>
 *       <li>29 内部引用, 跨 ProductService / ProductQuickSampleApplicationService /
 *           ProductSampleSettingService / ColonelPartnerSyncService 等</li>
 *       <li>未来 forRemoval=true 时应直接删除, 不再 DDD 切片</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>本控制器提供旧版商品库的查询、筛选、快速寄样、转链、审核、达人跟进等接口，
 * 已被标记为 {@code @Deprecated}，新功能请优先使用团长活动商品主链路接口
 * （{@code /colonel/activities/{activityId}/products}）。
 *
 * <ul>
 *   <li>商品库分页查询与多维度筛选（类目、佣金、销量、标签等 40+ 筛选条件）</li>
 *   <li>商品库弹窗式快速寄样申请（私海达人多选逐个创建）</li>
 *   <li>商品类目与动态筛选项聚合</li>
 *   <li>选品候选分页查询</li>
 *   <li>商品详情、绑定活动、分配招商、审核、转链等操作（均已废弃）</li>
 *   <li>商品推广记录查询与达人跟进管理</li>
 * </ul>
 *
 * <p><b>架构角色：</b>REST 控制器层，接收前端商品库页面请求，委托 {@link ProductService}、
 * {@link ProductQuickSampleApplicationService}、{@link ColonelPartnerSyncService} 完成业务逻辑。
 *
 * <p><b>访问控制：</b>需要 {@link RequireRoles} 注解限定业务角色（BIZ_LEADER / BIZ_STAFF /
 * CHANNEL_LEADER / CHANNEL_STAFF），部分操作进一步限制为特定角色。
 *
 * <p><b>迁移说明：</b>本控制器为旧版商品管理接口的兼容过渡层。
 * 新的前端已迁移至 {@code /colonel/} 命名空间下的主链路接口。
 * 各废弃接口均标注了对应的迁移目标路径。
 *
 * @see ProductService 商品核心业务服务
 * @see ProductQuickSampleApplicationService 快速寄样应用层
 * @see ColonelPartnerSyncService 团长合作方同步服务
 * @see BaseController 控制器基类，提供 ok()、okPage() 等统一响应封装
 */
@Validated
@Tag(name = "商品管理（已废弃）", description = "旧版商品兼容接口，仅用于平滑过渡。请优先使用团长活动商品主链路接口。")
@RestController
@RequestMapping("/products")
@RequirePermission("product:access")
@Deprecated(since = "2026-04-24", forRemoval = false)
public class ProductController extends BaseController {

    /** 商品核心业务服务，提供分页查询、筛选、详情、审核、转链、跟进等全部商品操作。 */
    private final ProductService productService;

    /** 快速寄样应用层，处理商品库弹窗式快速寄样申请的创建与校验。 */
    private final ProductQuickSampleApplicationService productQuickSampleApplicationService;

    /** 商品库分页查询应用层。 */
    private final ProductLibraryPageQueryService productLibraryPageQueryService;

    /** 团长合作方同步服务，提供团长合作方列表查询（已废弃的筛选项接口使用）。 */
    private final ColonelPartnerSyncService colonelPartnerSyncService;

    /** 商品寄样规则服务，读写商品运营状态中的寄样设置扩展数据。 */
    private final ProductSampleSettingService productSampleSettingService;

    /** 当前用户权限检查器，统一处理角色编码解析与匹配。 */
    private final CurrentUserPermissionChecker currentUserPermissionChecker;

    public ProductController(
            ProductService productService,
            ProductQuickSampleApplicationService productQuickSampleApplicationService,
            ProductLibraryPageQueryService productLibraryPageQueryService,
            ColonelPartnerSyncService colonelPartnerSyncService,
            ProductSampleSettingService productSampleSettingService,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        this.productService = productService;
        this.productQuickSampleApplicationService = productQuickSampleApplicationService;
        this.productLibraryPageQueryService = productLibraryPageQueryService;
        this.colonelPartnerSyncService = colonelPartnerSyncService;
        this.productSampleSettingService = productSampleSettingService;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
    }

    /**
     * 查询商品寄样设置。
     *
     * <p>relationId 对应商品库列表返回的 {@code product_snapshot.id}，与前端
     * {@code fetchSampleSetting} 使用的路径保持一致。</p>
     */
    @Operation(summary = "查询商品寄样设置", description = "读取商品当前保存的寄样规则及兼容字段。")
    @GetMapping("/{relationId}/sample-setting")
    public ApiResult<Map<String, Object>> getSampleSetting(
            @Parameter(description = "商品关系 ID，使用 product_snapshot.id。") @PathVariable UUID relationId) {
        return ok(productSampleSettingService.get(relationId));
    }

    /**
     * 保存商品寄样设置。
     *
     * <p>只更新寄样规则，不改变商品审核、上架和业务状态；请求体使用 Map 是为了
     * 兼容历史寄样字段与当前表单字段，具体字段校验在服务层完成。</p>
     */
    @Operation(summary = "保存商品寄样设置", description = "保存免费寄样开关和达人寄样门槛。")
    @PutMapping("/{relationId}/sample-setting")
    public ApiResult<Map<String, Object>> updateSampleSetting(
            @Parameter(description = "商品关系 ID，使用 product_snapshot.id。") @PathVariable UUID relationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样设置。支持 supportFreeSample、hasSampleThreshold、minWindowSales30d、minSales30d、minFans、minTalentLevel 等字段；达人带货等级范围为 LV0-LV7。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"supportFreeSample\":true,\"hasSampleThreshold\":true,\"minSales30d\":50000,\"minTalentLevel\":1}"))
            )
            @RequestBody Map<String, Object> request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productSampleSettingService.update(relationId, request, userId, deptId));
    }

    /**
     * 编辑商品推广补充信息。
     *
     * <p>该接口只更新商品运营状态中的 audit_payload，不改变审核、上架和官方推广状态。
     * 允许编辑的字段由商品服务统一校验，专属价金额使用元并保留两位小数。</p>
     */
    @Operation(summary = "编辑商品推广补充信息", description = "更新专属价金额、专属价说明、投流开关、奖励说明、参与要求和本系统推广时间覆盖。")
    @RequirePermission("product:update-product-supplement")
    @PutMapping("/{relationId}")
    public ApiResult<Product> updateProductSupplement(
            @Parameter(description = "商品关系 ID，使用 product_snapshot.id。") @PathVariable UUID relationId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.updateAuditSupplement(relationId, request, userId, deptId));
    }

    /**
     * 商品库分页查询。
     *
     * <p>查询已从选品库沉淀到共享商品库的商品列表，对全员可见。
     * 支持 40+ 筛选条件，包括：关键字、类目、佣金、销量区间、转链状态、
     * 联盟推广状态、标签、合作方、价格区间、招商活动等多维度组合筛选。
     *
     * @param page              页码，从 1 开始
     * @param size              每页条数。商品库主列表支持滚动批量加载，不再限制为 100
     * @param status            商品状态（可选），具体取值含义请联系产品
     * @param keyword           商品关键字，可匹配商品名称、商品 ID、店铺（可选）
     * @param shopKeyword       店铺/合作方名称关键字（可选）
     * @param categoryName      抖音同步类目关键字，单选兼容（可选）
     * @param categories        商品类目多选，英文逗号分隔（可选）
     * @param activityId        来源活动 ID（可选）
     * @param assigneeId        招商组长用户 ID（可选）
     * @param serviceFee        服务费率区间：gt20/10_20/lt10（可选）
     * @param supportsAds       是否支持投流：1/0（可选）
     * @param salesRange        近 30 天销量区间：lt100/100_999/1k_29k/gte30000（可选）
     * @param promotionLink     转链状态：PENDING/LINKED/FAILED（可选）
     * @param allianceStatus    联盟推广状态：promoting/pending_audit/rejected/terminated/canceled/expired（可选）
     * @param commission        佣金区间：gt20/10_20/lt10（可选）
     * @param hasSample         是否有寄样规则：1/0（可选）
     * @param assignee          负责人过滤：assigned/unassigned（可选）
     * @param systemTag         系统标签：high_commission/traffic/new/high_price（可选）
     * @param decision          推进判断：MAIN/SECONDARY/PAUSE/DROP/NONE（可选）
     * @param partnerId         合作方 ID；商家型为 shop_id，团长型为 colonel_buyin_id（可选）
     * @param partnerType       合作方类型：MERCHANT/COLONEL（可选）
     * @param sortBy            兼容旧参数，商品库固定按置顶优先 + 上游合作时间倒序（可选）
     * @param goodsTags         货品标签，多选时用英文逗号分隔（可选）
     * @param productTags       商品标签，多选时用英文逗号分隔（可选）
     * @param colonelName       团长名称关键字（可选）
     * @param published         是否已发布转链：1/0（可选）
     * @param cooperationType   合作类型（可选）
     * @param livePriceMin      直播价格下限（可选）
     * @param livePriceMax      直播价格上限（可选）
     * @param commissionMin     佣金率下限（可选）
     * @param commissionMax     佣金率上限（可选）
     * @param sampleSalesMin    寄样销售额下限（可选）
     * @param sampleSalesMax    寄样销售额上限（可选）
     * @param materialDownload  素材下载：1/0（可选）
     * @param exclusivePrice    专属价：1/0（可选）
     * @param productChain      商品链组：1/0（可选）
     * @param handCard          手卡：1/0（可选）
     * @param doubleCommission  双佣金：1/0（可选）
     * @param notInLibrary      仅未加入货盘：1/0（可选）
     * @param dedup             选品去重：1/0（可选）
     * @param recruitActivityId 招商活动 ID（可选）
     * @param recruitActivityName 招商活动名称关键字（可选）
     * @param listed            是否已挂车：1/0（可选）
     * @param freeSample        是否有免费样：1/0（可选）
     * @param productId         商品 ID，精确匹配（可选）
     * @return 分页商品列表
     */
    @Operation(summary = "商品库分页", description = "查询已从选品库沉淀到共享商品库的商品列表，对全员可见。")
    @RequirePermission("product:page")
    @GetMapping
    public ApiResult<PageResult<Product>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。商品库主列表支持滚动批量加载，允许超过 100。") @RequestParam(defaultValue = "20") @Min(1) long size,
            @Parameter(description = "无限下拉游标。首次查询不传，后续传上次返回的 nextCursor。") @RequestParam(name = "cursor", required = false) String cursor,
            @Parameter(description = "无限下拉单批条数，后端最大 500。") @RequestParam(name = "limit", required = false) @Min(1) @Max(500) Long limit,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status,
            @Parameter(description = "商品关键字，可匹配商品名称、商品 ID、店铺。") @RequestParam(required = false) String keyword,
            @Parameter(description = "店铺 / 合作方名称关键字。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "抖音同步类目关键字（单选兼容）。") @RequestParam(required = false) String categoryName,
            @Parameter(description = "商品类目多选，英文逗号分隔。") @RequestParam(required = false) String categories,
            @Parameter(description = "来源活动 ID。") @RequestParam(required = false) String activityId,
            @Parameter(description = "招商组长用户 ID。") @RequestParam(required = false) String assigneeId,
            @Parameter(description = "服务费率区间：gt20/10_20/lt10。") @RequestParam(required = false) String serviceFee,
            @Parameter(description = "是否支持投流：1/0。") @RequestParam(required = false) String supportsAds,
            @Parameter(description = "近 30 天销量区间：lt100/100_999/1k_29k/gte30000。") @RequestParam(required = false) String salesRange,
            @Parameter(description = "转链状态：PENDING/LINKED/FAILED。") @RequestParam(required = false) String promotionLink,
            @Parameter(description = "联盟推广状态：promoting/pending_audit/rejected/terminated/canceled/expired。") @RequestParam(required = false) String allianceStatus,
            @Parameter(description = "佣金区间：gt20/10_20/lt10。") @RequestParam(required = false) String commission,
            @Parameter(description = "是否有寄样规则：1/0。") @RequestParam(required = false) String hasSample,
            @Parameter(description = "负责人过滤：assigned/unassigned。") @RequestParam(required = false) String assignee,
            @Parameter(description = "系统标签：high_commission/traffic/new/high_price。") @RequestParam(required = false) String systemTag,
            @Parameter(description = "推进判断：MAIN/SECONDARY/PAUSE/DROP/NONE。") @RequestParam(required = false) String decision,
            @Parameter(description = "合作方 ID；商家型为 shop_id，团长型为 colonel_buyin_id。") @RequestParam(required = false) String partnerId,
            @Parameter(description = "合作方类型：MERCHANT/COLONEL。") @RequestParam(name = "partnerType", required = false) String partnerType,
            @Parameter(description = "兼容旧排序参数；商品库固定按置顶优先 + 上游合作时间倒序。") @RequestParam(name = "sortBy", required = false) String sortBy,
            @Parameter(description = "货品标签，多选时用英文逗号分隔。") @RequestParam(required = false) String goodsTags,
            @Parameter(description = "商品标签，多选时用英文逗号分隔。") @RequestParam(required = false) String productTags,
            @Parameter(description = "团长名称关键字。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "是否已发布转链：1/0。") @RequestParam(required = false) String published,
            @Parameter(description = "合作类型。") @RequestParam(required = false) String cooperationType,
            @Parameter(description = "直播价格下限。") @RequestParam(required = false) String livePriceMin,
            @Parameter(description = "直播价格上限。") @RequestParam(required = false) String livePriceMax,
            @Parameter(description = "佣金率下限。") @RequestParam(required = false) String commissionMin,
            @Parameter(description = "佣金率上限。") @RequestParam(required = false) String commissionMax,
            @Parameter(description = "寄样销售额下限。") @RequestParam(required = false) String sampleSalesMin,
            @Parameter(description = "寄样销售额上限。") @RequestParam(required = false) String sampleSalesMax,
            @Parameter(description = "素材下载：1/0。") @RequestParam(required = false) String materialDownload,
            @Parameter(description = "专属价：1/0。") @RequestParam(required = false) String exclusivePrice,
            @Parameter(description = "商品链组：1/0。") @RequestParam(required = false) String productChain,
            @Parameter(description = "手卡：1/0。") @RequestParam(required = false) String handCard,
            @Parameter(description = "双佣金：1/0。") @RequestParam(required = false) String doubleCommission,
            @Parameter(description = "仅未加入货盘：1/0。") @RequestParam(required = false) String notInLibrary,
            @Parameter(description = "选品去重：1/0。") @RequestParam(required = false) String dedup,
            @Parameter(description = "招商活动ID。") @RequestParam(required = false) String recruitActivityId,
            @Parameter(description = "招商活动名称关键字。") @RequestParam(required = false) String recruitActivityName,
            @Parameter(description = "是否已挂车：1/0。") @RequestParam(required = false) String listed,
            @Parameter(description = "是否有免费样：1/0。") @RequestParam(required = false) String freeSample,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(name = "productId", required = false) String productId) {
        ProductLibraryPageQuery query = new ProductLibraryPageQuery(
                keyword,
                status,
                shopKeyword,
                categoryName,
                categories,
                activityId,
                assigneeId,
                serviceFee,
                supportsAds,
                salesRange,
                promotionLink,
                allianceStatus,
                commission,
                hasSample,
                assignee,
                systemTag,
                decision,
                partnerId,
                partnerType,
                sortBy,
                goodsTags,
                productTags,
                colonelName,
                published,
                cooperationType,
                livePriceMin,
                livePriceMax,
                commissionMin,
                commissionMax,
                sampleSalesMin,
                sampleSalesMax,
                materialDownload,
                exclusivePrice,
                productChain,
                handCard,
                doubleCommission,
                notInLibrary,
                dedup,
                recruitActivityId,
                recruitActivityName,
                listed,
                freeSample,
                productId
        );
        if (limit != null || StringUtils.hasText(cursor)) {
            ProductLibraryCursorPage cursorPage =
                    productLibraryPageQueryService.getSelectedLibraryCursorPage(cursor, limit == null ? size : limit, query);
            if (cursorPage != null) {
                PageResult<Product> response = new PageResult<>();
                response.setTotal(0L);
                response.setPage(1L);
                response.setSize(cursorPage.limit());
                response.setRecords(cursorPage.records());
                response.setHasMore(cursorPage.hasMore());
                response.setNextCursor(cursorPage.nextCursor());
                return ok(response);
            }
        }
        IPage<Product> result = productLibraryPageQueryService.getSelectedLibraryPage(page, size, query);
        return okPage(result);
    }

    /**
     * 快速寄样申请。
     *
     * <p>商品库弹窗式快速寄样入口，支持私海达人多选逐个创建寄样申请。
     * 创建后将进入寄样审核流程，触发后续的状态机流转。
     *
     * <ol>
     *   <li>校验商品关联主键是否存在</li>
     *   <li>委托 {@link ProductQuickSampleApplicationService#applyQuickSample} 创建寄样申请</li>
     *   <li>返回创建结果，包含各达人对应的寄样单 ID</li>
     * </ol>
     *
     * @param relationId 商品关联主键（product_snapshot.id）
     * @param request    快速寄样请求体，包含达人列表与寄样条件
     * @param userId     当前登录用户 ID
     * @param deptId     当前用户所属部门 ID（可选）
     * @param roleCodes  当前用户角色编码列表（可选）
     * @return 快速寄样申请结果，包含各达人对应的寄样单 ID
     */
    @Operation(summary = "快速寄样", description = "商品库弹窗式快速寄样，支持私海达人多选逐个创建寄样申请。")
    @RequirePermission("product:quick-sample")
    @PostMapping({"/{relationId}/quick-sample", "/{relationId}/quick-sample-apply"})
    public ApiResult<QuickSampleApplyResponse> quickSample(
            @Parameter(description = "商品关联主键（product_snapshot.id）。") @PathVariable UUID relationId,
            @Valid @RequestBody QuickSampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(productQuickSampleApplicationService.applyQuickSample(relationId, request, userId, deptId, roleCodes));
    }

    /**
     * 商品库类目选项查询。
     *
     * <p>从当前展示中的商品库记录动态聚合类目列表，供前端筛选多选组件使用。
     * 返回的类目名称去重后按自然顺序排列。
     *
     * @return 商品类目名称列表
     */
    @Operation(summary = "商品库类目选项", description = "从当前展示中的商品库记录动态聚合类目，供筛选多选使用。")
    @GetMapping("/categories")
    public ApiResult<List<String>> libraryCategories() {
        return ok(productService.listLibraryCategories());
    }

    @Operation(summary = "商品库管理统计", description = "按 snapshot / relation / DISPLAYING 等口径返回只读统计，不改变商品库分页展示语义。")
    @RequirePermission("product:admin-counts")
    @GetMapping("/admin/counts")
    public ApiResult<ProductService.AdminProductCounts> adminCounts() {
        return ok(productService.getAdminCounts());
    }

    /**
     * 商品库动态筛选项查询。
     *
     * <p>返回商品库页面的动态筛选项，当前包含类目列表。
     * 每个筛选项包含值（value）、标签（label）和图标（icon）三个字段。
     *
     * @return 商品库筛选项 DTO，包含类目列表
     */
    @Operation(summary = "商品库筛选项", description = "返回商品库动态筛选项，当前包含类目。")
    @GetMapping("/filter-options")
    public ApiResult<ProductFilterOptionsDTO> filterOptions() {
        List<ProductFilterOptionItem> categories = productService.listLibraryCategories().stream()
                .map(category -> new ProductFilterOptionItem(category, category, null))
                .toList();
        return ok(new ProductFilterOptionsDTO(categories));
    }

    /**
     * 商品库团长筛选项（已废弃）。
     *
     * <p>根据关键字模糊查询团长合作方列表，供商品库筛选下拉使用。
     * 前端商品库已改用 {@code GET /colonel/partners} 获取团长主数据。
     *
     * @param keyword 团长名称关键字（可选）
     * @param limit   返回条数上限，默认 50
     * @return 团长合作方列表
     * @deprecated 请改用 {@code GET /colonel/partners}，前端商品库已走 colonel 主数据路径
     */
    @Deprecated(since = "2026-05-24", forRemoval = true)
    @Operation(summary = "[已废弃] 商品库团长筛选项", description = "请改用 GET /colonel/partners。前端商品库已走 colonel 主数据路径。")
    @GetMapping("/filter-options/colonel-partners")
    public ApiResult<List<ColonelPartner>> colonelPartnerFilterOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(colonelPartnerSyncService.listByNameKeyword(keyword, limit));
    }

    /**
     * 选品候选分页查询（已废弃）。
     *
     * <p>兼容 Token 缺失场景下的本地选品候选列表查询。
     * 对于 BIZ_STAFF 角色，自动限制为仅查看自己负责的商品（私海过滤）。
     *
     * <ol>
     *   <li>判断当前用户角色是否需要限制为私海范围</li>
     *   <li>若需要限制，将 assigneeId 设为当前 userId</li>
     *   <li>调用 {@link ProductService#getPage} 查询商品列表</li>
     * </ol>
     *
     * @param page      页码，从 1 开始
     * @param size      每页条数，上限 100
     * @param status    商品状态（可选）
     * @param userId    当前登录用户 ID（可选，用于私海过滤）
     * @param roleCodes 当前用户角色编码列表（可选，用于判断是否限制私海）
     * @return 分页商品列表
     */
    @Operation(summary = "[已废弃] 选品候选分页", description = "兼容 Token 缺失场景下的本地选品候选列表。")
    @GetMapping("/picks")
    public ApiResult<PageResult<Product>> pickPage(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        UUID assigneeId = shouldLimitPickPageToSelf(roleCodes) ? userId : null;
        return okPage(productService.getPage(page, size, status, assigneeId));
    }

    /**
     * 商品详情查询（已废弃）。
     *
     * <p>兼容旧版商品详情查询接口，根据商品主键 ID 返回完整商品信息。
     *
     * @param id 商品主键 ID（UUID 格式）
     * @return 商品详情
     * @deprecated 请迁移到 {@code /colonel/activities/{activityId}/products/{productId}}
     */
    @Operation(summary = "[已废弃] 商品详情", description = "兼容旧版商品详情查询。请迁移到 /colonel/activities/{activityId}/products/{productId}。")
    @RequirePermission("product:detail")
    @GetMapping("/{id}")
    public ApiResult<Product> detail(@Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id) {
        return ok(productService.getById(id));
    }

    /**
     * 商品绑定活动（已废弃）。
     *
     * <p>将商品关联到指定团长活动。仅 BIZ_LEADER 角色可操作。
     * 绑定后商品进入该活动的商品管理流程。
     *
     * @param id        商品主键 ID（UUID 格式）
     * @param request   绑定活动请求体，包含目标活动 ID
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID（可选）
     * @return 绑定后的商品详情
     * @deprecated 请迁移到 {@code /colonel/activities/{activityId}/products/{productId}/bind-activity}
     */
    @Operation(summary = "[已废弃] 商品绑定活动", description = "兼容旧版商品绑定活动入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/bind-activity。")
    @RequirePermission("product:bind-activity")
    @PutMapping("/{id}/activity")
    public ApiResult<Product> bindActivity(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "绑定活动请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"activityId\":\"11111111-1111-1111-1111-111111111111\"}"))
            )
            @Valid @RequestBody BindActivityRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.bindActivity(id, request.getActivityId(), userId, deptId));
    }

    /**
     * 商品分配招商组长（已废弃）。
     *
     * <p>将商品分配给指定招商组长。仅 BIZ_LEADER 角色可操作。
     * 分配后该招商人员即可在私海中看到并管理该商品。
     *
     * @param id        商品主键 ID（UUID 格式）
     * @param request   分配请求体，包含招商组长用户 ID
     * @return 分配后的商品详情
     * @deprecated 请迁移到 {@code /colonel/activities/{activityId}/products/{productId}/assignee}
     */
    @Operation(summary = "[已废弃] 商品分配招商", description = "兼容旧版商品分配招商入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/assignee。")
    @RequirePermission("product:assign")
    @PutMapping("/{id}/assignee")
    public ApiResult<Product> assign(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "指定招商组长。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
            )
            @Valid @RequestBody AssignProductRequest request) {
        return ok(productService.assignProduct(id, request.getAssigneeId()));
    }

    /**
     * 商品审核（已废弃）。
     *
     * <p>对商品执行审核操作（通过/驳回）。仅 BIZ_STAFF 角色可操作。
     * 驳回时建议填写原因，便于招商人员了解问题并修正。
     *
     * @param id       商品主键 ID（UUID 格式）
     * @param request  审核结果请求体，包含 approved（是否通过）和 reason（审核备注）
     * @return 审核后的商品详情
     * @deprecated 请迁移到 {@code /colonel/activities/{activityId}/products/{productId}/audit-result}
     */
    @Operation(summary = "[已废弃] 商品审核", description = "兼容旧版商品审核入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/audit-result。")
    @RequirePermission("product:audit")
    @PutMapping("/{id}/audit-result")
    public ApiResult<Product> audit(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "审核结果请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"approved\":true,\"reason\":\"素材完整，允许推进\"}"))
            )
            @Valid @RequestBody AuditProductRequest request) {
        return ok(productService.auditProduct(id, request.isApproved(), request.getReason()));
    }

    /**
     * 商品管理页审核通过。
     *
     * <p>最新商品管理口径中，待审核阶段只做通过 / 拒绝。审核通过后由服务层把当前商品关联
     * 加入商品库展示竞争，并触发展示规则。</p>
     *
     * @param relationId 商品关联主键（product_snapshot.id）
     * @param request    审核通过备注
     * @return 审核后的商品详情
     */
    @Operation(summary = "商品管理审核通过", description = "待审核商品通过后直接进入商品库展示竞争。")
    @RequirePermission("product:approve-managed-product")
    @PostMapping("/manage/{relationId}/approve")
    public ApiResult<Product> approveManagedProduct(
            @Parameter(description = "商品关联主键，使用 UUID 格式。") @PathVariable UUID relationId,
            @Valid @RequestBody ProductManageApproveRequest request) {
        return ok(productService.auditProduct(
                relationId,
                true,
                request == null ? null : request.getRemark(),
                request == null ? null : request.toSupplementMap()));
    }

    /**
     * 商品管理页审核拒绝。
     *
     * <p>审核拒绝后商品不会进入商品库展示，并由服务层把展示状态置为隐藏。</p>
     *
     * @param relationId 商品关联主键（product_snapshot.id）
     * @param request    拒绝原因
     * @return 审核后的商品详情
     */
    @Operation(summary = "商品管理审核拒绝", description = "待审核商品拒绝后不进入商品库展示。")
    @RequirePermission("product:reject-managed-product")
    @PostMapping("/manage/{relationId}/reject")
    public ApiResult<Product> rejectManagedProduct(
            @Parameter(description = "商品关联主键，使用 UUID 格式。") @PathVariable UUID relationId,
            @Valid @RequestBody ProductManageRejectRequest request) {
        return ok(productService.auditProduct(relationId, false, request == null ? null : request.getReason()));
    }

    @Operation(summary = "暂停发布商品", description = "将商品关系标记为本地暂停发布，商品库列表不再展示该关系。")
    @RequirePermission("product:pause-publish")
    @PostMapping("/{relationId}/pause")
    public ApiResult<Product> pausePublish(
            @Parameter(description = "商品关联主键，使用 UUID 格式。") @PathVariable UUID relationId,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.pausePublish(relationId, userId, deptId));
    }

    @Operation(summary = "恢复发布商品", description = "清除本地暂停发布标记，并重新进入商品库展示规则计算。")
    @RequirePermission("product:resume-publish")
    @PostMapping("/{relationId}/resume")
    public ApiResult<Product> resumePublish(
            @Parameter(description = "商品关联主键，使用 UUID 格式。") @PathVariable UUID relationId,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.resumePublish(relationId, userId, deptId));
    }

    /**
     * 商品生成推广链接（已废弃）。
     *
     * <p>为商品生成抖音推广链接（转链）。CHANNEL_LEADER / CHANNEL_STAFF 角色可操作。
     * 支持幂等键（Idempotency-Key 请求头）防止重复创建。
     *
     * <ol>
     *   <li>处理请求体为空的情况，使用默认值填充</li>
     *   <li>委托 {@link ProductService#generatePromotionLink} 调用抖音转链网关</li>
     *   <li>返回推广链接结果，包含推广链接、短链等信息</li>
     * </ol>
     *
     * @param id             商品主键 ID（UUID 格式）
     * @param request        转链请求体（可选），包含场景、达人标识等；为空时使用默认场景
     * @param idempotencyKey 幂等键请求头（可选），用于防止重复转链
     * @param userId         当前登录用户 ID
     * @param deptId         当前用户所属部门 ID（可选）
     * @return 推广链接生成结果
     * @deprecated 请迁移到 {@code /colonel/activities/{activityId}/products/{productId}/promotion-links}
     */
    @Operation(summary = "[已废弃] 商品转链", description = "兼容旧版商品转链入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/promotion-links。")
    @RequirePermission("product:generate-promotion-link")
    @PostMapping("/{id}/promotion-links")
    public ApiResult<PromotionLinkResponse> generatePromotionLink(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "转链请求体，可为空；为空时使用默认场景。",
                    content = @Content(examples = @ExampleObject(value = "{\"scene\":\"PRODUCT_LIBRARY\",\"talentId\":\"test_talent_001\"}"))
            )
            @RequestBody(required = false) PromotionLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        PromotionLinkRequest safeRequest = request == null ? new PromotionLinkRequest() : request;
        var result = productService.generatePromotionLink(
                id,
                userId,
                deptId,
                safeRequest.getExternalUniqueId(),
                safeRequest.getPromotionScene(),
                safeRequest.getNeedShortLink(),
                safeRequest.getScene(),
                safeRequest.getTalentId(),
                idempotencyKey
        );
        return ok(new PromotionLinkResponse(
                result.pickSource(),
                result.pickExtra(),
                result.shortId(),
                result.shortLink(),
                result.promoteLink(),
                result.uuidSeed()
        ));
    }

    /**
     * 商品推广历史记录查询（已废弃）。
     *
     * <p>按业务商品 ID 分页查询历史推广记录，包括转链时间、推广场景、链接状态等信息。
     *
     * @param productId 业务商品 ID
     * @param page      页码，从 1 开始
     * @param size      每页条数，上限 100
     * @return 分页推广历史记录列表
     */
    @Operation(summary = "[已废弃] 商品推广记录", description = "兼容旧版商品库按商品ID读取历史推广记录。")
    @RequirePermission("product:promotion-link-history")
    @GetMapping("/{productId}/promotion-links/history")
    public ApiResult<PageResult<java.util.Map<String, Object>>> promotionLinkHistory(
            @Parameter(description = "业务商品 ID。") @PathVariable String productId,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ok(productService.getPromotionLinkHistory(productId, page, size));
    }

    /**
     * 商品达人跟进（已废弃）。
     *
     * <p>记录商品与达人的跟进信息，包括跟进状态、跟进内容、下次跟进时间等。
     * CHANNEL_LEADER / CHANNEL_STAFF 角色可操作。
     *
     * <ol>
     *   <li>接收达人跟进请求体，包含达人 ID、跟进状态、跟进内容等</li>
     *   <li>委托 {@link ProductService#startTalentFollow} 创建跟进记录</li>
     *   <li>返回跟进结果</li>
     * </ol>
     *
     * @param id      商品主键 ID（UUID 格式）
     * @param request 达人跟进请求体
     * @param userId  当前登录用户 ID（可选）
     * @return 跟进结果 Map
     */
    @Operation(summary = "[已废弃] 商品达人跟进", description = "兼容旧版商品达人跟进入口。请逐步迁移到商品主链路跟进入口。")
    @RequirePermission("product:follow")
    @PostMapping("/{id}/follow")
    public ApiResult<java.util.Map<String, Object>> follow(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人跟进请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"talentName\":\"达人A\",\"followStatus\":\"FOLLOWING\",\"content\":\"已加微信跟进\",\"operatorName\":\"渠道A\"}"))
            )
            @Valid @RequestBody TalentFollowRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ok(productService.startTalentFollow(
                id,
                request.getTalentId(),
                request.getTalentName(),
                request.getFollowStatus(),
                request.getContent(),
                request.getNextFollowTime(),
                userId,
                request.getOperatorName()
        ));
    }

    /**
     * 商品绑定活动请求体。
     *
     * <p>用于旧版 {@code PUT /{id}/activity} 接口，指定商品要绑定的目标活动。
     */
    public static class BindActivityRequest {
        @Schema(description = "活动主键 ID，使用 UUID 格式。", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "activityId 不能为空")
        private UUID activityId;

        public UUID getActivityId() {
            return activityId;
        }

        public void setActivityId(UUID activityId) {
            this.activityId = activityId;
        }
    }

    /**
     * 商品分配招商请求体。
     *
     * <p>用于旧版 {@code PUT /{id}/assignee} 接口，指定商品的招商组长。
     */
    public static class AssignProductRequest {
        @Schema(description = "招商组长用户 ID，使用 UUID 格式。", example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "assigneeId 不能为空")
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }

    /**
     * 商品审核请求体。
     *
     * <p>用于旧版 {@code PUT /{id}/audit-result} 接口，包含审核结果与备注。
     * 驳回时建议填写原因，便于招商人员了解问题并修正。
     */
    public static class AuditProductRequest {
        @Schema(description = "是否审核通过。", example = "true")
        private boolean approved;

        @Schema(description = "审核备注，驳回时建议填写原因。", example = "素材完整，允许推进")
        private String reason;

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class AuditSupplementRequest {
        @Schema(description = "专属价金额，单位元。", example = "129.00")
        private BigDecimal exclusivePriceAmount;

        @Schema(description = "专属价说明。", example = "直播间专属价 129 元，日常到手价 149 元。")
        private String exclusivePriceRemark;

        public BigDecimal getExclusivePriceAmount() {
            return exclusivePriceAmount;
        }

        public void setExclusivePriceAmount(BigDecimal exclusivePriceAmount) {
            this.exclusivePriceAmount = exclusivePriceAmount;
        }

        @Schema(description = "发货信息。", example = "48 小时内发货，江浙沪次日达。")
        private String shippingInfo;

        @Schema(description = "商品卖点列表。", example = "[\"高复购刚需品\", \"夏季场景强\"]")
        private List<String> sellingPoints;

        @Schema(description = "推广话术。", example = "可主打复购和夏季囤货场景。")
        private String promotionScript;

        @Schema(description = "是否支持投流。", example = "true")
        private Boolean supportsAds;

        @Schema(description = "投流规则说明。", example = "投流比例1:0.5，保量10万曝光")
        private String adsRule;

        @Schema(description = "奖励说明。", example = "破 3 万 GMV 额外返 2 个点。")
        private String rewardRemark;

        @Schema(description = "参与要求。", example = "近 30 天食品饮料类目有成交。")
        private String participationRequirements;

        @Schema(description = "活动时间说明。", example = "4 月 1 日至 4 月 15 日。")
        private String campaignTimeRemark;

        @Schema(description = "手卡或素材文件列表。", example = "[\"https://example.com/material-1.png\"]")
        private List<String> materialFiles;

        @Schema(description = "货品标签列表。", example = "[\"家居\", \"零食\"]")
        private List<String> goodsTags;

        @Schema(description = "商品标签列表。", example = "[\"主推\", \"商品链组\"]")
        private List<String> productTags;

        @Schema(description = "30天销售额门槛。", example = "30000")
        private Long sampleThresholdSales;

        @Schema(description = "达人等级门槛。", example = "1")
        private Integer sampleThresholdLevel;

        @Schema(description = "寄样补充要求。", example = "需真人出镜，粉丝量>10万")
        private String sampleThresholdRemark;

        public String getExclusivePriceRemark() {
            return exclusivePriceRemark;
        }

        public void setExclusivePriceRemark(String exclusivePriceRemark) {
            this.exclusivePriceRemark = exclusivePriceRemark;
        }

        public String getShippingInfo() {
            return shippingInfo;
        }

        public void setShippingInfo(String shippingInfo) {
            this.shippingInfo = shippingInfo;
        }

        public List<String> getSellingPoints() {
            return sellingPoints;
        }

        public void setSellingPoints(List<String> sellingPoints) {
            this.sellingPoints = sellingPoints;
        }

        public String getPromotionScript() {
            return promotionScript;
        }

        public void setPromotionScript(String promotionScript) {
            this.promotionScript = promotionScript;
        }

        public Boolean getSupportsAds() {
            return supportsAds;
        }

        public void setSupportsAds(Boolean supportsAds) {
            this.supportsAds = supportsAds;
        }

        public String getAdsRule() {
            return adsRule;
        }

        public void setAdsRule(String adsRule) {
            this.adsRule = adsRule;
        }

        public String getRewardRemark() {
            return rewardRemark;
        }

        public void setRewardRemark(String rewardRemark) {
            this.rewardRemark = rewardRemark;
        }

        public String getParticipationRequirements() {
            return participationRequirements;
        }

        public void setParticipationRequirements(String participationRequirements) {
            this.participationRequirements = participationRequirements;
        }

        public String getCampaignTimeRemark() {
            return campaignTimeRemark;
        }

        public void setCampaignTimeRemark(String campaignTimeRemark) {
            this.campaignTimeRemark = campaignTimeRemark;
        }

        public List<String> getMaterialFiles() {
            return materialFiles;
        }

        public void setMaterialFiles(List<String> materialFiles) {
            this.materialFiles = materialFiles;
        }

        public List<String> getGoodsTags() {
            return goodsTags;
        }

        public void setGoodsTags(List<String> goodsTags) {
            this.goodsTags = goodsTags;
        }

        public List<String> getProductTags() {
            return productTags;
        }

        public void setProductTags(List<String> productTags) {
            this.productTags = productTags;
        }

        public Long getSampleThresholdSales() {
            return sampleThresholdSales;
        }

        public void setSampleThresholdSales(Long sampleThresholdSales) {
            this.sampleThresholdSales = sampleThresholdSales;
        }

        public Integer getSampleThresholdLevel() {
            return sampleThresholdLevel;
        }

        public void setSampleThresholdLevel(Integer sampleThresholdLevel) {
            this.sampleThresholdLevel = sampleThresholdLevel;
        }

        public String getSampleThresholdRemark() {
            return sampleThresholdRemark;
        }

        public void setSampleThresholdRemark(String sampleThresholdRemark) {
            this.sampleThresholdRemark = sampleThresholdRemark;
        }

        public Map<String, Object> toSupplementMap() {
            Map<String, Object> supplement = new LinkedHashMap<>();
            if (exclusivePriceAmount != null) {
                supplement.put("exclusivePriceAmount", exclusivePriceAmount);
            }
            putText(supplement, "exclusivePriceRemark", exclusivePriceRemark);
            putText(supplement, "shippingInfo", shippingInfo);
            putText(supplement, "promotionScript", promotionScript);
            putText(supplement, "rewardRemark", rewardRemark);
            putText(supplement, "participationRequirements", participationRequirements);
            putText(supplement, "campaignTimeRemark", campaignTimeRemark);
            putText(supplement, "sampleThresholdRemark", sampleThresholdRemark);
            if (supportsAds != null) {
                supplement.put("supportsAds", supportsAds);
            }
            putText(supplement, "adsRule", adsRule);
            if (sampleThresholdSales != null) {
                supplement.put("sampleThresholdSales", sampleThresholdSales);
            }
            if (sampleThresholdLevel != null) {
                supplement.put("sampleThresholdLevel", sampleThresholdLevel);
            }
            putList(supplement, "sellingPoints", sellingPoints);
            putList(supplement, "materialFiles", materialFiles);
            putList(supplement, "goodsTags", goodsTags);
            putList(supplement, "productTags", productTags);
            return supplement;
        }

        private void putText(Map<String, Object> supplement, String key, String value) {
            if (StringUtils.hasText(value)) {
                supplement.put(key, value.trim());
            }
        }

        private void putList(Map<String, Object> supplement, String key, List<String> values) {
            List<String> normalized = normalizeList(values);
            if (!normalized.isEmpty()) {
                supplement.put(key, normalized);
            }
        }

        private List<String> normalizeList(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    normalized.add(value.trim());
                }
            }
            return normalized;
        }
    }

    /**
     * 商品管理审核通过请求体。
     */
    public static class ProductManageApproveRequest extends AuditSupplementRequest {
        @Schema(description = "审核通过备注，可为空。", example = "素材完整，允许进入商品库")
        private String remark;

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    /**
     * 商品管理审核拒绝请求体。
     */
    public static class ProductManageRejectRequest {
        @Schema(description = "拒绝原因，必填。", example = "商品信息不完整")
        @NotBlank(message = "拒绝原因不能为空")
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 商品转链请求体。
     *
     * <p>用于旧版 {@code POST /{id}/promotion-links} 接口，控制推广链接的生成参数。
     * 所有字段均可选，为空时使用系统默认值。
     */
    public static class PromotionLinkRequest {
        @Schema(description = "外部幂等标识，不传则由系统按默认逻辑生成。", example = "external-unique-id-001")
        private String externalUniqueId;

        @Schema(description = "推广场景编码。待确认：取值含义请联系产品。", example = "4")
        private Integer promotionScene = 4;

        @Schema(description = "是否需要短链。", example = "true")
        private Boolean needShortLink = Boolean.TRUE;

        @Schema(description = "业务场景标识。", example = "PRODUCT_LIBRARY")
        private String scene = "PRODUCT_LIBRARY";

        @Schema(description = "达人标识，用于特定转链场景。", example = "test_talent_001")
        private String talentId;

        public String getExternalUniqueId() {
            return externalUniqueId;
        }

        public void setExternalUniqueId(String externalUniqueId) {
            this.externalUniqueId = externalUniqueId;
        }

        public Integer getPromotionScene() {
            return promotionScene;
        }

        public void setPromotionScene(Integer promotionScene) {
            this.promotionScene = promotionScene;
        }

        public Boolean getNeedShortLink() {
            return needShortLink == null || needShortLink;
        }

        public void setNeedShortLink(Boolean needShortLink) {
            this.needShortLink = needShortLink;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public String getTalentId() {
            return talentId;
        }

        public void setTalentId(String talentId) {
            this.talentId = talentId;
        }
    }

    /**
     * 商品转链响应体。
     */
    public record PromotionLinkResponse(
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed) {
    }

    /**
     * 商品达人跟进请求体。
     *
     * <p>用于旧版 {@code POST /{id}/follow} 接口，记录达人跟进状态与内容。
     */
    public static class TalentFollowRequest {
        @Schema(description = "达人主键 ID，使用 UUID 格式。", example = "33333333-3333-3333-3333-333333333333")
        private UUID talentId;

        @Schema(description = "达人名称。", example = "达人A")
        private String talentName;

        @Schema(description = "跟进状态。待确认：取值含义请联系产品。", example = "FOLLOWING")
        @NotBlank(message = "followStatus 不能为空")
        private String followStatus;

        @Schema(description = "跟进内容。", example = "已加微信跟进")
        private String content;

        @Schema(description = "下次跟进时间。", example = "2026-04-29T10:00:00")
        private LocalDateTime nextFollowTime;

        @Schema(description = "操作人显示名称。", example = "渠道A")
        private String operatorName;

        public UUID getTalentId() {
            return talentId;
        }

        public void setTalentId(UUID talentId) {
            this.talentId = talentId;
        }

        public String getTalentName() {
            return talentName;
        }

        public void setTalentName(String talentName) {
            this.talentName = talentName;
        }

        public String getFollowStatus() {
            return followStatus;
        }

        public void setFollowStatus(String followStatus) {
            this.followStatus = followStatus;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getNextFollowTime() {
            return nextFollowTime;
        }

        public void setNextFollowTime(LocalDateTime nextFollowTime) {
            this.nextFollowTime = nextFollowTime;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }
    }

    /**
     * 判断当前用户是否需要限制为仅查看自己的商品。
     *
     * <p>BIZ_STAFF（招商专员）角色默认只能查看分配给自己的商品；
     * ADMIN 和 BIZ_LEADER 角色不受此限制，可查看全部商品。
     *
     * @param roleCodes 当前用户的角色编码列表
     * @return {@code true} 表示需要限制为仅查看自己的商品，{@code false} 表示不限制
     */
    private boolean shouldLimitPickPageToSelf(List<String> roleCodes) {
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER)) {
            return false;
        }
        return currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF);
    }
}
