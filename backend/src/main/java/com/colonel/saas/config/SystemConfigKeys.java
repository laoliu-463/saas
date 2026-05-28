package com.colonel.saas.config;

/**
 * 系统配置项键名常量。
 * <p>
 * 集中定义所有运行时可配置的系统参数键名，避免各处散落硬编码字符串导致的
 * 维护困难和拼写错误。键名按业务域分组，使用点号分隔的层级命名规则
 * （如 {@code sample.restrict_days}）。
 * </p>
 *
 * <p>键名分组：</p>
 * <ul>
 *   <li><strong>达人域（talent.*）</strong> —— 达人保护期、独家判定阈值、预设标签</li>
 *   <li><strong>寄样域（sample.*）</strong> —— 寄样限制、自动关闭、默认门槛</li>
 *   <li><strong>提成域（commission.*）</strong> —— 招商和渠道的默认提成比例</li>
 *   <li><strong>推广域（promotion.*）</strong> —— 复制讲解模板、pick_extra 规则</li>
 *   <li><strong>安全域（auth.*）</strong> —— 登录失败锁定策略</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link ConfigDefinitionRegistry} —— 引用这些常量注册配置定义和校验规则</li>
 *   <li>{@link RuleCenterSchemaRegistry} —— 引用这些常量定义规则中心 UI 的 Schema</li>
 *   <li>{@link ConfigChangedEventFactory} —— 引用这些常量判断配置是否涉及提成相关键</li>
 * </ul>
 *
 * @see ConfigDefinitionRegistry
 * @see RuleCenterSchemaRegistry
 */
public final class SystemConfigKeys {

    // ==================== 达人域 ====================

    /** 达人认领保护期天数：达人被认领后受保护的有效天数 */
    public static final String TALENT_PROTECTION_DAYS = "talent.protection_days";
    /** 独家达人服务费占比阈值：判定独家达人的服务费占比下限（V2 预留） */
    public static final String TALENT_EXCLUSIVE_RATIO = "talent.exclusive.service_fee_ratio";
    /** 独家达人月寄样数量阈值：判定独家达人的月寄样数量下限（V2 预留） */
    public static final String TALENT_EXCLUSIVE_MONTHLY_SAMPLES = "talent.exclusive.monthly_samples";
    /** 独家商家服务费占比阈值：判定独家商家的服务费占比下限（V2 预留） */
    public static final String MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO = "merchant.exclusive.service_fee_ratio";
    /** 达人预设标签库：JSON 数组，用于新建/编辑达人时的标签快速选择 */
    public static final String PRESET_TALENT_TAGS = "talent.preset_tags";

    // ==================== 寄样域 ====================

    /** 寄样重复申请限制天数：同一达人在此天数内不能重复申请寄样 */
    public static final String SAMPLE_RESTRICT_DAYS = "sample.restrict_days";
    /** 寄样限制开关：true 开启重复申请限制，false 关闭 */
    public static final String SAMPLE_RESTRICT_ENABLED = "sample.restrict_enabled";
    /** 待交作业自动关闭天数：超过此天数未交作业的寄样自动关闭（V2 预留） */
    public static final String SAMPLE_TIMEOUT_HOMEWORK_DAYS = "sample.timeout_homework_days";
    /** 待发货自动关闭天数：超过此天数未发货的寄样自动关闭 */
    public static final String SAMPLE_TIMEOUT_PENDING_SHIP_DAYS = "sample.timeout_pending_ship_days";
    /** 寄样默认标准：JSON 对象，包含 min_30day_sales、min_level 等门槛字段 */
    public static final String SAMPLE_DEFAULT_STANDARD = "sample.default_standard";

    // ==================== 提成域 ====================

    /** 招商默认提成比例：范围 0~1，变更仅影响后续计算或手动重算 */
    public static final String COMMISSION_BUSINESS_DEFAULT_RATIO = "commission.business_default_ratio";
    /** 渠道默认提成比例：范围 0~1，变更仅影响后续计算或手动重算 */
    public static final String COMMISSION_CHANNEL_DEFAULT_RATIO = "commission.channel_default_ratio";

    // ==================== 推广域 ====================

    /** 复制讲解模板：达人复制商品讲解时使用的文本模板，最长 4000 字符 */
    public static final String PROMOTION_COPY_BRIEF_TEMPLATE = "promotion.copy_brief_template";
    /** pick_extra 生成规则：JSON 对象，定义 pick_extra 参数的 format 和 encode 方式 */
    public static final String PROMOTION_PICK_EXTRA_RULE = "promotion.pick_extra_rule";

    // ==================== 安全域 ====================

    /** 登录失败锁定次数：连续登录失败达到此次数后锁定账号 */
    public static final String LOGIN_MAX_FAILURES = "auth.login_max_failures";
    /** 登录锁定时长（分钟）：账号被锁定后的自动解锁等待时间 */
    public static final String LOGIN_LOCK_MINUTES = "auth.login_lock_minutes";

    /** 工具类，禁止实例化 */
    private SystemConfigKeys() {
    }
}
