package com.colonel.saas.domain.user.port;

/**
 * 用户渠道编码占用查询端口。
 *
 * <p>渠道编码用于推广链接和订单归因，已软删除用户的编码也必须视为占用。</p>
 */
@FunctionalInterface
public interface UserChannelCodeRegistry {

    boolean isOccupied(String channelCode);
}
