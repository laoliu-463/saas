package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.crawler.DouyinTalentCrawler;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.CrawlerTalentInfoMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.vo.SampleTalentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerTalentInfoServiceTest {

    @Mock
    private DouyinTalentCrawler crawler;
    @Mock
    private CrawlerTalentInfoMapper mapper;
    @Mock
    private TalentMapper talentMapper;

    private CrawlerTalentInfoService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerTalentInfoService(crawler, mapper, talentMapper);
    }

    @Test
    void crawlAndSave_shouldUpsertEachTalent() {
        String talentId = "talent_001";
        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId(talentId);
        info.setNickname("TestTalent");

        when(crawler.crawl(talentId)).thenReturn(Optional.of(info));

        int result = service.crawlAndSave(List.of(talentId));

        assertThat(result).isEqualTo(1);
        verify(mapper).upsert(info);
    }

    @Test
    void crawlAndSave_skipWhenCrawlFails() {
        when(crawler.crawl("bad_talent")).thenReturn(Optional.empty());

        int result = service.crawlAndSave(List.of("bad_talent"));

        assertThat(result).isZero();
    }

    @Test
    void searchTalents_shouldThrowOnNegativeMinFans() {
        assertThatThrownBy(() -> service.searchTalents(null, null, -1L, null, null, 1, 20))
                .isInstanceOf(ValidateException.class);
    }

    @Test
    void searchTalents_shouldThrowOnNegativeMaxFans() {
        assertThatThrownBy(() -> service.searchTalents(null, null, null, -5L, null, 1, 20))
                .isInstanceOf(ValidateException.class);
    }

    @Test
    void searchTalents_shouldThrowWhenMinFansExceedsMaxFans() {
        assertThatThrownBy(() -> service.searchTalents(null, null, 1000L, 500L, null, 1, 20))
                .isInstanceOf(ValidateException.class);
    }

    @Test
    void searchTalents_shouldThrowOnNegativeMinScore() {
        assertThatThrownBy(() -> service.searchTalents(null, null, null, null, new BigDecimal("-0.1"), 1, 20))
                .isInstanceOf(ValidateException.class);
    }

    @Test
    void searchTalents_shouldReturnConvertedPage() {
        CrawlerTalentInfo entity = new CrawlerTalentInfo();
        entity.setTalentId("t_001");
        entity.setNickname("达人A");
        entity.setFansCount(50000L);

        Page<CrawlerTalentInfo> page = new Page<>(1, 20);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(mapper.searchTalents(any(), any(), any(), any(), any(), any())).thenReturn(page);

        IPage<SampleTalentVO> result = service.searchTalents("达人", null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getNickname()).isEqualTo("达人A");
    }

    @Test
    void searchTalents_shouldFallbackToTalentTableWhenCrawlerDataEmpty() {
        Page<CrawlerTalentInfo> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(mapper.searchTalents(any(), any(), any(), any(), any(), any())).thenReturn(page);

        Talent talent = new Talent();
        talent.setDouyinUid("talent_mock_a");
        talent.setNickname("Mock达人A");
        talent.setFans(186000L);
        talent.setIpLocation("四川成都");
        talent.setStatus(1);
        when(talentMapper.selectList(any())).thenReturn(List.of(talent));

        IPage<SampleTalentVO> result = service.searchTalents("Mock", null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getTalentId()).isEqualTo("talent_mock_a");
    }

    @Test
    void findByTalentId_nullInput_returnsNull() {
        assertThat(service.findByTalentId(null)).isNull();
        assertThat(service.findByTalentId("  ")).isNull();
    }

    @Test
    void findByTalentId_shouldFallbackToTalentTableWhenCrawlerDataMissing() {
        when(mapper.selectOne(any())).thenReturn(null);
        Talent talent = new Talent();
        talent.setDouyinUid("talent_mock_a");
        talent.setNickname("Mock达人A");
        talent.setFans(186000L);
        when(talentMapper.selectOne(any())).thenReturn(talent);

        CrawlerTalentInfo result = service.findByTalentId("talent_mock_a");

        assertThat(result).isNotNull();
        assertThat(result.getTalentId()).isEqualTo("talent_mock_a");
        assertThat(result.getNickname()).isEqualTo("Mock达人A");
    }
}
