package com.colonel.saas.config;

/**
 * 系统配置的消费域枚举。
 * <p>
 * 定义配置变更事件影响的业务消费域，每个枚举值代表一个独立的业务模块。
 * 当配置发生变更时，通过消费域标识确定哪些模块需要感知或响应变更。
 * </p>
 *
 * <p>消费域与业务模块的对应关系：</p>
 * <ul>
 *   <li>{@link #SAMPLE} —— 寄样域（寄样规则、限制天数、默认标准等）</li>
 *   <li>{@link #TALENT} —— 达人域（达人保护期、预设标签、独家判定等）</li>
 *   <li>{@link #PERFORMANCE} —— 业绩域（提成比例、独家商家判定等）</li>
 *   <li>{@link #PRODUCT} —— 商品域（复制讲解模板、pick_extra 规则等）</li>
 *   <li>{@link #USER} —— 用户域（登录安全策略等）</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link RuleCenterSchemaRegistry} —— 每个配置项关联一个或多个消费域</li>
 *   <li>{@link ConfigChangedEventFactory} —— 配置变更事件中携带消费域列表，供下游消费者过滤</li>
 * </ul>
 *
 * @see RuleCenterSchemaRegistry
 * @see ConfigChangedEventFactory
 */
public enum ConfigConsumerDomain {

    /** 寄样域：寄样限制、自动关闭、默认门槛等配置 */
    SAMPLE("sample"),
    /** 达人域：保护期、预设标签库、独家达人判定等配置 */
    TALENT("talent"),
    /** 业绩域：提成比例、独家商家判定等配置 */
    PERFORMANCE("performance"),
    /** 商品域：复制讲解模板、pick_extra 生成规则等配置 */
    PRODUCT("product"),
    /** 用户域：登录失败锁定策略等安全配置 */
    USER("user");

    /** 消费域的短标识码，用于序列化和前端展示 */
    private final String code;

    ConfigConsumerDomain(String code) {
        this.code = code;
    }

    /**
     * 获取消费域标识码。
     *
     * @return 消费域短标识码（如 "sample"、"talent"）
     */
    public String code() {
        return code;
    }
}
