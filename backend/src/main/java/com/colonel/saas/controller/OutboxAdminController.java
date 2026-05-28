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

/**
 * 领域事件 Outbox 管理控制器，供管理员查看和重试 Outbox 事件。
 *
 * <ul>
 *   <li>查询 Outbox 事件列表：按状态分页查看待处理、失败、死信等事件</li>
 *   <li>重试死信/失败事件：将 DEAD 或 FAILED 状态的事件重新投入事件分发流程</li>
 * </ul>
 *
 * <p>所属业务领域：基础设施 / 领域事件 Outbox
 * <p>API 路径前缀：{@code /api/admin/outbox-events}
 * <p>访问权限：仅限管理员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}）
 *
 * @see com.colonel.saas.domain.event.DomainEventOutboxService
 */
@RestController
@RequestMapping("/api/admin/outbox-events")
public class OutboxAdminController {

    /** 领域事件 Outbox 服务，负责事件查询与重试分发 */
    private final DomainEventOutboxService domainEventOutboxService;

    /**
     * 构造注入 Outbox 服务。
     *
     * @param domainEventOutboxService 领域事件 Outbox 服务实例
     */
    public OutboxAdminController(DomainEventOutboxService domainEventOutboxService) {
        this.domainEventOutboxService = domainEventOutboxService;
    }

    /**
     * 查询 Outbox 事件列表（分页）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收可选的事件状态筛选条件和分页参数</li>
     *   <li>调用 Outbox 服务查询满足条件的事件记录</li>
     *   <li>手动构建分页结果对象并返回</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /api/admin/outbox-events}
     *
     * @param status 事件状态筛选（如 PENDING、FAILED、DEAD），可为空表示查询全部
     * @param page   当前页码，默认为 1
     * @param size   每页记录数，默认为 20
     * @return 分页后的 Outbox 事件列表
     */
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

    /**
     * 重试 DEAD 或 FAILED 状态的 Outbox 事件。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据事件 ID 查找对应的 Outbox 事件记录</li>
     *   <li>将事件状态重置，重新投入事件分发流程</li>
     *   <li>事件将被重新消费，失败时将重新计数并可能再次标记为 DEAD</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /api/admin/outbox-events/{id}/retry}
     *
     * @param eventId 需要重试的 Outbox 事件 ID
     * @return 空响应，表示重试请求已接受
     * @throws com.colonel.saas.common.exception.BusinessException 事件不存在或状态不允许重试
     */
    @Operation(summary = "重试 DEAD/FAILED Outbox 事件")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/{id}/retry")
    public ApiResult<Void> retry(@PathVariable("id") UUID eventId) {
        domainEventOutboxService.retryDeadEvent(eventId);
        return ApiResult.ok(null);
    }
}
