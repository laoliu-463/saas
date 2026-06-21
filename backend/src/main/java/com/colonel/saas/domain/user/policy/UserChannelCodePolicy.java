package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.port.UserChannelCodeRegistry;

import java.util.UUID;

/**
 * 用户渠道编码策略（DDD-USER-MIGRATION-U8）。
 *
 * <p>负责把用户名转换为全局唯一的渠道短码。已软删除用户的渠道编码也视为占用，
 * 以保证推广链接和归因映射中的渠道来源稳定可追溯。</p>
 */
public class UserChannelCodePolicy {

    private static final int MAX_CHANNEL_CODE_LEN = 16;
    private static final int RANDOM_SUFFIX_LEN = 4;
    private static final int MAX_RETRY = 8;
    private static final String FALLBACK_CODE = "user";

    private final UserChannelCodeRegistry userChannelCodeRegistry;

    public UserChannelCodePolicy(UserChannelCodeRegistry userChannelCodeRegistry) {
        this.userChannelCodeRegistry = userChannelCodeRegistry;
    }

    public String generateUnique(String username) {
        String base = normalize(username);
        if (base.isBlank()) {
            base = FALLBACK_CODE;
        }
        if (!exists(base)) {
            return base;
        }
        for (int i = 0; i < MAX_RETRY; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, RANDOM_SUFFIX_LEN);
            int maxBaseLen = MAX_CHANNEL_CODE_LEN - suffix.length();
            String candidate = (base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base) + suffix;
            if (!exists(candidate)) {
                return candidate;
            }
        }
        throw BusinessException.conflict("生成用户渠道编码失败，请重试");
    }

    private String normalize(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (normalized.length() > MAX_CHANNEL_CODE_LEN) {
            return normalized.substring(0, MAX_CHANNEL_CODE_LEN);
        }
        return normalized;
    }

    private boolean exists(String channelCode) {
        return userChannelCodeRegistry.isOccupied(channelCode);
    }
}
