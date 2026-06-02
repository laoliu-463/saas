package com.colonel.saas.service;

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
 * 团长主数据查询服务。
 * <p>
 * 供 {@link com.colonel.saas.controller.ColonelPartnerMasterDataController} 调用，
 * 负责团长主数据的分页查询与详情查询。包含关键字、来源、联系方式可用性等筛选条件。
 * </p>
 *
 * <p><b>业务域：</b>用户域 — 团长主数据查询</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link ColonelPartnerMapper} — 团长主数据访问</li>
 * </ul>
 *
 * <p><b>不变量：</b>本服务只读不写，不涉及业务规则；不参与业绩归属、提成、独家覆盖。</p>
 *
 * <p><b>实现说明：</b>使用 {@link QueryWrapper}（基于字符串列名）而非
 * {@code LambdaQueryWrapper}，与本仓库其他主数据服务（如
 * {@link UserMasterDataService}）保持一致，便于在不依赖 MyBatis-Plus
 * TableInfo 初始化的单测环境下验证 SQL 拼装。</p>
 */
@Service
public class ColonelPartnerMasterDataService {

    /** 团长主数据 Mapper */
    private final ColonelPartnerMapper colonelPartnerMapper;

    /**
     * 构造注入团长主数据 Mapper。
     *
     * @param colonelPartnerMapper 团长主数据 Mapper 实例
     */
    public ColonelPartnerMasterDataService(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    /**
     * 分页查询团长主数据列表。
     *
     * <p>筛选条件：</p>
     * <ul>
     *     <li>{@code keyword} — 团长名称模糊匹配（trim 后非空）</li>
     *     <li>{@code source} — 数据来源精确匹配（trim 后非空）</li>
     *     <li>{@code hasContact} — 联系方式可用性（true=任一联系方式非空、false=三者均为空、null=不限）</li>
     * </ul>
     *
     * <p>排序：先按最后同步时间倒序，再按团长名称正序，保证最近同步的团长靠前。</p>
     *
     * @param keyword    关键字（可空）
     * @param source     来源（可空）
     * @param hasContact 是否有联系方式（可空）
     * @param page       页码（从 1 开始，1 是首页）
     * @param size       每页条数
     * @return 分页后的团长主数据列表（PageResult 包装）
     */
    public PageResult<ColonelPartner> list(
            String keyword,
            String source,
            Boolean hasContact,
            long page,
            long size) {
        // 第一步：构建查询条件
        QueryWrapper<ColonelPartner> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("last_sync_at").orderByAsc("colonel_name");
        // 第二步：按关键字模糊匹配团长名称
        if (StringUtils.hasText(keyword)) {
            wrapper.like("colonel_name", keyword.trim());
        }
        // 第三步：按来源精确匹配
        if (StringUtils.hasText(source)) {
            wrapper.eq("source", source.trim());
        }
        // 第四步：按联系方式存在与否筛选
        if (Boolean.TRUE.equals(hasContact)) {
            // 任一联系方式非空即可
            wrapper.and(w -> w.isNotNull("contact_phone")
                    .or()
                    .isNotNull("contact_wechat")
                    .or()
                    .isNotNull("contact_name"));
        } else if (Boolean.FALSE.equals(hasContact)) {
            // 三种联系方式全部为空
            wrapper.isNull("contact_phone")
                    .isNull("contact_wechat")
                    .isNull("contact_name");
        }
        // 第五步：执行分页查询
        Page<ColonelPartner> result = colonelPartnerMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result);
    }

    /**
     * 按 ID 查询团长主数据详情。
     *
     * @param id 团长主数据 ID
     * @return 团长主数据详情
     * @throws BusinessException 团长主数据不存在时抛出
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
     * <p>用于下拉框候选数据；当前实现是委托给 mapper 的 selectList 取 source 字段后去重。</p>
     *
     * @return 来源列表
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
