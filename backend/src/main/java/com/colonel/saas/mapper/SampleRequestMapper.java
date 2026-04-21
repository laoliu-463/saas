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

@Mapper
public interface SampleRequestMapper extends BaseMapper<SampleRequest> {

    @DataScope(userField = "sr.channel_user_id")
    @Select("""
            <script>
            SELECT sr.*
            FROM sample_request sr
            WHERE sr.deleted = 0
            <if test="ew != null and ew.sqlSegment != null and ew.sqlSegment != ''">
                AND ${ew.sqlSegment}
            </if>
            ORDER BY sr.create_time DESC
            </script>
            """)
    IPage<SampleRequest> findPageWithScope(
            Page<SampleRequest> page,
            @Param(Constants.WRAPPER) QueryWrapper<SampleRequest> wrapper
    );
}
