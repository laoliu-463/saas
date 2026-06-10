package com.colonel.saas.domain.config.facade;

import com.colonel.saas.domain.config.facade.dto.CommissionRatesDTO;
import com.colonel.saas.domain.config.facade.dto.ExclusiveRulesDTO;
import com.colonel.saas.domain.config.facade.dto.PromotionTemplateDTO;
import com.colonel.saas.domain.config.facade.dto.SampleRulesDTO;
import com.colonel.saas.domain.config.facade.dto.TalentRulesDTO;

import java.math.BigDecimal;

/**
 * 配置域只读门面（DDD-CONFIG-002 + DDD-CONFIG-001）。
 * <p>
 * 寄样域、达人域、业绩域、推广域、商品域等业务模块应通过本接口读取系统配置，
 * 禁止直接注入 {@code SystemConfigMapper} 或绕过门面查询 {@code system_config} 表。
 * </p>
 *
 * <p>职责分层：</p>
 * <ul>
 *   <li>DDD-CONFIG-002：寄样/达人核心阈值（限频天数、超时天数、保护期、独家判定阈值）</li>
 *   <li>DDD-CONFIG-001：通用只读入口（{@link #getConfig}/{@link #getString}/{@link #getInt}/{@link #getDecimal}/{@link #getBoolean}/{@link #getJson}）
 *       + 聚合 DTO（佣金比例 / 寄样规则 / 达人规则 / 推广模板 / 商家规则）</li>
 * </ul>
 *
 * <p>当前所有方法由 {@link LegacyConfigDomainFacade} 实现，委派到旧
 * {@code com.colonel.saas.service.BusinessRuleConfigService} / {@code SysConfigService}，
 * 零行为变更（不修改配置表数据、不改变默认配置值）。</p>
 */
public interface ConfigDomainFacade {

    // ==================== DDD-CONFIG-002：寄样/达人核心阈值 ====================

    /**
     * 寄样重复申请限制天数（{@code sample.restrict_days}），默认 7。
     */
    int getSampleLimitDays();

    /**
     * 寄样重复申请限制开关（{@code sample.restrict_enabled}），默认 true。
     */
    boolean isSampleLimitEnabled();

    /**
     * 待交作业自动关闭天数（{@code sample.timeout_homework_days}），默认 30。
     */
    int getSampleAutoCloseDays();

    /**
     * 达人认领保护期天数（{@code talent.protection_days}），默认 30。
     */
    int getTalentClaimProtectDays();

    /**
     * 独家达人服务费占比阈值（{@code talent.exclusive.service_fee_ratio}），默认 70。
     */
    BigDecimal getExclusiveTalentFeeRatio();

    /**
     * 独家达人月寄样数量阈值（{@code talent.exclusive.monthly_samples}），默认 10。
     */
    int getExclusiveTalentMonthlySamples();

    // ==================== DDD-CONFIG-001：通用只读入口 ====================

    /**
     * 通用配置原始值读取。
     *
     * @param key 配置键
     * @return 配置原始字符串值；配置不存在时返回 {@code null}
     */
    String getConfig(String key);

    /**
     * 通用配置字符串值读取，含缺失 key 回退。
     *
     * @param key          配置键
     * @param defaultValue 缺失或为空白时的默认值
     * @return 配置值字符串（已 trim）；缺失或空白时返回 {@code defaultValue}
     */
    String getString(String key, String defaultValue);

    /**
     * 通用配置整型值读取，含缺失/格式异常回退。
     *
     * @param key          配置键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 整型值；缺失或解析失败时返回 {@code defaultValue}
     */
    Integer getInt(String key, Integer defaultValue);

    /**
     * 通用配置数值读取，含缺失/格式异常回退。
     *
     * @param key          配置键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 数值；缺失或解析失败时返回 {@code defaultValue}
     */
    BigDecimal getDecimal(String key, BigDecimal defaultValue);

    /**
     * 通用配置布尔值读取，含缺失/格式异常回退。
     * <p>识别 {@code true/false/1/0}（大小写不敏感、忽略前后空白），其余值回退默认。</p>
     *
     * @param key          配置键
     * @param defaultValue 缺失或无法识别时的默认值
     * @return 布尔值；缺失或无法识别时返回 {@code defaultValue}
     */
    Boolean getBoolean(String key, Boolean defaultValue);

    /**
     * 通用配置 JSON 反序列化读取，含缺失/格式异常回退。
     *
     * @param <T>          目标类型
     * @param key          配置键
     * @param type         目标类型
     * @param defaultValue 缺失或反序列化失败时返回的默认值
     * @return 反序列化结果；缺失或失败时返回 {@code defaultValue}
     */
    <T> T getJson(String key, Class<T> type, T defaultValue);

    // ==================== DDD-CONFIG-001：聚合 DTO 入口 ====================

    /**
     * 获取默认佣金比例（招商端 + 渠道端）。
     * <p>对应 {@code commission.business_default_ratio} 与 {@code commission.channel_default_ratio}。</p>
     *
     * @return 佣金比例聚合 DTO；任一 key 缺失时使用各自默认值（0.05 / 0.10）
     */
    CommissionRatesDTO getCommissionRates();

    /**
     * 获取寄样业务规则聚合（含达标标准 JSON 解析后的 DTO）。
     * <p>对应 {@code sample.restrict_days} / {@code sample.restrict_enabled} /
     * {@code sample.timeout_homework_days} / {@code sample.timeout_pending_ship_days} /
     * {@code sample.default_standard}。</p>
     *
     * @return 寄样规则聚合 DTO
     */
    SampleRulesDTO getSampleRules();

    /**
     * 获取达人业务规则聚合。
     * <p>对应 {@code talent.protection_days} / {@code talent.exclusive.service_fee_ratio} /
     * {@code talent.exclusive.monthly_samples}。</p>
     *
     * @return 达人规则聚合 DTO
     */
    TalentRulesDTO getTalentRules();

    /**
     * 获取推广文案模板与转链额外规则。
     * <p>对应 {@code promotion.copy_brief_template} 与 {@code promotion.pick_extra_rule}。</p>
     *
     * @return 推广模板聚合 DTO
     */
    PromotionTemplateDTO getPromotionTemplate();

    /**
     * 获取商家侧业务规则（独家）。
     * <p>当前仅聚合 {@code merchant.exclusive.service_fee_ratio}。</p>
     *
     * @return 商家规则聚合 DTO
     */
    ExclusiveRulesDTO getExclusiveRules();
}
