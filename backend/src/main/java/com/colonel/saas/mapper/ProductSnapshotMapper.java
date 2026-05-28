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
                  AND ps.category_name IS NOT NULL
                  AND TRIM(ps.category_name) <> ''
            ) categories
            GROUP BY category_name
            ORDER BY category_name
            """)
    List<Map<String, Object>> listDisplayingLibraryCategoryOptions();
}
