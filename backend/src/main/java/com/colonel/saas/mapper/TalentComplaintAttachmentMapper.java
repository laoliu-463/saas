package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentComplaintAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 达人投诉附件数据访问层。
 */
@Mapper
public interface TalentComplaintAttachmentMapper extends BaseMapper<TalentComplaintAttachment> {

    @Select("""
            SELECT *
            FROM talent_complaint_attachment
            WHERE complaint_id = #{complaintId}
              AND deleted = 0
            ORDER BY create_time ASC
            """)
    List<TalentComplaintAttachment> selectByComplaintId(@Param("complaintId") UUID complaintId);

    @Select("""
            SELECT *
            FROM talent_complaint_attachment
            WHERE id = #{attachmentId}
              AND complaint_id = #{complaintId}
              AND deleted = 0
            LIMIT 1
            """)
    TalentComplaintAttachment selectByIdAndComplaintId(
            @Param("attachmentId") UUID attachmentId,
            @Param("complaintId") UUID complaintId);

    @Select("""
            <script>
            SELECT storage_key
            FROM talent_complaint_attachment
            WHERE storage_key IN
            <foreach collection="storageKeys" item="storageKey" open="(" separator="," close=")">
              #{storageKey}
            </foreach>
            </script>
            """)
    List<String> selectExistingStorageKeys(
            @Param("storageKeys") Collection<String> storageKeys);
}
