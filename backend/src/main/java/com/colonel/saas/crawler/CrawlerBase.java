package com.colonel.saas.crawler;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class CrawlerBase {

    protected static final int MIN_INTERVAL_MS = 3000;
    protected static final int MAX_INTERVAL_MS = 6000;

    protected final List<String> uaPool;

    protected CrawlerBase(List<String> uaPool) {
        this.uaPool = uaPool;
    }

    protected String nextUa() {
        int idx = RandomUtil.randomInt(uaPool.size());
        return uaPool.get(idx);
    }

    protected long randomIntervalMs() {
        return RandomUtil.randomLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1L);
    }

    protected void sleepBetweenRequests() {
        long ms = randomIntervalMs();
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Crawler sleep interrupted");
        }
    }

    protected void sleepExponentialBackoff(int attempt) {
        long base = (long) Math.pow(2, attempt) * MIN_INTERVAL_MS;
        long jitter = RandomUtil.randomLong(0, Math.max(base / 3, 1));
        long ms = base + jitter;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Crawler backoff interrupted");
        }
    }

    protected abstract String fetch(String url) throws Exception;
}
