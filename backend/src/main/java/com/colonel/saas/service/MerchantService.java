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

/**
 * 合作方（商家/团长）管理服务。
 *
 * <p>职责：
 * <ul>
 *   <li>从订单同步中自动发现并持久化商家信息（ensureMerchantFromOrder）</li>
 *   <li>根据渠道 ID 查找或创建商家记录（findOrCreateByChannel）</li>
 *   <li>统一分页查询合作方列表，支持商家（MERCHANT）和团长（COLONEL）两种类型</li>
 *   <li>查询合作方详情及关联商品列表</li>
 *   <li>处理商家归属覆盖（overrideMerchantAssignment）并记录操作日志</li>
 * </ul>
 *
 * <p>架构角色：Service 层，聚合 MerchantMapper（商家持久化）、SysUserMapper（用户查询）、
 * OperationLogService（操作审计）和 JdbcTemplate（复杂 CTE 联合查询）。
 *
 * <p>业务领域：商家域 / 合作方管理。
 * 合作方数据来源包含三张核心表——merchant（商家表）、product_snapshot（商品快照表）、
 * colonelsettlement_order（团长结算订单表）以及 pick_source_mapping（转链映射表）。
 *
 * <p>依赖关系：
 * <ul>
 *   <li>{@link MerchantMapper} — 商家实体 CRUD</li>
 *   <li>{@link SysUserMapper} — 系统用户查询，用于归属覆盖时校验负责人</li>
 *   <li>{@link OperationLogService} — 操作日志审计</li>
 *   <li>{@link JdbcTemplate} — 执行原生 SQL（CTE 多表联合查询）</li>
 * </ul>
 */
@Service
public class MerchantService {

    /** 合作方类型常量：商家 */
    private static final String PARTNER_TYPE_MERCHANT = "MERCHANT";
    /** 合作方类型常量：团长 */
    private static final String PARTNER_TYPE_COLONEL = "COLONEL";

    /**
     * 商家合作方公共表表达式（CTE）。
     * 从 product_snapshot 和 merchant 表联合聚合，按 source_key 分组，
     * 汇总商家名称、店铺信息、商品数量和最新同步时间。
     */
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

    /**
     * 团长合作方公共表表达式（CTE）。
     * 从 colonelsettlement_order、colonel_activity、colonel_activity_product 和 pick_source_mapping
     * 四张表联合聚合，按 colonel_buyin_id 分组，汇总团长名称、关联商品数量和最新同步时间。
     */
    private static final String COLONEL_PARTNER_CTE = """
            WITH colonel_sources AS (
                SELECT
                    CAST(o.second_colonel_buyin_id AS TEXT) AS source_key,
                    NULL::TEXT AS colonel_display_name,
                    COUNT(DISTINCT o.product_id) AS product_count,
                    MAX(COALESCE(o.update_time, o.create_time)) AS latest_sync_time,
                    1 AS status
                FROM colonelsettlement_order o
                WHERE o.deleted = 0
                  AND o.second_colonel_buyin_id IS NOT NULL
                GROUP BY o.second_colonel_buyin_id
                UNION ALL
                SELECT
                    CAST(o.colonel_buyin_id AS TEXT),
                    MAX(NULLIF(TRIM(o.colonel_user_name), '')),
                    COUNT(DISTINCT o.product_id),
                    MAX(COALESCE(o.update_time, o.create_time)),
                    1
                FROM colonelsettlement_order o
                WHERE o.deleted = 0
                  AND o.colonel_buyin_id IS NOT NULL
                GROUP BY o.colonel_buyin_id
                UNION ALL
                SELECT
                    CAST(ca.colonel_buyin_id AS TEXT),
                    MAX(NULLIF(TRIM(ca.activity_name), '')),
                    COUNT(DISTINCT cap.product_id),
                    MAX(COALESCE(ca.last_sync_at, ca.update_time)),
                    1
                FROM colonel_activity ca
                LEFT JOIN colonel_activity_product cap
                    ON cap.activity_id = ca.activity_id AND cap.deleted = 0
                WHERE ca.deleted = 0
                  AND ca.colonel_buyin_id IS NOT NULL
                GROUP BY ca.colonel_buyin_id
                UNION ALL
                SELECT
                    NULLIF(TRIM(m.colonel_buyin_id), ''),
                    MAX(NULLIF(TRIM(m.channel_user_name), '')),
                    COUNT(DISTINCT m.product_id),
                    MAX(COALESCE(m.update_time, m.create_time)),
                    CASE WHEN MAX(COALESCE(m.status, 1)) > 0 THEN 1 ELSE 0 END
                FROM pick_source_mapping m
                WHERE m.deleted = 0
                  AND NULLIF(TRIM(m.colonel_buyin_id), '') IS NOT NULL
                GROUP BY NULLIF(TRIM(m.colonel_buyin_id), '')
            ),
            partners AS (
                SELECT
                    source_key AS partner_id,
                    COALESCE(
                        MAX(NULLIF(TRIM(colonel_display_name), '')),
                        '团长 ' || source_key
                    ) AS partner_name,
                    'COLONEL' AS partner_type,
                    NULL::BIGINT AS shop_id,
                    NULL::TEXT AS shop_name,
                    COALESCE(SUM(product_count), 0) AS product_count,
                    MAX(latest_sync_time) AS latest_sync_time,
                    COALESCE(MAX(status), 1) AS status
                FROM colonel_sources
                WHERE source_key IS NOT NULL
                GROUP BY source_key
            )
            """;

    /** 商家实体 MyBatis-Plus Mapper，提供商家 CRUD 操作 */
    private final MerchantMapper merchantMapper;
    /** 操作日志服务，记录商家归属覆盖等审计事件 */
    private final OperationLogService operationLogService;
    /** 系统用户 Mapper，用于查询负责人信息 */
    private final SysUserMapper sysUserMapper;
    /** Spring JDBC 模板，用于执行复杂原生 SQL（CTE 联合查询） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造注入全部依赖。
     *
     * @param merchantMapper     商家 Mapper
     * @param operationLogService 操作日志服务
     * @param sysUserMapper      系统用户 Mapper
     * @param jdbcTemplate       JDBC 模板（CTE 查询必需）
     */
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

    /**
     * 简化构造器，供测试等不需要 JdbcTemplate 的场景使用。
     *
     * @param merchantMapper     商家 Mapper
     * @param operationLogService 操作日志服务
     * @param sysUserMapper      系统用户 Mapper
     */
    public MerchantService(MerchantMapper merchantMapper, OperationLogService operationLogService, SysUserMapper sysUserMapper) {
        this(merchantMapper, operationLogService, sysUserMapper, null);
    }

    /**
     * 从订单中确保商家记录存在。
     * 在订单同步流程中被调用，幂等创建商家——如果商家已存在则直接跳过。
     *
     * <p>处理流程：
     * <ol>
     *   <li>第一步：从订单 extraData 或 shopId 中解析出商家 ID</li>
     *   <li>第二步：查询商家是否已存在，存在则直接返回（幂等）</li>
     *   <li>第三步：构建商家实体，填充商家名称、店铺信息、来源订单等字段</li>
     *   <li>第四步：尝试插入，忽略并发重复插入异常（DuplicateKeyException）</li>
     * </ol>
     *
     * @param order 团长结算订单，提供商家 ID、店铺等原始信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureMerchantFromOrder(ColonelsettlementOrder order) {
        // 第一步：从订单 extraData 或 shopId 中解析出商家 ID
        String merchantId = resolveMerchantId(order);
        if (!StringUtils.hasText(merchantId)) {
            return;
        }
        // 第二步：查询商家是否已存在，存在则直接返回（幂等）
        Merchant existing = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, merchantId)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        // 第三步：构建商家实体
        Merchant merchant = new Merchant();
        merchant.setMerchantId(merchantId);
        merchant.setMerchantName(resolveMerchantName(order));
        merchant.setShopId(order.getShopId());
        merchant.setShopName(order.getShopName());
        merchant.setSourceOrderId(order.getOrderId());
        merchant.setStatus(1);
        // 保持商家创建在订单同步中的弹性；订单原始数据已存于 colonelsettlement_order.extra_data，
        // 此处不重复存储 JSON 数据块
        merchant.setExtraData(null);
        merchant.setId(UUID.randomUUID());
        // 第四步：尝试插入，忽略并发重复插入异常
        try {
            merchantMapper.insert(merchant);
        } catch (DuplicateKeyException ignore) {
            // 并发插入是可接受的，直接忽略
        }
    }

    /**
     * 根据渠道 ID 查找或创建商家记录。
     * 用于订单同步中根据渠道标识自动关联商家；若商家不存在则新建（状态默认为 0=未激活）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>第一步：校验渠道 ID 非空</li>
     *   <li>第二步：按渠道 ID 查询商家，存在则直接返回</li>
     *   <li>第三步：构建新商家实体，填充渠道信息和订单关联信息</li>
     *   <li>第四步：尝试插入；若发生重复键异常则回查已有记录返回</li>
     * </ol>
     *
     * @param channelId 渠道 ID（作为商家唯一标识）
     * @param order     团长结算订单，可为 null（用于关联店铺信息）
     * @return 查找到的或新创建的商家实体；渠道 ID 为空时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Merchant findOrCreateByChannel(String channelId, ColonelsettlementOrder order) {
        // 第一步：校验渠道 ID 非空
        if (!StringUtils.hasText(channelId)) {
            return null;
        }
        // 第二步：按渠道 ID 查询商家
        Merchant existing = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, channelId)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        // 第三步：构建新商家实体
        Merchant merchant = new Merchant();
        merchant.setMerchantId(channelId);
        merchant.setMerchantName(channelId);
        merchant.setShopId(order == null ? null : order.getShopId());
        merchant.setShopName(order == null ? null : order.getShopName());
        merchant.setSourceOrderId(order == null ? null : order.getOrderId());
        merchant.setStatus(0);
        merchant.setExtraData(null);
        merchant.setId(UUID.randomUUID());
        // 第四步：尝试插入；若发生重复键异常则回查已有记录返回
        try {
            merchantMapper.insert(merchant);
            return merchant;
        } catch (DuplicateKeyException ignore) {
            return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getMerchantId, channelId)
                    .last("limit 1"));
        }
    }

    /**
     * 分页查询合作方列表，支持按关键词搜索和类型筛选。
     *
     * <p>处理流程：
     * <ol>
     *   <li>第一步：标准化分页参数（page >= 1, 1 <= size <= 100）</li>
     *   <li>第二步：标准化合作方类型（支持 SHOP/MERCHANT/商家/COLONEL/团长 等别名）</li>
     *   <li>第三步：根据类型选择对应的 CTE（商家或团长），类型无效时返回空结果</li>
     *   <li>第四步：执行分页查询并返回结果</li>
     * </ol>
     *
     * @param keyword     搜索关键词，模糊匹配合作方 ID、名称、店铺 ID
     * @param partnerType 合作方类型（MERCHANT/COLONEL/商家/团长/SHOP），null 表示查全部
     * @param page        页码（从 1 开始）
     * @param size        每页条数（最大 100）
     * @return 分页合作方列表
     */
    public IPage<PartnerVO> listPartners(String keyword, String partnerType, long page, long size) {
        // 第一步：标准化分页参数
        long normalizedPage = Math.max(page, 1L);
        long normalizedSize = Math.min(Math.max(size, 1L), 100L);
        // 第二步：标准化合作方类型
        String normalizedType = normalizePartnerType(partnerType);
        // 第三步：类型无效时返回空结果
        if (normalizedType != null && !PARTNER_TYPE_MERCHANT.equals(normalizedType) && !PARTNER_TYPE_COLONEL.equals(normalizedType)) {
            Page<PartnerVO> empty = new Page<>(normalizedPage, normalizedSize, 0);
            empty.setRecords(List.of());
            return empty;
        }
        // 第四步：根据类型选择对应的 CTE，执行分页查询
        String partnerCte = PARTNER_TYPE_COLONEL.equals(normalizedType) ? COLONEL_PARTNER_CTE : PARTNER_CTE;
        return queryPartnerPage(partnerCte, keyword, normalizedPage, normalizedSize);
    }

    /**
     * 执行合作方分页查询的内部实现。
     * 使用 CTE + WHERE + LIMIT/OFFSET 方式查询 PostgreSQL。
     *
     * <p>处理流程：
     * <ol>
     *   <li>第一步：拼装 WHERE 条件（关键词过滤）</li>
     *   <li>第二步：执行 COUNT 查询获取总记录数</li>
     *   <li>第三步：总记录为 0 时直接返回空分页</li>
     *   <li>第四步：拼装分页参数（LIMIT + OFFSET），执行数据查询</li>
     *   <li>第五步：将查询结果映射为 PartnerVO 列表返回</li>
     * </ol>
     *
     * @param partnerCte 合作方 CTE 语句（商家或团长）
     * @param keyword    搜索关键词
     * @param page       页码
     * @param size       每页条数
     * @return 分页合作方列表
     */
    private IPage<PartnerVO> queryPartnerPage(String partnerCte, String keyword, long page, long size) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required to list partners");
        // 第一步：拼装 WHERE 条件
        List<Object> args = new ArrayList<>();
        String where = buildPartnerWhere(keyword, args);
        // 第二步：执行 COUNT 查询获取总记录数
        Long total = jdbcTemplate.queryForObject(
                partnerCte + "SELECT COUNT(1) FROM partners " + where,
                Long.class,
                args.toArray()
        );

        // 第三步：总记录为 0 时直接返回空分页
        Page<PartnerVO> result = new Page<>(page, size, total == null ? 0L : total);
        if (result.getTotal() <= 0) {
            result.setRecords(List.of());
            return result;
        }

        // 第四步：拼装分页参数，执行数据查询
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add((page - 1L) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                partnerCte + """
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
        // 第五步：将查询结果映射为 PartnerVO 列表
        result.setRecords(rows.stream().map(this::toPartnerVO).toList());
        return result;
    }

    /**
     * 查询单个合作方详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>第一步：校验合作方 ID 非空且类型合法</li>
     *   <li>第二步：根据类型选择 CTE（商家或团长）</li>
     *   <li>第三步：执行精确查询（WHERE partner_id = ?）</li>
     *   <li>第四步：结果为空时抛出业务异常；否则映射为 PartnerDetailVO 返回</li>
     * </ol>
     *
     * @param partnerId   合作方 ID
     * @param partnerType 合作方类型（可为 null）
     * @return 合作方详情 VO
     * @throws BusinessException 合作方不存在或类型不支持时抛出
     */
    public PartnerDetailVO getPartnerDetail(String partnerId, String partnerType) {
        // 第一步：校验参数合法性
        String normalizedType = normalizePartnerType(partnerType);
        if (!StringUtils.hasText(partnerId)
                || (normalizedType != null
                && !PARTNER_TYPE_MERCHANT.equals(normalizedType)
                && !PARTNER_TYPE_COLONEL.equals(normalizedType))) {
            throw BusinessException.notFound("合作方不存在或类型暂不支持");
        }
        // 第二步：根据类型选择 CTE
        String partnerCte = PARTNER_TYPE_COLONEL.equals(normalizedType) ? COLONEL_PARTNER_CTE : PARTNER_CTE;
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required to get partner detail");
        // 第三步：执行精确查询
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                partnerCte + """
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
        // 第四步：结果为空时抛出异常，否则映射返回
        if (rows.isEmpty()) {
            throw BusinessException.notFound("合作方不存在");
        }
        return toPartnerDetailVO(rows.get(0));
    }

    /**
     * 查询商家合作方关联商品列表（无类型指定）。
     * <p>
     * 委托给 {@link #listPartnerProducts(String, String, long, long)}，类型为 null。
     * </p>
     *
     * @param partnerId 合作方 ID
     * @param page      页码
     * @param size      每页条数
     * @return 分页商品列表
     */
    public IPage<PartnerProductVO> listPartnerProducts(String partnerId, long page, long size) {
        return listPartnerProducts(partnerId, null, page, size);
    }

    /**
     * 查询合作方关联商品列表。
     * <p>
     * 根据合作方类型分流：COLONEL 走 {@link #listColonelPartnerProducts}，
     * 其他类型走 {@link #listMerchantPartnerProducts}。
     * partnerId 为空时直接返回空分页结果。
     * </p>
     *
     * @param partnerId   合作方 ID
     * @param partnerType 合作方类型（MERCHANT / COLONEL），null 时按商家处理
     * @param page        页码
     * @param size        每页条数
     * @return 分页商品列表
     */
    public IPage<PartnerProductVO> listPartnerProducts(String partnerId, String partnerType, long page, long size) {
        if (!StringUtils.hasText(partnerId)) {
            Page<PartnerProductVO> empty = new Page<>(Math.max(page, 1L), Math.min(Math.max(size, 1L), 100L), 0);
            empty.setRecords(List.of());
            return empty;
        }
        String normalizedType = normalizePartnerType(partnerType);
        if (PARTNER_TYPE_COLONEL.equals(normalizedType)) {
            return listColonelPartnerProducts(partnerId, page, size);
        }
        return listMerchantPartnerProducts(partnerId, page, size);
    }

    /**
     * 查询商家合作方关联商品列表。
     * <p>
     * 通过 CTE 选定目标商家（按 partner_id 匹配），然后从 product_snapshot
     * 表中关联查询该商家的店铺商品。支持 shop_id 精确匹配和 shop_name 模糊匹配。
     * </p>
     *
     * @param partnerId 商家合作方 ID
     * @param page      页码
     * @param size      每页条数
     * @return 分页商品列表
     */
    private IPage<PartnerProductVO> listMerchantPartnerProducts(String partnerId, long page, long size) {
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
        return queryPartnerProductPage(partnerProductsCte, fromWhere, partnerId, normalizedPage, normalizedSize);
    }

    /**
     * 查询团长合作方关联商品列表。
     * <p>
     * 通过多种数据源关联查找团长的商品：
     * <ul>
     *   <li>pick_source_mapping 中 colonel_buyin_id 匹配的商品</li>
     *   <li>colonel_activity + colonel_activity_product 关联的商品</li>
     *   <li>colonelsettlement_order 中 colonel_buyin_id / second_colonel_buyin_id 匹配的商品</li>
     * </ul>
     * </p>
     *
     * @param partnerId 团长百应 ID
     * @param page      页码
     * @param size      每页条数
     * @return 分页商品列表
     */
    private IPage<PartnerProductVO> listColonelPartnerProducts(String partnerId, long page, long size) {
        long normalizedPage = Math.max(page, 1L);
        long normalizedSize = Math.min(Math.max(size, 1L), 100L);
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required to list partner products");

        String partnerProductsCte = """
                WITH target_colonel AS (
                    SELECT ?::TEXT AS colonel_buyin_id
                )
                """;
        String fromWhere = """
                FROM product_snapshot ps
                CROSS JOIN target_colonel target
                WHERE ps.deleted = 0
                  AND (
                    EXISTS (
                        SELECT 1
                        FROM pick_source_mapping m
                        WHERE m.deleted = 0
                          AND m.colonel_buyin_id = target.colonel_buyin_id
                          AND m.product_id = ps.product_id
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM colonel_activity ca
                        JOIN colonel_activity_product cap
                            ON cap.activity_id = ca.activity_id AND cap.deleted = 0
                        WHERE ca.deleted = 0
                          AND CAST(ca.colonel_buyin_id AS TEXT) = target.colonel_buyin_id
                          AND cap.product_id = ps.product_id
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM colonelsettlement_order o
                        WHERE o.deleted = 0
                          AND o.product_id = ps.product_id
                          AND (
                              CAST(o.second_colonel_buyin_id AS TEXT) = target.colonel_buyin_id
                              OR CAST(o.colonel_buyin_id AS TEXT) = target.colonel_buyin_id
                          )
                    )
                  )
                """;
        return queryPartnerProductPage(partnerProductsCte, fromWhere, partnerId, normalizedPage, normalizedSize);
    }

    /**
     * 通用合作方商品分页查询实现。
     * <p>
     * 使用 CTE 前缀 + FROM/WHERE 子句拼装完整 SQL，支持 COUNT 查询和分页数据查询。
     * </p>
     *
     * @param ctePrefix  CTE 前缀 SQL
     * @param fromWhere  FROM + WHERE 子句
     * @param partnerId  合作方 ID（绑定参数）
     * @param page       页码
     * @param size       每页条数
     * @return 分页商品列表
     */
    private IPage<PartnerProductVO> queryPartnerProductPage(
            String ctePrefix,
            String fromWhere,
            String partnerId,
            long page,
            long size) {
        Long total = jdbcTemplate.queryForObject(
                ctePrefix + "SELECT COUNT(1) " + fromWhere,
                Long.class,
                partnerId
        );
        Page<PartnerProductVO> result = new Page<>(page, size, total == null ? 0L : total);
        if (result.getTotal() <= 0) {
            result.setRecords(List.of());
            return result;
        }

        List<Object> args = new ArrayList<>();
        args.add(partnerId);
        args.add(size);
        args.add((page - 1L) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                ctePrefix + """
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

    /**
     * 管理员覆盖商家归属。
     * <p>
     * 校验新负责人存在后，将商家 ownerId 和 ownerDeptId 更新为新负责人信息，
     * 并通过 {@link OptimisticLockSupport} 保证乐观锁更新成功，
     * 同时记录操作日志（含覆盖原因）。
     * </p>
     *
     * @param merchantId    商家 ID（merchant_id 字段）
     * @param newUserId     新负责人用户 ID
     * @param reason        覆盖原因（操作日志记录）
     * @param currentUserId 当前操作者用户 ID
     * @return 更新后的商家实体
     * @throws BusinessException 新负责人不存在、商家不存在时抛出
     */
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

    /**
     * 从订单中解析商家 ID。
     * <p>
     * 优先从 extraData.merchant_id 获取，否则回退到 shopId。
     * </p>
     *
     * @param order 结算订单
     * @return 商家 ID，无法解析时返回 null
     */
    private String resolveMerchantId(ColonelsettlementOrder order) {
        if (order.getExtraData() != null) {
            Object merchantId = order.getExtraData().get("merchant_id");
            if (merchantId != null && StringUtils.hasText(merchantId.toString())) {
                return merchantId.toString();
            }
        }
        return order.getShopId() == null ? null : String.valueOf(order.getShopId());
    }

    /**
     * 从订单中解析商家名称。
     * <p>
     * 优先从 extraData.merchant_name 获取，否则回退到 shopName。
     * </p>
     *
     * @param order 结算订单
     * @return 商家名称，无法解析时返回 null
     */
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

    /**
     * 构建合作方查询的 WHERE 子句。
     * <p>
     * 支持按关键词模糊匹配合作方 ID、名称和店铺 ID（忽略大小写）。
     * 参数通过 args 列表传出，使用 PreparedStatement 参数化绑定（防 SQL 注入）。
     * </p>
     *
     * @param keyword 搜索关键词
     * @param args    参数列表（方法向此列表追加绑定值）
     * @return WHERE 子句 SQL 字符串
     */
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

    /**
     * 归一化合作方类型。
     * <p>
     * 支持别名映射：SHOP / MERCHANT / 商家 → MERCHANT；COLONEL / 团长 → COLONEL。
     * 空值返回 null，未知类型返回大写原值。
     * </p>
     *
     * @param partnerType 原始类型字符串
     * @return 归一化后的类型标识，null 表示不指定
     */
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

    /**
     * 将数据库行映射为合作方 VO。
     *
     * @param row 数据库查询结果行
     * @return PartnerVO 实例
     */
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

    /**
     * 将数据库行映射为合作方详情 VO。
     *
     * @param row 数据库查询结果行
     * @return PartnerDetailVO 实例
     */
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

    /**
     * 将数据库行映射为合作方商品 VO。
     *
     * @param row 数据库查询结果行
     * @return PartnerProductVO 实例
     */
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

    /**
     * 将 Object 转换为 String，null 安全。
     *
     * @param value 原始值
     * @return 字符串，null 时返回 null
     */
    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 将 Object 转换为 Long，null 安全。
     * <p>
     * 支持 Number 类型直接转换和字符串解析，解析失败时返回 null。
     * </p>
     *
     * @param value 原始值
     * @return Long 值，null 或解析失败时返回 null
     */
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

    /**
     * 将 Object 转换为 Integer，null 安全。
     *
     * @param value 原始值
     * @return Integer 值，null 时返回 null
     */
    private Integer asInteger(Object value) {
        Long number = asLong(value);
        return number == null ? null : number.intValue();
    }

    /**
     * 将 Object 转换为 LocalDateTime，null 安全。
     * <p>
     * 支持 LocalDateTime 和 java.sql.Timestamp 两种类型转换。
     * </p>
     *
     * @param value 原始值
     * @return LocalDateTime 值，不支持的类型或 null 时返回 null
     */
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
