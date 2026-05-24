package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerAdminService;
import com.colonel.saas.service.ColonelPartnerSyncService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/colonel-partners")
public class AdminColonelPartnerController {

    private final ColonelPartnerAdminService colonelPartnerAdminService;
    private final ColonelPartnerSyncService colonelPartnerSyncService;

    public AdminColonelPartnerController(
            ColonelPartnerAdminService colonelPartnerAdminService,
            ColonelPartnerSyncService colonelPartnerSyncService) {
        this.colonelPartnerAdminService = colonelPartnerAdminService;
        this.colonelPartnerSyncService = colonelPartnerSyncService;
    }

    @Operation(summary = "更新团长联系方式")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
    @PutMapping("/{id}/contact")
    public ApiResult<ColonelPartner> updateContact(
            @PathVariable UUID id,
            @Valid @RequestBody ColonelPartnerContactUpdateRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ApiResult.ok(colonelPartnerAdminService.updateContactInfo(id, request, userId));
    }

    @Operation(summary = "手动触发团长主数据同步")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
    @PostMapping("/sync")
    public ApiResult<Map<String, Object>> sync() {
        int upserted = colonelPartnerSyncService.syncAll();
        return ApiResult.ok(Map.of("upserted", upserted));
    }
}
