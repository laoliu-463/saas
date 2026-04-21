package com.colonel.saas.crawler;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpRequest;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
public class DouyinTalentCrawler extends CrawlerBase {

    private static final int MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;
    private final String talentUrl;

    public DouyinTalentCrawler(
            UaPool uaPool,
            ObjectMapper objectMapper,
            @Value("${douyin.crawler.talent-url:https://api.douyin.com/internal/talent/list}") String talentUrl) {
        super(uaPool.getPool());
        this.objectMapper = objectMapper;
        this.talentUrl = talentUrl;
    }

    public Optional<CrawlerTalentInfo> crawl(String talentId) {
        String encodedTalentId = URLEncoder.encode(talentId, StandardCharsets.UTF_8);
        String url = talentUrl + "?open_id=" + encodedTalentId;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                sleepBetweenRequests();
                String raw = fetch(url);
                return parse(raw, talentId);
            } catch (Exception e) {
                log.warn("Talent crawl failed: talentId={}, attempt={}, error={}",
                        talentId, attempt + 1, ExceptionUtil.getMessage(e));
                if (attempt < MAX_RETRIES - 1) {
                    sleepExponentialBackoff(attempt + 1);
                }
            }
        }
        log.error("Talent crawl exhausted retries: talentId={}, retries={}", talentId, MAX_RETRIES);
        return Optional.empty();
    }

    @Override
    protected String fetch(String url) throws Exception {
        String ua = nextUa();
        HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", ua)
                .header("Accept", "application/json")
                .timeout(10_000)
                .execute();
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException("Unexpected crawler HTTP status: " + response.getStatus());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response body");
        }
        return body;
    }

    private Optional<CrawlerTalentInfo> parse(String raw, String talentId) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");

            CrawlerTalentInfo info = new CrawlerTalentInfo();
            info.setTalentId(talentId);
            info.setNickname(textOrNull(data, "nickname"));
            info.setAvatarUrl(textOrNull(data, "avatar_url"));
            info.setFansCount(longOrZero(data, "fans_count"));
            info.setCreditScore(decimalOrZero(data, "credit_score"));
            info.setMainCategory(textOrNull(data, "main_category"));
            info.setRegion(textOrNull(data, "region"));
            info.setLastCrawlTime(LocalDateTime.now());
            return Optional.of(info);
        } catch (Exception e) {
            log.warn("Talent parse failed: talentId={}, error={}", talentId, ExceptionUtil.getMessage(e));
            return Optional.empty();
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Long longOrZero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? 0L : value.asLong(0L);
    }

    private BigDecimal decimalOrZero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.asText()).max(BigDecimal.ZERO).min(new BigDecimal("5.00"));
        } catch (NumberFormatException ignore) {
            return BigDecimal.ZERO;
        }
    }
}
