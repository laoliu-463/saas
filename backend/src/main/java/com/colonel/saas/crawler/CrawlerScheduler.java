package com.colonel.saas.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.CrawlerTalentInfoMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 爬虫定时调度器，负责按固定周期触发抖音达人公开页数据采集任务。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>定时增量采集（每 30 分钟）：从 {@link Talent} 表中加载活跃且已绑定抖音 UID 的达人列表，
 *       调用 {@link CrawlerTalentInfoService#crawlAndSave} 执行批量采集</li>
 *   <li>定时统计刷新（每 2 小时）：从 {@link CrawlerTalentInfo} 表中选取最久未更新的记录，
 *       重新爬取以保持达人数据的时效性</li>
 *   <li>全局开关控制：通过配置项 {@code talent.data.public-page-crawl-enabled} 控制是否执行爬取，
 *       默认关闭以避免开发/测试环境误触发外部请求</li>
 * </ol>
 *
 * <p>所属业务领域：爬虫子系统（crawler）
 * <p>调度策略：Spring {@code @Scheduled} cron 表达式驱动
 *
 * @see CrawlerTalentInfoService 爬取并持久化的核心服务
 * @see DouyinTalentCrawler       实际执行 HTTP 请求的爬虫实现
 */
@Slf4j
@Component
public class CrawlerScheduler {

    /** 爬虫业务服务，负责协调爬取与持久化 */
    private final CrawlerTalentInfoService crawlerService;

    /** 达人主表 Mapper，用于加载待采集的达人抖音 UID 列表 */
    private final TalentMapper talentMapper;

    /** 爬虫达人信息表 Mapper，用于查询最久未更新的爬取记录 */
    private final CrawlerTalentInfoMapper crawlerTalentInfoMapper;

    /** 每批采集的最大达人数量，由配置项 {@code douyin.crawler.batch-size} 控制，默认 100 */
    private final int batchSize;

    /** 爬虫全局开关，由配置项 {@code talent.data.public-page-crawl-enabled} 控制，默认关闭 */
    private final boolean crawlEnabled;

    /**
     * 构造爬虫调度器。
     *
     * @param crawlerService           爬虫业务服务
     * @param talentMapper             达人主表 Mapper
     * @param crawlerTalentInfoMapper  爬虫达人信息表 Mapper
     * @param batchSize                每批采集数量，配置项 {@code douyin.crawler.batch-size}，默认 100
     * @param crawlEnabled             是否启用爬虫，配置项 {@code talent.data.public-page-crawl-enabled}，默认 false
     */
    public CrawlerScheduler(
            CrawlerTalentInfoService crawlerService,
            TalentMapper talentMapper,
            CrawlerTalentInfoMapper crawlerTalentInfoMapper,
            @Value("${douyin.crawler.batch-size:100}") int batchSize,
            @Value("${talent.data.public-page-crawl-enabled:false}") boolean crawlEnabled) {
        this.crawlerService = crawlerService;
        this.talentMapper = talentMapper;
        this.crawlerTalentInfoMapper = crawlerTalentInfoMapper;
        this.batchSize = batchSize;
        this.crawlEnabled = crawlEnabled;
    }

    /**
     * 定时增量采集任务，每 30 分钟执行一次。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>检查全局开关 {@link #crawlEnabled}，关闭则跳过</li>
     *   <li>调用 {@link #loadTargetTalentIds()} 加载活跃达人的抖音 UID 列表</li>
     *   <li>委托 {@link CrawlerTalentInfoService#crawlAndSave} 执行批量爬取与持久化</li>
     *   <li>记录耗时与成功/失败日志</li>
     * </ol>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */30 * * * *")
    public void crawlTalentInfo() {
        if (!crawlEnabled) {
            log.debug("Scheduled crawler skipped: public-page-crawl-enabled=false");
            return;
        }
        log.info("Scheduled crawler start");
        long start = System.currentTimeMillis();
        List<String> targetIds = loadTargetTalentIds();
        if (targetIds.isEmpty()) {
            log.info("Scheduled crawler skipped: no target talent ids");
            return;
        }
        try {
            int saved = crawlerService.crawlAndSave(targetIds);
            long cost = System.currentTimeMillis() - start;
            log.info("Scheduled crawler done: total={}, saved={}, costMs={}", targetIds.size(), saved, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("Scheduled crawler failed: total={}, costMs={}, error={}", targetIds.size(), cost, e.getMessage(), e);
        }
    }

    /**
     * 定时统计刷新任务，每 2 小时执行一次。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>检查全局开关 {@link #crawlEnabled}，关闭则跳过</li>
     *   <li>从爬虫记录表中按 {@code lastCrawlTime} 升序选取最久未更新的记录（最多 {@link #batchSize} 条）</li>
     *   <li>委托 {@link CrawlerTalentInfoService#crawlAndSave} 重新爬取并更新</li>
     *   <li>记录耗时与成功/失败日志</li>
     * </ol>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 */2 * * *")
    public void updateTalentStats() {
        if (!crawlEnabled) {
            log.debug("Scheduled stats update skipped: public-page-crawl-enabled=false");
            return;
        }
        log.info("Scheduled stats update start");
        long start = System.currentTimeMillis();
        try {
            // 按 lastCrawlTime 升序选取最久未刷新的记录，实现轮转更新策略
            List<String> targetIds = crawlerTalentInfoMapper.selectList(new LambdaQueryWrapper<CrawlerTalentInfo>()
                            .orderByAsc(CrawlerTalentInfo::getLastCrawlTime)
                            .last("limit " + Math.max(batchSize, 1)))
                    .stream()
                    .map(CrawlerTalentInfo::getTalentId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            if (targetIds.isEmpty()) {
                log.info("Scheduled stats update skipped: no crawler records");
                return;
            }
            int saved = crawlerService.crawlAndSave(targetIds);
            long cost = System.currentTimeMillis() - start;
            log.info("Scheduled stats update done: total={}, saved={}, costMs={}", targetIds.size(), saved, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("Scheduled stats update failed: costMs={}, error={}", cost, e.getMessage(), e);
        }
    }

    /**
     * 加载待采集的目标达人抖音 UID 列表。
     * <p>
     * 筛选条件：状态为启用（status=1）、未删除（deleted=0）、已绑定抖音 UID。
     * 按更新时间倒序排列，取最新的 {@link #batchSize} 条记录。
     *
     * @return 达人抖音 UID 列表，可能为空但不会为 null
     */
    private List<String> loadTargetTalentIds() {
        int limit = Math.max(batchSize, 1);
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getStatus, 1)
                        .eq(Talent::getDeleted, 0)
                        .isNotNull(Talent::getDouyinUid)
                        .orderByDesc(Talent::getUpdateTime)
                        .last("limit " + limit))
                .stream()
                .map(Talent::getDouyinUid)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }
}
