package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

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
