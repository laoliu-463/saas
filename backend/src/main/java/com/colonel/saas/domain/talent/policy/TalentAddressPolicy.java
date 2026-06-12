package com.colonel.saas.domain.talent.policy;

import org.springframework.util.StringUtils;

/**
 * 达人收货地址归一化 Policy（DDD-TALENT-003）。
 *
 * <p>从 {@code TalentService.trimToNull} 抽离的纯地址规则：
 * <ul>
 *   <li>trim 首尾空白</li>
 *   <li>空白字符串（trim 后空）转为 {@code null}，便于上层以"非空即覆盖"语义写入</li>
 *   <li>提供三字段（姓名 / 电话 / 地址）批量归一化入口</li>
 * </ul>
 *
 * <p>无 Spring 依赖，便于单测和复用。注：本 Policy 不做电话号格式校验 — 那是
 * {@code ConfigDomainFacade} 配置规则 + Controller 层校验的责任。</p>
 */
public final class TalentAddressPolicy {

    private TalentAddressPolicy() {
    }

    /**
     * 字符串 trim，空白时返回 {@code null}。
     */
    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 批量归一化姓名 / 电话 / 地址三字段。
     *
     * @param recipientName   收件人姓名
     * @param recipientPhone  收件人电话
     * @param recipientAddress 收件地址
     * @return 三字段归一化结果（每项可为 {@code null}）
     */
    public static NormalizedAddress normalize(String recipientName, String recipientPhone, String recipientAddress) {
        return new NormalizedAddress(
                trimToNull(recipientName),
                trimToNull(recipientPhone),
                trimToNull(recipientAddress));
    }

    /**
     * 归一化后的地址三字段。
     */
    public record NormalizedAddress(String recipientName, String recipientPhone, String recipientAddress) {
    }
}
