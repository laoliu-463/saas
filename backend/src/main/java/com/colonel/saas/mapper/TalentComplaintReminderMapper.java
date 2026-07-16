package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentComplaintReminder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 达人投诉提醒数据访问层。
 */
@Mapper
public interface TalentComplaintReminderMapper extends BaseMapper<TalentComplaintReminder> {

    @Select("""
            <script>
            SELECT *
            FROM talent_complaint_reminder
            WHERE recipient_user_id = #{recipientUserId}
              AND deleted = 0
            <if test="beforeCreateTime != null and beforeId != null">
              AND (create_time, id) &lt; (#{beforeCreateTime}, #{beforeId})
            </if>
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<TalentComplaintReminder> selectPageByRecipientUserId(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("beforeCreateTime") LocalDateTime beforeCreateTime,
            @Param("beforeId") UUID beforeId,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM talent_complaint_reminder
            WHERE recipient_user_id = #{recipientUserId}
              AND deleted = 0
              AND read_at IS NULL
            """)
    long countUnreadByRecipientUserId(@Param("recipientUserId") UUID recipientUserId);

    @Select("""
            SELECT *
            FROM talent_complaint_reminder
            WHERE id = #{id}
              AND recipient_user_id = #{recipientUserId}
              AND deleted = 0
            LIMIT 1
            """)
    TalentComplaintReminder selectByIdAndRecipientUserId(
            @Param("id") UUID id,
            @Param("recipientUserId") UUID recipientUserId);

    @Update("""
            UPDATE talent_complaint_reminder
            SET read_at = #{readAt},
                update_by = #{recipientUserId},
                update_time = now(),
                version = version + 1
            WHERE id = #{id}
              AND recipient_user_id = #{recipientUserId}
              AND deleted = 0
              AND read_at IS NULL
            """)
    int markRead(
            @Param("id") UUID id,
            @Param("recipientUserId") UUID recipientUserId,
            @Param("readAt") LocalDateTime readAt);
}
