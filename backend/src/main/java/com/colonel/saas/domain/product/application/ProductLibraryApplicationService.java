package com.colonel.saas.domain.product.application;

import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品库（Product Library）查询 Application Service（DDD-PRODUCT-001 Slice 1）。
 *
 * <p>从 {@code service.ProductService} 切出的独立查询方法：
 * <ul>
 *   <li>{@link #listLibraryCategories} —— 商品库展示分类名去重排序</li>
 * </ul>
 *
 * <p>本类承接 ProductService 的 listLibraryCategories 业务逻辑，作为 domain 层
 * 入口；Service 改为 1-line 委派壳。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link ProductSnapshotMapper} —— 快照数据访问（listDisplayingLibraryCategoryNames）</li>
 * </ul>
 */
@Service
public class ProductLibraryApplicationService {

    private final ProductSnapshotMapper snapshotMapper;

    public ProductLibraryApplicationService(ProductSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
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
}