package com.colonel.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 物流服务商配置属性。
 * <p>
 * 通过 {@code logistics.*} 前缀的配置项，管理物流查询服务的提供商选择、
 * 认证凭据和同步策略。支持快递鸟（kuaidiniao）和快递100（kuaidi100）
 * 两家服务商，以及 Mock 模式用于开发测试。
 * </p>
 *
 * <p>物流查询模式：</p>
 * <ul>
 *   <li><strong>mock</strong>（默认）—— 返回模拟物流数据，用于开发和测试</li>
 *   <li><strong>kuaidiniao</strong> —— 使用快递鸟 API 进行物流查询</li>
 *   <li><strong>kuaidi100</strong> —— 使用快递100 API 进行物流查询和订阅</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>物流同步定时任务使用 {@link Sync} 中的 cron 表达式和批次大小</li>
 *   <li>快递100的回调 URL 与 {@link RuntimeExposurePolicy} 中的公开路径配合</li>
 * </ul>
 *
 * @see RuntimeExposurePolicy
 */
@Data
@Component
@ConfigurationProperties(prefix = "logistics")
public class LogisticsProperties {

    /**
     * 物流服务商标识。
     * <p>支持：mock（默认）| kuaidiniao（快递鸟）| kuaidi100（快递100）</p>
     */
    private String provider = "mock";

    /** 物流查询通用配置（超时、重试） */
    private final Query query = new Query();
    /** 快递鸟（kuaidiniao）专用配置 */
    private final Kdn kdn = new Kdn();
    /** 快递100（kuaidi100）专用配置 */
    private final Kd100 kd100 = new Kd100();
    /** 物流数据同步策略配置 */
    private final Sync sync = new Sync();

    /**
     * 物流查询通用配置。
     * <p>控制所有物流服务商共用的超时和重试策略。</p>
     */
    @Data
    public static class Query {
        /** 查询请求超时时间（秒），默认 10 秒 */
        private int timeoutSeconds = 10;
        /** 查询请求重试次数，默认 1 次 */
        private int retryCount = 1;
    }

    /**
     * 快递鸟（kuaidiniao）服务商配置。
     * <p>
     * 快递鸟是国内主流的物流查询聚合平台，通过其 API 可查询数百家快递公司的物流轨迹。
     * 官方文档：https://www.kdniao.com/
     * </p>
     */
    @Data
    public static class Kdn {
        /** 是否启用快递鸟服务 */
        private boolean enabled;
        /** 快递鸟商户 ID（Ebusiness ID） */
        private String ebusinessId;
        /** 快递鸟 API Key */
        private String apiKey;
        /** 快递鸟 API 端点地址 */
        private String endpoint = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
        /** 是否启用沙箱环境（测试用） */
        private boolean sandboxEnabled;
    }

    /**
     * 快递100（kuaidi100）服务商配置。
     * <p>
     * 快递100是国内另一家主流物流查询平台，支持物流查询和物流订阅（主动推送）。
     * 官方文档：https://www.kuaidi100.com/
     * </p>
     */
    @Data
    public static class Kd100 {
        /** 是否启用快递100服务 */
        private boolean enabled;
        /** 是否启用物流订阅（主动推送物流状态变更） */
        private boolean subscribeEnabled;
        /** 快递100 授权码（customer） */
        private String customer;
        /** 快递100 API Key */
        private String key;
        /** 快递100 查询 API 端点 */
        private String endpoint = "https://poll.kuaidi100.com/poll/query.do";
        /** 快递100 订阅 API 端点 */
        private String subscribeEndpoint = "https://poll.kuaidi100.com/poll";
        /** 物流状态变更回调 URL（快递100推送时调用） */
        private String callbackUrl;
        /** 回调签名盐值，用于验证回调请求的合法性 */
        private String callbackSalt;
        /** 查询结果版本，默认 "4"（V2 接口） */
        private String resultV2 = "4";
        /** 是否启用沙箱环境（测试用） */
        private boolean sandboxEnabled;
    }

    /**
     * 物流数据同步策略配置。
     * <p>控制定时同步物流信息的调度策略，适用于大批量物流轨迹更新场景。</p>
     */
    @Data
    public static class Sync {
        /** 是否启用定时同步 */
        private boolean enabled;
        /** 同步调度 cron 表达式，默认每 30 分钟执行一次 */
        private String cron = "0 */30 * * * ?";
        /** 每次同步的批次大小（查询单数），默认 100 单 */
        private int batchSize = 100;
    }
}
