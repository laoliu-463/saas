package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 商品快照数据访问层
 * <p>
 * 对应数据库表：product_snapshot
 * 所属业务领域：商品域 - 商品快照管理
 * 主要操作：商品快照的 UPSERT 写入，以及已上架且选入精选库商品的品类查询
 * </p>
 *
 * @see com.colonel.saas.entity.ProductSnapshot
 */
@Mapper
public interface ProductSnapshotMapper extends BaseMapper<ProductSnapshot> {

    /**
     * 插入或更新商品快照（UPSERT）
     * <p>
     * 基于业务主键做冲突更新，当快照已存在时覆盖更新。
     * 用于商品信息同步时记录快照数据。
     * </p>
     *
     * @param snapshot 商品快照实体
     * @return 受影响行数
     */
    int upsert(@Param("snapshot") ProductSnapshot snapshot);

    /**
     * 查询已上架且已选入精选库商品的品类名称列表
     * <p>
     * 关联 product_operation_state 表，筛选条件：
     * - display_status = 'DISPLAYING'（已上架展示中）
     * - selected_to_library = TRUE（已选入精选库）
     * - 品类名称非空
     * 结果去重并按品类名称排序。
     * </p>
     *
     * @return 品类名称列表
     */
    @Select("""
            SELECT DISTINCT ps.category_name
            FROM product_snapshot ps
            INNER JOIN product_operation_state pos
                ON ps.activity_id = pos.activity_id AND ps.product_id = pos.product_id
            WHERE pos.display_status = 'DISPLAYING'
              AND pos.selected_to_library = TRUE
              AND pos.deleted = 0
              AND ps.status = 1
              AND (pos.audit_status IS NULL OR pos.audit_status <> 3)
              AND COALESCE(pos.manual_disabled, FALSE) = FALSE
              AND ps.category_name IS NOT NULL
              AND TRIM(ps.category_name) <> ''
            ORDER BY ps.category_name
            """)
    List<String> listDisplayingLibraryCategoryNames();

    /**
     * 查询已上架且已选入精选库商品的品类选项（含商品数量统计）
     * <p>
     * 返回格式为 label/count 的 Map 列表，适用于前端下拉筛选器。
     * 筛选条件与 {@link #listDisplayingLibraryCategoryNames()} 一致。
     * </p>
     *
     * @return 品类选项列表，每项包含 label（品类名称）和 count（商品数量）
     */
    @Select("""
            SELECT category_name AS label, COUNT(*) AS count
            FROM (
                SELECT TRIM(ps.category_name) AS category_name
                FROM product_snapshot ps
                INNER JOIN product_operation_state pos
                    ON ps.activity_id = pos.activity_id AND ps.product_id = pos.product_id
                WHERE pos.display_status = 'DISPLAYING'
                  AND pos.selected_to_library = TRUE
                  AND pos.deleted = 0
                  AND ps.status = 1
                  AND (pos.audit_status IS NULL OR pos.audit_status <> 3)
                  AND COALESCE(pos.manual_disabled, FALSE) = FALSE
                  AND ps.category_name IS NOT NULL
                  AND TRIM(ps.category_name) <> ''
            ) categories
            GROUP BY category_name
            ORDER BY category_name
            """)
    List<Map<String, Object>> listDisplayingLibraryCategoryOptions();

    /**
     * SQL 级别排序 + 分页查询活动商品快照。
     * <p>
     * 用于解决非 latest 排序时全量加载再内存排序的性能问题。
     * LEFT JOIN product_operation_state 以便按推广链接和置顶状态排序；
     * WHERE 条件由外层 LambdaQueryWrapper 构造。
     * </p>
     *
     * @param activityId  活动 ID（固定条件，写入 SQL）
     * @param limit       本次查询上限（MySQL LIMIT）
     * @param offset      偏移量（MySQL OFFSET）
     * @return 按 (置顶 DESC, 已推广 DESC, 佣金 DESC, 同步时间 DESC) 排序的商品快照
     */
    /**
     * SQL 级别排序 + 分页查询活动商品快照。
     * <p>
     * 用于解决非 latest 排序时全量加载再内存排序的性能问题。
     * 具体 SQL 实现见 {@code ProductSnapshotMapper.xml}。
     * </p>
     *
     * @param activityId        活动 ID
     * @param promotionStatus   推广状态过滤（可 null）
     * @param productInfo       商品信息模糊搜索（可 null/空）
     * @param bizStatusFilterMode BizStatusFilter 模式：NONE / EMPTY / INCLUDE_ONLY / PENDING_AUDIT
     * @param includeProductIds IN 过滤的商品 ID 列表（PENDING_AUDIT / INCLUDE_ONLY 模式使用）
     * @param excludeProductIds NOT IN 过滤的商品 ID 列表（PENDING_AUDIT 模式使用）
     * @param productIdScope    audit-tag 过滤的商品 ID 范围（可 null/空）
     * @param limit             本次查询上限
     * @param offset            偏移量
     * @param now               当前时间（用于 pinned_until 比较）
     * @return 按 (置顶 DESC, 已推广 DESC, 佣金 DESC, 同步时间 DESC) 排序的商品快照
     */
    List<ProductSnapshot> selectPageSorted(
            @Param("activityId")            String          activityId,
            @Param("promotionStatus")      Integer         promotionStatus,
            @Param("productInfo")           String          productInfo,
            @Param("bizStatusFilterMode")   String          bizStatusFilterMode,
            @Param("includeProductIds")     java.util.List<String> includeProductIds,
            @Param("excludeProductIds")     java.util.List<String> excludeProductIds,
            @Param("productIdScope")        java.util.List<String> productIdScope,
            @Param("limit")                 long            limit,
            @Param("offset")                long            offset,
            @Param("now")                  java.time.LocalDateTime now);
}
