package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 团长结算订单数据访问层
 * <p>
 * 对应数据库表：colonelsettlement_order
 * 所属业务领域：订单域 - 结算订单管理
 * 主要操作：结算订单的查询、幂等插入、同步更新，支持数据权限范围过滤
 * </p>
 *
 * @see com.colonel.saas.entity.ColonelsettlementOrder
 */
@Mapper
public interface ColonelsettlementOrderMapper extends BaseMapper<ColonelsettlementOrder> {

    /**
     * 根据订单号查询结算订单
     *
     * @param orderId 抖音订单号
     * @return 对应的结算订单记录，不存在时返回 null
     */
    ColonelsettlementOrder findByOrderId(@Param("orderId") String orderId);

    /**
     * 幂等插入结算订单
     * <p>
     * 基于 order_id 做冲突忽略，如果订单已存在则跳过插入。
     * 用于订单同步场景，防止重复写入。
     * </p>
     *
     * @param order 待插入的结算订单实体
     * @return 受影响行数（0 表示已存在被忽略，1 表示新增成功）
     */
    int insertIgnoreByOrderId(ColonelsettlementOrder order);

    /**
     * 根据主键更新已同步的结算订单
     * <p>
     * 用于订单二次同步时更新已存在的订单数据
     * </p>
     *
     * @param order 待更新的结算订单实体（需包含主键 id）
     * @return 受影响行数
     */
    int updateSyncedById(ColonelsettlementOrder order);

    /**
     * 订单分页查询。
     * <p>
     * 数据范围过滤由 Controller/Service 在传入的 {@code wrapper} 中显式追加，
     * 本 Mapper 不再叠加 {@code @DataScope} 切面，避免同一 SQL 同时走注解和手动
     * wrapper 两套口径。
     * </p>
     *
     * @param page    分页参数
     * @param wrapper 查询条件构造器
     * @return 分页结果
     */
    IPage<ColonelsettlementOrder> findPageWithScope(
            Page<ColonelsettlementOrder> page,
            @Param(Constants.WRAPPER) QueryWrapper<ColonelsettlementOrder> wrapper
    );

    /**
     * 批量读取订单列表展示所需的商品扩展字段。
     *
     * <p>列表主查询仍排除 {@code extra_data} 大字段；这里仅投影图片、数量、
     * 佣金率和服务费率四个轻量字段，用于补齐订单列表的商品信息列。</p>
     */
    @Select("""
            <script>
            SELECT
                order_id AS "orderId",
                COALESCE(
                    NULLIF(product_pic, ''),
                    NULLIF(extra_data ->> 'product_img', ''),
                    NULLIF(extra_data ->> 'productImg', ''),
                    NULLIF(extra_data ->> 'product_pic', ''),
                    NULLIF(extra_data ->> 'productPic', '')
                ) AS "productPic",
                CASE
                    WHEN COALESCE(
                        NULLIF(extra_data ->> 'item_num', ''),
                        NULLIF(extra_data ->> 'itemNum', ''),
                        NULLIF(extra_data ->> 'quantity', ''),
                        NULLIF(extra_data ->> 'productQuantity', ''),
                        NULLIF(extra_data ->> 'goods_num', ''),
                        NULLIF(extra_data ->> 'goodsNum', '')
                    ) ~ '^[0-9]+$'
                    THEN COALESCE(
                        NULLIF(extra_data ->> 'item_num', ''),
                        NULLIF(extra_data ->> 'itemNum', ''),
                        NULLIF(extra_data ->> 'quantity', ''),
                        NULLIF(extra_data ->> 'productQuantity', ''),
                        NULLIF(extra_data ->> 'goods_num', ''),
                        NULLIF(extra_data ->> 'goodsNum', '')
                    )::INTEGER
                    ELSE NULL
                END AS "itemNum",
                CASE
                    WHEN COALESCE(
                        NULLIF(extra_data ->> 'commission_rate', ''),
                        NULLIF(extra_data ->> 'commissionRate', ''),
                        NULLIF(extra_data ->> 'cos_ratio', ''),
                        NULLIF(extra_data ->> 'cosRatio', '')
                    ) ~ '^[0-9]+([.][0-9]+)?$'
                    THEN COALESCE(
                        NULLIF(extra_data ->> 'commission_rate', ''),
                        NULLIF(extra_data ->> 'commissionRate', ''),
                        NULLIF(extra_data ->> 'cos_ratio', ''),
                        NULLIF(extra_data ->> 'cosRatio', '')
                    )::NUMERIC
                    ELSE NULL
                END AS "commissionRate",
                CASE
                    WHEN COALESCE(
                        NULLIF(extra_data ->> 'service_fee_rate', ''),
                        NULLIF(extra_data ->> 'serviceFeeRate', ''),
                        NULLIF(extra_data ->> 'service_rate', ''),
                        NULLIF(extra_data ->> 'serviceRate', '')
                    ) ~ '^[0-9]+([.][0-9]+)?$'
                    THEN COALESCE(
                        NULLIF(extra_data ->> 'service_fee_rate', ''),
                        NULLIF(extra_data ->> 'serviceFeeRate', ''),
                        NULLIF(extra_data ->> 'service_rate', ''),
                        NULLIF(extra_data ->> 'serviceRate', '')
                    )::NUMERIC
                    ELSE NULL
                END AS "serviceFeeRate"
            FROM colonelsettlement_order
            WHERE deleted = 0
              AND order_id IN
              <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
                  #{orderId}
              </foreach>
            </script>
            """)
    List<Map<String, Object>> listDisplayProductInfoByOrderIds(@Param("orderIds") List<String> orderIds);
}
