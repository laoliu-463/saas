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

/**
 * 抖音达人公开页爬虫，负责通过抖音内部 API 采集达人的公开信息。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>通过达人 open_id 构造请求 URL，调用抖音内部达人列表接口</li>
 *   <li>内置最多 3 次重试机制，失败时采用指数退避策略</li>
 *   <li>解析 JSON 响应，提取昵称、头像、粉丝数、信用分、主营类目、地区等字段</li>
 *   <li>返回 {@link CrawlerTalentInfo} 实体供上层持久化</li>
 * </ol>
 *
 * <p>所属业务领域：爬虫子系统（crawler）
 * <p>技术细节：
 * <ul>
 *   <li>HTTP 客户端使用 Hutool {@link HttpRequest}，超时 10 秒</li>
 *   <li>每次请求前通过 {@link #sleepBetweenRequests()} 控速</li>
 *   <li>UA 轮换由父类 {@link CrawlerBase} 提供</li>
 * </ul>
 *
 * @see CrawlerBase    爬虫基类，提供 UA 轮换与退避休眠
 * @see CrawlerTalentInfo 爬取结果实体
 * @see UaPool         User-Agent 池
 */
@Slf4j
@Component
public class DouyinTalentCrawler extends CrawlerBase {

    /** 最大重试次数，超过此次数后放弃并返回空结果 */
    private static final int MAX_RETRIES = 3;

    /** Jackson JSON 解析器，用于解析爬取到的原始响应 */
    private final ObjectMapper objectMapper;

    /** 抖音达人列表接口基础 URL，由配置项 {@code douyin.crawler.talent-url} 指定 */
    private final String talentUrl;

    /**
     * 构造抖音达人爬虫。
     *
     * @param uaPool       User-Agent 池，注入父类用于 UA 轮换
     * @param objectMapper Jackson ObjectMapper 实例
     * @param talentUrl    达人接口 URL，配置项 {@code douyin.crawler.talent-url}
     */
    public DouyinTalentCrawler(
            UaPool uaPool,
            ObjectMapper objectMapper,
            @Value("${douyin.crawler.talent-url:https://api.douyin.com/internal/talent/list}") String talentUrl) {
        super(uaPool.getPool());
        this.objectMapper = objectMapper;
        this.talentUrl = talentUrl;
    }

    /**
     * 爬取指定达人的公开信息。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>对 talentId 进行 URL 编码，拼接完整的请求 URL</li>
     *   <li>最多重试 {@link #MAX_RETRIES} 次：先执行随机间隔休眠，再调用 {@link #fetch} 获取原始响应</li>
     *   <li>调用 {@link #parse} 解析 JSON 响应为 {@link CrawlerTalentInfo}</li>
     *   <li>失败时执行指数退避（{@link #sleepExponentialBackoff}），最终失败返回 {@link Optional#empty()}</li>
     * </ol>
     *
     * @param talentId 达人的抖音 open_id
     * @return 爬取成功时返回包含达人信息的 {@link Optional}，全部重试失败返回空
     */
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

    /**
     * 执行具体的 HTTP GET 请求。
     * <p>
     * 使用随机选取的 User-Agent，设置 Accept 为 JSON，超时 10 秒。
     * 非 2xx 响应码或空响应体会抛出异常触发重试。
     *
     * @param url 完整的请求 URL
     * @return 原始响应体字符串
     * @throws IllegalStateException 当 HTTP 状态码非 2xx 或响应体为空时
     */
    @Override
    protected String fetch(String url) throws Exception {
        String ua = nextUa();
        // 发送 GET 请求，携带随机 UA 以降低被识别概率
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

    /**
     * 解析原始 JSON 响应为 {@link CrawlerTalentInfo} 实体。
     * <p>
     * 从 {@code data} 节点中提取 nickname、avatar_url、fans_count、
     * credit_score、main_category、region 等字段，并设置当前时间为 lastCrawlTime。
     *
     * @param raw       原始 JSON 响应字符串
     * @param talentId  达人 ID，用于填充实体的 talentId 字段
     * @return 解析成功返回包含达人信息的 {@link Optional}，解析失败返回空
     */
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

    /**
     * 从 JSON 节点中安全提取文本字段。
     * <p>
     * 若节点不存在（missing）或为 null 值则返回 null，否则返回 asText() 结果。
     *
     * @param node  父级 JSON 节点
     * @param field 字段名
     * @return 文本值或 null
     */
    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * 从 JSON 节点中安全提取 Long 型字段。
     * <p>
     * 若节点不存在或为 null 值则返回 0L。
     *
     * @param node  父级 JSON 节点
     * @param field 字段名
     * @return Long 值，默认 0L
     */
    private Long longOrZero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? 0L : value.asLong(0L);
    }

    /**
     * 从 JSON 节点中安全提取 BigDecimal 型字段，并做范围钳位。
     * <p>
     * 返回值被限制在 [0, 5.00] 范围内（信用分范围），解析失败时返回 {@link BigDecimal#ZERO}。
     *
     * @param node  父级 JSON 节点
     * @param field 字段名
     * @return 钳位后的 BigDecimal 值，默认 {@link BigDecimal#ZERO}
     */
    private BigDecimal decimalOrZero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            // 信用分钳位在 [0, 5.00] 范围
            return new BigDecimal(value.asText()).max(BigDecimal.ZERO).min(new BigDecimal("5.00"));
        } catch (NumberFormatException ignore) {
            return BigDecimal.ZERO;
        }
    }
}
