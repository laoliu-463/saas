package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.dto.performance.ExclusiveMerchantDetailDTO;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.entity.SysUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 独家商家查询服务，提供商家独家状态查询和招商员独家商家列表查询。
 *
 * <p>核心逻辑：根据商家 ID 或招商员 ID 查询当前月份的独家商家记录，
 * 仅返回状态为有效（status=1）且未删除的记录。</p>
 *
 * <ul>
 *   <li>提供 {@link #getByPartnerId} 根据商家 ID 查询当月独家状态</li>
 *   <li>提供 {@link #listMyExclusiveMerchants} 查询招商员名下当月独家商家列表</li>
 *   <li>独家判定以当月 effective_month 为准，到期月份自动计算为 effectiveMonth + 1 个月</li>
 * </ul>
 *
 * <p><b>业务领域：</b>配置域 — 独家商家查询</p>
 * <p><b>协作关系：</b>依赖 {@link ExclusiveMerchantMapper} 查询独家商家记录；
 * 依赖 {@link SysUserMapper} 解析招商员用户名</p>
 *
 * @see ExclusiveMerchantMapper
 * @see ExclusiveMerchantDetailDTO
 */
@Service
public class ExclusiveMerchantQueryService {

    /** 月份格式化器，解析和格式化 "yyyy-MM" 格式的月份字符串 */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 独家商家 Mapper，查询 exclusive_merchants 表 */
    private final ExclusiveMerchantMapper exclusiveMerchantMapper;

    /** 系统用户 Mapper，用于解析招商员用户名 */
    private final SysUserMapper sysUserMapper;

    public ExclusiveMerchantQueryService(
            ExclusiveMerchantMapper exclusiveMerchantMapper,
            SysUserMapper sysUserMapper) {
        this.exclusiveMerchantMapper = exclusiveMerchantMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 根据商家 ID 查询当月独家状态。
     *
     * <ol>
     *   <li>第一步：校验 partnerId 非空，为空则返回 exclusive=false 的空 DTO</li>
     *   <li>第二步：查询 exclusive_merchants 表，条件为 merchantId + 当月 effectiveMonth + status=1 + deleted=0</li>
     *   <li>第三步：匹配到记录则转换为详情 DTO（exclusive=true），未匹配则返回 exclusive=false</li>
     * </ol>
     *
     * @param partnerId 商家 ID（shopId）
     * @return 独家商家详情 DTO，包含独家状态和招商员信息
     */
    public ExclusiveMerchantDetailDTO getByPartnerId(String partnerId) {
        ExclusiveMerchantDetailDTO dto = new ExclusiveMerchantDetailDTO();
        dto.setPartnerId(partnerId);
        dto.setExclusive(false);
        if (!StringUtils.hasText(partnerId)) {
            return dto;
        }
        String month = YearMonth.now().format(MONTH);
        ExclusiveMerchant match = exclusiveMerchantMapper.selectOne(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getMerchantId, partnerId.trim())
                .eq(ExclusiveMerchant::getEffectiveMonth, month)
                .eq(ExclusiveMerchant::getStatus, 1)
                .eq(ExclusiveMerchant::getDeleted, 0)
                .orderByDesc(ExclusiveMerchant::getCreateTime)
                .last("LIMIT 1"));
        if (match == null) {
            return dto;
        }
        return toDetail(match, true);
    }

    /**
     * 查询招商员名下当月独家商家列表。
     *
     * <ol>
     *   <li>第一步：校验 recruiterId 非空，为空则返回空列表</li>
     *   <li>第二步：查询 exclusive_merchants 表，条件为 userId + 当月 effectiveMonth + status=1 + deleted=0</li>
     *   <li>第三步：逐条转换为详情 DTO 并返回</li>
     * </ol>
     *
     * @param recruiterId 招商员用户 ID
     * @return 当月独家商家详情列表，无记录时返回空列表
     */
    public List<ExclusiveMerchantDetailDTO> listMyExclusiveMerchants(UUID recruiterId) {
        if (recruiterId == null) {
            return List.of();
        }
        String month = YearMonth.now().format(MONTH);
        List<ExclusiveMerchant> rows = exclusiveMerchantMapper.selectList(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getUserId, recruiterId)
                .eq(ExclusiveMerchant::getEffectiveMonth, month)
                .eq(ExclusiveMerchant::getStatus, 1)
                .eq(ExclusiveMerchant::getDeleted, 0)
                .orderByDesc(ExclusiveMerchant::getCreateTime));
        List<ExclusiveMerchantDetailDTO> result = new ArrayList<>();
        for (ExclusiveMerchant row : rows) {
            result.add(toDetail(row, true));
        }
        return result;
    }

    /**
     * 将独家商家实体转换为详情 DTO。
     *
     * @param merchant  独家商家实体
     * @param exclusive 是否标记为独家状态
     * @return 独家商家详情 DTO，包含招商员名称和到期月份
     */
    private ExclusiveMerchantDetailDTO toDetail(ExclusiveMerchant merchant, boolean exclusive) {
        ExclusiveMerchantDetailDTO dto = new ExclusiveMerchantDetailDTO();
        dto.setPartnerId(merchant.getMerchantId());
        dto.setPartnerName(merchant.getMerchantName());
        dto.setExclusive(exclusive);
        dto.setRecruiterId(merchant.getUserId() == null ? null : merchant.getUserId().toString());
        dto.setRecruiterName(resolveUserName(merchant.getUserId()));
        dto.setEffectiveMonth(merchant.getEffectiveMonth());
        dto.setExpireMonth(resolveExpireMonth(merchant.getEffectiveMonth()));
        dto.setStatus(merchant.getStatus() != null && merchant.getStatus() == 1 ? "ACTIVE" : "INACTIVE");
        return dto;
    }

    /**
     * 根据生效月份计算到期月份（生效月份 + 1 个月）。
     *
     * @param effectiveMonth 生效月份，格式 "yyyy-MM"
     * @return 到期月份字符串，解析失败或为空时返回 null
     */
    private String resolveExpireMonth(String effectiveMonth) {
        if (!StringUtils.hasText(effectiveMonth)) {
            return null;
        }
        try {
            return YearMonth.parse(effectiveMonth, MONTH).plusMonths(1).format(MONTH);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 根据用户 ID 查询用户名，用于填充招商员名称。
     *
     * @param userId 用户 ID
     * @return 用户名，用户不存在时返回 null
     */
    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }
}
