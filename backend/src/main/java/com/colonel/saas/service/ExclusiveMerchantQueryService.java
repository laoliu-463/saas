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

@Service
public class ExclusiveMerchantQueryService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExclusiveMerchantMapper exclusiveMerchantMapper;
    private final SysUserMapper sysUserMapper;

    public ExclusiveMerchantQueryService(
            ExclusiveMerchantMapper exclusiveMerchantMapper,
            SysUserMapper sysUserMapper) {
        this.exclusiveMerchantMapper = exclusiveMerchantMapper;
        this.sysUserMapper = sysUserMapper;
    }

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

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }
}
