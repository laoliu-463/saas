package com.colonel.saas.domain.colonel.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 团长主数据查询 Application Service（DDD-COLONEL-002 Slice 3）。
 *
 * <p>负责团长主数据的分页查询与详情查询。包含关键字、来源、联系方式可用性等筛选条件。</p>
 *
 * <p>业务域：用户域 — 团长主数据查询。本服务只读不写，不涉及业务规则。</p>
 */
@Service
public class ColonelPartnerMasterDataApplicationService {

    private final ColonelPartnerMapper colonelPartnerMapper;

    public ColonelPartnerMasterDataApplicationService(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    /**
     * 分页查询团长主数据列表。
     */
    public PageResult<ColonelPartner> list(
            String keyword,
            String source,
            Boolean hasContact,
            long page,
            long size) {
        QueryWrapper<ColonelPartner> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("last_sync_at").orderByAsc("colonel_name");
        if (StringUtils.hasText(keyword)) {
            wrapper.like("colonel_name", keyword.trim());
        }
        if (StringUtils.hasText(source)) {
            wrapper.eq("source", source.trim());
        }
        if (Boolean.TRUE.equals(hasContact)) {
            wrapper.and(w -> w.isNotNull("contact_phone")
                    .or()
                    .isNotNull("contact_wechat")
                    .or()
                    .isNotNull("contact_name"));
        } else if (Boolean.FALSE.equals(hasContact)) {
            wrapper.isNull("contact_phone")
                    .isNull("contact_wechat")
                    .isNull("contact_name");
        }
        Page<ColonelPartner> result = colonelPartnerMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    /**
     * 按 ID 查询团长主数据详情。
     */
    public ColonelPartner detail(UUID id) {
        ColonelPartner partner = colonelPartnerMapper.selectById(id);
        if (partner == null) {
            throw BusinessException.notFound("团长主数据不存在");
        }
        return partner;
    }

    /**
     * 列出全部已存在的来源枚举值（去重）。
     */
    public List<String> listSources() {
        QueryWrapper<ColonelPartner> wrapper = new QueryWrapper<>();
        wrapper.select("source").isNotNull("source");
        List<ColonelPartner> records = colonelPartnerMapper.selectList(wrapper);
        return records.stream()
                .map(ColonelPartner::getSource)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }
}