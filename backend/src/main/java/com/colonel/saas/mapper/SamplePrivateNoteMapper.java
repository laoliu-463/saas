package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SamplePrivateNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Insert("""
            INSERT INTO sample_private_note (
                id, sample_request_id, user_id, content, version, deleted,
                create_time, update_time, create_by, update_by
            ) VALUES (
                #{id}, #{sampleRequestId}, #{userId}, #{content}, 0, 0,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, #{userId}, #{userId}
            )
            ON CONFLICT (sample_request_id, user_id) WHERE deleted = 0
            DO UPDATE SET
                content = EXCLUDED.content,
                version = sample_private_note.version + 1,
                update_by = EXCLUDED.update_by,
                update_time = CURRENT_TIMESTAMP
            """)
    int upsertActive(SamplePrivateNote note);

    @Update("""
            UPDATE sample_private_note
            SET deleted = 1,
                version = version + 1,
                update_by = #{userId},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND user_id = #{userId}
              AND version = #{version}
              AND deleted = 0
            """)
    int softDeleteActive(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("version") Integer version);
}
