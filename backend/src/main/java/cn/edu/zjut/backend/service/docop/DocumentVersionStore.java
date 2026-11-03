package cn.edu.zjut.backend.service.docop;

import cn.edu.zjut.backend.dto.VersionInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 文档版本存储（本地文件，版本化可回滚）。
 * <p>
 * 目录结构：{baseDir}/{docId}/v1.docx、v2.docx…… 与 meta.json（版本说明）。
 * 回滚不删除历史版本，而是把旧版本内容另存为一个新版本，保证操作可追溯。
 */
public class DocumentVersionStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentVersionStore.class);

    private final Path baseDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentVersionStore(String baseDir) {
        this.baseDir = Path.of(baseDir);
    }

    /**
     * 保存新版本。
     *
     * @return 新版本号
     */
    public synchronized int save(String docId, byte[] content, String note) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("版本内容为空");
        }
        Path dir = dirOf(docId);
        try {
            Files.createDirectories(dir);
            List<VersionInfo> versions = readMeta(dir);
            int next = versions.stream().mapToInt(VersionInfo::version).max().orElse(0) + 1;
            Files.write(dir.resolve("v" + next + ".docx"), content);
            versions.add(new VersionInfo(next, note == null ? "" : note, content.length,
                    OffsetDateTime.now().toString()));
            writeMeta(dir, versions);
            return next;
        } catch (IOException e) {
            throw new UncheckedIOException("保存文档版本失败", e);
        }
    }

    /**
     * 读取指定版本内容。
     */
    public byte[] load(String docId, int version) {
        Path file = dirOf(docId).resolve("v" + version + ".docx");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("版本不存在: " + docId + " v" + version);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("读取版本失败", e);
        }
    }

    /**
     * 版本列表（按版本号升序）。
     */
    public List<VersionInfo> list(String docId) {
        try {
            List<VersionInfo> versions = readMeta(dirOf(docId));
            versions.sort(Comparator.comparingInt(VersionInfo::version));
            return versions;
        } catch (IOException e) {
            // 目录不存在视为无版本
            log.debug("读取版本列表失败（视为空）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 回滚：把指定历史版本内容另存为新版本。
     *
     * @return 新版本号
     */
    public synchronized int rollback(String docId, int version) {
        byte[] old = load(docId, version);
        return save(docId, old, "rollback from v" + version);
    }

    /**
     * 文档标识安全化（避免路径穿越）。
     */
    public static String safeDocId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "document";
        }
        int dot = raw.lastIndexOf('.');
        String base = dot > 0 ? raw.substring(0, dot) : raw;
        String safe = base.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        if (safe.isBlank()) {
            return "document";
        }
        return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }

    private Path dirOf(String docId) {
        return baseDir.resolve(safeDocId(docId));
    }

    private List<VersionInfo> readMeta(Path dir) throws IOException {
        Path meta = dir.resolve("meta.json");
        if (!Files.exists(meta)) {
            return new ArrayList<>();
        }
        byte[] bytes = Files.readAllBytes(meta);
        if (bytes.length == 0) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(bytes, new TypeReference<List<VersionInfo>>() {
        });
    }

    private void writeMeta(Path dir, List<VersionInfo> versions) throws IOException {
        Files.write(dir.resolve("meta.json"), objectMapper.writeValueAsBytes(versions));
    }
}
