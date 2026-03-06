package com.stability.martrix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用llvm-readelf获取ELF文件的BUILD ID工具类
 */
public class BuildIdExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BuildIdExtractor.class);

    /**
     * BUILD ID信息
     */
    public static class BuildIdInfo {
        private final String buildId;
        private final String filePath;

        public BuildIdInfo(String buildId, String filePath) {
            this.buildId = buildId;
            this.filePath = filePath;
        }

        public String getBuildId() {
            return buildId;
        }

        public String getFilePath() {
            return filePath;
        }

        @Override
        public String toString() {
            return "BuildIdInfo{buildId='" + buildId + "', filePath='" + filePath + "'}";
        }
    }

    /**
     * 从单个ELF文件获取BUILD ID
     *
     * @param elfFilePath ELF文件路径
     * @return BUILD ID字符串，如果获取失败返回null
     */
    public static String getBuildId(String elfFilePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("llvm-readelf", "-n", elfFilePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("llvm-readelf执行失败，退出码: {}, 文件: {}", exitCode, elfFilePath);
                return null;
            }

            // 解析BUILD ID
            return parseBuildId(output.toString());
        } catch (IOException | InterruptedException e) {
            logger.error("获取BUILD ID失败: {}", elfFilePath, e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 从单个ELF文件获取BUILD ID信息
     *
     * @param elfFilePath ELF文件路径
     * @return BUILD ID信息，如果获取失败返回null
     */
    public static BuildIdInfo getBuildIdInfo(String elfFilePath) {
        String buildId = getBuildId(elfFilePath);
        if (buildId != null) {
            return new BuildIdInfo(buildId, elfFilePath);
        }
        return null;
    }

    /**
     * 解析llvm-readelf输出获取BUILD ID
     * 输出格式类似于:
     * Build ID: abc123def456789...
     */
    private static String parseBuildId(String output) {
        // 匹配 Build ID: xxxxx 格式
        Pattern pattern = Pattern.compile("Build ID:\\s*([a-fA-F0-9]+)");
        Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
