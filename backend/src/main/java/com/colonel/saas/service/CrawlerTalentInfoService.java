package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.crawler.DouyinTalentCrawler;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.mapper.CrawlerTalentInfoMapper;
import com.colonel.saas.vo.SampleTalentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerTalentInfoService {

    private final DouyinTalentCrawler crawler;
    private final CrawlerTalentInfoMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public int crawlAndSave(List<String> talentIds) {
        int success = 0;
        for (String talentId : talentIds) {
            Optional<CrawlerTalentInfo> optional = crawler.crawl(talentId);
            if (optional.isPresent()) {
                CrawlerTalentInfo info = optional.get();
                mapper.upsert(info);
                success++;
                log.info("Upsert talent success: talentId={}, nickname={}", talentId, info.getNickname());
            }
        }
        log.info("Crawl batch done: total={}, success={}", talentIds.size(), success);
        return success;
    }

    public IPage<SampleTalentVO> searchTalents(
            String keyword,
            String region,
            Long minFans,
            Long maxFans,
            BigDecimal minScore,
            int page,
            int size) {
        if (minFans != null && minFans < 0) {
            throw new ValidateException("minFans 涓嶈兘灏忎簬 0");
        }
        if (maxFans != null && maxFans < 0) {
            throw new ValidateException("maxFans 涓嶈兘灏忎簬 0");
        }
        if (minFans != null && maxFans != null && minFans > maxFans) {
            throw new ValidateException("minFans 涓嶈兘澶т簬 maxFans");
        }
        if (minScore != null && minScore.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidateException("minScore 涓嶈兘灏忎簬 0");
        }
        Page<CrawlerTalentInfo> pager = new Page<>(page, size);
        IPage<CrawlerTalentInfo> result = mapper.searchTalents(
                pager, keyword, region, minFans, maxFans, minScore
        );
        return result.convert(entity -> {
            SampleTalentVO vo = new SampleTalentVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        });
    }

    public CrawlerTalentInfo findByTalentId(String talentId) {
        if (talentId == null || talentId.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<CrawlerTalentInfo>()
                .eq(CrawlerTalentInfo::getTalentId, talentId)
                .last("limit 1"));
    }
}

