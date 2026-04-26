package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.crawler.DouyinTalentCrawler;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.CrawlerTalentInfoMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.vo.SampleTalentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerTalentInfoService {

    private final DouyinTalentCrawler crawler;
    private final CrawlerTalentInfoMapper mapper;
    private final TalentMapper talentMapper;

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
            throw new ValidateException("minFans must be >= 0");
        }
        if (maxFans != null && maxFans < 0) {
            throw new ValidateException("maxFans must be >= 0");
        }
        if (minFans != null && maxFans != null && minFans > maxFans) {
            throw new ValidateException("minFans must be <= maxFans");
        }
        if (minScore != null && minScore.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidateException("minScore must be >= 0");
        }

        Page<CrawlerTalentInfo> pager = new Page<>(page, size);
        IPage<CrawlerTalentInfo> result = mapper.searchTalents(
                pager, keyword, region, minFans, maxFans, minScore
        );
        if (result.getTotal() == 0) {
            return searchTalentsFromTalentTable(keyword, region, minFans, maxFans, page, size);
        }

        return result.convert(entity -> {
            SampleTalentVO vo = new SampleTalentVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        });
    }

    public CrawlerTalentInfo findByTalentId(String talentId) {
        if (!StringUtils.hasText(talentId)) {
            return null;
        }
        CrawlerTalentInfo info = mapper.selectOne(new LambdaQueryWrapper<CrawlerTalentInfo>()
                .eq(CrawlerTalentInfo::getTalentId, talentId)
                .last("limit 1"));
        if (info != null) {
            return info;
        }

        Talent talent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, talentId)
                .last("limit 1"));
        if (talent == null) {
            return null;
        }

        CrawlerTalentInfo fallback = new CrawlerTalentInfo();
        fallback.setTalentId(talent.getDouyinUid());
        fallback.setNickname(talent.getNickname());
        fallback.setAvatarUrl(talent.getAvatarUrl());
        fallback.setFansCount(talent.getFans());
        fallback.setRegion(talent.getIpLocation());
        return fallback;
    }

    private IPage<SampleTalentVO> searchTalentsFromTalentTable(
            String keyword,
            String region,
            Long minFans,
            Long maxFans,
            int page,
            int size) {
        List<Talent> talents = talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getStatus, 1)
                .orderByDesc(Talent::getFans));

        List<SampleTalentVO> filtered = talents.stream()
                .filter(talent -> matchTalent(talent, keyword, region, minFans, maxFans))
                .map(this::toSampleTalentVO)
                .collect(Collectors.toList());

        int fromIndex = Math.max((page - 1) * size, 0);
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<SampleTalentVO> records = fromIndex >= filtered.size() ? List.of() : filtered.subList(fromIndex, toIndex);

        Page<SampleTalentVO> result = new Page<>(page, size, filtered.size());
        result.setRecords(records);
        return result;
    }

    private boolean matchTalent(Talent talent, String keyword, String region, Long minFans, Long maxFans) {
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim().toLowerCase();
            String nickname = String.valueOf(talent.getNickname()).toLowerCase();
            String douyinUid = String.valueOf(talent.getDouyinUid()).toLowerCase();
            if (!nickname.contains(normalized) && !douyinUid.contains(normalized)) {
                return false;
            }
        }
        if (StringUtils.hasText(region) && !String.valueOf(talent.getIpLocation()).contains(region.trim())) {
            return false;
        }
        long fans = talent.getFans() == null ? 0L : talent.getFans();
        if (minFans != null && fans < minFans) {
            return false;
        }
        if (maxFans != null && fans > maxFans) {
            return false;
        }
        return true;
    }

    private SampleTalentVO toSampleTalentVO(Talent talent) {
        SampleTalentVO vo = new SampleTalentVO();
        vo.setTalentId(talent.getDouyinUid());
        vo.setNickname(talent.getNickname());
        vo.setAvatarUrl(talent.getAvatarUrl());
        vo.setFansCount(talent.getFans());
        vo.setRegion(talent.getIpLocation());
        return vo;
    }
}
