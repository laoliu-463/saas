package com.colonel.saas.config;

import com.colonel.saas.service.talent.TalentCollectEnvironmentReporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 达人资料采集环境状态启动报告器。
 * <p>
 * 实现 {@link ApplicationRunner} 接口，在 Spring Boot 应用启动完成后自动检测并记录
 * 达人资料采集的环境联调状态。帮助运维人员在应用启动时快速确认采集链路是否正常。
 * </p>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>通过 {@link TalentCollectEnvironmentReporter} 检测当前采集链路状态</li>
 *   <li>将状态标签以 INFO 级别输出到日志</li>
 *   <li>运维人员可通过日志快速判断是否需要调整采集配置</li>
 * </ol>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link TalentCollectEnvironmentReporter} —— 负责实际的环境状态探测逻辑</li>
 *   <li>{@link TalentCollectEnvironmentStatus} —— 定义可能的状态枚举值</li>
 *   <li>{@link TalentCollectProperties} —— 采集模式配置，影响状态判定</li>
 *   <li>{@link StartupEnvironmentLogger} —— 类似的启动报告器，输出全局环境信息</li>
 * </ul>
 *
 * @see TalentCollectEnvironmentReporter
 * @see TalentCollectEnvironmentStatus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentCollectStartupReporter implements ApplicationRunner {

    /** 环境状态探测器，负责检测采集链路的真实连通状态 */
    private final TalentCollectEnvironmentReporter environmentReporter;

    /**
     * 应用启动后执行，输出达人资料采集环境状态到日志。
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Talent collect environment status={}", environmentReporter.resolveStatusLabel());
    }
}
