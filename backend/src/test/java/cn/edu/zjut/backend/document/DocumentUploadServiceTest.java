package cn.edu.zjut.backend.document;

import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.entity.Document;
import cn.edu.zjut.backend.document.entity.DocumentVersion;
import cn.edu.zjut.backend.document.mapper.DocumentMapper;
import cn.edu.zjut.backend.document.mapper.DocumentVersionMapper;
import cn.edu.zjut.backend.document.service.DocumentUploadService;
import cn.edu.zjut.backend.exception.BizException;
import cn.edu.zjut.backend.security.UserContext;
import cn.edu.zjut.backend.security.UserContextHolder;
import cn.edu.zjut.backend.storage.MinioStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentVersionMapper documentVersionMapper;

    @Mock
    private MinioStorageService minioStorageService;

    private DocumentUploadService documentUploadService;

    @BeforeEach
    void setUp() {
        documentUploadService = new DocumentUploadService(
                documentMapper, documentVersionMapper, minioStorageService);
        UserContextHolder.set(new UserContext(9L, 3L, "alice", "DISPATCHER"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void shouldUploadNewDocumentAndWriteToDeptFolder() {
        MockMultipartFile file = pdfFile("合同.pdf");
        stubGeneratedIds();

        UploadDocumentResponse response = documentUploadService.upload(file, "source");

        assertThat(response.duplicate()).isFalse();
        assertThat(response.documentId()).isEqualTo(100L);
        assertThat(response.versionId()).isEqualTo(200L);
        assertThat(response.fileName()).isEqualTo("合同.pdf");
        assertThat(response.fileCategory()).isEqualTo("SOURCE");
        assertThat(response.versionNo()).isEqualTo(1);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).putObject(
                keyCaptor.capture(), any(InputStream.class), eq(5L), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).startsWith("dept/3/source/").endsWith(".pdf");

        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(documentVersionMapper).insert(versionCaptor.capture());
        DocumentVersion savedVersion = versionCaptor.getValue();
        assertThat(savedVersion.getSha256()).hasSize(64);
        assertThat(savedVersion.getIsCurrent()).isEqualTo(1);
        assertThat(savedVersion.getParseStatus()).isEqualTo("UPLOADED");
        assertThat(savedVersion.getStorageKey()).isEqualTo(keyCaptor.getValue());
    }

    @Test
    void shouldScopeDeduplicationAndStorageToCurrentDepartment() {
        UserContextHolder.set(new UserContext(10L, 4L, "bob", "DISPATCHER"));
        stubGeneratedIds();

        documentUploadService.upload(pdfFile("report.pdf"), "SOURCE");

        verify(documentMapper).selectBySha256InDept(eq(4L), anyString());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).putObject(
                keyCaptor.capture(), any(InputStream.class), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).startsWith("dept/4/source/");
    }

    @Test
    void shouldReturnExistingDocumentWhenDuplicateInSameDepartment() {
        Document existing = new Document();
        existing.setId(50L);
        existing.setDeptId(3L);
        existing.setFileName("合同.pdf");
        existing.setFileCategory("SOURCE");

        DocumentVersion currentVersion = new DocumentVersion();
        currentVersion.setId(60L);
        currentVersion.setDocumentId(50L);
        currentVersion.setVersionNo(1);
        currentVersion.setFileSize(5L);
        currentVersion.setSha256("a".repeat(64));
        currentVersion.setIsCurrent(1);

        when(documentMapper.selectBySha256InDept(eq(3L), anyString())).thenReturn(existing);
        when(documentVersionMapper.selectOne(any())).thenReturn(currentVersion);

        UploadDocumentResponse response = documentUploadService.upload(pdfFile("合同.pdf"), "SOURCE");

        assertThat(response.duplicate()).isTrue();
        assertThat(response.documentId()).isEqualTo(50L);
        assertThat(response.versionId()).isEqualTo(60L);
        verify(minioStorageService, never()).putObject(anyString(), any(), anyLong(), anyString());
        verify(documentMapper, never()).insert(any(Document.class));
        verify(documentVersionMapper, never()).insert(any(DocumentVersion.class));
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        BizException exception = catchThrowableOfType(
                () -> documentUploadService.upload(emptyFile, "SOURCE"), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(400);
        verifyNoInteractions(documentMapper, documentVersionMapper, minioStorageService);
    }

    @Test
    void shouldRejectUnsupportedCategory() {
        BizException exception = catchThrowableOfType(
                () -> documentUploadService.upload(pdfFile("a.pdf"), "EXPORT"), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(400);
        verifyNoInteractions(documentMapper, documentVersionMapper, minioStorageService);
    }

    @Test
    void shouldRejectUploadWithoutUserContext() {
        UserContextHolder.clear();

        BizException exception = catchThrowableOfType(
                () -> documentUploadService.upload(pdfFile("a.pdf"), "SOURCE"), BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(401);
        verifyNoInteractions(documentMapper, documentVersionMapper, minioStorageService);
    }

    @Test
    void shouldStripPathFromOriginalFileName() {
        stubGeneratedIds();
        MockMultipartFile file = new MockMultipartFile(
                "file", "..\\..\\etc\\passwd.pdf", "application/pdf",
                "hello".getBytes(StandardCharsets.UTF_8));

        UploadDocumentResponse response = documentUploadService.upload(file, "SOURCE");

        assertThat(response.fileName()).isEqualTo("passwd.pdf");
    }

    private void stubGeneratedIds() {
        doAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(100L);
            return 1;
        }).when(documentMapper).insert(any(Document.class));
        doAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            version.setId(200L);
            return 1;
        }).when(documentVersionMapper).insert(any(DocumentVersion.class));
    }

    private static MockMultipartFile pdfFile(String fileName) {
        return new MockMultipartFile("file", fileName, "application/pdf",
                "hello".getBytes(StandardCharsets.UTF_8));
    }
}
