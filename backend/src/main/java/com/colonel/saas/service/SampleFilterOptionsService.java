package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.SampleFilterOptionItem;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SampleFilterOptionsService {

    private static final int DYNAMIC_LIMIT = 200;
    private static final int PAGE_SIZE = 200;

    private final SampleRequestMapper sampleRequestMapper;
    private final ProductMapper productMapper;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final SysUserMapper sysUserMapper;

    public SampleFilterOptionsService(
            SampleRequestMapper sampleRequestMapper,
            ProductMapper productMapper,
            ProductSnapshotMapper productSnapshotMapper,
            ProductOperationStateMapper productOperationStateMapper,
            SysUserMapper sysUserMapper) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.productMapper = productMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public SampleFilterOptionsDTO buildOptions(
            UUID userId,
            UUID deptId,
            com.colonel.saas.common.enums.DataScope dataScope,
            Object roleCodes) {
        SampleFilterOptionsDTO dto = new SampleFilterOptionsDTO();
        dto.setStatuses(staticStatuses());
        dto.setCooperationTypes(staticCooperationTypes());
        dto.setSampleOwnerTypes(staticSampleOwnerTypes());
        dto.setHomeworkTypes(staticHomeworkTypes());

        List<SampleRequest> samples = loadScopedSamples(userId, deptId, dataScope, roleCodes);
        if (samples.isEmpty()) {
            dto.setChannels(List.of());
            dto.setRecruiters(List.of());
            dto.setProducts(List.of());
            dto.setPartners(List.of());
            dto.setShops(List.of());
            dto.setLogisticsCompanies(List.of());
            return dto;
        }

        Map<UUID, Product> productMap = loadProducts(samples);
        Map<UUID, ProductSnapshot> snapshotMap = loadSnapshots(productMap.values());
        Map<UUID, String> userNameMap = loadUserNames(samples);

        dto.setChannels(buildChannelOptions(samples, userNameMap));
        dto.setRecruiters(buildRecruiterOptions(samples, productMap));
        dto.setProducts(buildProductOptions(samples, productMap));
        dto.setPartners(buildPartnerOptions(snapshotMap));
        dto.setShops(buildShopOptions(snapshotMap));
        dto.setLogisticsCompanies(buildLogisticsOptions(samples));
        return dto;
    }

    private List<SampleFilterOptionItem> staticStatuses() {
        return List.of(
                item("待审核", "PENDING_AUDIT"),
                item("待发货", "PENDING_SHIP"),
                item("快递中", "SHIPPED"),
                item("待交作业", "PENDING_TASK"),
                item("已完成", "FINISHED"),
                item("已拒绝", "REJECTED"),
                item("已关闭", "CLOSED"));
    }

    private List<SampleFilterOptionItem> staticCooperationTypes() {
        return List.of(
                item("免费寄样", "FREE_SAMPLE"),
                item("付费寄样", "PAID_SAMPLE"),
                item("置换寄样", "EXCHANGE_SAMPLE"));
    }

    private List<SampleFilterOptionItem> staticSampleOwnerTypes() {
        return List.of(
                item("商家", "MERCHANT"),
                item("团长", "COLONEL"),
                item("其他", "OTHER"));
    }

    private List<SampleFilterOptionItem> staticHomeworkTypes() {
        return List.of(
                item("有订单", "HAS_ORDER"),
                item("无订单", "NO_ORDER"),
                item("部分完成", "PARTIAL"));
    }

    private List<SampleRequest> loadScopedSamples(
            UUID userId,
            UUID deptId,
            com.colonel.saas.common.enums.DataScope dataScope,
            Object roleCodes) {
        Page<SampleRequest> pageReq = new Page<>(1, PAGE_SIZE);
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        IPage<SampleRequest> samplePage;
        if (dataScope == com.colonel.saas.common.enums.DataScope.PERSONAL
                && hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF)
                && !hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER)) {
            samplePage = sampleRequestMapper.findPageForAuditor(pageReq, userId, wrapper);
        } else {
            samplePage = sampleRequestMapper.findPageWithScope(pageReq, wrapper);
        }
        return samplePage.getRecords() == null ? List.of() : samplePage.getRecords();
    }

    private List<SampleFilterOptionItem> buildChannelOptions(
            List<SampleRequest> samples,
            Map<UUID, String> userNameMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (SampleRequest sample : samples) {
            UUID channelId = sample.getChannelUserId();
            if (channelId == null || map.size() >= DYNAMIC_LIMIT) {
                continue;
            }
            String value = channelId.toString();
            if (map.containsKey(value)) {
                continue;
            }
            String label = userNameMap.getOrDefault(channelId, value);
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildRecruiterOptions(
            List<SampleRequest> samples,
            Map<UUID, Product> productMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (SampleRequest sample : samples) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            Product product = productMap.get(sample.getProductId());
            if (product == null) {
                continue;
            }
            UUID recruiterId = resolveRecruiterId(product);
            if (recruiterId == null) {
                continue;
            }
            String value = recruiterId.toString();
            if (map.containsKey(value)) {
                continue;
            }
            SysUser user = sysUserMapper.selectById(recruiterId);
            String label = formatUserLabel(user, value);
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildProductOptions(
            List<SampleRequest> samples,
            Map<UUID, Product> productMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (SampleRequest sample : samples) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            Product product = productMap.get(sample.getProductId());
            if (product == null) {
                continue;
            }
            String value = product.getId().toString();
            if (map.containsKey(value)) {
                continue;
            }
            String label = StringUtils.hasText(product.getName()) ? product.getName() : value;
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildPartnerOptions(Map<UUID, ProductSnapshot> snapshotMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (ProductSnapshot snapshot : snapshotMap.values()) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            if (snapshot.getActivityId() == null) {
                continue;
            }
            String value = snapshot.getActivityId();
            if (map.containsKey(value)) {
                continue;
            }
            String label = value;
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildShopOptions(Map<UUID, ProductSnapshot> snapshotMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (ProductSnapshot snapshot : snapshotMap.values()) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            if (snapshot.getShopId() == null && !StringUtils.hasText(snapshot.getShopName())) {
                continue;
            }
            String value = snapshot.getShopId() != null
                    ? String.valueOf(snapshot.getShopId())
                    : snapshot.getShopName();
            if (map.containsKey(value)) {
                continue;
            }
            String label = StringUtils.hasText(snapshot.getShopName()) ? snapshot.getShopName() : value;
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildLogisticsOptions(List<SampleRequest> samples) {
        LinkedHashSet<String> companies = new LinkedHashSet<>();
        for (SampleRequest sample : samples) {
            if (companies.size() >= DYNAMIC_LIMIT) {
                break;
            }
            if (StringUtils.hasText(sample.getShipperCode())) {
                companies.add(sample.getShipperCode().trim());
            }
        }
        return companies.stream()
                .map(code -> item(code, code))
                .toList();
    }

    private Map<UUID, Product> loadProducts(List<SampleRequest> samples) {
        Set<UUID> ids = samples.stream()
                .map(SampleRequest::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(p -> p != null && p.getId() != null)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, ProductSnapshot> loadSnapshots(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductSnapshot> map = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
            if (snapshot != null) {
                map.put(product.getId(), snapshot);
            }
        }
        return map;
    }

    private Map<UUID, String> loadUserNames(List<SampleRequest> samples) {
        Set<UUID> userIds = samples.stream()
                .map(SampleRequest::getChannelUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .filter(u -> u != null && u.getId() != null)
                .collect(Collectors.toMap(SysUser::getId, u -> formatUserLabel(u, u.getId().toString()), (a, b) -> a));
    }

    private UUID resolveRecruiterId(Product product) {
        ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
        if (snapshot == null) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                        .eq(ProductOperationState::getProductId, snapshot.getProductId())
                        .last("LIMIT 1"));
        return state == null ? null : state.getAssigneeId();
    }

    private static String formatUserLabel(SysUser user, String fallback) {
        if (user == null) {
            return fallback;
        }
        String realName = user.getRealName() == null ? "" : user.getRealName().trim();
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        if (StringUtils.hasText(realName) && StringUtils.hasText(username)) {
            return realName + " (" + username + ")";
        }
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return fallback;
    }

    private static SampleFilterOptionItem item(String label, String value) {
        return new SampleFilterOptionItem(label, value);
    }

    private static boolean hasAnyRole(Object roleCodes, String... roles) {
        if (!(roleCodes instanceof Collection<?> collection)) {
            return false;
        }
        for (String role : roles) {
            if (collection.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
