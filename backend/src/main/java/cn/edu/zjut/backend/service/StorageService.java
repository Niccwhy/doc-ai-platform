package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.config.MinioConfig.MinioProperties;
import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 对象存储服务：上传、下载、预签名 URL。bucket 不存在时自动创建。
 */
@Service
public class StorageService {

    private final MinioClient minioClient;
    private final MinioProperties props;

    public StorageService(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "初始化对象存储 bucket 失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件，返回对象键。
     */
    public String upload(MultipartFile file) {
        String objectKey = buildObjectKey(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 生成预签名下载 URL（默认 1 小时有效）。
     */
    public String presignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "生成下载链接失败: " + e.getMessage());
        }
    }

    /**
     * 上传并返回对象键 + 预签名 URL。
     */
    public Map<String, String> uploadWithUrl(MultipartFile file) {
        String key = upload(file);
        Map<String, String> result = new HashMap<>();
        result.put("objectKey", key);
        result.put("url", presignedUrl(key));
        return result;
    }

    private String buildObjectKey(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "file" : originalName;
        // 按日期分目录，避免同名覆盖
        return "uploads/" + java.time.LocalDate.now() + "/" + UUID.randomUUID() + "-" + name;
    }

}
