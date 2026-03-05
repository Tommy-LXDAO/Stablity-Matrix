package com.stability.martrix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码片段提取工具类
 * 通过文件名+行号、commit-id和branch-name获取代码片段
 */
public class CodeSnippetExtractor {

    private static final Logger logger = LoggerFactory.getLogger(CodeSnippetExtractor.class);

    /**
     * 代码片段结果
     */
    public static class SnippetResult {
        private final String filePath;
        private final int startLine;
        private final int endLine;
        private final String commitId;
        private final String branchName;
        private final String codeUrl;
        private final List<String> lines;
        private final String rawUrl;

        public SnippetResult(String filePath, int startLine, int endLine,
                             String commitId, String branchName, String codeUrl,
                             List<String> lines, String rawUrl) {
            this.filePath = filePath;
            this.startLine = startLine;
            this.endLine = endLine;
            this.commitId = commitId;
            this.branchName = branchName;
            this.codeUrl = codeUrl;
            this.lines = lines;
            this.rawUrl = rawUrl;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public String getCommitId() {
            return commitId;
        }

        public String getBranchName() {
            return branchName;
        }

        public String getCodeUrl() {
            return codeUrl;
        }

        public List<String> getLines() {
            return lines;
        }

        public String getRawUrl() {
            return rawUrl;
        }

        @Override
        public String toString() {
            return "SnippetResult{filePath='" + filePath + "', startLine=" + startLine +
                    ", endLine=" + endLine + ", commitId='" + commitId + '\'' +
                    ", branchName='" + branchName + "'}";
        }
    }

    /**
     * 获取代码片段
     *
     * @param baseUrl    代码源基础URL（如 https://example.com/repo）
     * @param filePath   文件路径
     * @param lineNumber 行号
     * @param commitId   commit ID或branch名称
     * @return 代码片段结果
     */
    public static SnippetResult getSnippet(String baseUrl, String filePath,
                                            int lineNumber, String commitId) {
        return getSnippet(baseUrl, filePath, lineNumber, lineNumber, commitId, commitId);
    }

    /**
     * 获取代码片段（多行）
     *
     * @param baseUrl    代码源基础URL
     * @param filePath   文件路径
     * @param startLine  起始行号
     * @param endLine    结束行号
     * @param commitId   commit ID
     * @param branchName branch名称
     * @return 代码片段结果
     */
    public static SnippetResult getSnippet(String baseUrl, String filePath,
                                            int startLine, int endLine,
                                            String commitId, String branchName) {
        // 构建代码浏览链接
        String codeUrl = buildCodeUrl(baseUrl, filePath, startLine, commitId);

        // 构建raw链接获取实际代码
        String rawUrl = buildRawUrl(baseUrl, filePath, commitId);

        // 获取代码内容
        List<String> lines = fetchCodeLines(rawUrl, startLine, endLine);

        return new SnippetResult(filePath, startLine, endLine, commitId,
                branchName, codeUrl, lines, rawUrl);
    }

    /**
     * 构建代码浏览链接
     *
     * @param baseUrl    基础URL
     * @param filePath   文件路径
     * @param lineNumber 行号
     * @param ref        commit ID或branch
     * @return 代码浏览链接
     */
    public static String buildCodeUrl(String baseUrl, String filePath, int lineNumber, String ref) {
        baseUrl = baseUrl.replaceAll("/$", "");
        String encodedPath = filePath.replace(" ", "%20");
        return baseUrl + "/blob/" + ref + "/" + encodedPath + "#L" + lineNumber;
    }

    /**
     * 构建Raw代码链接
     *
     * @param baseUrl  基础URL
     * @param filePath 文件路径
     * @param ref      commit ID或branch
     * @return Raw链接
     */
    private static String buildRawUrl(String baseUrl, String filePath, String ref) {
        baseUrl = baseUrl.replaceAll("/$", "");
        return baseUrl + "/raw/" + ref + "/" + filePath;
    }

    /**
     * 从Raw URL获取代码行
     *
     * @param rawUrl    Raw URL
     * @param startLine 起始行
     * @param endLine   结束行
     * @return 代码行列表
     */
    private static List<String> fetchCodeLines(String rawUrl, int startLine, int endLine) {
        List<String> result = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String[] allLines = response.body().split("\n");
                int linesToFetch = Math.min(endLine, allLines.length);
                for (int i = startLine - 1; i < linesToFetch; i++) {
                    result.add(allLines[i]);
                }
            } else {
                logger.warn("获取代码片段失败，HTTP状态码: {}, URL: {}",
                        response.statusCode(), rawUrl);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("获取代码片段失败: {}", rawUrl, e);
            Thread.currentThread().interrupt();
        }

        return result;
    }
}
