package com.colonel.saas.domain.product.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.domain.product.application.port.ProductLibraryQueryPort;
import com.colonel.saas.entity.Product;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品库（Product Library）查询 Application Service（DDD-PRODUCT-001 Slice 1+2）。
 *
 * <p>从 {@code service.ProductService} 切出的独立查询方法：
 * <ul>
 *   <li>{@link #listLibraryCategories} —— 商品库展示分类名去重排序（Slice 1）</li>
 *   <li>{@link #getAdminCounts} —— 后台管理面板统计计数（Slice 2）</li>
 *   <li>{@link #getSelectedLibraryPage} / {@link #getSelectedLibraryCursorPage} —— 商品库分页查询端口（Slice 3）</li>
 * </ul>
 *
 * <p>本类承接商品库应用层入口；分页查询的旧实现暂由 Legacy 适配器提供，
 * 后续可在端口后替换而不改变 Controller 契约。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link ProductSnapshotMapper} —— 快照数据访问</li>
 *   <li>{@link ProductOperationStateMapper} —— 运营状态数据访问</li>
 * </ul>
 */
@Service
public class ProductLibraryApplicationService {

    private final ProductSnapshotMapper snapshotMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final ProductLibraryQueryPort queryPort;

    @Autowired
    public ProductLibraryApplicationService(
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper,
            ProductLibraryQueryPort queryPort) {
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
        this.queryPort = queryPort;
    }

    /**
     * 保留给现有纯单元测试的构造器；商品库分页能力必须通过 Spring 注入查询端口。
     */
    public ProductLibraryApplicationService(
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper) {
        this(snapshotMapper, operationStateMapper, null);
    }

    public IPage<Product> getSelectedLibraryPage(long page, long size, ProductLibraryPageQuery query) {
        return requireQueryPort().getSelectedLibraryPage(page, size, query);
    }

    public ProductLibraryCursorPage getSelectedLibraryCursorPage(
            String cursor,
            long limit,
            ProductLibraryPageQuery query) {
        return requireQueryPort().getSelectedLibraryCursorPage(cursor, limit, query);
    }

    private ProductLibraryQueryPort requireQueryPort() {
        if (queryPort == null) {
            throw new IllegalStateException("商品库查询端口未配置");
        }
        return queryPort;
    }

    /**
     * 商品库展示分类名（去重 + 排序）。
     *
     * @return 去重、按字典序排序的分类名列表
     */
    public List<String> listLibraryCategories() {
        List<String> categories = snapshotMapper.listDisplayingLibraryCategoryNames();
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * 后台管理面板统计计数。
     *
     * <p>聚合 7 个统计指标：</p>
     * <ul>
     *   <li>{@code snapshotTotal} — 快照总行数</li>
     *   <li>{@code relationTotal} — 运营状态总行数</li>
     *   <li>{@code distinctProductTotal} — 不同商品数</li>
     *   <li>{@code displayingTotal} — 展示中数</li>
     *   <li>{@code pendingTotal} — 待审核数</li>
     *   <li>{@code hiddenTotal} — 已隐藏数</li>
     *   <li>{@code activityTotal} — 不同活动数</li>
     * </ul>
     */
    public AdminProductCounts getAdminCounts() {
        return new AdminProductCounts(
                snapshotMapper.countActiveRows(),
                operationStateMapper.countActiveRows(),
                snapshotMapper.countDistinctProducts(),
                operationStateMapper.countDisplayingRows(),
                operationStateMapper.countPendingRows(),
                operationStateMapper.countHiddenRows(),
                snapshotMapper.countDistinctActivities());
    }

    /**
     * 后台管理面板统计计数 DTO（迁自 {@code ProductService.AdminProductCounts}）。
     */
    public record AdminProductCounts(
            long snapshotTotal,
            long relationTotal,
            long distinctProductTotal,
            long displayingTotal,
            long pendingTotal,
            long hiddenTotal,
            long activityTotal) {
    }
}
