package com.colonel.saas.crawler;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * User-Agent 池，为爬虫提供多样化的浏览器身份标识以降低反爬检测概率。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>维护一组覆盖 iOS / Android / iPad / Windows / macOS 的主流浏览器 UA 字符串</li>
 *   <li>提供不可变的 UA 列表供 {@link CrawlerBase} 随机选取</li>
 * </ol>
 *
 * <p>所属业务领域：爬虫子系统（crawler）
 * <p>技术细节：UA 列表使用 {@link List#of} 创建不可变集合，包含 5 个覆盖主要平台的浏览器标识
 *
 * @see CrawlerBase#nextUa() 爬虫基类的 UA 随机选取方法
 */
@Component
public class UaPool {

    /**
     * UA 池列表，覆盖以下平台和浏览器：
     * <ul>
     *   <li>iOS 17 Safari（iPhone）</li>
     *   <li>Android 14 Chrome（Pixel 8 Pro）</li>
     *   <li>iPadOS 17 Safari</li>
     *   <li>Windows 10 Chrome 120</li>
     *   <li>macOS 14 Chrome 120</li>
     * </ul>
     * 使用不可变列表，运行期间不可修改。
     */
    private final List<String> uaList = List.of(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    );

    /**
     * 获取 UA 池中的所有 User-Agent 字符串。
     * <p>
     * 返回不可变列表，调用方不应尝试修改。
     *
     * @return User-Agent 字符串的不可变列表
     */
    public List<String> getPool() {
        return uaList;
    }
}
