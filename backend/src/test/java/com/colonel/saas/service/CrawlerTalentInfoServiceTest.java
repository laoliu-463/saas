package com.colonel.saas.service;

import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.crawler.DouyinTalentCrawler;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.mapper.CrawlerTalentInfoMapper;
import com.colonel.saas.vo.SampleTalentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private CrawlerTalentInfoService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerTalentInfoService(crawler, mapper);
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
    void findByTalentId_nullInput_returnsNull() {
        assertThat(service.findByTalentId(null)).isNull();
        assertThat(service.findByTalentId("  ")).isNull();
    }
}
