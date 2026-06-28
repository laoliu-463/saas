package com.colonel.saas.service;

import com.colonel.saas.domain.config.application.SysConfigService;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.config.event.ConfigCacheRefresher;
import com.colonel.saas.domain.config.event.ConfigDomainEventPublisher;
import com.colonel.saas.domain.config.event.ConfigUpdatedEvent;
import com.colonel.saas.domain.config.event.InProcessConfigDomainEventPublisher;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.entity.SystemConfigChangeLog;
import com.colonel.saas.mapper.SystemConfigChangeLogMapper;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysConfigService 配置更新事件及兼容层集成测试（DDD-CONFIG-004）。
 */
@Transactional
class SysConfigServiceEventTest extends BaseIntegrationTest {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private SystemConfigChangeLogMapper systemConfigChangeLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // 用来收集测试期间发布的事件
    private static final List<ConfigUpdatedEvent> publishedEvents = new ArrayList<>();
    // 用来模拟抛异常的 Listener 开关
    private static boolean shouldThrowException = false;

    @TestConfiguration
    static class EventTestConfig {
        @Bean
        @Primary
        public ConfigDomainEventPublisher testConfigDomainEventPublisher(
                ApplicationEventPublisher applicationEventPublisher,
                List<ConfigCacheRefresher> refreshers) {
            return new InProcessConfigDomainEventPublisher(applicationEventPublisher, refreshers);
        }

        @Bean
        public ConfigCacheRefresher mockCacheRefresher() {
            return event -> {
                publishedEvents.add(event);
                if (shouldThrowException) {
                    throw new RuntimeException("Simulated cache refresh failure!");
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        publishedEvents.clear();
        shouldThrowException = false;
    }

    @Test
    void update_config_ShouldRecordLogAndPublishSerializableEvent() throws Exception {
        // 1. 准备测试数据并插入数据库
        UUID configId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        config.setConfigKey("test.config.key.ddd.004");
        config.setConfigName("测试配置DDD-004");
        config.setConfigValue("original-value");
        config.setConfigGroup("test-group");
        config.setConfigType("STRING");
        config.setStatus(1);
        config.setConfigVersion(1);
        systemConfigMapper.insert(config);

        UUID operatorId = UUID.randomUUID();

        // 2. 执行更新
        SystemConfig updateData = new SystemConfig();
        updateData.setConfigValue("new-updated-value");
        SystemConfig updated = sysConfigService.update(configId, updateData, operatorId);

        // 3. 验证值正确保存
        assertThat(updated.getConfigValue()).isEqualTo("new-updated-value");

        // 4. 验证变更日志被正确记录
        List<SystemConfigChangeLog> logs = systemConfigChangeLogMapper.selectList(null);
        SystemConfigChangeLog matchedLog = logs.stream()
                .filter(log -> configId.equals(log.getConfigId()))
                .findFirst()
                .orElse(null);
        assertThat(matchedLog).isNotNull();
        assertThat(matchedLog.getOldValue()).isEqualTo("original-value");
        assertThat(matchedLog.getNewValue()).isEqualTo("new-updated-value");
        assertThat(matchedLog.getOperatorId()).isEqualTo(operatorId);

        // 5. 验证 ConfigUpdatedEvent 成功发布
        assertThat(publishedEvents).hasSize(1);
        ConfigUpdatedEvent event = publishedEvents.get(0);
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getConfigKey()).isEqualTo("test.config.key.ddd.004");
        assertThat(event.getOldValue()).isEqualTo("original-value");
        assertThat(event.getNewValue()).isEqualTo("new-updated-value");
        assertThat(event.getValueType()).isEqualTo("STRING");
        assertThat(event.getOperatorId()).isEqualTo(operatorId);
        assertThat(event.getUpdatedAt()).isNotNull();

        // 6. 验证事件 Payload 可序列化和反序列化
        String json = objectMapper.writeValueAsString(event);
        ConfigUpdatedEvent deserialized = objectMapper.readValue(json, ConfigUpdatedEvent.class);
        assertThat(deserialized.getEventId()).isEqualTo(event.getEventId());
        assertThat(deserialized.getConfigKey()).isEqualTo(event.getConfigKey());
        assertThat(deserialized.getOldValue()).isEqualTo(event.getOldValue());
        assertThat(deserialized.getNewValue()).isEqualTo(event.getNewValue());
        assertThat(deserialized.getValueType()).isEqualTo(event.getValueType());
        assertThat(deserialized.getOperatorId()).isEqualTo(event.getOperatorId());
    }

    @Test
    void update_config_ShouldSucceedEvenIfRefresherThrowsException() {
        // 1. 准备测试数据并插入数据库
        UUID configId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        config.setConfigKey("test.config.exception.ddd.004");
        config.setConfigName("测试配置异常测试");
        config.setConfigValue("val-1");
        config.setConfigGroup("test-group");
        config.setConfigType("STRING");
        config.setStatus(1);
        config.setConfigVersion(1);
        systemConfigMapper.insert(config);

        UUID operatorId = UUID.randomUUID();

        // 2. 开启 Listener 抛异常开关
        shouldThrowException = true;

        // 3. 执行更新，事件发布器内部应把异常捕获并吞掉，主流程不得失败
        SystemConfig updateData = new SystemConfig();
        updateData.setConfigValue("val-2");
        SystemConfig updated = sysConfigService.update(configId, updateData, operatorId);

        // 4. 断言保存依然成功
        assertThat(updated.getConfigValue()).isEqualTo("val-2");
        SystemConfig saved = systemConfigMapper.selectById(configId);
        assertThat(saved.getConfigValue()).isEqualTo("val-2");

        // 5. 验证即使抛出异常，事件依然曾经投递到了 Listener
        assertThat(publishedEvents).hasSize(1);
    }
}
