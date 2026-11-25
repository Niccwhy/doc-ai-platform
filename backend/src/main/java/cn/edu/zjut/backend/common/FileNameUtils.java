package cn.edu.zjut.backend.common;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class FileNameUtils {

    private static final int MAX_EXTENSION_LENGTH = 10;

    private FileNameUtils() {
    }

    /**
     * 去除客户端传入文件名中的路径，避免路径穿越。
     */
    public static String sanitize(String originalFileName, String fallback) {
        String fileName = StringUtils.hasText(originalFileName)
                ? StringUtils.cleanPath(originalFileName) : fallback;
        int separatorIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (separatorIndex >= 0) {
            fileName = fileName.substring(separatorIndex + 1);
        }
        return StringUtils.hasText(fileName) ? fileName : fallback;
    }

    /**
     * 提取并清洗文件扩展名，返回形如 ".pdf" 的后缀；无有效扩展名时返回空串。
     */
    public static String extensionSuffix(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (extension.isEmpty()) {
            return "";
        }
        if (extension.length() > MAX_EXTENSION_LENGTH) {
            extension = extension.substring(0, MAX_EXTENSION_LENGTH);
        }
        return "." + extension;
    }
}
