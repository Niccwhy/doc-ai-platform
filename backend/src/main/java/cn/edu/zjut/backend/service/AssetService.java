package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.AssetBatchResult;
import cn.edu.zjut.backend.dto.AssetUploadResult;
import cn.edu.zjut.backend.dto.AssetView;
import cn.edu.zjut.backend.dto.UploadFile;
import cn.edu.zjut.backend.entity.Document;
import cn.edu.zjut.backend.mapper.DocumentMapper;
import cn.edu.zjut.backend.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 资产池服务：文件入库、查重、列表、软删除。
 * <p>
 * 三条与 v3 设计一致的约定：
 * <ol>
 *   <li><b>去重不靠唯一键</b>：同内容重复提交是正常业务（同一合同可能有多份），
 *       命中时返回已有文件并标记 {@code duplicate=true}，由调用方决定复用还是强制新建；</li>
 *   <li><b>内容寻址</b>：对象 key 由 sha256 派生，同内容共享同一对象，
 *       节省存储与后续 embedding 计算；</li>
 *   <li><b>租户隔离</b>：所有查询强制带 dept_id；软删只失效元数据，
 *       对象在"无其他记录引用"时才物理清理。</li>
 * </ol>
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    /** 赛题样本类型：docx / md / xlsx / txt（pdf 留作扩展） */
    private static final Set<String> ALLOWED_TYPES = Set.of("docx", "xlsx", "md", "txt", "pdf");
    private static final Set<String> CATEGORIES = Set.of("SOURCE", "TEMPLATE", "OUTPUT");
    private static final long MAX_SIZE = 100L * 1024 * 1024;

    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    public AssetService(DocumentMapper documentMapper, StorageService storageService) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
    }

    /**
     * 单个文件入库。
     *
     * @param file           已读入内存的文件（异步场景必须传字节，不能传 MultipartFile）
     * @param category       SOURCE / TEMPLATE / OUTPUT，默认 SOURCE
     * @param allowDuplicate true=即使内容相同也新建一条记录
     */
    public AssetUploadResult upload(UploadFile file, String category, boolean allowDuplicate) {
        String categoryValue = normalizeCategory(category);
        String fileName = safeFileName(file.fileName());
        String fileType = detectType(fileName);
        byte[] content = file.content();
        if (content == null || content.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件内容为空：" + fileName);
        }
        if (content.length > MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件超过 100MB 上限：" + fileName);
        }

        Long deptId = CurrentUser.deptId();
        String sha256 = sha256Hex(content);

        // 1. 查重（走 idx_doc_sha，非唯一索引，毫秒级）
        if (!allowDuplicate) {
            Document existing = findDuplicate(deptId, sha256);
            if (existing != null) {
                log.info("检测到内容重复，复用已有文件：{} -> docId={}", fileName, existing.getId());
                return new AssetUploadResult(existing.getId(), existing.getFileName(), true,
                        existing.getStorageKey(), true);
            }
        }

        // 2. 内容寻址落盘：同内容只存一份
        String storageKey = buildStorageKey(deptId, sha256);
        boolean written = storageService.putAtKeyIfAbsent(storageKey, content, file.contentType());

        // 3. 元数据入库
        Document doc = new Document();
        doc.setDeptId(deptId);
        doc.setUploaderId(CurrentUser.id());
        doc.setUploaderType("USER");
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setMimeType(file.contentType());
        doc.setFileCategory(categoryValue);
        doc.setFileSize((long) content.length);
        doc.setSha256(sha256);
        doc.setStorageBucket(storageService.bucket());
        doc.setStorageKey(storageKey);
        doc.setSourceType("WEB");
        doc.setIsShared(false);
        doc.setParseStatus("UPLOADED");
        doc.setParseAttempts(0);
        doc.setCreatedAt(OffsetDateTime.now());
        documentMapper.insert(doc);

        log.info("文件入库完成：{} (docId={}, {}KB, 新对象={})",
                fileName, doc.getId(), content.length / 1024, written);
        return new AssetUploadResult(doc.getId(), fileName, false, storageKey, !written);
    }

    /**
     * 批量入库。逐个处理，单个失败不影响其他文件（评测一次要灌 16 个文件）。
     */
    public AssetBatchResult uploadBatch(List<UploadFile> files, String category) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请至少选择一个文件");
        }
        List<AssetUploadResult> ok = new ArrayList<>();
        List<AssetBatchResult.Failed> failed = new ArrayList<>();
        for (UploadFile file : files) {
            try {
                ok.add(upload(file, category, false));
            } catch (BizException e) {
                failed.add(new AssetBatchResult.Failed(file.fileName(), e.getMessage()));
            } catch (Exception e) {
                log.error("文件入库异常: {}", file.fileName(), e);
                failed.add(new AssetBatchResult.Failed(file.fileName(),
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.info("批量入库完成：成功 {} 个，失败 {} 个", ok.size(), failed.size());
        return new AssetBatchResult(ok, failed);
    }

    /**
     * 文件池列表：本部门 + 全局共享资产。
     */
    public List<AssetView> list(String category, String keyword) {
        Long deptId = CurrentUser.deptId();
        QueryWrapper<Document> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");
        wrapper.and(w -> w.eq("dept_id", deptId).or().eq("is_shared", true));
        if (category != null && !category.isBlank()) {
            wrapper.eq("file_category", normalizeCategory(category));
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("file_name", keyword.trim());
        }
        wrapper.orderByDesc("created_at").last("LIMIT 500");
        return documentMapper.selectList(wrapper).stream().map(AssetView::from).toList();
    }

    /**
     * 取文件详情（含部门校验）。
     */
    public Document getOwned(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || doc.getDeletedAt() != null) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        boolean sameDept = CurrentUser.deptId().equals(doc.getDeptId());
        boolean shared = Boolean.TRUE.equals(doc.getIsShared());
        if (!sameDept && !shared && !CurrentUser.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问其他部门的文件");
        }
        return doc;
    }

    /**
     * 生成临时下载链接。对象存储不可用时会抛明确错误，而不是返回坏链接。
     */
    public String presignedUrl(Long id, int expireSeconds) {
        Document doc = getOwned(id);
        return storageService.presignedUrl(doc.getStorageKey(), expireSeconds);
    }

    /**
     * 软删除。仅失效元数据；对象在"无其他未删记录引用同一 key"时才物理删除。
     */
    public void softDelete(Long id) {
        Document doc = getOwned(id);
        if (!CurrentUser.deptId().equals(doc.getDeptId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能删除本部门的文件");
        }
        doc.setDeletedAt(OffsetDateTime.now());
        documentMapper.updateById(doc);

        Long others = documentMapper.selectCount(new QueryWrapper<Document>()
                .eq("storage_key", doc.getStorageKey())
                .isNull("deleted_at"));
        if (others == null || others == 0) {
            // 没有其他记录引用该内容，物理清理；失败只告警，留待 GC 重试
            storageService.delete(doc.getStorageKey());
            log.info("文件已软删且无引用，清理对象: {}", doc.getStorageKey());
        } else {
            log.info("文件已软删，仍有 {} 条记录引用同一内容，保留对象: {}", others, doc.getStorageKey());
        }
    }

    /**
     * 内容指纹：小写十六进制，与库中 CHECK(sha256 ~ '^[0-9a-f]{64}$') 一致。
     */
    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "计算文件指纹失败: " + e.getMessage());
        }
    }

    /**
     * 内容寻址 key：dept/{deptId}/blob/{前2位}/{sha256}。
     * 用前两位分目录，避免单目录对象过多。
     */
    public static String buildStorageKey(Long deptId, String sha256) {
        return "dept/" + deptId + "/blob/" + sha256.substring(0, 2) + "/" + sha256;
    }

    private Document findDuplicate(Long deptId, String sha256) {
        List<Document> list = documentMapper.selectList(new QueryWrapper<Document>()
                .eq("dept_id", deptId)
                .eq("sha256", sha256)
                .isNull("deleted_at")
                .orderByAsc("id")
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private String normalizeCategory(String category) {
        String value = (category == null || category.isBlank())
                ? "SOURCE" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(value)) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件分类仅支持 SOURCE / TEMPLATE / OUTPUT");
        }
        return value;
    }

    /**
     * 只保留文件名部分，丢弃路径（防目录穿越），并限制长度。
     */
    private String safeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件名不能为空");
        }
        String name = rawName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法文件名");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /**
     * 取扩展名作为逻辑类型，统一小写（避免 DOCX / docx / EXCEL 多种写法混用）。
     */
    private String detectType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件缺少扩展名：" + fileName);
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        // xls 统一按 xlsx 处理会误判老格式，这里明确不支持
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "不支持的文件类型 ." + ext + "（支持 docx / xlsx / md / txt / pdf）");
        }
        return ext;
    }
}
