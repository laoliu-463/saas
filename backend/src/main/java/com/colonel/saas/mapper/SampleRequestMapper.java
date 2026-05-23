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

@Mapper
public interface SampleRequestMapper extends BaseMapper<SampleRequest> {

    default IPage<SampleRequest> findPageWithScope(
            Page<SampleRequest> page,
            QueryWrapper<SampleRequest> wrapper
    ) {
        return findPageWithScope(page, wrapper, null);
    }

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

    default IPage<SampleRequest> findPageForAuditor(
            Page<SampleRequest> page,
            java.util.UUID userId,
            QueryWrapper<SampleRequest> wrapper
    ) {
        return findPageForAuditor(page, userId, wrapper, null);
    }

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
