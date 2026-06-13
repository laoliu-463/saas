package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.dto.sample.SampleFilterOptionItem;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 寄样单列表筛选条件服务：为前端筛选面板提供静态枚举选项与动态选项。
 */
@Service
public class SampleFilterOptionsService {

    private static final int DYNAMIC_LIMIT = 200;
    private static final int PAGE_SIZE = 200;

    private final SampleRequestMapper sampleRequestMapper;
    private final ProductDomainFacade productDomainFacade;
    private final UserDomainFacade userDomainFacade;

    public SampleFilterOptionsService(
            SampleRequestMapper sampleRequestMapper,
            ProductDomainFacade productDomainFacade,
            UserDomainFacade userDomainFacade) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.productDomainFacade = productDomainFacade;
        this.userDomainFacade = userDomainFacade;
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

        Map<UUID, ProductReadDTO> productMap = loadProducts(samples);
        Map<UUID, ProductSnapshotReadDTO> snapshotMap = loadSnapshots(productMap.values());
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
            Map<UUID, ProductReadDTO> productMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (SampleRequest sample : samples) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            ProductReadDTO product = productMap.get(sample.getProductId());
            if (product == null) {
                continue;
            }
            UUID recruiterId = productDomainFacade.findProductSnapshotAssigneeId(product.id());
            if (recruiterId == null) {
                continue;
            }
            String value = recruiterId.toString();
            if (map.containsKey(value)) {
                continue;
            }
            UserOptionResponse user = userDomainFacade.getUserById(recruiterId);
            String label = formatUserLabel(user, value);
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildProductOptions(
            List<SampleRequest> samples,
            Map<UUID, ProductReadDTO> productMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (SampleRequest sample : samples) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            ProductReadDTO product = productMap.get(sample.getProductId());
            if (product == null) {
                continue;
            }
            String value = product.id().toString();
            if (map.containsKey(value)) {
                continue;
            }
            String label = StringUtils.hasText(product.name()) ? product.name() : value;
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildPartnerOptions(Map<UUID, ProductSnapshotReadDTO> snapshotMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (ProductSnapshotReadDTO snapshot : snapshotMap.values()) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            if (snapshot.activityId() == null) {
                continue;
            }
            String value = snapshot.activityId();
            if (map.containsKey(value)) {
                continue;
            }
            map.put(value, item(value, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildShopOptions(Map<UUID, ProductSnapshotReadDTO> snapshotMap) {
        LinkedHashMap<String, SampleFilterOptionItem> map = new LinkedHashMap<>();
        for (ProductSnapshotReadDTO snapshot : snapshotMap.values()) {
            if (map.size() >= DYNAMIC_LIMIT) {
                break;
            }
            if (snapshot.shopId() == null && !StringUtils.hasText(snapshot.shopName())) {
                continue;
            }
            String value = snapshot.shopId() != null
                    ? String.valueOf(snapshot.shopId())
                    : snapshot.shopName();
            if (map.containsKey(value)) {
                continue;
            }
            String label = StringUtils.hasText(snapshot.shopName()) ? snapshot.shopName() : value;
            map.put(value, item(label, value));
        }
        return new ArrayList<>(map.values());
    }

    private List<SampleFilterOptionItem> buildLogisticsOptions(List<SampleRequest> samples) {
        java.util.LinkedHashSet<String> companies = new java.util.LinkedHashSet<>();
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

    private Map<UUID, ProductReadDTO> loadProducts(List<SampleRequest> samples) {
        Set<UUID> ids = samples.stream()
                .map(SampleRequest::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productDomainFacade.loadProductsByIds(ids);
    }

    private Map<UUID, ProductSnapshotReadDTO> loadSnapshots(Collection<ProductReadDTO> products) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductSnapshotReadDTO> map = new LinkedHashMap<>();
        for (ProductReadDTO product : products) {
            if (product == null || product.id() == null) {
                continue;
            }
            ProductSnapshotReadDTO snapshot = productDomainFacade.findSnapshotById(product.id());
            if (snapshot != null) {
                map.put(product.id(), snapshot);
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
        return userDomainFacade.getUsersByIds(userIds).stream()
                .filter(u -> u != null && u.id() != null)
                .collect(Collectors.toMap(
                        UserOptionResponse::id,
                        u -> formatUserLabel(u, u.id().toString()),
                        (a, b) -> a));
    }

    private static String formatUserLabel(UserOptionResponse user, String fallback) {
        if (user == null) {
            return fallback;
        }
        String realName = user.realName() == null ? "" : user.realName().trim();
        String username = user.username() == null ? "" : user.username().trim();
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
