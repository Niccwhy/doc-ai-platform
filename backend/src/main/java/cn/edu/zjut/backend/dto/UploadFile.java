package cn.edu.zjut.backend.dto;

/**
 * 已读入内存的上传文件，用于把请求线程中的 MultipartFile 安全地交给异步任务。
 * <p>
 * MultipartFile 的底层临时文件会在请求结束后被容器清理，
 * 异步线程再访问会报「文件不存在」，因此必须提前取字节。
 *
 * @param fileName    原始文件名
 * @param content     文件字节
 * @param contentType MIME 类型
 */
public record UploadFile(
        String fileName,
        byte[] content,
        String contentType
) {
}
