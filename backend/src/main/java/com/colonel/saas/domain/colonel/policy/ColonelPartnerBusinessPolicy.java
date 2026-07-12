package com.colonel.saas.domain.colonel.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelPartner;

import java.util.regex.Pattern;

/**
 * 团长主数据业务规则策略（DDD-COLONEL-002 Wave 1.4 补全）。
 *
 * <p>封装 colonel 域核心业务规则：
 * <ul>
 *   <li>团长名称非空校验</li>
 *   <li>团长 buyinId 格式校验（抖音百应 ID 必须为纯数字字符串）</li>
 *   <li>联系方式有效性校验（电话、微信至少一种）</li>
 * </ul>
 *
 * <p>本类是 colonel 域的"业务规则"层，与 Application Service 分离，
 * 便于跨 Application 复用同一规则集。</p>
 */
public class ColonelPartnerBusinessPolicy {

    /** 抖音百应 ID 格式：纯数字字符串，长度 10-25 位。 */
    private static final Pattern BUYIN_ID_PATTERN = Pattern.compile("^\\d{10,25}$");

    /**
     * 校验团长主数据的可持久化性。
     *
     * @param partner 团长实体
     * @throws BusinessException 校验失败时抛出
     */
    public void validatePersistable(ColonelPartner partner) {
        if (partner == null) {
            throw BusinessException.param("团长主数据不能为空");
        }
        if (!hasText(partner.getColonelName())) {
            throw BusinessException.param("团长名称不能为空");
        }
        if (!hasText(partner.getColonelBuyinId())) {
            throw BusinessException.param("团长百应 ID 不能为空");
        }
        if (!BUYIN_ID_PATTERN.matcher(partner.getColonelBuyinId()).matches()) {
            throw BusinessException.param("团长百应 ID 格式非法: " + partner.getColonelBuyinId());
        }
    }

    /**
     * 校验联系方式完整性。
     *
     * <p>团长必须至少保留一种联系方式（电话 / 微信 / 联系人姓名）。</p>
     *
     * @param partner 团长实体
     * @throws BusinessException 联系方式全部为空时抛出
     */
    public void validateContactPresent(ColonelPartner partner) {
        if (partner == null) {
            return;
        }
        boolean hasPhone = hasText(partner.getContactPhone());
        boolean hasWechat = hasText(partner.getContactWechat());
        boolean hasName = hasText(partner.getContactName());
        if (!hasPhone && !hasWechat && !hasName) {
            throw BusinessException.param("团长联系方式不能全部为空");
        }
    }

    /**
     * 校验团长 buyinId 是否合法（用于 upsert 前的快速检查）。
     *
     * @param buyinId 抖音百应 ID
     * @return 是否合法
     */
    public boolean isValidBuyinId(String buyinId) {
        return buyinId != null && BUYIN_ID_PATTERN.matcher(buyinId).matches();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
