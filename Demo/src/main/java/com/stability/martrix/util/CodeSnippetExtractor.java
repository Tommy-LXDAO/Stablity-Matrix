package com.stability.martrix.util;

import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 代码片段提取工具类
 * 通过文件名+行号、commit-id和branch-name获取代码片段
 */
public class CodeSnippetExtractor {

    /**
     * 通过Git获取代码片段
     *
     * @param cloneUrl   仓库克隆URL
     * @param filePath   文件路径
     * @param line       崩溃发生的行号
     * @param lineRange  代码片段的范围（向前或向后几行）
     * @param commitId   commit ID
     * @param branchName branch名称
     * @return 代码片段内容
     */
    public static String getSnippetByGit(String cloneUrl, String filePath,
                                                 int line, int lineRange,
                                                 String commitId, String branchName) {
        return getSnippetByGit(cloneUrl, filePath, line, lineRange, commitId, branchName, null);
    }

    /**
     * 使用git clone将代码下载下来，然后切换到对应的分支，然后切到正确的代码位置，最后使用filePath+行号范围获取代码信息，返回字符串为代码片段字符串
     *
     * @param cloneUrl   仓库克隆URL
     * @param filePath   文件路径
     * @param line       崩溃发生的行号
     * @param lineRange  代码片段的范围（向前或向后几行）
     * @param commitId   commit ID
     * @param branchName branch名称
     * @param workDir    工作目录，如果为null则使用临时目录，注意：这里的工作目录必须是绝对路径
     * @return 代码片段内容
     */
    public static String getSnippetByGit(String cloneUrl, String filePath,
                                                 int line, int lineRange,
                                                 String commitId, String branchName,
                                                 String workDir) {
        // 确定工作目录
        String targetDir = workDir;
        if (targetDir == null || targetDir.isEmpty()) {
            // 使用临时目录
            try {
                targetDir = java.nio.file.Files.createTempDirectory("git_clone_").toString();
            } catch (java.io.IOException e) {
                return "Error: Failed to create temp directory - " + e.getMessage();
            }
        }

        try {
            File directory = new File(targetDir);
            // 1. git clone
            ProcessBuilder clonePb = new ProcessBuilder("git", "clone", cloneUrl, targetDir);
            clonePb.directory(directory);
            clonePb.redirectErrorStream(true);
            Process cloneProcess = clonePb.start();
            int cloneExitCode = cloneProcess.waitFor();
            if (cloneExitCode != 0) {
                return "Error: git clone failed with exit code " + cloneExitCode;
            }

            // 2. git checkout -b newBranch branchName
            ProcessBuilder checkoutPb = new ProcessBuilder("git", "checkout", "-b", "newBranch", "remotes/origin/" + branchName);
            checkoutPb.directory(directory);
            checkoutPb.redirectErrorStream(true);
            Process checkoutProcess = checkoutPb.start();
            int checkoutExitCode = checkoutProcess.waitFor();
            if (checkoutExitCode != 0) {
                return "Error: git checkout failed with exit code " + checkoutExitCode;
            }

            // 3. git reset --hard commitId
            ProcessBuilder resetPb = new ProcessBuilder("git", "reset", "--hard", commitId);
            resetPb.directory(directory);
            resetPb.redirectErrorStream(true);
            Process resetProcess = resetPb.start();
            int resetExitCode = resetProcess.waitFor();
            if (resetExitCode != 0) {
                return "Error: git reset failed with exit code " + resetExitCode;
            }

            // 4. 使用filePath+行号范围获取代码信息
            String fullFilePath = targetDir + java.io.File.separator + filePath;
            java.io.File file = new java.io.File(fullFilePath);
            if (!file.exists()) {
                return "Error: File not found - " + fullFilePath;
            }

            java.util.List<String> allLines = java.nio.file.Files.readAllLines(file.toPath());
            int startLine = Math.max(1, line - lineRange);
            int endLine = Math.min(allLines.size(), line + lineRange);

            StringBuilder snippet = new StringBuilder();
            for (int i = startLine; i <= endLine; i++) {
                String prefix = (i == line) ? ">>> " : "    ";
                snippet.append(prefix).append(i).append(" | ").append(allLines.get(i - 1)).append("\n");
            }

            return snippet.toString();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 通过Git获取代码片段（使用文件名+路径解析函数）
     * 可参考上面的做法，不同的是，在clone并且切换到正确的分支和commitId之后，在获取代码信息之前调用pathResolver获取文件路径
     *
     * @param cloneUrl       仓库克隆URL
     * @param fileName       文件名
     * @param pathResolver   文件路径解析函数，根据workDir和fileName返回文件路径
     * @param line           崩溃发生的行号
     * @param lineRange      代码片段的范围（向前或向后几行）
     * @param commitId       commit ID
     * @param branchName     branch名称
     * @param workDir        工作目录，如果为null则使用临时目录
     * @return 代码片段内容
     */
    public static String getSnippetByGit(String cloneUrl, String fileName,
                                                 BiFunction<String, String, String> pathResolver,
                                                 int line, int lineRange,
                                                 String commitId, String branchName,
                                                 String workDir) {
        // 确定工作目录
        String targetDir = workDir;
        if (targetDir == null || targetDir.isEmpty()) {
            try {
                targetDir = java.nio.file.Files.createTempDirectory("git_clone_").toString();
            } catch (java.io.IOException e) {
                return "Error: Failed to create temp directory - " + e.getMessage();
            }
        }

        try {
            File directory = new File(targetDir);
            // 1. git clone
            ProcessBuilder clonePb = new ProcessBuilder("git", "clone", cloneUrl, targetDir);
            clonePb.directory(directory);
            clonePb.redirectErrorStream(true);
            Process cloneProcess = clonePb.start();
            int cloneExitCode = cloneProcess.waitFor();
            if (cloneExitCode != 0) {
                return "Error: git clone failed with exit code " + cloneExitCode;
            }

            // 2. git checkout -b newBranch branchName
            ProcessBuilder checkoutPb = new ProcessBuilder("git", "checkout", "-b", "newBranch", "remotes/origin/" + branchName);
            checkoutPb.directory(directory);
            checkoutPb.redirectErrorStream(true);
            Process checkoutProcess = checkoutPb.start();
            int checkoutExitCode = checkoutProcess.waitFor();
            if (checkoutExitCode != 0) {
                return "Error: git checkout failed with exit code " + checkoutExitCode;
            }

            // 3. git reset --hard commitId
            ProcessBuilder resetPb = new ProcessBuilder("git", "reset", "--hard", commitId);
            resetPb.directory(directory);
            resetPb.redirectErrorStream(true);
            Process resetProcess = resetPb.start();
            int resetExitCode = resetProcess.waitFor();
            if (resetExitCode != 0) {
                return "Error: git reset failed with exit code " + resetExitCode;
            }

            // 4. 调用pathResolver获取文件路径
            String filePath = pathResolver.apply(targetDir, fileName);
            if (filePath == null || filePath.isEmpty()) {
                return "Error: pathResolver returned null or empty path for file: " + fileName;
            }

            // 5. 使用filePath+行号范围获取代码信息
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                return "Error: File not found - " + filePath;
            }

            java.util.List<String> allLines = java.nio.file.Files.readAllLines(file.toPath());
            int startLine = Math.max(1, line - lineRange);
            int endLine = Math.min(allLines.size(), line + lineRange);

            StringBuilder snippet = new StringBuilder();
            for (int i = startLine; i <= endLine; i++) {
                String prefix = (i == line) ? ">>> " : "    ";
                snippet.append(prefix).append(i).append(" | ").append(allLines.get(i - 1)).append("\n");
            }

            return snippet.toString();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
