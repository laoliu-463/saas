package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.vo.PartnerDetailVO;
import com.colonel.saas.vo.PartnerProductVO;
import com.colonel.saas.vo.PartnerVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MerchantService {

    private static final String PARTNER_TYPE_MERCHANT = "MERCHANT";
    private static final String PARTNER_TYPE_COLONEL = "COLONEL";
    private static final String PARTNER_CTE = """
            WITH partner_sources AS (
                SELECT
                    COALESCE(CAST(shop_id AS TEXT), NULLIF(TRIM(shop_name), '')) AS source_key,
                    CAST(shop_id AS TEXT) AS merchant_id,
                    NULLIF(TRIM(shop_name), '') AS merchant_name,
                    shop_id,
                    NULLIF(TRIM(shop_name), '') AS shop_name,
                    COUNT(DISTINCT product_id) AS product_count,
                    MAX(sync_time) AS latest_sync_time,
                    1 AS status
                FROM product_snapshot
                WHERE deleted = 0
                  AND (shop_id IS NOT NULL OR NULLIF(TRIM(shop_name), '') IS NOT NULL)
                GROUP BY shop_id, shop_name
                UNION ALL
                SELECT
                    COALESCE(CAST(shop_id AS TEXT), NULLIF(TRIM(merchant_id), '')) AS source_key,
                    NULLIF(TRIM(merchant_id), '') AS merchant_id,
                    NULLIF(TRIM(merchant_name), '') AS merchant_name,
                    shop_id,
                    NULLIF(TRIM(shop_name), '') AS shop_name,
                    0 AS product_count,
                    update_time AS latest_sync_time,
                    status
                FROM merchant
                WHERE deleted = 0
                  AND (shop_id IS NOT NULL OR NULLIF(TRIM(merchant_id), '') IS NOT NULL)
            ),
            partners AS (
                SELECT
                    COALESCE(MAX(merchant_id), source_key) AS partner_id,
                    COALESCE(MAX(merchant_name), MAX(shop_name), source_key) AS partner_name,
                    'MERCHANT' AS partner_type,
                    MAX(shop_id) AS shop_id,
                    COALESCE(MAX(shop_name), MAX(merchant_name)) AS shop_name,
                    COALESCE(SUM(product_count), 0) AS product_count,
                    MAX(latest_sync_time) AS latest_sync_time,
                    COALESCE(MAX(status), 1) AS status
                FROM partner_sources
                WHERE source_key IS NOT NULL
                GROUP BY source_key
            )
            """;

    private final MerchantMapper merchantMapper;
    private final OperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MerchantService(
            MerchantMapper merchantMapper,
            OperationLogService operationLogService,
            SysUserMapper sysUserMapper,
            JdbcTemplate jdbcTemplate) {
        this.merchantMapper = merchantMapper;
        this.operationLogService = operationLogService;
        this.sysUserMapper = sysUserMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public MerchantService(MerchantMapper merchantMapper, OperationLogService operationLogService, SysUserMapper sysUserMapper) {
        this(merchantMapper, operationLogService, sysUserMapper, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureMerchantFromOrder(ColonelsettlementOrder order) {
        String merchantId = resolveMerchantId(order);
        if (!StringUtils.hasText(merchantId)) {
            return;
        }
        Merchant existing = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, merchantId)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        Merchant merchant = new Merchant();
        merchant.setMerchantId(merchantId);
        merchant.setMerchantName(resolveMerchantName(order));
        merchant.setShopId(order.getShopId());
        merchant.setShopName(order.getShopName());
        merchant.setSourceOrderId(order.getOrderId());
        merchant.setStatus(1);
        // Keep merchant creation resilient during order sync. The order raw payload
        // already lives on colonelsettlement_order.extra_data, so merchant can be
        // created without duplicating the JSON blob here.
        merchant.setExtraData(null);
        merchant.setId(UUID.randomUUID());
        try {
            merchantMapper.insert(merchant);
        } catch (DuplicateKeyException ignore) {
            // concurrent insert is acceptable
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Merchant findOrCreateByChannel(String channelId, ColonelsettlementOrder order) {
        if (!StringUtils.hasText(channelId)) {
            return null;
        }
        Merchant existing = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, channelId)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        Merchant merchant = new Merchant();
        merchant.setMerchantId(channelId);
        merchant.setMerchantName(channelId);
        merchant.setShopId(order == null ? null : order.getShopId());
        merchant.setShopName(order == null ? null : order.getShopName());
        merchant.setSourceOrderId(order == null ? null : order.getOrderId());
        merchant.setStatus(0);
        merchant.setExtraData(null);
        merchant.setId(UUID.randomUUID());
        try {
            merchantMapper.insert(merchant);
            return merchant;
        } catch (DuplicateKeyException ignore) {
            return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getMerchantId, channelId)
                    .last("limit 1"));
        }
    }

    public IPage<PartnerVO> listPartners(String keyword, String partnerType, long page, long size) {
        long normalizedPage = Math.max(page, 1L);
        long normalizedSize = Math.min(Math.max(size, 1L), 100L);
        String normalizedType = normalizePartnerType(partnerType);
        if (PARTNER_TYPE_COLONEL.equals(normalizedType) || (normalizedType != null && !PARTNER_TYPE_MERCHANT.equals(normalizedType))) {
            Page<PartnerVO> empty = new Page<>(normalizedPage, normalizedSize, 0);
            empty.setRecords(List.of());
            return empty;
        }

        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required to list partners");
        List<Object> args = new ArrayList<>();
        String where = buildPartnerWhere(keyword, args);
        Long total = jdbcTemplate.queryForObject(
                PARTNER_CTE + "SELECT COUNT(1) FROM partners " + where,
                Long.class,
                args.toArray()
        );

        Page<PartnerVO> result = new Page<>(normalizedPage, normalizedSize, total == null ? 0L : total);
        if (result.getTotal() <= 0) {
            result.setRecords(List.of());
            return result;
        }

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(normalizedSize);
        listArgs.add((normalizedPage - 1L) * normalizedSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                PARTNER_CTE + """
                        SELECT
                            partner_id,
                            partner_name,
                            partner_type,
                            shop_id,
                            shop_name,
                            product_count,
                            latest_sync_time,
                            status
                        FROM partners
                        """ + where + " ORDER BY latest_sync_time DESC NULLS LAST, partner_name ASC LIMIT ? OFFSET ?",
                listArgs.toArray()
        );
        result.setRecords(rows.stream().map(this::toPartnerVO).toList());
        return result;
    }

    public PartnerDetailVO getPartnerDetail(String partnerId, String partnerType) {
        String normalizedType = normalizePartnerType(partnerType);
        if (!StringUtils.hasText(partnerId)
                || PARTNER_TYPE_COLONEL.equals(normalizedType)
                || (normalizedType != null && !PARTNER_TYPE_MERCHANT.equals(normalizedType))) {
            throw BusinessException.notFound("合作方不存在或类型暂不支持");
        }

        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required to get partner detail");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                PARTNER_CTE + """
                        SELECT
                            partner_id,
                            partner_name,
                            partner_type,
                            shop_id,
                            shop_name,
                            product_count,
                            latest_sync_time,
                            status
                        FROM partners
                        WHERE partner_id = ?
                        LIMIT 1
                        """,
                partnerId
        );
        if (rows.isEmpty()) {
            throw BusinessException.notFound("合作方不存在");
        }
        return toPartnerDetailVO(rows.get(0));
    }

    public IPage<PartnerProductVO> listPartnerProducts(String partnerId, long page, long size) {
        if (!StringUtils.hasText(partnerId)) {
            Page<PartnerProductVO> empty = new Page<>(Math.max(page, 1L), Math.min(Math.max(size, 1L), 100L), 0);
            empty.setRecords(List.of());
            return empty;
        }
        long normalizedPage = Math.max(page, 1L);
        long normalizedSize = Math.min(Math.max(size, 1L), 100L);
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required to list partner products");

        String partnerProductsCte = PARTNER_CTE + """
                , selected_partner AS (
                    SELECT partner_id, shop_id, shop_name
                    FROM partners
                    WHERE partner_id = ?
                    LIMIT 1
                )
                """;
        String fromWhere = """
                FROM product_snapshot ps
                JOIN selected_partner partner ON (
                    (partner.shop_id IS NOT NULL AND ps.shop_id = partner.shop_id)
                    OR (partner.shop_name IS NOT NULL AND LOWER(COALESCE(ps.shop_name, '')) = LOWER(partner.shop_name))
                    OR CAST(ps.shop_id AS TEXT) = partner.partner_id
                )
                WHERE ps.deleted = 0
                """;
        Long total = jdbcTemplate.queryForObject(
                partnerProductsCte + "SELECT COUNT(1) " + fromWhere,
                Long.class,
                partnerId
        );
        Page<PartnerProductVO> result = new Page<>(normalizedPage, normalizedSize, total == null ? 0L : total);
        if (result.getTotal() <= 0) {
            result.setRecords(List.of());
            return result;
        }

        List<Object> args = new ArrayList<>();
        args.add(partnerId);
        args.add(normalizedSize);
        args.add((normalizedPage - 1L) * normalizedSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                partnerProductsCte + """
                        SELECT
                            ps.product_id,
                            ps.title AS product_name,
                            ps.activity_id,
                            ps.cover,
                            ps.price_text,
                            ps.shop_id,
                            ps.shop_name,
                            ps.category_name,
                            ps.sales,
                            ps.status,
                            ps.status_text,
                            ps.sync_time
                        """ + fromWhere + " ORDER BY ps.sync_time DESC NULLS LAST, ps.product_id ASC LIMIT ? OFFSET ?",
                args.toArray()
        );
        result.setRecords(rows.stream().map(this::toPartnerProductVO).toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Merchant overrideMerchantAssignment(String merchantId, UUID newUserId, String reason, UUID currentUserId) {
        if (newUserId == null) {
            throw BusinessException.param("新负责人ID不能为空");
        }
        SysUser targetUser = sysUserMapper.selectById(newUserId);
        if (targetUser == null || targetUser.getDeleted() == 1) {
            throw BusinessException.notFound("目标负责人不存在");
        }
        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, merchantId)
                .last("limit 1"));
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        merchant.setOwnerId(newUserId);
        merchant.setOwnerDeptId(targetUser.getDeptId());
        OptimisticLockSupport.requireUpdated(merchantMapper.updateById(merchant));

        operationLogService.recordSystemAction(
                currentUserId,
                "商家管理",
                "归属覆盖",
                "POST",
                "merchant",
                merchantId,
                merchant.getMerchantName(),
                String.format("归属覆盖: 新负责人=%s, 原因=%s", newUserId, reason));

        return merchant;
    }

    private String resolveMerchantId(ColonelsettlementOrder order) {
        if (order.getExtraData() != null) {
            Object merchantId = order.getExtraData().get("merchant_id");
            if (merchantId != null && StringUtils.hasText(merchantId.toString())) {
                return merchantId.toString();
            }
        }
        return order.getShopId() == null ? null : String.valueOf(order.getShopId());
    }

    private String resolveMerchantName(ColonelsettlementOrder order) {
        Map<String, Object> extra = order.getExtraData();
        if (extra != null) {
            Object merchantName = extra.get("merchant_name");
            if (merchantName != null && StringUtils.hasText(merchantName.toString())) {
                return merchantName.toString();
            }
        }
        return order.getShopName();
    }

    private String buildPartnerWhere(String keyword, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            where.append("""
                     AND (
                        LOWER(COALESCE(partner_id, '')) LIKE ?
                        OR LOWER(COALESCE(partner_name, '')) LIKE ?
                        OR CAST(COALESCE(shop_id, 0) AS TEXT) LIKE ?
                     )
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return where.toString();
    }

    private String normalizePartnerType(String partnerType) {
        if (!StringUtils.hasText(partnerType)) {
            return null;
        }
        String normalized = partnerType.trim().toUpperCase(java.util.Locale.ROOT);
        if ("SHOP".equals(normalized) || "MERCHANT".equals(normalized) || "商家".equals(partnerType.trim())) {
            return PARTNER_TYPE_MERCHANT;
        }
        if ("COLONEL".equals(normalized) || "团长".equals(partnerType.trim())) {
            return PARTNER_TYPE_COLONEL;
        }
        return normalized;
    }

    private PartnerVO toPartnerVO(Map<String, Object> row) {
        PartnerVO vo = new PartnerVO();
        vo.setPartnerId(asText(row.get("partner_id")));
        vo.setPartnerName(asText(row.get("partner_name")));
        vo.setPartnerType(asText(row.get("partner_type")));
        vo.setShopId(asLong(row.get("shop_id")));
        vo.setShopName(asText(row.get("shop_name")));
        vo.setProductCount(asLong(row.get("product_count")));
        vo.setLatestSyncTime(asDateTime(row.get("latest_sync_time")));
        vo.setStatus(asInteger(row.get("status")));
        return vo;
    }

    private PartnerDetailVO toPartnerDetailVO(Map<String, Object> row) {
        PartnerDetailVO vo = new PartnerDetailVO();
        vo.setPartnerId(asText(row.get("partner_id")));
        vo.setPartnerName(asText(row.get("partner_name")));
        vo.setPartnerType(asText(row.get("partner_type")));
        vo.setShopId(asLong(row.get("shop_id")));
        vo.setShopName(asText(row.get("shop_name")));
        vo.setProductCount(asLong(row.get("product_count")));
        vo.setLatestSyncTime(asDateTime(row.get("latest_sync_time")));
        vo.setStatus(asInteger(row.get("status")));
        return vo;
    }

    private PartnerProductVO toPartnerProductVO(Map<String, Object> row) {
        PartnerProductVO vo = new PartnerProductVO();
        vo.setProductId(asText(row.get("product_id")));
        vo.setProductName(asText(row.get("product_name")));
        vo.setActivityId(asText(row.get("activity_id")));
        vo.setCover(asText(row.get("cover")));
        vo.setPriceText(asText(row.get("price_text")));
        vo.setShopId(asLong(row.get("shop_id")));
        vo.setShopName(asText(row.get("shop_name")));
        vo.setCategoryName(asText(row.get("category_name")));
        vo.setSales(asLong(row.get("sales")));
        vo.setStatus(asInteger(row.get("status")));
        vo.setStatusText(asText(row.get("status_text")));
        vo.setLatestSyncTime(asDateTime(row.get("sync_time")));
        return vo;
    }

    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        Long number = asLong(value);
        return number == null ? null : number.intValue();
    }

    private LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
