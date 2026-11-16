package cn.edu.zjut.backend.storage;

import cn.edu.zjut.backend.config.MinioProperties;
import cn.edu.zjut.backend.exception.BizException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private volatile boolean bucketReady;

    public void putObject(String objectKey, InputStream inputStream, long size, String contentType) {
        ensureBucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(StringUtils.hasText(contentType)
                            ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .build());
        } catch (Exception exception) {
            throw new BizException(500, "文件上传至对象存储失败", exception);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
                }
                bucketReady = true;
            } catch (Exception exception) {
                throw new BizException(500, "对象存储初始化失败", exception);
            }
        }
    }
}
