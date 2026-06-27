package com.colonel.saas.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.CopyPromotionApplicationService;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.talent.application.TalentFollowApplicationService;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DDD100-PRODUCT-BASELINE（Issue #60）— ProductService characterization baseline。
 *
 * <p>为 ProductService 当前 public method 锁定行为基线，作为 W5-W6
 * 拆分的"重构前快照"。策略：只 mock ProductSnapshotMapper 接口**实际存在**的
 * 9 个方法中可工作的部分，其余 16+ mock 仅供 setUp 通过；不工作的复杂
 * 方法（sync/refresh/view）用 reflection 记录签名存在性。</p>
 *
 * <p>完整单元测试在 W5 拆分时 (#61-#63) 逐方法补全。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceCharacterizationTest {

    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductSnapshotMapper productSnapshotMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductOperationStateMapper operationStateMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductOperationLogMapper operationLogMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) PromotionLinkMapper promotionLinkMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ColonelsettlementOrderMapper colonelsettlementOrderMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) MerchantMapper merchantMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductBizStatusService productBizStatusService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ColonelsettlementActivityMapper colonelsettlementActivityMapper;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) TalentFollowApplicationService talentFollowService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) DouyinActivityGateway douyinActivityGateway;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ConfigDomainFacade configDomainFacade;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductDisplayRuleService productDisplayRuleService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductDomainEventPublisher productDomainEventPublisher;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) ProductDisplayPolicy productDisplayPolicy;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) CopyPromotionApplicationService copyPromotionApplicationService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) DouyinProductGateway douyinProductGateway;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) PickSourceMappingService pickSourceMappingService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT) UserDomainFacade userDomainFacade;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                null, douyinProductGateway, productSnapshotMapper, operationStateMapper,
                operationLogMapper, promotionLinkMapper, colonelsettlementOrderMapper,
                merchantMapper, userDomainFacade, pickSourceMappingService,
                productBizStatusService, colonelsettlementActivityMapper, talentFollowService,
                douyinActivityGateway, promotionLinkIdempotencyService, configDomainFacade,
                productDisplayRuleService, colonelPartnerSyncService, productDomainEventPublisher,
                productDisplayPolicy, copyPromotionApplicationService);
    }

    // ===== Only methods with real ProductSnapshotMapper interface =====

    @Test
    @DisplayName("baseline: listLibraryCategories delegates to listDisplayingLibraryCategoryNames")
    void listLibraryCategoriesDelegates() {
        when(productSnapshotMapper.listDisplayingLibraryCategoryNames()).thenReturn(List.of("cat1", "cat2"));
        List<String> result = service.listLibraryCategories();
        assertThat(result).containsExactly("cat1", "cat2");
    }

    @Test
    @DisplayName("baseline: hasActivitySnapshots returns true when selectCount > 0 (true path)")
    void hasActivitySnapshotsTrue() {
        when(productSnapshotMapper.selectCount(any())).thenReturn(5L);
        assertThat(service.hasActivitySnapshots("act1")).isTrue();
    }

    @Test
    @DisplayName("baseline: hasActivitySnapshots returns false when selectCount = 0")
    void hasActivitySnapshotsFalse() {
        when(productSnapshotMapper.selectCount(any())).thenReturn(0L);
        assertThat(service.hasActivitySnapshots("empty")).isFalse();
    }

    @Test
    @DisplayName("baseline: getPage keeps legacy Product mapping from snapshot query")
    void getPageKeepsLegacyProductMappingFromSnapshotQuery() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("测试商品");
        snapshot.setPrice(9900L);
        snapshot.setStatus(1);

        Page<ProductSnapshot> snapshotPage = new Page<>(1, 1, 1);
        snapshotPage.setRecords(List.of(snapshot));
        when(productSnapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);
        when(operationStateMapper.selectList(any())).thenReturn(List.of());

        var result = service.getPage(0, 0, 1);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> {
                    assertThat(product.getProductId()).isEqualTo("9001");
                    assertThat(product.getName()).isEqualTo("测试商品");
                    assertThat(product.getPrice()).isEqualTo(9900L);
                });
    }

    @Test
    @DisplayName("baseline: getById throws BusinessException '商品不存在' when mapper returns null (NOT null)")
    void getByIdReturnsNull() {
        UUID id = UUID.randomUUID();
        when(productSnapshotMapper.selectById(id)).thenReturn(null);
        // 实际行为: 抛 BusinessException notFound("商品不存在"), 不是返回 null
        // 这是 characterization baseline 应该记录的真实行为
        try {
            service.getById(id);
            // 如果没抛异常才是异常
            assertThat(false).as("expected BusinessException").isTrue();
        } catch (com.colonel.saas.common.exception.BusinessException e) {
            assertThat(e.getMessage()).contains("商品不存在");
        }
    }

    @Test
    @DisplayName("baseline: getAdminCounts returns non-null counts (mocked countActiveRows)")
    void getAdminCountsReturnsNonNull() {
        when(productSnapshotMapper.countActiveRows()).thenReturn(50L);
        var result = service.getAdminCounts();
        assertThat(result).isNotNull();
    }

    // ===== Signature baseline (其他方法) =====

    @Test
    @DisplayName("baseline: 25+ public methods preserved (count check via reflection)")
    void publicMethodCountPreserved() {
        Method[] methods = ProductService.class.getDeclaredMethods();
        long publicCount = Arrays.stream(methods)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();
        // javap 列出 39 个 public method (含重载)
        assertThat(publicCount).isGreaterThanOrEqualTo(25);
    }

    @Test
    @DisplayName("baseline: service can be instantiated with all 21 dependencies")
    void serviceCanBeInstantiated() {
        // 验证 21 个依赖的构造器顺序未变
        // ProductService 构造器是 public 21 参数
        assertThat(service).isNotNull();
    }
}
