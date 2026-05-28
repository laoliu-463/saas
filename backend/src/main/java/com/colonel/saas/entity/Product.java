package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品实体类，映射数据库 {@code product} 表。
 * <p>
 * 该实体属于商品域，负责存储从抖店同步的商品基础信息，
 * 包括商品名称、价格、分类、佣金比例等核心数据。
 * 部分字段（{@code exist = false}）为虚拟字段，
 * 由 Service 层在查询时从 {@code product_operation_state} 等关联表中填充，
 * 用于列表展示和详情页渲染，不直接对应 {@code product} 表列。
 * </p>
 *
 * @see com.colonel.saas.entity.ProductOperationState 商品运营状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product", autoResultMap = true)
public class Product extends BaseEntity {

    /** 商品唯一标识，系统内部生成的主键 */
    @TableField("product_id")
    private String productId;

    /** 抖店侧商品 ID（outer = 外部），用于与抖店 API 做关联匹配 */
    @TableField("outer_product_id")
    private String outerProductId;

    /** 商品名称，来源于抖店同步 */
    private String name;

    /** 商品描述/详情文案 */
    private String description;

    /** 市场原价，单位：分 */
    @TableField("market_price")
    private Long marketPrice;

    /** 优惠后价格，单位：分 */
    @TableField("discount_price")
    private Long price;

    /** 商品封面图 URL */
    private String cover;

    /** 商品详情页链接 */
    @TableField("detail_url")
    private String detailUrl;

    /** 一级分类 ID */
    @TableField("first_cid")
    private Long firstCid;

    /** 二级分类 ID */
    @TableField("second_cid")
    private Long secondCid;

    /** 三级分类 ID */
    @TableField("third_cid")
    private Long thirdCid;

    /** 四级分类 ID */
    @TableField("fourth_cid")
    private Long fourthCid;

    /**
     * 分类详情 JSON，存储完整的分类路径信息。
     * 数据库列为 JSONB 类型，通过 JacksonTypeHandler 自动序列化/反序列化。
     */
    @TableField(value = "category_detail", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> categoryDetail;

    /** 商品图片列表，JSONB 数组，存储多张商品图片 URL */
    @TableField(value = "pics", typeHandler = JacksonTypeHandler.class)
    private List<String> pics;

    /** 规格与价格映射列表，JSONB 数组，存储各 SKU 的规格和对应价格 */
    @TableField(value = "spec_prices", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> specPrices;

    /** 佣金比例，例如 0.20 表示 20% */
    @TableField("cos_ratio")
    private BigDecimal cosRatio;

    /** 佣金金额，单位：分 */
    @TableField("cos_fee")
    private Long cosFee;

    /** 服务费率，平台收取的服务费比例 */
    @TableField("service_ratio")
    private BigDecimal serviceRatio;

    /** 商品上架状态：1 = 上架，0 = 下架 */
    private Integer status;

    /** 虚拟字段：分类名称路径（如"服饰 > 女装 > 连衣裙"），由 Service 层拼接 */
    @TableField(exist = false)
    private String category;

    /** 虚拟字段：叶子分类名称，用于列表展示 */
    @TableField(exist = false)
    private String categoryName;

    /** 虚拟字段：上架状态的中文文案（如"上架中"/"已下架"） */
    @TableField(exist = false)
    private String statusText;

    /** 虚拟字段：当前绑定的活动 ID，来源于 product_operation_state */
    @TableField(exist = false)
    private UUID activityId;

    /** 审核状态，标识商品审核流程当前所处阶段 */
    @TableField("check_status")
    private Integer checkStatus;

    /** 虚拟字段：审核备注信息 */
    @TableField(exist = false)
    private String auditRemark;

    /** 虚拟字段：当前负责运营人员的用户 ID */
    @TableField(exist = false)
    private UUID assigneeId;

    /** 虚拟字段：推广链接（转链后的达人专属链接） */
    @TableField(exist = false)
    private String promoteLink;

    /** 虚拟字段：短链接，推广链接的缩短版本 */
    @TableField(exist = false)
    private String shortLink;

    /** 虚拟字段：业务状态标识（如 active / inactive / pending 等） */
    @TableField(exist = false)
    private String bizStatus;

    /** 虚拟字段：业务状态的中文展示标签 */
    @TableField(exist = false)
    private String bizStatusLabel;

    /** 虚拟字段：所属店铺名称 */
    @TableField(exist = false)
    private String shopName;

    /** 虚拟字段：价格格式化后的展示文案（如"¥99.00"） */
    @TableField(exist = false)
    private String priceText;

    /** 虚拟字段：活动佣金比例的展示文案 */
    @TableField(exist = false)
    private String activityCosRatioText;

    /** 虚拟字段：预估服务费金额的展示文案 */
    @TableField(exist = false)
    private String estimatedServiceFee;

    /** 虚拟字段：负责运营人员姓名 */
    @TableField(exist = false)
    private String assigneeName;

    /** 虚拟字段：来源活动 ID，标识商品最初来自哪个活动 */
    @TableField(exist = false)
    private String sourceActivityId;

    /** 虚拟字段：是否已被选入商品库 */
    @TableField(exist = false)
    private Boolean selectedToLibrary;

    /** 虚拟字段：系统自动生成的标签列表（如"高佣""新上架"等） */
    @TableField(exist = false)
    private List<String> systemTags;

    /** 虚拟字段：预警标签列表（如"即将下架""库存不足"等） */
    @TableField(exist = false)
    private List<String> alertTags;

    /** 虚拟字段：审核补充信息，JSON 结构，存储额外审核相关内容 */
    @TableField(exist = false)
    private Map<String, Object> auditSupplement;

    /** 虚拟字段：被选入商品库的时间 */
    @TableField(exist = false)
    private java.time.LocalDateTime selectedAt;

    /** 虚拟字段：最新决策级别（如"通过"/"拒绝"/"待定"） */
    @TableField(exist = false)
    private String latestDecisionLevel;

    /** 虚拟字段：最新决策的中文展示标签 */
    @TableField(exist = false)
    private String latestDecisionLabel;

    /** 虚拟字段：最新决策的原因说明 */
    @TableField(exist = false)
    private String latestDecisionReason;

    /** 虚拟字段：最新决策的时间 */
    @TableField(exist = false)
    private String latestDecisionAt;

    /** 虚拟字段：是否有素材（图片/视频等推广材料） */
    @TableField(exist = false)
    private Boolean hasMaterial;

    /** 虚拟字段：是否配置了寄样规则 */
    @TableField(exist = false)
    private Boolean hasSampleRule;

    /** 虚拟字段：近 30 天销量 */
    @TableField(exist = false)
    private Long sales30d;

    /** 虚拟字段：是否置顶展示 */
    @TableField(exist = false)
    private Boolean pinned;

    /** 虚拟字段：置顶到期时间，超过该时间后自动取消置顶 */
    @TableField(exist = false)
    private LocalDateTime pinnedUntil;

    /** 虚拟字段：展示状态（如 visible / hidden / forced_visible 等） */
    @TableField(exist = false)
    private String displayStatus;

    /** 虚拟字段：展示状态的中文文案 */
    @TableField(exist = false)
    private String displayStatusLabel;

    /** 虚拟字段：隐藏原因，当商品被隐藏时记录具体原因 */
    @TableField(exist = false)
    private String hiddenReason;

    /** 虚拟字段：是否支持投放广告 */
    @TableField(exist = false)
    private Boolean supportsAds;

    /** 虚拟字段：广告投放规则配置 */
    @TableField(exist = false)
    private String adsRule;
}
