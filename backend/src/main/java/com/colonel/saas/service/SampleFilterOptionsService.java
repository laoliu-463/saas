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

/**
 * 寄样单列表筛选条件服务：为前端筛选面板提供静态枚举选项与动态选项。
 * <p>
 * 静态选项（状态、合作类型、寄样方、作业类型）固定返回；
 * 动态选项（渠道、招商、商品、合作方、店铺、物流）根据当前用户数据权限范围内的寄样单实时聚合，
 * 上限 {@code DYNAMIC_LIMIT} 条，防止选项过多导致前端渲染性能问题。
 */
@Service
public class SampleFilterOptionsService {

    /** 动态选项最大条数，防止选项列表过大 */
    private static final int DYNAMIC_LIMIT = 200;
    /** 加载寄样单的分页大小，用于提取动态选项的源数据 */
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

    /**
     * 构建寄样单列表的全部筛选选项。
     * <p>
     * 静态选项直接硬编码返回；动态选项基于当前用户数据权限范围内的寄样单聚合生成，
     * 通过 {@link #loadScopedSamples} 加载受权限约束的样本数据。
     *
     * @param userId    当前用户 ID
     * @param deptId    当前用户所属部门 ID
     * @param dataScope 数据范围（PERSONAL / GROUP / ALL）
     * @param roleCodes 当前用户角色编码集合，用于判断是否为业务员专属视图
     * @return 包含所有筛选选项的 DTO
     */
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

    /** 返回寄样单状态的静态筛选选项（待审核、待发货、快递中、待交作业、已完成、已拒绝、已关闭）。 */
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

    /** 返回合作类型的静态筛选选项（免费寄样、付费寄样、置换寄样）。 */
    private List<SampleFilterOptionItem> staticCooperationTypes() {
        return List.of(
                item("免费寄样", "FREE_SAMPLE"),
                item("付费寄样", "PAID_SAMPLE"),
                item("置换寄样", "EXCHANGE_SAMPLE"));
    }

    /** 返回寄样方类型的静态筛选选项（商家、团长、其他）。 */
    private List<SampleFilterOptionItem> staticSampleOwnerTypes() {
        return List.of(
                item("商家", "MERCHANT"),
                item("团长", "COLONEL"),
                item("其他", "OTHER"));
    }

    /** 返回作业类型的静态筛选选项（有订单、无订单、部分完成）。 */
    private List<SampleFilterOptionItem> staticHomeworkTypes() {
        return List.of(
                item("有订单", "HAS_ORDER"),
                item("无订单", "NO_ORDER"),
                item("部分完成", "PARTIAL"));
    }

    /**
     * 加载当前用户数据权限范围内的寄样单（上限 {@link #PAGE_SIZE} 条），作为动态选项聚合的数据源。
     * <p>
     * 当用户为业务员（BIZ_STAFF）且非管理员/业务主管时，仅返回该用户审核的寄样单；
     * 否则按全局查询（受 RowPolicy 自动注入范围限制）。
     */
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

    /** 从寄样单中聚合去重的渠道用户选项，label 优先使用真实姓名。 */
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

    /** 从商品快照的操作状态中解析招商人，聚合去重生成招商人筛选选项。 */
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

    /** 从关联商品中聚合去重的商品筛选选项，label 优先使用商品名称。 */
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

    /** 从商品快照中聚合去重的合作方（活动 ID）筛选选项。 */
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

    /** 从商品快照中聚合去重的店铺筛选选项，label 优先使用店铺名称。 */
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

    /** 从寄样单发货编码中聚合去重的物流公司筛选选项。 */
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

    /** 批量加载寄样单关联的商品，按商品 ID 去重建 Map。 */
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

    /** 批量加载商品对应的快照，用于提取合作方和店铺信息。 */
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

    /** 批量加载渠道用户的真实姓名，返回 userId → 显示名称的映射。 */
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

    /**
     * 解析商品对应的招商人 ID：通过商品快照查找操作状态记录中的 assigneeId。
     *
     * @return 招商人 ID，无法解析时返回 null
     */
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

    /**
     * 格式化用户显示名称：优先"真实姓名 (用户名)"，其次真实姓名，再其次用户名，均无则使用 fallback。
     */
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

    /** 创建筛选选项项的便捷工厂方法。 */
    private static SampleFilterOptionItem item(String label, String value) {
        return new SampleFilterOptionItem(label, value);
    }

    /** 判断角色编码集合中是否包含指定角色之一。 */
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
