package com.colonel.saas.crawler;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 抽象爬虫基类，为所有具体爬虫提供公共的反爬策略和请求基础设施。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>User-Agent 轮换：从 {@link UaPool} 中随机选取 UA，降低请求指纹特征被识别的概率</li>
 *   <li>请求间隔控制：在连续请求之间插入随机 3~6 秒延迟，模拟人类浏览行为</li>
 *   <li>指数退避重试：当请求失败时，以 2^n 倍基础间隔叠加随机抖动进行退避等待</li>
 *   <li>抽象 fetch 模板方法：由子类实现具体的 HTTP 请求逻辑</li>
 * </ol>
 *
 * <p>所属业务领域：爬虫子系统（crawler）
 *
 * @see UaPool          UA 池，提供可轮换的 User-Agent 列表
 * @see DouyinTalentCrawler  具体子类实现，负责抖音达人公开页信息采集
 */
@Slf4j
public abstract class CrawlerBase {

    /** 最小请求间隔（毫秒），用于基本的请求限速 */
    protected static final int MIN_INTERVAL_MS = 3000;

    /** 最大请求间隔（毫秒），与 {@link #MIN_INTERVAL_MS} 共同构成随机间隔区间 */
    protected static final int MAX_INTERVAL_MS = 6000;

    /** User-Agent 池列表，子类构造时注入，用于随机选取 UA */
    protected final List<String> uaPool;

    /**
     * 构造爬虫基类。
     *
     * @param uaPool User-Agent 列表，通常由 {@link UaPool} 提供
     */
    protected CrawlerBase(List<String> uaPool) {
        this.uaPool = uaPool;
    }

    /**
     * 从 UA 池中随机选取一个 User-Agent 字符串。
     * <p>
     * 每次调用从 {@link #uaPool} 中以随机索引选取，实现请求指纹轮换。
     *
     * @return 随机选取的 User-Agent 字符串
     */
    protected String nextUa() {
        int idx = RandomUtil.randomInt(uaPool.size());
        return uaPool.get(idx);
    }

    /**
     * 生成随机的请求间隔时间（毫秒）。
     * <p>
     * 返回值范围为 [{@link #MIN_INTERVAL_MS}, {@link #MAX_INTERVAL_MS}]，
     * 通过随机化避免固定节奏被服务端限流算法识别。
     *
     * @return 随机间隔毫秒数
     */
    protected long randomIntervalMs() {
        return RandomUtil.randomLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1L);
    }

    /**
     * 在两次 HTTP 请求之间执行随机休眠。
     * <p>
     * 休眠时间由 {@link #randomIntervalMs()} 生成。若线程被中断，
     * 会恢复中断标志并记录警告日志，不会向上抛出异常。
     */
    protected void sleepBetweenRequests() {
        long ms = randomIntervalMs();
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // 恢复中断标志，确保调用方能感知到中断
            Thread.currentThread().interrupt();
            log.warn("Crawler sleep interrupted");
        }
    }

    /**
     * 执行指数退避休眠，用于请求失败后的重试等待。
     * <p>
     * 退避公式为 {@code (2^attempt * MIN_INTERVAL_MS) + random_jitter}，
     * 其中 jitter 为 {@code [0, base/3)} 范围内的随机值，防止多客户端同时重试导致惊群效应。
     *
     * @param attempt 当前重试次数（从 1 开始），指数越大等待越久
     */
    protected void sleepExponentialBackoff(int attempt) {
        // 计算指数退避基准值：2^n * 3000ms
        long base = (long) Math.pow(2, attempt) * MIN_INTERVAL_MS;
        // 叠加随机抖动，范围 [0, base/3)，避免惊群效应
        long jitter = RandomUtil.randomLong(0, Math.max(base / 3, 1));
        long ms = base + jitter;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // 恢复中断标志，确保调用方能感知到中断
            Thread.currentThread().interrupt();
            log.warn("Crawler backoff interrupted");
        }
    }

    /**
     * 模板方法：执行具体的 HTTP 请求获取原始响应文本。
     * <p>
     * 子类必须实现此方法，负责构造完整的 HTTP 请求（含 UA、超时等），
     * 并返回原始响应体字符串。请求失败时应抛出异常以便上层重试逻辑处理。
     *
     * @param url 目标请求的完整 URL
     * @return 原始响应体字符串
     * @throws Exception HTTP 请求失败或响应异常时抛出
     */
    protected abstract String fetch(String url) throws Exception;
}
