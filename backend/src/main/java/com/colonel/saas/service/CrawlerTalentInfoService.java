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

/**
 * 达人信息爬取与搜索服务。
 *
 * <p>职责：通过抖音爬虫抓取达人公开信息并持久化，同时提供达人搜索能力。
 * 搜索时优先从爬虫表查询，若爬虫表无结果则回退到达人主表查询，确保搜索结果的完整性。
 *
 * <p>数据回退策略：
 * <ul>
 *   <li>搜索（{@link #searchTalents}）：爬虫表无结果 -> 回退 {@link TalentMapper#searchActiveTalents}</li>
 *   <li>单查（{@link #findByTalentId}）：爬虫表无结果 -> 回退 {@link TalentMapper#selectOne} 按 douyinUid 查询</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link DouyinTalentCrawler} —— 抖音达人信息爬虫</li>
 *   <li>{@link CrawlerTalentInfoMapper} —— 爬虫达人信息数据访问</li>
 *   <li>{@link TalentMapper} —— 达人主表数据访问（回退查询用）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerTalentInfoService {

    /** 抖音达人信息爬虫 */
    private final DouyinTalentCrawler crawler;
    /** 爬虫达人信息数据访问（存储从抖音抓取的达人详情） */
    private final CrawlerTalentInfoMapper mapper;
    /** 达人主表数据访问（作为搜索回退数据源） */
    private final TalentMapper talentMapper;

    /**
     * 批量爬取并持久化达人信息。
     * 逐个调用爬虫抓取达人详情，成功后通过 upsert 写入爬虫达人表（存在则更新，不存在则插入）。
     * 整体在同一事务中执行，任一异常将回滚所有已写入数据。
     *
     * @param talentIds 达人ID列表（抖音达人UID）
     * @return 成功爬取并持久化的达人数量
     */
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

    /**
     * 搜索达人列表（带回退策略）。
     * 优先从爬虫达人表按关键词、地域、粉丝数、评分等条件搜索；
     * 若爬虫表无结果（total=0），自动回退到达人主表进行搜索，保证搜索结果的完整性。
     *
     * @param keyword  搜索关键词（匹配达人昵称等字段）
     * @param region   地域筛选（可选）
     * @param minFans  最低粉丝数（可选，须 >= 0）
     * @param maxFans  最高粉丝数（可选，须 >= 0 且 >= minFans）
     * @param minScore 最低评分（可选，须 >= 0）
     * @param page     页码
     * @param size     每页大小
     * @return 分页的达人搜索结果
     * @throws ValidateException 参数校验失败时（如 minFans < 0 或 minFans > maxFans）
     */
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

    /**
     * 根据达人ID查找达人信息（带回退策略）。
     * 优先从爬虫达人表查询；若未找到，则回退到达人主表按 douyinUid 查询，
     * 并将主表记录转换为 CrawlerTalentInfo 返回，确保调用方始终能获取到达人基本信息。
     *
     * @param talentId 达人ID（抖音达人UID）
     * @return 达人信息；若两表均无记录则返回 null
     */
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

    /**
     * 从达人主表搜索达人（回退数据源）。
     * 当爬虫达人表无匹配结果时，调用此方法从达人主表中搜索活跃达人。
     *
     * @param keyword 搜索关键词
     * @param region  地域筛选
     * @param minFans 最低粉丝数
     * @param maxFans 最高粉丝数
     * @param page    页码
     * @param size    每页大小
     * @return 分页的达人搜索结果（转换为 SampleTalentVO）
     */
    private IPage<SampleTalentVO> searchTalentsFromTalentTable(
            String keyword,
            String region,
            Long minFans,
            Long maxFans,
            int page,
            int size) {
        Page<Talent> pager = new Page<>(page, size);
        IPage<Talent> talentPage = talentMapper.searchActiveTalents(pager, keyword, region, minFans, maxFans);
        return talentPage.convert(this::toSampleTalentVO);
    }

    /**
     * 将达人主表实体转换为 SampleTalentVO。
     * 映射字段：douyinUid -> talentId, nickname, avatarUrl, fans -> fansCount, ipLocation -> region。
     *
     * @param talent 达人主表实体
     * @return 达人展示VO
     */
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
