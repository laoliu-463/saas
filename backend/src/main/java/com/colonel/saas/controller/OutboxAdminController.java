package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.event.DomainEventOutbox;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/outbox-events")
public class OutboxAdminController {

    private final DomainEventOutboxService domainEventOutboxService;

    public OutboxAdminController(DomainEventOutboxService domainEventOutboxService) {
        this.domainEventOutboxService = domainEventOutboxService;
    }

    @Operation(summary = "Outbox 事件列表")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping
    public ApiResult<PageResult<DomainEventOutbox>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        List<DomainEventOutbox> records = domainEventOutboxService.pageEvents(status, page, size);
        PageResult<DomainEventOutbox> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setTotal(records.size());
        return ApiResult.ok(pageResult);
    }

    @Operation(summary = "重试 DEAD/FAILED Outbox 事件")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/{id}/retry")
    public ApiResult<Void> retry(@PathVariable("id") UUID eventId) {
        domainEventOutboxService.retryDeadEvent(eventId);
        return ApiResult.ok(null);
    }
}
