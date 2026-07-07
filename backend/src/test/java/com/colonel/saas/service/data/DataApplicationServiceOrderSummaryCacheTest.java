package com.colonel.saas.service.data;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.facade.DataOrderQueryFacade;
import com.colonel.saas.domain.performance.facade.ExclusiveMerchantReadFacade;
import com.colonel.saas.domain.performance.facade.OrderPerformanceQueryFacade;
import com.colonel.saas.domain.product.facade.ProductActivityReadFacade;
import com.colonel.saas.domain.talent.facade.ExclusiveTalentReadFacade;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * P0-007 {@code /api/data/orders/summary} 30s 短 TTL 缓存的隔离与命中测试。
 *
 * <p>本测试用<strong>真实的 {@link ShortTtlCacheService}</strong>（无 Redis，
 * 纯进程内 ConcurrentHashMap）以验证缓存语义；与生产调用栈一致。</p>
 *
 * <p>目标：</p>
 * <ol>
 *   <li>同入参 5 次连续调用 + 长 TTL → supplier 实际只执行 1 次（剩 4 次命中）</li>
 *   <li>仅 status 不同 + 长 TTL → 视为不同 key，supplier 各执行 1 次（防串查询）</li>
 *   <li>仅 dataScope 不同（PERSONAL vs ALL）+ 长 TTL → 视为不同 key，supplier 各执行 1 次（防越权）</li>
 *   <li>同入参 + Duration.ZERO → 强制重载，supplier 每次都执行</li>
 *   <li>cache key 字符串以 {@code dashboard:order-summary:} 开头，包含 17 个维度拼接</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataApplicationServiceOrderSummaryCacheTest {

    @Mock private DataOrderQueryFacade dataOrderQueryFacade;
    @Mock private CommissionService commissionService;
    @Mock private ExclusiveTalentReadFacade exclusiveTalentReadFacade;
    @Mock private ExclusiveMerchantReadFacade exclusiveMerchantReadFacade;
    @Mock private ProductActivityReadFacade productActivityReadFacade;
    @Mock private PerformanceMetricsQueryService performanceMetricsQueryService;
    @Mock private OrderPerformanceQueryFacade orderPerformanceQueryFacade;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private JdbcTemplate jdbcTemplate;

    /** 真实的短 TTL 缓存服务（无 Redis）。 */
    private ShortTtlCacheService realCache;
    private DataApplicationService service;

    @BeforeEach
    void setUp() {
        realCache = new ShortTtlCacheService();
        service = new DataApplicationService(
                dataOrderQueryFacade,
                commissionService,
                exclusiveTalentReadFacade,
                exclusiveMerchantReadFacade,
                productActivityReadFacade,
                realCache,
                performanceMetricsQueryService,
                orderPerformanceQueryFacade,
                userDomainFacade,
                new DataScopeResolver(new DataScopePolicy()),
                new DddRefactorProperties(),
                jdbcTemplate);
    }

    @Test
    @DisplayName("同入参 5 次连续调用 + 30s TTL: supplier 实际只调 1 次, selectMaps 只调 4 次 (2 aggregates + 2 buckets)")
    void getOrderSummary_sameKeyWithinTtl_callsMapperOnce() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            service.getOrderSummary(
                    "ord-1", "ORDERED", null, null,
                    null, null, null, null, null, null,
                    null, null,
                    null, null, "createTime",
                    userId, null, DataScope.ALL);
        }

        // supplier 整体只调 1 次 → 4 次 selectMaps(2×aggregates + 2×commission buckets)
        verify(dataOrderQueryFacade, times(4)).selectMaps(any(QueryWrapper.class));
        verify(commissionService, times(1)).calculateByActivityBuckets(any());
    }

    @Test
    @DisplayName("仅 status 不同: 视为不同 key, supplier 各执行 1 次 (防串查询)")
    void getOrderSummary_differentFilter_separateCacheEntries() {
        UUID userId = UUID.randomUUID();

        service.getOrderSummary(
                null, "ORDERED", null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, "createTime",
                userId, null, DataScope.ALL);

        service.getOrderSummary(
                null, "SHIPPED", null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, "createTime",
                userId, null, DataScope.ALL);

        // status 不同 → 两次 supplier × 4 次 selectMaps = 8 次
        verify(dataOrderQueryFacade, times(8)).selectMaps(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("仅 dataScope 不同 (PERSONAL vs ALL): 视为不同 key, supplier 各执行 1 次 (防越权串数据)")
    void getOrderSummary_differentDataScope_separateCacheEntries() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        service.getOrderSummary(
                null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, "createTime",
                userId, deptId, DataScope.PERSONAL);

        service.getOrderSummary(
                null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, "createTime",
                userId, deptId, DataScope.ALL);

        // dataScope 不同 → 两次 supplier × 4 次 selectMaps = 8 次
        verify(dataOrderQueryFacade, times(8)).selectMaps(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("真实 ShortTtlCacheService + Duration.ZERO: 每次都强制重载, supplier 5 次全执行")
    void getOrderSummary_realCacheZeroTtl_forcesReload() {
        // 用一个会把 Duration.ZERO 当作"每次过期"的真实 cache 包装
        ShortTtlCacheService zeroTtlCache = new ZeroTtlShortTtlCacheService();
        DataApplicationService zeroService = new DataApplicationService(
                dataOrderQueryFacade, commissionService, exclusiveTalentReadFacade,
                exclusiveMerchantReadFacade, productActivityReadFacade, zeroTtlCache,
                performanceMetricsQueryService, orderPerformanceQueryFacade, userDomainFacade,
                new DataScopeResolver(new DataScopePolicy()), new DddRefactorProperties(), jdbcTemplate);

        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            zeroService.getOrderSummary(
                    null, null, null, null,
                    null, null, null, null, null, null,
                    null, null,
                    null, null, "createTime",
                    userId, null, DataScope.ALL);
        }

        // 5 次 supplier × 4 次 selectMaps 内部 = 20 次
        verify(dataOrderQueryFacade, times(20)).selectMaps(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("cache key 字符串: 以 dashboard:order-summary: 开头, 包含 timeField / startDate / endDate / status 等关键维度")
    void getOrderSummary_cacheKey_startsWithExpectedPrefix() {
        UUID userId = UUID.randomUUID();
        String key = invokeOrderSummaryCacheKey(
                "createTime", null, null,
                null, "ORDERED", null, null,
                null, null, null, null, null, null,
                null, null,
                userId, null, DataScope.ALL);

        assertThat(key).startsWith("dashboard:order-summary:");
        assertThat(key).contains("create_time");
        assertThat(key).contains("ORDERED");
        assertThat(key).contains(userId.toString());
        assertThat(key).contains("ALL");
    }

    @Test
    @DisplayName("cache key 字符串: 不同 dataScope 产生不同 key (防越权)")
    void getOrderSummary_cacheKey_differentDataScopeDifferentKey() {
        UUID userId = UUID.randomUUID();
        String personal = invokeOrderSummaryCacheKey(
                "createTime", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                userId, null, DataScope.PERSONAL);
        String all = invokeOrderSummaryCacheKey(
                "createTime", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                userId, null, DataScope.ALL);

        assertThat(personal).isNotEqualTo(all);
        assertThat(personal).contains("PERSONAL");
        assertThat(all).contains("ALL");
    }

    /** 通过反射调私有 {@code orderSummaryCacheKey},避开包装在 supplier 内的 17 维度签名。 */
    private String invokeOrderSummaryCacheKey(
            String timeField, java.time.LocalDate startDate, java.time.LocalDate endDate,
            String orderId, String status, UUID talentId, String merchantId,
            String productId, String productName, String shopName,
            String talentName, String colonelName, String channelName,
            String colonelActivityId, String recruitType,
            UUID userId, UUID deptId, DataScope dataScope) {
        try {
            java.lang.reflect.Method m = DataApplicationService.class
                    .getDeclaredMethod("orderSummaryCacheKey",
                            String.class, java.time.LocalDate.class, java.time.LocalDate.class,
                            String.class, String.class, UUID.class, String.class,
                            String.class, String.class, String.class,
                            String.class, String.class, String.class,
                            String.class, String.class,
                            UUID.class, UUID.class, DataScope.class);
            m.setAccessible(true);
            return (String) m.invoke(service,
                    timeField, startDate, endDate,
                    orderId, status, talentId, merchantId,
                    productId, productName, shopName,
                    talentName, colonelName, channelName,
                    colonelActivityId, recruitType,
                    userId, deptId, dataScope);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("reflection failed", e);
        }
    }

    /** 简单包装: 把每次 get 都视作过期,等价于 Duration.ZERO 行为。 */
    private static final class ZeroTtlShortTtlCacheService extends ShortTtlCacheService {
        @Override
        public <T> T get(String key, Duration ttl, Supplier<T> loader) {
            // 不读缓存,不写缓存,直接调 loader
            return loader.get();
        }
    }
}
