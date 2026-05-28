package com.colonel.saas.dto.colonel;

import jakarta.validation.constraints.Size;

/**
 * 团长合作伙伴联系信息更新请求 DTO。
 * <p>
 * 用于更新团长与合作伙伴（商家）之间的联系人信息，包括联系人姓名、电话、微信和备注。
 * 关联业务领域：团长域（Colonel）。
 * </p>
 */
public record ColonelPartnerContactUpdateRequest(
        /** 联系人姓名，最大 100 字符 */
        @Size(max = 100) String contactName,
        /** 联系人电话，最大 50 字符 */
        @Size(max = 50) String contactPhone,
        /** 联系人微信号，最大 100 字符 */
        @Size(max = 100) String contactWechat,
        /** 联系备注，最大 2000 字符 */
        @Size(max = 2000) String contactRemark) {
}
