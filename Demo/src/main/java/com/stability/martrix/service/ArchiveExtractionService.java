package com.stability.martrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 归档文件解压服务
 * 支持 ZIP、TAR.GZ、TAR 格式
 */
@Service
public class ArchiveExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExtractionService.class);

    /**
     * 归档文件类型
     */
    public enum ArchiveType {
        ZIP("zip"),
        TAR_GZ("tar.gz"),
        TAR("tar"),
        UNKNOWN("unknown");

        private final String extension;

        ArchiveType(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    /**
     * 检测归档类型
     *
     * @param fileName 文件名
     * @return 归档类型
     */
    public static ArchiveType detectArchiveType(String fileName) {
        if (fileName == null) {
            return ArchiveType.UNKNOWN;
        }

        String lower = fileName.toLowerCase();

        // 检查扩展名
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return ArchiveType.TAR_GZ;
        } else if (lower.endsWith(".tar")) {
            return ArchiveType.TAR;
        } else if (lower.endsWith(".zip")) {
            return ArchiveType.ZIP;
        }

        // 通过魔数检测
        Path path = Paths.get(fileName);
        if (Files.exists(path)) {
            return detectArchiveTypeByMagicNumber(path);
        }

        return ArchiveType.UNKNOWN;
    }

    /**
     * 通过魔数检测归档类型
     */
    private static ArchiveType detectArchiveTypeByMagicNumber(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] header = new byte[10];
            int read = is.read(header);
            if (read >= 4) {
                // ZIP: PK
                if (header[0] == 0x50 && header[1] == 0x4B) {
                    return ArchiveType.ZIP;
                }
                // TAR: 0x75 0x73 0x74 0x61 0x72
                if (read >= 5 && header[0] == 0x75 && header[1] == 0x73 &&
                    header[2] == 0x74 && header[3] == 0x61 && header[4] == 0x72) {
                    return ArchiveType.TAR;
                }
            }
        } catch (IOException e) {
            logger.warn("检测归档类型失败: {}", e.getMessage());
        }
        return ArchiveType.UNKNOWN;
    }

    /**
     * 解压归档文件到指定目录
     *
     * @param archiveFilePath 归档文件路径
     * @param targetDir 目标目录
     * @return 解压后的文件列表
     */
    public List<String> extractArchive(String archiveFilePath, String targetDir) {
        Path archivePath = Paths.get(archiveFilePath);
        ArchiveType archiveType = detectArchiveTypeByMagicNumber(archivePath);

        // 如果魔数检测失败，尝试通过文件名判断
        if (archiveType == ArchiveType.UNKNOWN) {
            archiveType = detectArchiveType(archiveFilePath);
        }

        logger.info("开始解压归档: {}, 类型: {}, 目标: {}", archiveFilePath, archiveType, targetDir);

        switch (archiveType) {
            case ZIP:
                return extractZip(archivePath, Paths.get(targetDir));
            case TAR_GZ:
                return extractTarGz(archivePath, Paths.get(targetDir));
            case TAR:
                return extractTar(archivePath, Paths.get(targetDir));
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 解压ZIP文件
     */
    private List<String> extractZip(Path zipPath, Path targetDir) {
        List<String> extractedFiles = new ArrayList<>();

        // 规范化目标目录路径用于安全检查
        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipPath)))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    Path filePath = targetDir.resolve(entryName).normalize();

                    // 安全检查：防止ZIP滑洞攻击
                    if (!filePath.startsWith(normalizedTargetDir)) {
                        logger.warn("跳过可疑的ZIP条目（路径遍历攻击）: {}", entryName);
                        continue;
                    }

                    // 确保父目录存在
                    if (filePath.getParent() != null) {
                        Files.createDirectories(filePath.getParent());
                    }

                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles.add(filePath.toString());
                    logger.debug("解压文件: {}", filePath);
                }
            }
            logger.info("ZIP解压完成: {}, 文件数: {}", zipPath, extractedFiles.size());
        } catch (IOException e) {
            logger.error("解压ZIP失败: {}, error={}", zipPath, e.getMessage());
            throw new RuntimeException("Failed to extract ZIP file: " + e.getMessage(), e);
        }

        return extractedFiles;
    }

    /**
     * 解压TAR.GZ文件
     */
    private List<String> extractTarGz(Path tarGzPath, Path targetDir) {
        // 先解压GZ，再解压TAR
        Path tempTarFile = targetDir.resolve("~temp.tar");

        try {
            decompressGzip(tarGzPath, tempTarFile);
            List<String> extractedFiles = extractTar(tempTarFile, targetDir);
            return extractedFiles;
        } finally {
            // 删除临时TAR文件
            try {
                Files.deleteIfExists(tempTarFile);
            } catch (IOException e) {
                logger.warn("删除临时TAR文件失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 解压TAR文件
     */
    private List<String> extractTar(Path tarPath, Path targetDir) {
        List<String> extractedFiles = new ArrayList<>();

        // 规范化目标目录路径用于安全检查
        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();

        try (org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarInput =
                new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                        new BufferedInputStream(Files.newInputStream(tarPath)))) {

            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                if (entry.isFile()) {
                    String entryName = entry.getName();
                    Path filePath = targetDir.resolve(entryName).normalize();

                    // 安全检查：防止TAR滑洞攻击
                    if (!filePath.startsWith(normalizedTargetDir)) {
                        logger.warn("跳过可疑的TAR条目（路径遍历攻击）: {}", entryName);
                        continue;
                    }

                    // 确保父目录存在
                    if (filePath.getParent() != null) {
                        Files.createDirectories(filePath.getParent());
                    }

                    Files.copy(tarInput, filePath, StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles.add(filePath.toString());
                    logger.debug("解压文件: {}", filePath);
                }
            }
            logger.info("TAR解压完成: {}, 文件数: {}", tarPath, extractedFiles.size());
        } catch (IOException e) {
            logger.error("解压TAR失败: {}, error={}", tarPath, e.getMessage());
            throw new RuntimeException("Failed to extract TAR file: " + e.getMessage(), e);
        }

        return extractedFiles;
    }

    /**
     * 解压GZIP文件
     */
    private Path decompressGzip(Path gzipPath, Path targetPath) {
        try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(gzipPath)))) {

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = gzis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                baos.flush();
                Files.write(targetPath, baos.toByteArray());
            }
            logger.info("GZIP解压完成: {} -> {}", gzipPath, targetPath);
            return targetPath;
        } catch (IOException e) {
            logger.error("解压GZIP失败: {}, error={}", gzipPath, e.getMessage());
            throw new RuntimeException("Failed to decompress GZIP file: " + e.getMessage(), e);
        }
    }

    /**
     * 批量解压归档文件
     *
     * @param archiveFilePaths 归档文件路径列表
     * @param targetDir 目标目录
     * @return 所有解压后的文件路径列表
     */
    public List<String> extractArchives(List<String> archiveFilePaths, String targetDir) {
        if (archiveFilePaths == null || archiveFilePaths.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> allFiles = new ArrayList<>();

        for (String archivePath : archiveFilePaths) {
            try {
                List<String> extractedFiles = extractArchive(archivePath, targetDir);
                allFiles.addAll(extractedFiles);
            } catch (Exception e) {
                logger.warn("解压归档失败，跳过: {}, error={}", archivePath, e.getMessage());
            }
        }

        return allFiles;
    }
}
