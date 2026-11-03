package cn.edu.zjut.backend.dto;

import java.util.List;

/**
 * 批量上传结果：成功的逐条返回，失败的带原因，避免"一个文件不合规整批报错"。
 */
public record AssetBatchResult(
        List<AssetUploadResult> succeeded,
        List<Failed> failed
) {

    public record Failed(String fileName, String reason) {
    }
}
