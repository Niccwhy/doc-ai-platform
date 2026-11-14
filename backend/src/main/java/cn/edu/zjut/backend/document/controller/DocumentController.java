package cn.edu.zjut.backend.document.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.service.DocumentUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentUploadService documentUploadService;

    @PostMapping
    public Result<UploadDocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(defaultValue = "SOURCE") String fileCategory) {
        return Result.success(documentUploadService.upload(file, fileCategory));
    }
}
