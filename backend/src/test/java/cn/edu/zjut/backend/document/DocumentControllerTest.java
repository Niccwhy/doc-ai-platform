package cn.edu.zjut.backend.document;

import cn.edu.zjut.backend.document.controller.DocumentController;
import cn.edu.zjut.backend.document.dto.UploadDocumentResponse;
import cn.edu.zjut.backend.document.service.DocumentUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private DocumentUploadService documentUploadService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentUploadService = mock(DocumentUploadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentUploadService)).build();
    }

    @Test
    void shouldUploadMultipartFile() throws Exception {
        when(documentUploadService.upload(any(), eq("SOURCE"))).thenReturn(new UploadDocumentResponse(
                1L, 2L, "合同.pdf", "SOURCE", 5L, "a".repeat(64), 1, false));
        MockMultipartFile file = new MockMultipartFile(
                "file", "合同.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documentId").value(1))
                .andExpect(jsonPath("$.data.versionId").value(2))
                .andExpect(jsonPath("$.data.fileName").value("合同.pdf"))
                .andExpect(jsonPath("$.data.duplicate").value(false));
    }

    @Test
    void shouldPassFileCategoryToService() throws Exception {
        when(documentUploadService.upload(any(), eq("TEMPLATE"))).thenReturn(new UploadDocumentResponse(
                3L, 4L, "模板.docx", "TEMPLATE", 8L, "b".repeat(64), 1, false));
        MockMultipartFile file = new MockMultipartFile(
                "file", "模板.docx", "application/octet-stream", "template".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/documents").file(file).param("fileCategory", "TEMPLATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileCategory").value("TEMPLATE"));
    }
}
