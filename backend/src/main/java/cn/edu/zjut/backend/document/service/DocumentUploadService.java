package cn.edu.zjut.backend.document.service;

import cn.edu.zjut.backend.common.FileNameUtils;
import cn.edu.zjut.backend.common.Sha256Utils;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.entity.Document;
import cn.edu.zjut.backend.document.entity.DocumentVersion;
import cn.edu.zjut.backend.document.mapper.DocumentMapper;
import cn.edu.zjut.backend.document.mapper.DocumentVersionMapper;
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.security.TenantAccessChecker;
import cn.edu.zjut.backend.security.UserContext;
import cn.edu.zjut.backend.security.UserContextHolder;
import cn.edu.zjut.backend.storage.service.MinioStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private static final Set<String> UPLOADABLE_CATEGORIES = Set.of("SOURCE", "TEMPLATE");
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String DEFAULT_FILE_NAME = "unnamed";

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentPersistenceService documentPersistenceService;
    private final MinioStorageService minioStorageService;
    private final TenantAccessChecker tenantAccessChecker;

    public UploadDocumentResponse upload(MultipartFile file, String fileCategory) {
        UserContext userContext = UserContextHolder.getRequired();
        tenantAccessChecker.requireAnyRole("ADMIN", "DISPATCHER");
        tenantAccessChecker.requireSameDepartment(userContext.deptId());
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件不能为空");
        }
        String category = normalizeCategory(fileCategory);
        String fileName = FileNameUtils.sanitize(file.getOriginalFilename(), DEFAULT_FILE_NAME);
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            fileName = fileName.substring(fileName.length() - MAX_FILE_NAME_LENGTH);
        }
        String sha256 = Sha256Utils.hash(file);

        UploadDocumentResponse duplicateResponse = findDuplicate(userContext.deptId(), sha256);
        if (duplicateResponse != null) {
            return duplicateResponse;
        }

        String storageKey = buildStorageKey(userContext.deptId(), category, fileName);
        try (InputStream inputStream = file.getInputStream()) {
            minioStorageService.putObject(storageKey, inputStream, file.getSize(), file.getContentType());
        } catch (IOException exception) {
            throw new BizException(500, "读取上传文件失败", exception);
        }

        try {
            Document document = new Document();
            document.setDeptId(userContext.deptId());
            document.setUploaderId(userContext.userId());
            document.setFileName(fileName);
            document.setFileCategory(category);
            document.setSourceType("WEB");

            DocumentVersion version = new DocumentVersion();
            version.setDeptId(userContext.deptId());
            version.setVersionNo(1);
            version.setFileSize(file.getSize());
            version.setSha256(sha256);
            version.setStorageKey(storageKey);
            version.setParseStatus("UPLOADED");
            version.setIsCurrent(1);
            DocumentVersion savedVersion = documentPersistenceService.save(document, version);

            return toResponse(document, savedVersion, false);
        } catch (RuntimeException exception) {
            boolean duplicateConflict = isDuplicateConflict(exception);

            try {
                minioStorageService.removeObject(storageKey);
            } catch (RuntimeException cleanupException) {
                log.warn("文件数据库入库失败，MinIO 对象补偿清理失败: {}", storageKey, cleanupException);
            }

            if (duplicateConflict) {
                UploadDocumentResponse duplicateAfterConflict =
                        findDuplicate(userContext.deptId(), sha256);
                if (duplicateAfterConflict != null) {
                    return duplicateAfterConflict;
                }
            }
            throw new BizException(500, "文件数据库入库失败", exception);
        }
    }

    public UploadDocumentResponse uploadEmailAttachment(String originalFileName, String contentType,
                                                         byte[] content) {
        UserContext userContext = UserContextHolder.getRequired();
        tenantAccessChecker.requireAnyRole("ADMIN", "DISPATCHER");
        tenantAccessChecker.requireSameDepartment(userContext.deptId());
        if (content == null || content.length == 0) {
            throw new BizException(400, "邮件附件不能为空");
        }

        String fileName = FileNameUtils.sanitize(originalFileName, DEFAULT_FILE_NAME);
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            fileName = fileName.substring(fileName.length() - MAX_FILE_NAME_LENGTH);
        }
        String sha256;
        try {
            sha256 = Sha256Utils.hash(new ByteArrayInputStream(content));
        } catch (IOException exception) {
            throw new BizException(500, "读取邮件附件失败", exception);
        }
        UploadDocumentResponse duplicateResponse = findDuplicate(userContext.deptId(), sha256);
        if (duplicateResponse != null) {
            return duplicateResponse;
        }

        String storageKey = buildStorageKey(userContext.deptId(), "SOURCE", fileName);
        minioStorageService.putObject(storageKey, new ByteArrayInputStream(content), content.length, contentType);
        try {
            Document document = new Document();
            document.setDeptId(userContext.deptId());
            document.setUploaderId(userContext.userId());
            document.setFileName(fileName);
            document.setFileCategory("SOURCE");
            document.setSourceType("EMAIL");

            DocumentVersion version = new DocumentVersion();
            version.setDeptId(userContext.deptId());
            version.setVersionNo(1);
            version.setFileSize((long) content.length);
            version.setSha256(sha256);
            version.setStorageKey(storageKey);
            version.setParseStatus("UPLOADED");
            version.setIsCurrent(1);
            DocumentVersion savedVersion = documentPersistenceService.save(document, version);
            return toResponse(document, savedVersion, false);
        } catch (RuntimeException exception) {
            boolean duplicateConflict = isDuplicateConflict(exception);
            try {
                minioStorageService.removeObject(storageKey);
            } catch (RuntimeException cleanupException) {
                log.warn("邮件附件数据库入库失败，MinIO 对象补偿清理失败: {}", storageKey, cleanupException);
            }
            if (duplicateConflict) {
                UploadDocumentResponse duplicate = findDuplicate(userContext.deptId(), sha256);
                if (duplicate != null) {
                    return duplicate;
                }
            }
            throw new BizException(500, "邮件附件入库失败", exception);
        }
    }

    private static boolean isDuplicateConflict(RuntimeException exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return true;
        }
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && (message.contains("duplicate key")
                    || message.contains("uk_document_version_dept_sha256_current"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private UploadDocumentResponse findDuplicate(Long deptId, String sha256) {
        Document existing = documentMapper.selectBySha256InDept(deptId, sha256);
        if (existing == null) {
            return null;
        }
        DocumentVersion currentVersion = documentVersionMapper.selectOne(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, existing.getId())
                        .eq(DocumentVersion::getSha256, sha256)
                        .eq(DocumentVersion::getIsCurrent, 1)
                        .last("LIMIT 1"));
        if (currentVersion == null) {
            return null;
        }
        return toResponse(existing, currentVersion, true);
    }

    private static UploadDocumentResponse toResponse(Document document, DocumentVersion version, boolean duplicate) {
        return new UploadDocumentResponse(
                document.getId(),
                version.getId(),
                document.getFileName(),
                document.getFileCategory(),
                version.getFileSize(),
                version.getSha256(),
                version.getVersionNo(),
                duplicate);
    }

    private static String normalizeCategory(String fileCategory) {
        String category = StringUtils.hasText(fileCategory)
                ? fileCategory.trim().toUpperCase(Locale.ROOT) : "SOURCE";
        if (!UPLOADABLE_CATEGORIES.contains(category)) {
            throw new BizException(400, "不支持的上传文件分类: " + fileCategory);
        }
        return category;
    }

    private static String buildStorageKey(Long deptId, String category, String fileName) {
        return "dept/" + deptId + "/" + category.toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + FileNameUtils.extensionSuffix(fileName);
    }
}
