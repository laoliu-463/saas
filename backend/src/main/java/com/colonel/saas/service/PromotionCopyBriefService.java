package com.colonel.saas.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 推广文案简介渲染服务。
 * <p>
 * 负责将推广文案模板中的占位符（{@code {productName}}、{@code {commissionRate}}、
 * {@code {shortLink}}、{@code {pickSource}}）替换为实际值，生成最终的推广文案。
 * </p>
 * <p>
 * 模板内容通过 {@link BusinessRuleConfigService} 从配置中心获取，支持动态更新。
 * </p>
 *
 * @see BusinessRuleConfigService#getPromotionCopyBriefTemplate()
 */
@Service
public class PromotionCopyBriefService {

    /** 业务规则配置服务，用于获取推广文案模板 */
    private final BusinessRuleConfigService businessRuleConfigService;

    /**
     * 构造注入依赖。
     *
     * @param businessRuleConfigService 业务规则配置服务
     */
    public PromotionCopyBriefService(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    /**
     * 渲染推广文案简介（四参数版本）。
     * <p>
     * 从配置中心获取模板，将四个占位符替换为实际值。
     * 任何为 null 或空白的参数将被替换为空字符串，避免模板中残留占位符。
     * </p>
     *
     * @param productName   商品名称
     * @param commissionRate 佣金比率（如 "20%"）
     * @param shortLink     推广短链接
     * @param pickSource    精选联盟来源标识（pick_source）
     * @return 渲染后的推广文案
     */
    public String render(String productName, String commissionRate, String shortLink, String pickSource) {
        String template = businessRuleConfigService.getPromotionCopyBriefTemplate();
        return template
                .replace("{productName}", safe(productName))
                .replace("{commissionRate}", safe(commissionRate))
                .replace("{shortLink}", safe(shortLink))
                .replace("{pickSource}", safe(pickSource));
    }

    /**
     * 渲染推广文案简介（Map 参数版本）。
     * <p>
     * 支持通过 Map 传入占位符键值对，内部委托四参数版本处理。
     * 当 Map 为 null 或空时，直接返回原始模板（不做替换）。
     * </p>
     *
     * @param placeholders 占位符键值对，支持的 key：productName、commissionRate、shortLink、pickSource
     * @return 渲染后的推广文案；当 placeholders 为空时返回未替换的原始模板
     */
    public String render(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return businessRuleConfigService.getPromotionCopyBriefTemplate();
        }
        return render(
                placeholders.get("productName"),
                placeholders.get("commissionRate"),
                placeholders.get("shortLink"),
                placeholders.get("pickSource"));
    }

    /**
     * 安全取值：当输入为 null 或空白时返回空字符串，避免模板占位符残留。
     *
     * @param value 原始值
     * @return 非空白值原样返回；null 或空白返回空字符串
     */
    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
