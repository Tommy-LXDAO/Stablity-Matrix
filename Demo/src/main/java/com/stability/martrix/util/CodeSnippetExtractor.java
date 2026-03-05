package com.stability.martrix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码片段提取工具类
 * 支持通过文件名+行号、commit-id和branch-name获取代码片段
 * 代码来源链接可自定义，支持GitHub、GitLab等平台
 */
public class CodeSnippetExtractor {

    private static final Logger logger = LoggerFactory.getLogger(CodeSnippetExtractor.class);

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "(https?://[^/]+)/([^/]+)/([^/]+)/(blob|tree)/([^/]+)/(.+)");

    private static final Pattern GITLAB_URL_PATTERN = Pattern.compile(
            "(https?://[^/]+)/([^/]+)/([^/]+)/-/\\s*(blob|tree)/([^/]+)/(.+)");

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
     * 从自定义代码源URL获取代码片段
     *
     * @param baseUrl    代码源基础URL（如 https://github.com/owner/repo）
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
     * 从自定义代码源URL获取代码片段（多行）
     *
     * @param baseUrl    代码源基础URL（如 https://github.com/owner/repo）
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
     * 从GitHub获取代码片段
     *
     * @param owner      仓库所有者
     * @param repo       仓库名
     * @param filePath   文件路径
     * @param startLine  起始行号
     * @param endLine    结束行号
     * @param commitId   commit ID或branch名称
     * @return 代码片段结果
     */
    public static SnippetResult getGithubSnippet(String owner, String repo,
                                                   String filePath, int startLine,
                                                   int endLine, String commitId) {
        String baseUrl = "https://github.com/" + owner + "/" + repo;
        return getSnippet(baseUrl, filePath, startLine, endLine, commitId, commitId);
    }

    /**
     * 从GitHub获取单行代码片段
     *
     * @param owner      仓库所有者
     * @param repo       仓库名
     * @param filePath   文件路径
     * @param lineNumber 行号
     * @param commitId   commit ID或branch名称
     * @return 代码片段结果
     */
    public static SnippetResult getGithubSnippet(String owner, String repo,
                                                   String filePath, int lineNumber,
                                                   String commitId) {
        return getGithubSnippet(owner, repo, filePath, lineNumber, lineNumber, commitId);
    }

    /**
     * 从GitLab获取代码片段
     *
     * @param host       GitLab主机（如 gitlab.com）
     * @param owner      仓库所有者
     * @param repo       仓库名
     * @param filePath   文件路径
     * @param startLine  起始行号
     * @param endLine    结束行号
     * @param commitId   commit ID或branch名称
     * @return 代码片段结果
     */
    public static SnippetResult getGitlabSnippet(String host, String owner, String repo,
                                                   String filePath, int startLine,
                                                   int endLine, String commitId) {
        String baseUrl = "https://" + host + "/" + owner + "/" + repo;
        return getSnippet(baseUrl, filePath, startLine, endLine, commitId, commitId);
    }

    /**
     * 从GitLab获取单行代码片段
     *
     * @param host       GitLab主机
     * @param owner      仓库所有者
     * @param repo       仓库名
     * @param filePath   文件路径
     * @param lineNumber 行号
     * @param commitId   commit ID或branch名称
     * @return 代码片段结果
     */
    public static SnippetResult getGitlabSnippet(String host, String owner, String repo,
                                                   String filePath, int lineNumber,
                                                   String commitId) {
        return getGitlabSnippet(host, owner, repo, filePath, lineNumber, lineNumber, commitId);
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
        // 移除末尾斜杠
        baseUrl = baseUrl.replaceAll("/$", "");

        // 编码文件路径
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
        // 移除末尾斜杠
        baseUrl = baseUrl.replaceAll("/$", "");

        // 根据URL类型构建raw URL
        String rawUrl;
        if (baseUrl.contains("github.com")) {
            rawUrl = baseUrl.replace("github.com", "raw.githubusercontent.com")
                    + "/" + ref + "/" + filePath;
        } else if (baseUrl.contains("gitlab.com")) {
            rawUrl = baseUrl.replace("gitlab.com", "gitlab.com/-/raw")
                    + "/" + ref + "/" + filePath;
        } else {
            // 通用构建方式
            rawUrl = baseUrl + "/raw/" + ref + "/" + filePath;
        }

        return rawUrl;
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
                    .header("Accept", "application/vnd.github.v3.raw")
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

    /**
     * 解析代码源URL类型
     *
     * @param url 代码源URL
     * @return URL类型：github, gitlab, 或 other
     */
    public static String parseUrlType(String url) {
        if (url.contains("github.com")) {
            return "github";
        } else if (url.contains("gitlab.com") || url.contains("git")) {
            return "gitlab";
        }
        return "other";
    }
}
