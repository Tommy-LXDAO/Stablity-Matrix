package com.stability.martrix.service;

import com.stability.martrix.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 会话文件存储服务
 * 负责管理会话相关的文件存储
 */
@Service
public class SessionFileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(SessionFileStorageService.class);

    private final FileStorageProperties fileStorageProperties;

    public SessionFileStorageService(FileStorageProperties fileStorageProperties) {
        this.fileStorageProperties = fileStorageProperties;
    }

    /**
     * 为会话创建存储文件夹
     *
     * @param sessionId 会话ID
     * @return 文件夹路径
     */
    public String createSessionFolder(String sessionId) {
        String sessionPath = fileStorageProperties.getSessionPath(sessionId);
        Path path = Paths.get(sessionPath);

        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("创建会话文件夹: {}", sessionPath);
            } else {
                logger.debug("会话文件夹已存在: {}", sessionPath);
            }
            return sessionPath;
        } catch (IOException e) {
            logger.error("创建会话文件夹失败: {}, error={}", sessionPath, e.getMessage());
            throw new RuntimeException("Failed to create session folder: " + e.getMessage(), e);
        }
    }

    /**
     * 存储上传的文件到会话文件夹
     *
     * @param sessionId 会话ID
     * @param file 上传的文件
     * @return 存储后的文件路径
     */
    public String storeFile(String sessionId, MultipartFile file) {
        String sessionPath = fileStorageProperties.getSessionPath(sessionId);
        String originalFileName = file.getOriginalFilename();
        // 确定最终使用的文件名
        final String fileName = (originalFileName == null || originalFileName.isEmpty())
                ? "unnamed_" + System.currentTimeMillis()
                : originalFileName;
        Path targetPath = Paths.get(sessionPath, fileName);

        try {
            // 确保文件夹存在
            createSessionFolder(sessionId);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("文件已存储: sessionId={}, file={}", sessionId, targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            logger.error("存储文件失败: sessionId={}, file={}, error={}", sessionId, fileName, e.getMessage());
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    /**
     * 存储多个文件
     *
     * @param sessionId 会话ID
     * @param files 上传的文件列表
     * @return 存储后的文件路径列表
     */
    public List<String> storeFiles(String sessionId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<String> filePaths = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                try {
                    String filePath = storeFile(sessionId, file);
                    filePaths.add(filePath);
                } catch (Exception e) {
                    logger.warn("存储文件失败，跳过: file={}, error={}",
                        file.getOriginalFilename(), e.getMessage());
                }
            }
        }

        return filePaths;
    }

    /**
     * 获取会话文件夹中的所有文件
     *
     * @param sessionId 会话ID
     * @return 文件路径列表
     */
    public List<String> getSessionFiles(String sessionId) {
        String sessionPath = fileStorageProperties.getSessionPath(sessionId);
        Path path = Paths.get(sessionPath);

        try {
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }

            try (Stream<Path> stream = Files.walk(path)) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .toList();
            }
        } catch (IOException e) {
            logger.error("获取会话文件列表失败: sessionId={}, error={}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 清理会话文件夹
     *
     * @param sessionId 会话ID
     * @return 是否清理成功
     */
    public boolean deleteSessionFolder(String sessionId) {
        String sessionPath = fileStorageProperties.getSessionPath(sessionId);
        Path path = Paths.get(sessionPath);

        try {
            if (!Files.exists(path)) {
                logger.warn("会话文件夹不存在，无需删除: {}", sessionPath);
                return true;
            }

            // 递归删除文件夹
            try (Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                logger.warn("删除文件失败: {}, error={}", p, e.getMessage());
                            }
                        });
            }

            Files.deleteIfExists(path);
            logger.info("会话文件夹已删除: {}", sessionPath);
            return true;
        } catch (IOException e) {
            logger.error("删除会话文件夹失败: {}, error={}", sessionPath, e.getMessage());
            return false;
        }
    }

    /**
     * 获取文件存储配置
     *
     * @return 文件存储配置
     */
    public FileStorageProperties getFileStorageProperties() {
        return fileStorageProperties;
    }

    /**
     * 清理过期的会话文件夹
     *
     * @return 清理的文件夹数量
     */
    public long cleanupExpiredSessions() {
        long expireMillis = fileStorageProperties.getCleanupExpiredHours() * 60 * 60 * 1000L;
        String basePath = fileStorageProperties.getBasePath();
        long now = System.currentTimeMillis();

        try {
            Path base = Paths.get(basePath);
            if (!Files.exists(base)) {
                return 0L;
            }

            try (Stream<Path> stream = Files.list(base)) {
                return stream
                        .filter(Files::isDirectory)
                        .filter(dir -> dir.getFileName().toString().startsWith(fileStorageProperties.getSessionFolderPrefix()))
                        .filter(dir -> {
                            try {
                                return Files.getLastModifiedTime(dir).toMillis() < (now - expireMillis);
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .peek(dir -> {
                            try {
                                // 递归删除
                                try (Stream<Path> walkStream = Files.walk(dir)) {
                                    walkStream
                                            .sorted(Comparator.reverseOrder())
                                            .forEach(p -> {
                                                try {
                                                    Files.deleteIfExists(p);
                                                } catch (IOException ignored) {
                                                }
                                            });
                                }
                                Files.deleteIfExists(dir);
                                logger.info("清理过期会话文件夹: {}", dir);
                            } catch (IOException e) {
                                logger.warn("清理文件夹失败: {}, error={}", dir, e.getMessage());
                            }
                        })
                        .count();
            }
        } catch (IOException e) {
            logger.error("清理过期会话文件夹失败: error={}", e.getMessage());
            return 0L;
        }
    }
}
