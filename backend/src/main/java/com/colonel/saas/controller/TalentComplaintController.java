package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.talent.facade.TalentComplaintFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.dto.talent.TalentComplaintRiskRequest;
import com.colonel.saas.vo.talent.TalentComplaintReminderVO;
import com.colonel.saas.vo.talent.TalentComplaintVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 达人投诉风险摘要、领导详情和接收人提醒入口。 */
@Validated
@RestController
@RequestMapping("/talent-complaints")
public class TalentComplaintController {

    private final TalentComplaintFacade talentComplaintFacade;

    public TalentComplaintController(TalentComplaintFacade talentComplaintFacade) {
        this.talentComplaintFacade = talentComplaintFacade;
    }

    @RequireRoles({
            RoleCodes.ADMIN,
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF,
            RoleCodes.OPS_STAFF
    })
    @PostMapping("/risks")
    public ApiResult<List<TalentComplaintRiskDTO>> loadRisks(
            @Valid @RequestBody TalentComplaintRiskRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute("dataScope") DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(talentComplaintFacade.loadRisks(
                request, userId, deptId, dataScope, roleCodes));
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<TalentComplaintVO> getDetail(
            @PathVariable UUID id,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(talentComplaintFacade.getDetail(id, roleCodes));
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @GetMapping("/reminders/unread-count")
    public ApiResult<Map<String, Long>> unreadCount(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(Map.of(
                "unreadCount", talentComplaintFacade.unreadCount(userId, roleCodes)));
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @GetMapping("/reminders")
    public ApiResult<List<TalentComplaintReminderVO>> listReminders(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime beforeCreateTime,
            @RequestParam(required = false) UUID beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(talentComplaintFacade.listReminders(
                userId, roleCodes, beforeCreateTime, beforeId, limit));
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @PutMapping("/reminders/{id:[0-9a-fA-F\\-]{36}}/read")
    public ApiResult<TalentComplaintReminderVO> markReminderRead(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(talentComplaintFacade.markReminderRead(id, userId, roleCodes));
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @GetMapping("/{complaintId:[0-9a-fA-F\\-]{36}}/attachments/{attachmentId:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<ByteArrayResource> downloadAttachment(
            @PathVariable UUID complaintId,
            @PathVariable UUID attachmentId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        TalentComplaintFacade.AttachmentDownload download =
                talentComplaintFacade.downloadAttachment(complaintId, attachmentId, roleCodes);
        byte[] content = download.content();
        String safeName = safeFilename(download.originalName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(safeName, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);
        headers.setContentType(safeMediaType(download.contentType()));
        return ResponseEntity.ok()
                .headers(headers)
                .body(new ByteArrayResource(content));
    }

    private String safeFilename(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment";
        }
        return originalName.replaceAll("[\\r\\n\\\\/]", "_");
    }

    private MediaType safeMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
