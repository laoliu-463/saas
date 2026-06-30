package com.colonel.saas.domain.product.application;

import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
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
 * </ul>
 *
 * <p>本类承接 ProductService 的两个查询业务逻辑，作为 domain 层
 * 入口；Service 改为 1-line 委派壳。</p>
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

    public ProductLibraryApplicationService(
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper) {
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
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