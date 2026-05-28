package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.DataScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SampleRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.util.UUID;

/**
 * 寄样申请数据访问层
 * <p>
 * 对应数据库表：sample_request
 * 所属业务领域：寄样域 - 寄样申请管理
 * 主要操作：寄样申请的分页查询，支持数据权限范围过滤和招稿人筛选，
 * 提供渠道用户视角和审核人视角两种查询模式
 * </p>
 *
 * @see com.colonel.saas.entity.SampleRequest
 */
@Mapper
public interface SampleRequestMapper extends BaseMapper<SampleRequest> {

    /**
     * 带数据权限范围的分页查询寄样申请（无招稿人筛选）
     * <p>
     * 默认方法，委托给带 recruiterUserId 参数的重载方法，recruiterUserId 传 null。
     * </p>
     *
     * @param page    分页参数
     * @param wrapper 查询条件构造器
     * @return 分页结果
     */
    default IPage<SampleRequest> findPageWithScope(
            Page<SampleRequest> page,
            QueryWrapper<SampleRequest> wrapper
    ) {
        return findPageWithScope(page, wrapper, null);
    }

    /**
     * 带数据权限范围的分页查询寄样申请
     * <p>
     * 通过 @DataScope 注解按 sr.channel_user_id 字段注入数据权限过滤。
     * 当指定 recruiterUserId 时，额外关联 product 和 product_operation_state 表，
     * 只返回该招稿人负责商品对应的寄样申请。
     * </p>
     *
     * @param page             分页参数
     * @param wrapper          查询条件构造器
     * @param recruiterUserId  招稿人用户 ID，为 null 时不过滤招稿人
     * @return 分页结果
     */
    @DataScope(userField = "sr.channel_user_id")
    @Select("""
            <script>
            SELECT DISTINCT sr.*
            FROM sample_request sr
            <if test="recruiterUserId != null">
                JOIN product p_filter ON p_filter.id = sr.product_id AND p_filter.deleted = 0
                JOIN product_operation_state pos_filter
                    ON p_filter.product_id = pos_filter.product_id
                    AND pos_filter.assignee_id = #{recruiterUserId}
            </if>
            WHERE sr.deleted = 0
            <if test="ew != null and ew.sqlSegment != null and ew.sqlSegment != ''">
                AND ${ew.sqlSegment}
            </if>
            ORDER BY sr.create_time DESC
            </script>
            """)
    IPage<SampleRequest> findPageWithScope(
            Page<SampleRequest> page,
            @Param(Constants.WRAPPER) QueryWrapper<SampleRequest> wrapper,
            @Param("recruiterUserId") UUID recruiterUserId
    );

    /**
     * 审核人视角的分页查询寄样申请（无招稿人筛选）
     * <p>
     * 默认方法，委托给带 recruiterUserId 参数的重载方法，recruiterUserId 传 null。
     * </p>
     *
     * @param page    分页参数
     * @param userId  当前审核人用户 ID
     * @param wrapper 查询条件构造器
     * @return 分页结果
     */
    default IPage<SampleRequest> findPageForAuditor(
            Page<SampleRequest> page,
            java.util.UUID userId,
            QueryWrapper<SampleRequest> wrapper
    ) {
        return findPageForAuditor(page, userId, wrapper, null);
    }

    /**
     * 审核人视角的分页查询寄样申请
     * <p>
     * 通过关联 product 和 product_operation_state 表，
     * 只返回 assignee_id 匹配当前审核人的商品对应的寄样申请。
     * 当指定 recruiterUserId 时进一步筛选特定招稿人。
     * </p>
     *
     * @param page             分页参数
     * @param userId           当前审核人用户 ID（匹配 product_operation_state.assignee_id）
     * @param wrapper          查询条件构造器
     * @param recruiterUserId  招稿人用户 ID，为 null 时不过滤
     * @return 分页结果
     */
    @Select("""
            <script>
            SELECT DISTINCT sr.*
            FROM sample_request sr
            JOIN product p ON p.id = sr.product_id AND p.deleted = 0
            JOIN product_operation_state pos ON p.product_id = pos.product_id
            WHERE sr.deleted = 0
            AND pos.assignee_id = #{userId}
            <if test="recruiterUserId != null">
                AND pos.assignee_id = #{recruiterUserId}
            </if>
            <if test="ew != null and ew.sqlSegment != null and ew.sqlSegment != ''">
                AND ${ew.sqlSegment}
            </if>
            ORDER BY sr.create_time DESC
            </script>
            """)
    IPage<SampleRequest> findPageForAuditor(
            Page<SampleRequest> page,
            @Param("userId") UUID userId,
            @Param(Constants.WRAPPER) QueryWrapper<SampleRequest> wrapper,
            @Param("recruiterUserId") UUID recruiterUserId
    );
}
