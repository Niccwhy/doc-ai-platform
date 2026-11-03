package cn.edu.zjut.backend;

import cn.edu.zjut.backend.dto.VersionInfo;
import cn.edu.zjut.backend.service.docop.DocumentVersionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档版本存储单元测试：版本递增、读取、回滚、路径安全。
 */
class DocumentVersionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveCreatesIncrementingVersions() {
        DocumentVersionStore store = new DocumentVersionStore(tempDir.toString());

        int v1 = store.save("合同A", "第一版".getBytes(StandardCharsets.UTF_8), "初始");
        int v2 = store.save("合同A", "第二版".getBytes(StandardCharsets.UTF_8), "全文加粗");

        assertThat(v1).isEqualTo(1);
        assertThat(v2).isEqualTo(2);

        List<VersionInfo> versions = store.list("合同A");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(1).note()).isEqualTo("全文加粗");
        assertThat(versions.get(1).size()).isGreaterThan(0);
        assertThat(versions.get(1).createdAt()).isNotBlank();
    }

    @Test
    void loadReturnsSavedContent() {
        DocumentVersionStore store = new DocumentVersionStore(tempDir.toString());
        store.save("doc", "ABC".getBytes(StandardCharsets.UTF_8), "v1");

        assertThat(store.load("doc", 1)).isEqualTo("ABC".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rollbackCreatesNewVersionWithOldContent() {
        DocumentVersionStore store = new DocumentVersionStore(tempDir.toString());
        store.save("doc", "原始内容".getBytes(StandardCharsets.UTF_8), "初始");
        store.save("doc", "改动内容".getBytes(StandardCharsets.UTF_8), "修改");

        int rollbackVersion = store.rollback("doc", 1);

        assertThat(rollbackVersion).isEqualTo(3);
        assertThat(store.load("doc", 3)).isEqualTo("原始内容".getBytes(StandardCharsets.UTF_8));
        // 历史版本未被删除
        assertThat(store.list("doc")).hasSize(3);
    }

    @Test
    void loadingMissingVersionFails() {
        DocumentVersionStore store = new DocumentVersionStore(tempDir.toString());
        assertThatThrownBy(() -> store.load("doc", 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本不存在");
    }

    @Test
    void listReturnsEmptyForUnknownDocument() {
        DocumentVersionStore store = new DocumentVersionStore(tempDir.toString());
        assertThat(store.list("not-exists")).isEmpty();
    }

    @Test
    void safeDocIdPreventsPathTraversal() {
        String safe = DocumentVersionStore.safeDocId("../../etc/passwd");
        assertThat(safe).doesNotContain("..").doesNotContain("/").doesNotContain("\\");
    }

    @Test
    void safeDocIdStripsExtension() {
        assertThat(DocumentVersionStore.safeDocId("合同模板.docx")).isEqualTo("合同模板");
    }
}
