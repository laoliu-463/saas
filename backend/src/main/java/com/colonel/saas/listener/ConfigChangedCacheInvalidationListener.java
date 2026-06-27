package com.colonel.saas.listener;

import com.colonel.saas.event.ConfigChangedApplicationEvent;
import com.colonel.saas.domain.config.infrastructure.BusinessRuleConfigService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 配置变更缓存失效监听器。
 * <p>
 * 监听配置变更应用事件（{@link ConfigChangedApplicationEvent}），在事务提交后
 * 自动清除受影响的业务规则配置缓存。确保配置更新后，后续读取能获取最新值。
 * </p>
 * <p>
 * 使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，
 * 仅在事务成功提交后才执行缓存失效操作，避免因事务回滚导致缓存误清。
 * </p>
 * <p>
 * 处理逻辑：遍历事件中的所有变更 key，逐一调用配置服务的失效方法。
 * </p>
 *
 * @see ConfigChangedApplicationEvent
 * @see BusinessRuleConfigService#invalidate(String)
 */
@Component
public class ConfigChangedCacheInvalidationListener {

    /** 业务规则配置服务，负责缓存失效 */
    private final BusinessRuleConfigService businessRuleConfigService;

    /**
     * 构造函数，注入依赖。
     *
     * @param businessRuleConfigService 业务规则配置服务
     */
    public ConfigChangedCacheInvalidationListener(BusinessRuleConfigService businessRuleConfigService) {
        this.businessRuleConfigService = businessRuleConfigService;
    }

    /**
     * 事务提交后处理配置变更事件，清除受影响的缓存。
     *
     * @param event 配置变更应用事件，包含变更的配置 key 列表
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterConfigChanged(ConfigChangedApplicationEvent event) {
        if (event == null || event.changedKeys() == null) {
            return;
        }
        // 遍历所有变更的配置 key，逐一清除缓存
        for (String configKey : event.changedKeys()) {
            if (StringUtils.hasText(configKey)) {
                businessRuleConfigService.invalidate(configKey);
            }
        }
    }
}
