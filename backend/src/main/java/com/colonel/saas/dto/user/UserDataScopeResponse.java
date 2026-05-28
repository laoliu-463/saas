package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 当前用户数据范围解析结果 DTO。
 * <p>
 * 返回当前登录用户的数据访问范围，包含范围标识（self/group/all）、范围编码以及
 * 限制的用户 ID 列表。用于前端和后端进行行级数据过滤。
 * 关联业务领域：用户域（User）。
 * </p>
 */
@Schema(description = "当前用户数据范围解析结果")
public record UserDataScopeResponse(
        @Schema(description = "数据范围：self/group/all")
        String scope,

        @Schema(description = "数据范围编码：1=自己，2=本组，3=全部")
        int code,

        @Schema(description = "限制用户ID列表；all 时为空")
        List<UUID> userIds
) {
}
