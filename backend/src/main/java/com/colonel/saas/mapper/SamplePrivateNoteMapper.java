package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SamplePrivateNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * 合作单私有备注数据访问层。
 */
@Mapper
public interface SamplePrivateNoteMapper extends BaseMapper<SamplePrivateNote> {

    @Select("""
            SELECT *
            FROM sample_private_note
            WHERE sample_request_id = #{sampleRequestId}
              AND user_id = #{userId}
              AND deleted = 0
            LIMIT 1
            """)
    SamplePrivateNote selectBySampleRequestAndUser(
            @Param("sampleRequestId") UUID sampleRequestId,
            @Param("userId") UUID userId);
}
