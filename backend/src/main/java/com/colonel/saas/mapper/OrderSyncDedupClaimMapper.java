package com.colonel.saas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

@Mapper
public interface OrderSyncDedupClaimMapper {

    @Insert("""
            INSERT INTO order_sync_dedup_claim (
                order_id, order_row_id, first_seen_at, last_seen_at
            ) VALUES (
                #{orderId}, #{orderRowId}, NOW(), NOW()
            )
            ON CONFLICT (order_id) DO NOTHING
            """)
    int claim(@Param("orderId") String orderId, @Param("orderRowId") UUID orderRowId);

    @Update("""
            UPDATE order_sync_dedup_claim
            SET order_row_id = #{orderRowId},
                last_seen_at = NOW()
            WHERE order_id = #{orderId}
              AND (order_row_id IS DISTINCT FROM #{orderRowId})
            """)
    int bindOrderRow(@Param("orderId") String orderId, @Param("orderRowId") UUID orderRowId);
}
