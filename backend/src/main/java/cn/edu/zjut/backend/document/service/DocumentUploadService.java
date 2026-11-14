package cn.edu.zjut.backend.document.service;

import cn.edu.zjut.backend.common.Sha256Utils;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.entity.Document;
import cn.edu.zjut.backend.document.entity.DocumentVersion;
import cn.edu.zjut.backend.document.mapper.DocumentMapper;
import cn.edu.zjut.backend.document.mapper.DocumentVersionMapper;
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.security.UserContext;
import cn.edu.zjut.backend.security.UserContextHolder;
import cn.edu.zjut.backend.storage.MinioStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private static final Set<String> UPLOADABLE_CATEGORIES = Set.of("SOURCE", "TEMPLATE");
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_EXTENSION_LENGTH = 10;
    private static final String DEFAULT_FILE_NAME = "unnamed";

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final MinioStorageService minioStorageService;

    @Transactional
    public UploadDocumentResponse upload(MultipartFile file, String fileCategory) {
        UserContext userContext = UserContextHolder.getRequired();
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件不能为空");
        }
        String category = normalizeCategory(fileCategory);
        String fileName = sanitizeFileName(file.getOriginalFilename());
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

        Document document = new Document();
        document.setDeptId(userContext.deptId());
        document.setUploaderId(userContext.userId());
        document.setFileName(fileName);
        document.setFileCategory(category);
        document.setSourceType("WEB");
        documentMapper.insert(document);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setFileSize(file.getSize());
        version.setSha256(sha256);
        version.setStorageKey(storageKey);
        version.setParseStatus("UPLOADED");
        version.setIsCurrent(1);
        documentVersionMapper.insert(version);

        return toResponse(document, version, false);
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

    private static String sanitizeFileName(String originalFilename) {
        String fileName = StringUtils.hasText(originalFilename)
                ? StringUtils.cleanPath(originalFilename) : DEFAULT_FILE_NAME;
        int separatorIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (separatorIndex >= 0) {
            fileName = fileName.substring(separatorIndex + 1);
        }
        if (!StringUtils.hasText(fileName)) {
            fileName = DEFAULT_FILE_NAME;
        }
        return fileName.length() > MAX_FILE_NAME_LENGTH
                ? fileName.substring(fileName.length() - MAX_FILE_NAME_LENGTH) : fileName;
    }

    private static String buildStorageKey(Long deptId, String category, String fileName) {
        return "dept/" + deptId + "/" + category.toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + buildExtensionSuffix(fileName);
    }

    private static String buildExtensionSuffix(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (extension.isEmpty()) {
            return "";
        }
        if (extension.length() > MAX_EXTENSION_LENGTH) {
            extension = extension.substring(0, MAX_EXTENSION_LENGTH);
        }
        return "." + extension;
    }
}
