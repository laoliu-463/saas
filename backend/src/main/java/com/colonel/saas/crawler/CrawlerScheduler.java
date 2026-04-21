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

@Slf4j
@Component
public class CrawlerScheduler {

    private final CrawlerTalentInfoService crawlerService;
    private final TalentMapper talentMapper;
    private final CrawlerTalentInfoMapper crawlerTalentInfoMapper;
    private final int batchSize;

    public CrawlerScheduler(
            CrawlerTalentInfoService crawlerService,
            TalentMapper talentMapper,
            CrawlerTalentInfoMapper crawlerTalentInfoMapper,
            @Value("${douyin.crawler.batch-size:100}") int batchSize) {
        this.crawlerService = crawlerService;
        this.talentMapper = talentMapper;
        this.crawlerTalentInfoMapper = crawlerTalentInfoMapper;
        this.batchSize = batchSize;
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */30 * * * *")
    public void crawlTalentInfo() {
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

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 */2 * * *")
    public void updateTalentStats() {
        log.info("Scheduled stats update start");
        long start = System.currentTimeMillis();
        try {
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
