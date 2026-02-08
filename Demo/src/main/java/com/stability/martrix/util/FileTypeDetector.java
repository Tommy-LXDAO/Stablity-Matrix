package com.stability.martrix.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件类型检测工具
 * 通过文件头（魔数）准确判断文件类型
 */
public class FileTypeDetector {

    /**
     * 文件类型枚举
     */
    public enum FileType {
        TXT("text/plain", "txt"),
        ELF("application/x-elf", "elf"),
        ZIP("application/zip", "zip"),
        UNKNOWN("unknown", "unknown");

        private final String mimeType;
        private final String extension;

        FileType(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getExtension() {
            return extension;
        }
    }

    // ELF文件魔数：0x7F 'E' 'L' 'F'
    private static final int[] ELF_MAGIC = {0x7F, 'E', 'L', 'F'};
    // ZIP文件魔数：PK
    private static final int[] ZIP_MAGIC = {'P', 'K'};

    /**
     * 检测文件类型
     * 通过文件头（魔数）判断，不依赖扩展名
     *
     * @param file MultipartFile对象
     * @return 文件类型
     */
    public static FileType detectFileType(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return FileType.UNKNOWN;
        }

        return detectByMagicNumber(file);
    }

    /**
     * 通过魔数检测文件类型
     *
     * @param file MultipartFile对象
     * @return 文件类型
     */
    private static FileType detectByMagicNumber(MultipartFile file) throws IOException {
        // 方法1：直接读取流（如果流支持mark/reset）
        try (InputStream is = file.getInputStream()) {
            // 检查流是否支持mark
            if (is.markSupported()) {
                is.mark(8);
                byte[] header = new byte[8];
                int read = is.read(header);
                is.reset();

                if (read >= 2) {
                    return detectByHeader(header, read);
                }
            }
        }

        // 方法2：保存到临时文件后读取（适用于不支持mark的流）
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("file-type-detect-", ".tmp");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            byte[] header = Files.readAllBytes(tempFile);
            if (header.length >= 2) {
                int readLength = Math.min(header.length, 8);
                return detectByHeader(header, readLength);
            }

            return FileType.TXT;
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // 忽略删除失败
                }
            }
        }
    }

    /**
     * 通过文件头字节数组检测文件类型
     *
     * @param header 文件头字节数组
     * @param length 实际读取长度
     * @return 文件类型
     */
    private static FileType detectByHeader(byte[] header, int length) {
        // 检查ELF魔数
        if (length >= 4 && isElfMagic(header)) {
            return FileType.ELF;
        }

        // 检查ZIP魔数
        if (length >= 2 && isZipMagic(header)) {
            return FileType.ZIP;
        }

        // 默认当作文本处理
        return FileType.TXT;
    }

    /**
     * 检查是否为ELF魔数
     *
     * @param header 文件头
     * @return 是否为ELF文件
     */
    private static boolean isElfMagic(byte[] header) {
        if (header.length < 4) {
            return false;
        }
        for (int i = 0; i < ELF_MAGIC.length; i++) {
            if ((header[i] & 0xFF) != ELF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否为ZIP魔数
     *
     * @param header 文件头
     * @return 是否为ZIP文件
     */
    private static boolean isZipMagic(byte[] header) {
        if (header.length < 2) {
            return false;
        }
        return (header[0] & 0xFF) == ZIP_MAGIC[0] && (header[1] & 0xFF) == ZIP_MAGIC[1];
    }

    /**
     * 检查是否为Tombstone文件
     * Tombstone文件通常是文本文件，包含特定的关键字
     *
     * @param file MultipartFile对象
     * @return 是否为Tombstone文件
     */
    public static boolean isTombstoneFile(MultipartFile file) throws IOException {
        if (detectFileType(file) != FileType.TXT) {
            return false;
        }

        // 读取文件前几行检查是否包含tombstone特征
        try (InputStream is = file.getInputStream()) {
            byte[] content = new byte[2048];
            int read = is.read(content);
            if (read > 0) {
                String text = new String(content, 0, read);
                // Tombstone文件特征
                return text.contains("pid:") && text.contains("signal") &&
                       (text.contains("backtrace:") || text.contains("ABI:"));
            }
        }
        return false;
    }
}
