package com.colonel.saas.domain.colonel.api;

import jakarta.validation.constraints.Size;

/**
 * 团长合作伙伴联系信息更新请求 DTO（DDD 域内版本，DDD-COLONEL-002 Wave 1 补全）。
 *
 * <p>本 DTO 是 {@code ColonelPartnerContactUpdateApplicationService} 的输入契约。
 * 与 {@code com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest}
 * 字段相同；本版本是 colonel 域的"自有"API DTO，避免跨域 DTO 依赖。</p>
 *
 * <p><b>DDD 边界：</b>本 DTO 仅在 colonel 域内流转，跨域调用方继续使用
 * {@code com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest}，
 * 在 colonel 域边界由 Facade 做转换。</p>
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
