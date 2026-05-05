package com.colonel.saas.crawler;

import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.CrawlerTalentInfoMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlerSchedulerTest {

    @Mock
    private CrawlerTalentInfoService crawlerService;

    @Mock
    private TalentMapper talentMapper;

    @Mock
    private CrawlerTalentInfoMapper crawlerTalentInfoMapper;

    private CrawlerScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CrawlerScheduler(crawlerService, talentMapper, crawlerTalentInfoMapper, 100, true);
    }

    @Test
    void crawlTalentInfo_withEmptyTargets_skipsAndDoesNotCallService() {
        when(talentMapper.selectList(any())).thenReturn(List.of());

        scheduler.crawlTalentInfo();

        verify(crawlerService, never()).crawlAndSave(anyList());
    }

    @Test
    void crawlTalentInfo_withTargets_callsServiceAndLogsResult() {
        List<Talent> talents = List.of(
                createTalent("uid1"), createTalent("uid2"));
        when(talentMapper.selectList(any())).thenReturn(talents);
        when(crawlerService.crawlAndSave(anyList())).thenReturn(2);

        scheduler.crawlTalentInfo();

        verify(crawlerService).crawlAndSave(argThat(list ->
                list.contains("uid1") && list.contains("uid2")));
    }

    @Test
    void crawlTalentInfo_whenServiceThrows_logsError() {
        List<Talent> talents = List.of(createTalent("uid1"));
        when(talentMapper.selectList(any())).thenReturn(talents);
        when(crawlerService.crawlAndSave(anyList()))
                .thenThrow(new RuntimeException("network failure"));

        scheduler.crawlTalentInfo();

        verify(crawlerService).crawlAndSave(anyList());
    }

    @Test
    void updateTalentStats_withEmptyCrawlerRecords_skipsAndDoesNotCallService() {
        when(crawlerTalentInfoMapper.selectList(any())).thenReturn(List.of());

        scheduler.updateTalentStats();

        verify(crawlerService, never()).crawlAndSave(anyList());
    }

    @Test
    void updateTalentStats_withCrawlerRecords_callsService() {
        List<CrawlerTalentInfo> records = List.of(
                createCrawlerRecord("uid1"), createCrawlerRecord("uid2"));
        when(crawlerTalentInfoMapper.selectList(any())).thenReturn(records);
        when(crawlerService.crawlAndSave(anyList())).thenReturn(2);

        scheduler.updateTalentStats();

        verify(crawlerService).crawlAndSave(argThat(list ->
                list.size() == 2));
    }

    @Test
    void updateTalentStats_whenServiceThrows_logsError() {
        List<CrawlerTalentInfo> records = List.of(createCrawlerRecord("uid1"));
        when(crawlerTalentInfoMapper.selectList(any())).thenReturn(records);
        when(crawlerService.crawlAndSave(anyList()))
                .thenThrow(new RuntimeException("crawl error"));

        scheduler.updateTalentStats();

        verify(crawlerService).crawlAndSave(anyList());
    }

    @Test
    void crawlTalentInfo_filtersBlankTalentIds() {
        Talent blankTalent = createTalent("");
        Talent validTalent = createTalent("valid-uid");
        when(talentMapper.selectList(any())).thenReturn(List.of(blankTalent, validTalent));
        when(crawlerService.crawlAndSave(anyList())).thenReturn(1);

        scheduler.crawlTalentInfo();

        verify(crawlerService).crawlAndSave(argThat(list ->
                list.size() == 1 && list.contains("valid-uid")));
    }

    @Test
    void crawlTalentInfo_whenDisabled_skipsExecution() {
        CrawlerScheduler disabledScheduler = new CrawlerScheduler(
                crawlerService, talentMapper, crawlerTalentInfoMapper, 100, false);

        disabledScheduler.crawlTalentInfo();

        verify(talentMapper, never()).selectList(any());
        verify(crawlerService, never()).crawlAndSave(anyList());
    }

    @Test
    void updateTalentStats_whenDisabled_skipsExecution() {
        CrawlerScheduler disabledScheduler = new CrawlerScheduler(
                crawlerService, talentMapper, crawlerTalentInfoMapper, 100, false);

        disabledScheduler.updateTalentStats();

        verify(crawlerTalentInfoMapper, never()).selectList(any());
        verify(crawlerService, never()).crawlAndSave(anyList());
    }

    private Talent createTalent(String douyinUid) {
        Talent talent = new Talent();
        talent.setDouyinUid(douyinUid);
        return talent;
    }

    private CrawlerTalentInfo createCrawlerRecord(String talentId) {
        CrawlerTalentInfo record = new CrawlerTalentInfo();
        record.setTalentId(talentId);
        return record;
    }
}