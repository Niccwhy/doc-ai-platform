package cn.edu.zjut.backend.common;

import cn.edu.zjut.backend.exception.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Sha256Utils {

    private static final int BUFFER_SIZE = 8192;

    private Sha256Utils() {
    }

    public static String hash(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return hash(inputStream);
        } catch (IOException exception) {
            throw new BizException(500, "读取上传文件失败", exception);
        }
    }

    public static String hash(InputStream inputStream) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
