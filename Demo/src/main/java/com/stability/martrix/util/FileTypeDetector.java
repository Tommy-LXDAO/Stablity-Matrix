package com.stability.martrix.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;

/**
 * 文件类型检测工具
 * 通过文件头（魔数）准确判断文件类型
 */
public class FileTypeDetector {

    private static final int SAMPLE_SIZE = 4096;
    private static final int TAR_MAGIC_OFFSET = 257;
    private static final double UTF16_ZERO_RATIO_THRESHOLD = 0.3;
    private static final double UTF16_ASCII_RATIO_THRESHOLD = 0.5;
    private static final double CONTROL_CHAR_RATIO_THRESHOLD = 0.05;
    private static final double ASCII_TEXT_RATIO_THRESHOLD = 0.2;

    /**
     * 文件类型枚举
     */
    public enum FileType {
        TXT("text/plain", "txt"),
        ELF("application/x-elf", "elf"),
        ZIP("application/zip", "zip"),
        GZIP("application/gzip", "gz"),
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
    // GZIP文件魔数：1F 8B
    private static final int[] GZIP_MAGIC = {0x1F, 0x8B};

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

    public static FileType detectFileType(Path path) throws IOException {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return FileType.UNKNOWN;
        }

        try (InputStream is = Files.newInputStream(path)) {
            return detectByHeader(is.readNBytes(SAMPLE_SIZE));
        }
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
                is.mark(SAMPLE_SIZE);
                byte[] header = is.readNBytes(SAMPLE_SIZE);
                is.reset();

                if (header.length > 0) {
                    return detectByHeader(header);
                }
            }
        }

        // 方法2：保存到临时文件后读取（适用于不支持mark的流）
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("file-type-detect-", ".tmp");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            return detectFileType(tempFile);
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
     * @return 文件类型
     */
    private static FileType detectByHeader(byte[] header) {
        int length = header.length;
        if (length == 0) {
            return FileType.UNKNOWN;
        }

        // 检查ELF魔数
        if (length >= 4 && isElfMagic(header)) {
            return FileType.ELF;
        }

        // 检查ZIP魔数
        if (length >= 2 && isZipMagic(header)) {
            return FileType.ZIP;
        }

        if (length >= 2 && isGzipMagic(header)) {
            return FileType.GZIP;
        }

        if (hasTarMagic(header, length)) {
            return FileType.UNKNOWN;
        }

        if (isLikelyText(header, length)) {
            return FileType.TXT;
        }

        return FileType.UNKNOWN;
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

    private static boolean isGzipMagic(byte[] header) {
        if (header.length < 2) {
            return false;
        }
        return (header[0] & 0xFF) == GZIP_MAGIC[0] && (header[1] & 0xFF) == GZIP_MAGIC[1];
    }

    private static boolean isLikelyText(byte[] header, int length) {
        return hasUtf8Bom(header)
            || detectBomlessUtf16(header, length) != null
            || hasLowControlCharRatio(header, length);
    }

    private static boolean hasUtf8Bom(byte[] header) {
        return header.length >= 3
            && (header[0] & 0xFF) == 0xEF
            && (header[1] & 0xFF) == 0xBB
            && (header[2] & 0xFF) == 0xBF;
    }

    private static boolean hasTarMagic(byte[] header, int length) {
        return length > TAR_MAGIC_OFFSET + 4
            && header[TAR_MAGIC_OFFSET] == 'u'
            && header[TAR_MAGIC_OFFSET + 1] == 's'
            && header[TAR_MAGIC_OFFSET + 2] == 't'
            && header[TAR_MAGIC_OFFSET + 3] == 'a'
            && header[TAR_MAGIC_OFFSET + 4] == 'r';
    }

    private static java.nio.charset.Charset detectBomlessUtf16(byte[] header, int length) {
        int sampleLength = Math.min(length - (length % 2), SAMPLE_SIZE);
        if (sampleLength < 16) {
            return null;
        }

        if (looksLikeUtf16(header, sampleLength, true)) {
            return StandardCharsets.UTF_16LE;
        }

        if (looksLikeUtf16(header, sampleLength, false)) {
            return StandardCharsets.UTF_16BE;
        }

        return null;
    }

    private static boolean looksLikeUtf16(byte[] header, int sampleLength, boolean littleEndian) {
        int pairs = sampleLength / 2;
        int zeroHighByteCount = 0;
        int zeroLowByteCount = 0;
        int printableLowByteCount = 0;

        for (int i = 0; i < sampleLength; i += 2) {
            int lowByte = header[littleEndian ? i : i + 1] & 0xFF;
            int highByte = header[littleEndian ? i + 1 : i] & 0xFF;

            if (highByte == 0) {
                zeroHighByteCount++;
            }
            if (lowByte == 0) {
                zeroLowByteCount++;
            }
            if (isAsciiTextByte(lowByte)) {
                printableLowByteCount++;
            }
        }

        double zeroHighRatio = (double) zeroHighByteCount / pairs;
        double zeroLowRatio = (double) zeroLowByteCount / pairs;
        double printableLowRatio = (double) printableLowByteCount / pairs;

        return zeroHighRatio >= UTF16_ZERO_RATIO_THRESHOLD
            && zeroLowRatio < CONTROL_CHAR_RATIO_THRESHOLD
            && printableLowRatio >= UTF16_ASCII_RATIO_THRESHOLD;
    }

    private static boolean hasLowControlCharRatio(byte[] header, int length) {
        int zeroCount = 0;
        int controlCount = 0;
        int asciiTextCount = 0;

        for (int i = 0; i < length; i++) {
            int value = header[i] & 0xFF;
            if (value == 0) {
                zeroCount++;
                continue;
            }
            if (!isTextByte(value)) {
                controlCount++;
            }
            if (isAsciiTextByte(value)) {
                asciiTextCount++;
            }
        }

        return zeroCount == 0
            && ((double) controlCount / length) <= CONTROL_CHAR_RATIO_THRESHOLD
            && ((double) asciiTextCount / length) >= ASCII_TEXT_RATIO_THRESHOLD;
    }

    private static boolean isTextByte(int value) {
        return value == '\t'
            || value == '\n'
            || value == '\r'
            || value >= 0x20;
    }

    private static boolean isAsciiTextByte(int value) {
        return value == '\t'
            || value == '\n'
            || value == '\r'
            || (value >= 0x20 && value <= 0x7E);
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
