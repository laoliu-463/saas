package com.colonel.saas.crawler;

import com.colonel.saas.entity.CrawlerTalentInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinTalentCrawlerTest {

    @Mock
    private UaPool uaPool;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(uaPool.getPool()).thenReturn(List.of("TestUA/1.0"));
        objectMapper = new ObjectMapper();
    }

    @Test
    void crawl_parseFailure_returnsEmptyOptional() {
        TestCrawler crawler = new TestCrawler(uaPool, objectMapper, "https://fake", "not-json");

        Optional<CrawlerTalentInfo> result = crawler.crawl("talent123");

        assertThat(result).isEmpty();
    }

    @Test
    void crawl_withNullDataNode_returnsEmptyOptional() {
        String body = "{\"data\":{}}";
        TestCrawler crawler = new TestCrawler(uaPool, objectMapper, "https://fake", body);

        Optional<CrawlerTalentInfo> result = crawler.crawl("talent123");

        assertThat(result).isPresent();
        assertThat(result.get().getNickname()).isNull();
    }

    @Test
    void crawl_withValidData_parsesAndReturnsTalentInfo() {
        String body = """
                {
                  "data": {
                    "nickname": "测试达人",
                    "avatar_url": "https://example.com/avatar.jpg",
                    "fans_count": 123456,
                    "credit_score": "4.5",
                    "main_category": "美妆",
                    "region": "浙江"
                  }
                }
                """;
        TestCrawler crawler = new TestCrawler(uaPool, objectMapper, "https://fake", body);

        Optional<CrawlerTalentInfo> result = crawler.crawl("talent123");

        assertThat(result).isPresent();
        CrawlerTalentInfo info = result.get();
        assertThat(info.getTalentId()).isEqualTo("talent123");
        assertThat(info.getNickname()).isEqualTo("测试达人");
        assertThat(info.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
        assertThat(info.getFansCount()).isEqualTo(123456L);
        assertThat(info.getCreditScore()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(info.getMainCategory()).isEqualTo("美妆");
        assertThat(info.getRegion()).isEqualTo("浙江");
        assertThat(info.getLastCrawlTime()).isNotNull();
    }

    @Test
    void crawl_withOutOfRangeCreditScore_clampedToRange() {
        String body = "{\"data\":{\"nickname\":\"A\",\"credit_score\":\"10.0\"}}";
        TestCrawler crawler = new TestCrawler(uaPool, objectMapper, "https://fake", body);

        Optional<CrawlerTalentInfo> result = crawler.crawl("talent789");

        assertThat(result).isPresent();
        assertThat(result.get().getCreditScore()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    private static class TestCrawler extends DouyinTalentCrawler {
        private final String fixedBody;

        TestCrawler(UaPool uaPool, ObjectMapper objectMapper, String talentUrl, String fixedBody) {
            super(uaPool, objectMapper, talentUrl);
            this.fixedBody = fixedBody;
        }

        @Override
        protected String fetch(String url) {
            return fixedBody;
        }
    }
}
