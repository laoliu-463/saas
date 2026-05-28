package com.colonel.saas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 订单同步去重声明数据访问层
 * <p>
 * 对应数据库表：order_sync_dedup_claim
 * 所属业务领域：订单域 - 订单同步去重
 * 主要操作：订单同步时的幂等去重声明，防止同一订单在并发同步场景下被重复处理
 * </p>
 */
@Mapper
public interface OrderSyncDedupClaimMapper {

    /**
     * 声明订单同步去重锁（幂等插入）
     * <p>
     * 基于 order_id 做冲突忽略（ON CONFLICT DO NOTHING），
     * 第一个成功插入的调用者获得该订单的处理权，后续调用返回 0 表示已被他人认领。
     * </p>
     *
     * @param orderId    抖音订单号
     * @param orderRowId 订单行记录 UUID
     * @return 受影响行数（1 表示成功认领，0 表示已被其他线程认领）
     */
    @Insert("""
            INSERT INTO order_sync_dedup_claim (
                order_id, order_row_id, first_seen_at, last_seen_at
            ) VALUES (
                #{orderId}, #{orderRowId}, NOW(), NOW()
            )
            ON CONFLICT (order_id) DO NOTHING
            """)
    int claim(@Param("orderId") String orderId, @Param("orderRowId") UUID orderRowId);

    /**
     * 绑定订单行 ID（条件更新）
     * <p>
     * 当 order_row_id 与当前值不同时才执行更新，同时刷新 last_seen_at 时间戳。
     * 用于订单行 ID 发生变更的场景（如订单重写后的新行记录）。
     * </p>
     *
     * @param orderId    抖音订单号
     * @param orderRowId 新的订单行记录 UUID
     * @return 受影响行数（0 表示无需更新，1 表示绑定成功）
     */
    @Update("""
            UPDATE order_sync_dedup_claim
            SET order_row_id = #{orderRowId},
                last_seen_at = NOW()
            WHERE order_id = #{orderId}
              AND (order_row_id IS DISTINCT FROM #{orderRowId})
            """)
    int bindOrderRow(@Param("orderId") String orderId, @Param("orderRowId") UUID orderRowId);
}
