package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductSnapshotMapper extends BaseMapper<ProductSnapshot> {

    int upsert(@Param("snapshot") ProductSnapshot snapshot);

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
