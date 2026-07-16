package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.TalentComplaintReminder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 达人投诉提醒数据访问层。
 */
@Mapper
public interface TalentComplaintReminderMapper extends BaseMapper<TalentComplaintReminder> {

    @Select("""
            SELECT *
            FROM talent_complaint_reminder
            WHERE recipient_user_id = #{recipientUserId}
              AND deleted = 0
            ORDER BY create_time DESC
            """)
    List<TalentComplaintReminder> selectByRecipientUserId(
            @Param("recipientUserId") UUID recipientUserId);
}
