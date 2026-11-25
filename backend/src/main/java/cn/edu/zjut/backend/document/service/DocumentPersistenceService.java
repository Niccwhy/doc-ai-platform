package cn.edu.zjut.backend.document.service;

import cn.edu.zjut.backend.document.entity.Document;
import cn.edu.zjut.backend.document.entity.DocumentVersion;
import cn.edu.zjut.backend.document.mapper.DocumentMapper;
import cn.edu.zjut.backend.document.mapper.DocumentVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentPersistenceService {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentVersion save(Document document, DocumentVersion version) {
        documentMapper.insert(document);
        version.setDocumentId(document.getId());
        documentVersionMapper.insert(version);
        return version;
    }
}
