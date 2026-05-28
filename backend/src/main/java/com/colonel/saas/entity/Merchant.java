package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

/**
 * 商家实体。
 * <p>
 * 对应数据库表：{@code merchant}，记录抖店平台商家的基础信息。
 * 商家通过订单同步自动入库，包含商家名称、店铺信息、负责人归属等核心字段。
 * 商家是业绩归属和独家协议的关键业务对象。
 * 继承 {@link com.colonel.saas.common.base.VersionedEntity}，拥有乐观锁支持。
 * </p>
 *
 * @see ExclusiveMerchant 独家商家协议
 * @see Product 商品，关联商家店铺
 * @see ColonelsettlementOrder 结算订单，关联商家信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "merchant", autoResultMap = true)
public class Merchant extends VersionedEntity {

    /**
     * 商家 ID
     * <p>对应数据库列：{@code merchant_id}，抖店平台的商家唯一标识</p>
     */
    @TableField("merchant_id")
    private String merchantId;

    /**
     * 商家名称
     * <p>对应数据库列：{@code merchant_name}，抖店平台的商家注册名称</p>
     */
    @TableField("merchant_name")
    private String merchantName;

    /**
     * 店铺 ID
     * <p>对应数据库列：{@code shop_id}，抖店平台的店铺唯一标识</p>
     */
    @TableField("shop_id")
    private Long shopId;

    /**
     * 店铺名称
     * <p>对应数据库列：{@code shop_name}，抖店平台的店铺显示名称</p>
     */
    @TableField("shop_name")
    private String shopName;

    /**
     * 来源订单 ID
     * <p>对应数据库列：{@code source_order_id}，首次同步该商家时所关联的订单 ID，
     * 用于追溯商家入库来源</p>
     */
    @TableField("source_order_id")
    private String sourceOrderId;

    /**
     * 状态
     * <p>1=正常, 0=禁用。用于控制商家在系统中的可用性</p>
     */
    private Integer status;

    /**
     * 扩展数据
     * <p>JSON 格式，对应数据库列：{@code extra_data}，存储商家的附加属性和扩展信息，
     * 由 JacksonTypeHandler 自动序列化/反序列化</p>
     */
    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    /**
     * 负责人用户 ID
     * <p>对应数据库列：{@code owner_id}，该商家在系统中归属的业务负责人，
     * 用于数据范围过滤和业绩归属</p>
     */
    @TableField("owner_id")
    private UUID ownerId;

    /**
     * 负责人所属部门 ID
     * <p>对应数据库列：{@code owner_dept_id}，负责人的部门归属，
     * 用于部门级数据范围过滤（self/group/all）</p>
     */
    @TableField("owner_dept_id")
    private UUID ownerDeptId;

    /**
     * 获取商家 ID。
     *
     * @return 抖店平台的商家唯一标识
     */
    public String getMerchantId() {
        return merchantId;
    }

    /**
     * 设置商家 ID。
     *
     * @param merchantId 抖店平台的商家唯一标识
     */
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    /**
     * 获取商家名称。
     *
     * @return 抖店平台的商家注册名称
     */
    public String getMerchantName() {
        return merchantName;
    }

    /**
     * 设置商家名称。
     *
     * @param merchantName 抖店平台的商家注册名称
     */
    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    /**
     * 获取店铺 ID。
     *
     * @return 抖店平台的店铺唯一标识
     */
    public Long getShopId() {
        return shopId;
    }

    /**
     * 设置店铺 ID。
     *
     * @param shopId 抖店平台的店铺唯一标识
     */
    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    /**
     * 获取店铺名称。
     *
     * @return 抖店平台的店铺显示名称
     */
    public String getShopName() {
        return shopName;
    }

    /**
     * 设置店铺名称。
     *
     * @param shopName 抖店平台的店铺显示名称
     */
    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    /**
     * 获取来源订单 ID。
     *
     * @return 首次同步该商家时所关联的订单 ID
     */
    public String getSourceOrderId() {
        return sourceOrderId;
    }

    /**
     * 设置来源订单 ID。
     *
     * @param sourceOrderId 首次同步该商家时所关联的订单 ID
     */
    public void setSourceOrderId(String sourceOrderId) {
        this.sourceOrderId = sourceOrderId;
    }

    /**
     * 获取商家状态。
     *
     * @return 商家状态值，1=正常，0=禁用
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置商家状态。
     *
     * @param status 商家状态值，1=正常，0=禁用
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取扩展数据。
     *
     * @return JSON 格式的商家附加属性和扩展信息
     */
    public Map<String, Object> getExtraData() {
        return extraData;
    }

    /**
     * 设置扩展数据。
     *
     * @param extraData JSON 格式的商家附加属性和扩展信息
     */
    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }
}
