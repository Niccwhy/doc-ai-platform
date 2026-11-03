package cn.edu.zjut.backend.service;

import cn.edu.zjut.backend.config.MinioConfig.MinioProperties;
import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.ResultCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 对象存储服务：上传、下载、预签名 URL。bucket 不存在时自动创建。
 * <p>
 * 启动健壮性：对象存储不可用时<b>不阻断应用启动</b>（仅告警），
 * bucket 在首次上传时懒创建，失败后下次调用重试。
 * 这样开发环境未启动容器也能把服务跑起来（接口调用时才报明确错误）。
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final MinioClient minioClient;
    private final MinioProperties props;

    /** bucket 是否已确认存在（懒初始化） */
    private volatile boolean bucketReady = false;

    public StorageService(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    /**
     * 确保 bucket 存在。失败只告警不抛出。
     *
     * @return 是否可用
     */
    public synchronized boolean ensureBucket() {
        if (bucketReady) {
            return true;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("已创建对象存储 bucket: {}", props.getBucket());
            }
            bucketReady = true;
            return true;
        } catch (Exception e) {
            log.warn("对象存储暂不可用（不影响启动，上传时重试）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 上传文件，返回对象键。
     */
    public String upload(MultipartFile file) {
        if (!ensureBucket()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "对象存储不可用，请确认 MinIO/silo 容器已启动（" + props.getEndpoint() + "）");
        }
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
            bucketReady = false; // 连接可能已失效，下次重试
            throw new BizException(ResultCode.FILE_PARSE_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传字节数组（供导出的成品文件、预览副本使用）。
     */
    public String uploadBytes(byte[] bytes, String fileName, String contentType) {
        if (!ensureBucket()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "对象存储不可用，请确认 MinIO/silo 容器已启动（" + props.getEndpoint() + "）");
        }
        String objectKey = buildObjectKey(fileName);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            bucketReady = false;
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

    /**
     * 下载对象内容。
     */
    public byte[] download(String objectKey) {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(props.getBucket()).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件下载失败: " + e.getMessage());
        }
    }

    private String buildObjectKey(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "file" : originalName;
        // 按日期分目录，避免同名覆盖
        return "uploads/" + java.time.LocalDate.now() + "/" + UUID.randomUUID() + "-" + name;
    }
}
