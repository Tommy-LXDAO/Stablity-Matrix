package com.stability.martrix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tar.gz文件解压工具
 */
public class TarGzExtractor {

    private static final Logger logger = LoggerFactory.getLogger(TarGzExtractor.class);

    /**
     * 解压tar.gz文件到指定目录
     *
     * @param filePath tar.gz文件路径
     * @param outputDir 输出目录
     * @return 解压后的文件路径列表
     */
    public static List<String> extractToDirectory(String filePath, String outputDir) {
        List<String> extractedFiles = new ArrayList<>();
        try {
            // 检查源文件是否存在
            Path sourcePath = Paths.get(filePath);
            if (!Files.exists(sourcePath)) {
                logger.error("源文件不存在: {}", filePath);
                return null;
            }

            Path outputPath = Paths.get(outputDir);
            // 如果输出目录不存在，则创建
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            // 使用ProcessBuilder调用系统tar命令进行解压
            ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", filePath, "-C", outputDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("tar解压失败，退出码: {}", exitCode);
                return extractedFiles;
            }

            // 列出解压后的文件
            Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .forEach(extractedFiles::add);

            logger.info("解压tar.gz文件成功: {} -> {}, 共 {} 个文件",
                    filePath, outputDir, extractedFiles.size());
        } catch (IOException | InterruptedException e) {
            logger.error("解压tar.gz文件失败: {} -> {}", filePath, outputDir, e);
            Thread.currentThread().interrupt();
        }
        return extractedFiles;
    }
}
