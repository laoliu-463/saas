package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 结算订单分区表实体。
 * <p>
 * 对应数据库表：{@code colonelsettlement_order}，存储从抖店平台同步的结算订单数据。
 * 该表采用分区设计（LCK-01），不继承 {@link com.colonel.saas.common.base.VersionedEntity}，
 * 因表无 create_by/update_by 且主键含 create_time 分区键。
 * 订单包含金额、佣金、服务费等核心财务字段，支持双团长（first/second colonel）佣金结算模式。
 * 通过 pickSource 进行订单归属，结合 {@link PerformanceRecord} 完成业绩计算。
 * 实现 Serializable 接口，手动管理乐观锁（@Version）和审计字段。
 * </p>
 *
 * @see PerformanceRecord 业绩记录，基于订单进行归属和提成计算
 * @see ColonelsettlementActivity 团长活动，订单关联的活动上下文
 * @see PickSourceMapping 推广来源映射，用于订单归属
 */
@Data
@TableName(value = "colonelsettlement_order", autoResultMap = true)
public class ColonelsettlementOrder implements Serializable {

    /**
     * 主键 ID
     * <p>手动输入的 UUID 主键</p>
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 订单 ID
     * <p>对应数据库列：{@code order_id}，抖店平台的订单唯一标识</p>
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，订单关联的商品标识</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 商品名称
     * <p>对应数据库列：{@code product_name}，冗余存储商品名称，便于列表展示</p>
     */
    @TableField("product_name")
    private String productName;

    /**
     * 店铺 ID
     * <p>对应数据库列：{@code shop_id}，订单所属抖店店铺标识</p>
     */
    @TableField("shop_id")
    private Long shopId;

    /**
     * 店铺名称
     * <p>对应数据库列：{@code shop_name}，冗余存储店铺名称，便于展示</p>
     */
    @TableField("shop_name")
    private String shopName;

    /**
     * 订单金额（单位：分）
     * <p>对应数据库列：{@code order_amount}，订单的原始金额</p>
     */
    @TableField("order_amount")
    private Long orderAmount;

    /**
     * 实付金额（单位：分）
     * <p>对应数据库列：{@code actual_amount}，用户实际支付的金额</p>
     */
    @TableField("actual_amount")
    private Long actualAmount;

    /**
     * 结算金额（单位：分）
     * <p>对应数据库列：{@code settle_amount}，平台最终结算的金额，用于佣金和服务费计算</p>
     */
    @TableField("settle_amount")
    private Long settleAmount;

    /**
     * 预估服务费（单位：分）
     * <p>对应数据库列：{@code estimate_service_fee}，基于预估数据计算的服务费，
     * 可能在结算后被 effectiveServiceFee 替代</p>
     */
    @TableField("estimate_service_fee")
    private Long estimateServiceFee;

    /**
     * 实际服务费（单位：分）
     * <p>对应数据库列：{@code effective_service_fee}，结算确认后的实际服务费金额</p>
     */
    @TableField("effective_service_fee")
    private Long effectiveServiceFee;

    /**
     * 预估技术服务费（单位：分）
     * <p>对应数据库列：{@code estimate_tech_service_fee}，基于预估数据计算的技术服务费</p>
     */
    @TableField("estimate_tech_service_fee")
    private Long estimateTechServiceFee;

    /**
     * 实际技术服务费（单位：分）
     * <p>对应数据库列：{@code effective_tech_service_fee}，结算确认后的实际技术服务费</p>
     */
    @TableField("effective_tech_service_fee")
    private Long effectiveTechServiceFee;

    /**
     * 团长百应 ID
     * <p>对应数据库列：{@code colonel_buyin_id}，第一团长的百应标识，
     * 用于关联团长活动和佣金结算</p>
     */
    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    /**
     * 团长佣金（单位：分）
     * <p>对应数据库列：{@code settle_colonel_commission}，第一团长的结算佣金金额</p>
     */
    @TableField("settle_colonel_commission")
    private Long settleColonelCommission;

    /**
     * 团长技术服务费（单位：分）
     * <p>对应数据库列：{@code settle_colonel_tech_service_fee}，
     * 第一团长需承担的技术服务费金额</p>
     */
    @TableField("settle_colonel_tech_service_fee")
    private Long settleColonelTechServiceFee;

    /**
     * 第二团长百应 ID
     * <p>对应数据库列：{@code second_colonel_buyin_id}，双团长模式下第二团长的百应标识，
     * 非双团长模式时为 null</p>
     */
    @TableField("second_colonel_buyin_id")
    private Long secondColonelBuyinId;

    /**
     * 第二团长活动 ID
     * <p>对应数据库列：{@code second_colonel_activity_id}，双团长模式下第二团长的活动标识</p>
     */
    @TableField("second_colonel_activity_id")
    private String secondActivityId;

    /**
     * 第二团长佣金（单位：分）
     * <p>对应数据库列：{@code settle_second_colonel_commission}，
     * 双团长模式下第二团长的结算佣金金额</p>
     */
    @TableField("settle_second_colonel_commission")
    private Long settleSecondColonelCommission;

    /**
     * 结算阶段 ID
     * <p>对应数据库列：{@code phase_id}，标识订单所属的结算周期/批次</p>
     */
    @TableField("phase_id")
    private String phaseId;

    /**
     * 订单状态
     * <p>对应数据库列：{@code order_status}，抖店平台的订单状态码，
     * 如待付款、已付款、已发货、已完成、已退款等</p>
     */
    @TableField("order_status")
    private Integer orderStatus;

    /**
     * 订单类型
     * <p>对应数据库列：{@code order_type}，标识订单的业务类型分类</p>
     */
    @TableField("order_type")
    private Integer orderType;

    /**
     * 推广来源标识
     * <p>对应数据库列：{@code pick_source}，订单关联的推广来源编码，
     * 用于通过 PickSourceMapping 进行订单归属</p>
     */
    @TableField("pick_source")
    private String pickSource;

    /**
     * 分页游标
     * <p>对应数据库列：{@code cursor}，订单同步时的分页游标标识，
     * 用于增量拉取订单数据</p>
     */
    @TableField("cursor")
    private String cursor;

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，订单关联的达人标识</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 渠道用户 ID
     * <p>对应数据库列：{@code channel_user_id}，订单归属的渠道业务人员标识</p>
     */
    @TableField("channel_user_id")
    private UUID channelUserId;

    /**
     * 渠道用户名称
     * <p>对应数据库列：{@code channel_user_name}，冗余存储渠道人员姓名，便于展示</p>
     */
    @TableField("channel_user_name")
    private String channelUserName;

    /**
     * 团长用户 ID
     * <p>对应数据库列：{@code colonel_user_id}，订单对应的系统内团长用户标识</p>
     */
    @TableField("colonel_user_id")
    private UUID colonelUserId;

    /**
     * 团长用户名称
     * <p>对应数据库列：{@code colonel_user_name}，冗余存储团长姓名，便于展示</p>
     */
    @TableField("colonel_user_name")
    private String colonelUserName;

    /**
     * 推广链接 ID
     * <p>对应数据库列：{@code promotion_link_id}，关联推广链接表，追溯订单来源</p>
     */
    @TableField("promotion_link_id")
    private UUID promotionLinkId;

    /**
     * 商品标题
     * <p>对应数据库列：{@code product_title}，冗余存储商品标题，便于列表展示</p>
     */
    @TableField("product_title")
    private String productTitle;

    /**
     * 商品图片 URL
     * <p>对应数据库列：{@code product_pic}，冗余存储商品封面图，便于列表展示。</p>
     */
    @TableField("product_pic")
    private String productPic;

    /**
     * 商品图片 URL（前端标准展示别名）
     * <p>非数据库字段，返回时与 {@link #productPic} 保持兼容。</p>
     */
    @TableField(exist = false)
    private String productImage;

    /**
     * 商品数量
     * <p>列表展示字段，查询时从订单 {@code extra_data} 的轻量投影补齐，不参与落库。</p>
     */
    @TableField(exist = false)
    private Integer itemNum;

    /**
     * 商品数量（前端标准展示别名）
     * <p>非数据库字段，返回时与 {@link #itemNum} 保持兼容。</p>
     */
    @TableField(exist = false)
    private Integer productQuantity;

    /**
     * 佣金率
     * <p>列表展示字段，优先从订单 {@code extra_data} 轻量投影读取，缺失时回退商品快照。</p>
     */
    @TableField(exist = false)
    private BigDecimal commissionRate;

    /**
     * 服务费率
     * <p>列表展示字段，从商品快照或商品基础信息补齐，不参与订单事实落库。</p>
     */
    @TableField(exist = false)
    private BigDecimal serviceFeeRate;

    /**
     * 渠道负责人 ID（前端标准展示别名）
     * <p>非数据库字段，返回时由 {@link #channelUserId} 转换。</p>
     */
    @TableField(exist = false)
    private String channelId;

    /**
     * 渠道负责人名称（前端标准展示别名）
     * <p>非数据库字段，返回时与 {@link #channelUserName} 保持兼容。</p>
     */
    @TableField(exist = false)
    private String channelName;

    /**
     * 出单视频 ID（前端展示用）。
     * <p>非数据库字段，由 Service 从订单 {@code extra_data} 的
     * {@code aweme_id / video_id / item_id} 轻量投影补齐。历史订单或上游未返回
     * 时为 null，前端走"无值不渲染"路径。</p>
     */
    @TableField(exist = false)
    private String awemeId;

    /**
     * 订单类型文本（前端展示用）。
     * <p>非数据库字段，由 Service 从 {@link #orderType} 派生：
     * 1=MAIN、2=SETTLEMENT、其它 / null=UNKNOWN。
     * 历史数据或上游未返回时为空字符串。</p>
     */
    @TableField(exist = false)
    private String orderTypeText;

    /**
     * 内容类型文本（前端展示用）。
     * <p>非数据库字段，由 Service 从订单 {@code extra_data} 的
     * {@code content_type / contentType / content_type_text} 轻量投影补齐。
     * 历史订单或上游未返回时为空字符串。</p>
     */
    @TableField(exist = false)
    private String contentTypeText;

    /**
     * 达人名称
     * <p>对应数据库列：{@code talent_name}，冗余存储达人昵称，便于展示</p>
     */
    @TableField("talent_name")
    private String talentName;

    /**
     * 渠道部门 ID
     * <p>对应数据库列：{@code channel_dept_id}，渠道业务人员所属部门，用于部门级数据过滤</p>
     */
    @TableField("channel_dept_id")
    private UUID channelDeptId;

    /**
     * 负责人用户 ID
     * <p>对应数据库列：{@code user_id}，订单最终归属的业务负责人标识</p>
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 部门 ID
     * <p>对应数据库列：{@code dept_id}，负责人所属部门，用于数据范围过滤</p>
     */
    @TableField("dept_id")
    private UUID deptId;

    /**
     * 活动 ID
     * <p>对应数据库列：{@code colonel_activity_id}，订单关联的团长活动标识</p>
     */
    @TableField("colonel_activity_id")
    private String activityId;

    /**
     * 支付成功时间。
     * <p>对应数据库列：{@code pay_time}，仅保存上游明确返回的支付成功时间。</p>
     */
    @TableField("pay_time")
    private LocalDateTime payTime;

    /**
     * 上游订单创建时间。
     * <p>对应数据库列：{@code order_create_time}，用于区分订单事实时间与本地入库分区时间。</p>
     */
    @TableField("order_create_time")
    private LocalDateTime orderCreateTime;

    /**
     * 结算时间
     * <p>对应数据库列：{@code settle_time}，订单完成结算的时间</p>
     */
    @TableField("settle_time")
    private LocalDateTime settleTime;

    /**
     * 归属状态
     * <p>对应数据库列：{@code attribution_status}，订单归属（attribution）的处理结果，
     * 如 "ATTRIBUTED"（已归属）、"UNATTRIBUTED"（未归属）、"DISPUTED"（争议中）等</p>
     */
    @TableField("attribution_status")
    private String attributionStatus;

    /**
     * 归属备注
     * <p>对应数据库列：{@code attribution_remark}，归属处理的补充说明信息</p>
     */
    @TableField("attribution_remark")
    private String attributionRemark;

    /**
     * 未归属原因
     * <p>非数据库持久化字段（exist = false），仅在未归属时填充，
     * 用于前端展示和问题排查</p>
     */
    @TableField(exist = false)
    private String unattributedReason;

    /**
     * 创建时间
     * <p>对应数据库列：{@code create_time}，订单记录入库时间，同时作为分区键</p>
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     * <p>对应数据库列：{@code update_time}，订单记录最后变更时间</p>
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     * <p>对应数据库列：{@code version}，用于并发更新控制（LCK-01），
     * 每次更新自动递增</p>
     */
    @Version
    @TableField("version")
    private Integer version;

    /**
     * 逻辑删除标记
     * <p>0=未删除, 1=已删除</p>
     */
    private Integer deleted;

    /**
     * 扩展数据
     * <p>JSON 格式，对应数据库列：{@code extra_data}，存储订单的附加属性，
     * 由 JacksonTypeHandler 自动序列化/反序列化</p>
     */
    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    /**
     * 同步来源。
     * <p>非数据库字段，用于持久化层判断本次更新属于事实源还是结算源，避免双轨字段互相覆盖。</p>
     */
    @TableField(exist = false)
    private String syncSource;

    /**
     * 获取主键 ID。
     *
     * @return 手动输入的 UUID 主键
     */
    public UUID getId() {
        return id;
    }

    /**
     * 设置主键 ID。
     *
     * @param id 手动输入的 UUID 主键
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * 获取订单 ID。
     *
     * @return 抖店平台的订单唯一标识
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * 设置订单 ID。
     *
     * @param orderId 抖店平台的订单唯一标识
     */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /**
     * 获取商品 ID。
     *
     * @return 订单关联的商品标识
     */
    public String getProductId() {
        return productId;
    }

    /**
     * 设置商品 ID。
     *
     * @param productId 订单关联的商品标识
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }

    /**
     * 获取商品名称。
     *
     * @return 冗余存储的商品名称，便于列表展示
     */
    public String getProductName() {
        return productName;
    }

    /**
     * 设置商品名称。
     *
     * @param productName 商品名称
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * 获取店铺 ID。
     *
     * @return 订单所属抖店店铺标识
     */
    public Long getShopId() {
        return shopId;
    }

    /**
     * 设置店铺 ID。
     *
     * @param shopId 订单所属抖店店铺标识
     */
    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    /**
     * 获取店铺名称。
     *
     * @return 冗余存储的店铺名称，便于展示
     */
    public String getShopName() {
        return shopName;
    }

    /**
     * 设置店铺名称。
     *
     * @param shopName 店铺名称
     */
    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    /**
     * 获取订单金额（单位：分）。
     *
     * @return 订单的原始金额
     */
    public Long getOrderAmount() {
        return orderAmount;
    }

    /**
     * 设置订单金额（单位：分）。
     *
     * @param orderAmount 订单的原始金额
     */
    public void setOrderAmount(Long orderAmount) {
        this.orderAmount = orderAmount;
    }

    /**
     * 获取实付金额（单位：分）。
     *
     * @return 用户实际支付的金额
     */
    public Long getActualAmount() {
        return actualAmount;
    }

    /**
     * 设置实付金额（单位：分）。
     *
     * @param actualAmount 用户实际支付的金额
     */
    public void setActualAmount(Long actualAmount) {
        this.actualAmount = actualAmount;
    }

    /**
     * 获取团长百应 ID。
     *
     * @return 第一团长的百应标识，用于关联团长活动和佣金结算
     */
    public Long getColonelBuyinId() {
        return colonelBuyinId;
    }

    /**
     * 设置团长百应 ID。
     *
     * @param colonelBuyinId 第一团长的百应标识
     */
    public void setColonelBuyinId(Long colonelBuyinId) {
        this.colonelBuyinId = colonelBuyinId;
    }

    /**
     * 获取团长佣金（单位：分）。
     *
     * @return 第一团长的结算佣金金额
     */
    public Long getSettleColonelCommission() {
        return settleColonelCommission;
    }

    /**
     * 设置团长佣金（单位：分）。
     *
     * @param settleColonelCommission 第一团长的结算佣金金额
     */
    public void setSettleColonelCommission(Long settleColonelCommission) {
        this.settleColonelCommission = settleColonelCommission;
    }

    /**
     * 获取团长技术服务费（单位：分）。
     *
     * @return 第一团长需承担的技术服务费金额
     */
    public Long getSettleColonelTechServiceFee() {
        return settleColonelTechServiceFee;
    }

    /**
     * 设置团长技术服务费（单位：分）。
     *
     * @param settleColonelTechServiceFee 第一团长需承担的技术服务费金额
     */
    public void setSettleColonelTechServiceFee(Long settleColonelTechServiceFee) {
        this.settleColonelTechServiceFee = settleColonelTechServiceFee;
    }

    /**
     * 获取第二团长百应 ID。
     *
     * @return 双团长模式下第二团长的百应标识，非双团长模式时为 null
     */
    public Long getSecondColonelBuyinId() {
        return secondColonelBuyinId;
    }

    /**
     * 设置第二团长百应 ID。
     *
     * @param secondColonelBuyinId 双团长模式下第二团长的百应标识
     */
    public void setSecondColonelBuyinId(Long secondColonelBuyinId) {
        this.secondColonelBuyinId = secondColonelBuyinId;
    }

    /**
     * 获取第二团长活动 ID。
     *
     * @return 双团长模式下第二团长的活动标识
     */
    public String getSecondActivityId() {
        return secondActivityId;
    }

    /**
     * 设置第二团长活动 ID。
     *
     * @param secondActivityId 双团长模式下第二团长的活动标识
     */
    public void setSecondActivityId(String secondActivityId) {
        this.secondActivityId = secondActivityId;
    }

    /**
     * 获取第二团长佣金（单位：分）。
     *
     * @return 双团长模式下第二团长的结算佣金金额
     */
    public Long getSettleSecondColonelCommission() {
        return settleSecondColonelCommission;
    }

    /**
     * 设置第二团长佣金（单位：分）。
     *
     * @param settleSecondColonelCommission 双团长模式下第二团长的结算佣金金额
     */
    public void setSettleSecondColonelCommission(Long settleSecondColonelCommission) {
        this.settleSecondColonelCommission = settleSecondColonelCommission;
    }

    /**
     * 获取结算阶段 ID。
     *
     * @return 标识订单所属的结算周期/批次
     */
    public String getPhaseId() {
        return phaseId;
    }

    /**
     * 设置结算阶段 ID。
     *
     * @param phaseId 标识订单所属的结算周期/批次
     */
    public void setPhaseId(String phaseId) {
        this.phaseId = phaseId;
    }

    /**
     * 获取订单状态。
     *
     * @return 抖店平台的订单状态码，如待付款、已付款、已发货、已完成、已退款等
     */
    public Integer getOrderStatus() {
        return orderStatus;
    }

    /**
     * 设置订单状态。
     *
     * @param orderStatus 抖店平台的订单状态码
     */
    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    /**
     * 获取订单类型。
     *
     * @return 标识订单的业务类型分类
     */
    public Integer getOrderType() {
        return orderType;
    }

    /**
     * 设置订单类型。
     *
     * @param orderType 标识订单的业务类型分类
     */
    public void setOrderType(Integer orderType) {
        this.orderType = orderType;
    }

    /**
     * 获取推广来源标识。
     *
     * @return 订单关联的推广来源编码，用于通过 PickSourceMapping 进行订单归属
     */
    public String getPickSource() {
        return pickSource;
    }

    /**
     * 设置推广来源标识。
     *
     * @param pickSource 订单关联的推广来源编码
     */
    public void setPickSource(String pickSource) {
        this.pickSource = pickSource;
    }

    /**
     * 获取分页游标。
     *
     * @return 订单同步时的分页游标标识，用于增量拉取订单数据
     */
    public String getCursor() {
        return cursor;
    }

    /**
     * 设置分页游标。
     *
     * @param cursor 订单同步时的分页游标标识
     */
    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    /**
     * 获取渠道用户 ID。
     *
     * @return 订单归属的渠道业务人员标识
     */
    public UUID getChannelUserId() {
        return channelUserId;
    }

    /**
     * 设置渠道用户 ID。
     *
     * @param channelUserId 订单归属的渠道业务人员标识
     */
    public void setChannelUserId(UUID channelUserId) {
        this.channelUserId = channelUserId;
    }

    /**
     * 获取负责人用户 ID。
     *
     * @return 订单最终归属的业务负责人标识
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * 设置负责人用户 ID。
     *
     * @param userId 订单最终归属的业务负责人标识
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    /**
     * 获取渠道部门 ID。
     *
     * @return 渠道业务人员所属部门，用于部门级数据过滤
     */
    public UUID getChannelDeptId() {
        return channelDeptId;
    }

    /**
     * 设置渠道部门 ID。
     *
     * @param channelDeptId 渠道业务人员所属部门标识
     */
    public void setChannelDeptId(UUID channelDeptId) {
        this.channelDeptId = channelDeptId;
    }

    /**
     * 获取部门 ID。
     *
     * @return 负责人所属部门，用于数据范围过滤
     */
    public UUID getDeptId() {
        return deptId;
    }

    /**
     * 设置部门 ID。
     *
     * @param deptId 负责人所属部门标识
     */
    public void setDeptId(UUID deptId) {
        this.deptId = deptId;
    }

    /**
     * 获取活动 ID。
     *
     * @return 订单关联的团长活动标识
     */
    public String getActivityId() {
        return activityId;
    }

    /**
     * 设置活动 ID。
     *
     * @param activityId 订单关联的团长活动标识
     */
    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    /**
     * 获取结算时间。
     *
     * @return 订单完成结算的时间
     */
    public LocalDateTime getSettleTime() {
        return settleTime;
    }

    /**
     * 设置结算时间。
     *
     * @param settleTime 订单完成结算的时间
     */
    public void setSettleTime(LocalDateTime settleTime) {
        this.settleTime = settleTime;
    }

    /**
     * 获取归属状态。
     *
     * @return 订单归属（attribution）的处理结果，如 "ATTRIBUTED"、"UNATTRIBUTED"、"DISPUTED" 等
     */
    public String getAttributionStatus() {
        return attributionStatus;
    }

    /**
     * 设置归属状态。
     *
     * @param attributionStatus 订单归属的处理结果
     */
    public void setAttributionStatus(String attributionStatus) {
        this.attributionStatus = attributionStatus;
    }

    /**
     * 获取归属备注。
     *
     * @return 归属处理的补充说明信息
     */
    public String getAttributionRemark() {
        return attributionRemark;
    }

    /**
     * 设置归属备注。
     *
     * @param attributionRemark 归属处理的补充说明信息
     */
    public void setAttributionRemark(String attributionRemark) {
        this.attributionRemark = attributionRemark;
    }

    /**
     * 获取创建时间。
     *
     * @return 订单记录入库时间，同时作为分区键
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     *
     * @param createTime 订单记录入库时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间。
     *
     * @return 订单记录最后变更时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间。
     *
     * @param updateTime 订单记录最后变更时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取逻辑删除标记。
     *
     * @return 逻辑删除标记，0=未删除, 1=已删除
     */
    public Integer getDeleted() {
        return deleted;
    }

    /**
     * 设置逻辑删除标记。
     *
     * @param deleted 逻辑删除标记，0=未删除, 1=已删除
     */
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    /**
     * 获取扩展数据。
     *
     * @return JSON 格式的订单附加属性，由 JacksonTypeHandler 自动序列化/反序列化
     */
    public Map<String, Object> getExtraData() {
        return extraData;
    }

    /**
     * 设置扩展数据。
     *
     * @param extraData JSON 格式的订单附加属性
     */
    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }
}
