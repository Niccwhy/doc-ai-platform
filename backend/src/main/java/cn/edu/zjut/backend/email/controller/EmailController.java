package cn.edu.zjut.backend.email.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.email.dto.EmailMessageResponse;
import cn.edu.zjut.backend.email.dto.ImportAttachmentsRequest;
import cn.edu.zjut.backend.email.service.EmailManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
public class EmailController {

    private final EmailManagementService emailManagementService;

    @PostMapping("/discover")
    public Result<List<EmailMessageResponse>> discover() {
        return Result.success(emailManagementService.discoverNewMessages());
    }

    @GetMapping
    public Result<List<EmailMessageResponse>> list(@RequestParam(required = false) String status) {
        return Result.success(emailManagementService.listMessages(status));
    }

    @GetMapping("/{emailId}")
    public Result<EmailMessageResponse> get(@PathVariable Long emailId) {
        return Result.success(emailManagementService.getMessage(emailId));
    }

    @PostMapping("/{emailId}/attachments/import")
    public Result<List<UploadDocumentResponse>> importAttachments(
            @PathVariable Long emailId, @Valid @RequestBody ImportAttachmentsRequest request) {
        return Result.success(emailManagementService.importAttachments(emailId, request.attachmentIds()));
    }

    @PostMapping("/{emailId}/attachments/{attachmentId}/ignore")
    public Result<Void> ignoreAttachment(@PathVariable Long emailId, @PathVariable Long attachmentId) {
        emailManagementService.ignoreAttachment(emailId, attachmentId);
        return Result.success(null);
    }

    @PostMapping("/{emailId}/ignore")
    public Result<Void> ignoreMessage(@PathVariable Long emailId) {
        emailManagementService.ignoreMessage(emailId);
        return Result.success(null);
    }
}