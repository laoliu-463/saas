package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.DouyinWebhookEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抖音 Webhook 事件数据访问层
 * <p>
 * 对应数据库表：douyin_webhook_event
 * 所属业务领域：订单域 - 抖音事件接收
 * 主要操作：抖音 Webhook 事件记录的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.DouyinWebhookEvent
 */
@Mapper
public interface DouyinWebhookEventMapper extends BaseMapper<DouyinWebhookEvent> {
}
