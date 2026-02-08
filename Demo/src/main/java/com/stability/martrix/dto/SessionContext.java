package com.stability.martrix.dto;

import com.stability.martrix.entity.AArch64Tombstone;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * 会话上下文
 * 存储会话期间的所有相关信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 创建时间戳
     */
    private Long createdAt;

    /**
     * 过期时间戳
     */
    private Long expireAt;

    /**
     * 用户问题列表
     */
    private java.util.List<String> questions = new ArrayList<>();

    /**
     * 解析后的用户提问列表
     */
    private java.util.List<String> parsedQuestions = new ArrayList<>();

    /**
     * Tombstone信息（如果有）
     */
    private AArch64Tombstone tombstone;

    /**
     * 会话文件列表（存储在文件系统中的文件路径）
     */
    private java.util.List<SessionFile> files = new ArrayList<>();

    /**
     * 是否解析成功
     */
    private boolean success = false;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 会话文件信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionFile {
        /**
         * 文件名
         */
        private String fileName;

        /**
         * 文件路径
         */
        private String filePath;

        /**
         * 文件大小（字节）
         */
        private Long fileSize;

        /**
         * 文件类型（TXT, ZIP, TAR等）
         */
        private String fileType;

        /**
         * 上传时间戳
         */
        private Long uploadedAt;
    }

    /**
     * 构造函数
     */
    public SessionContext(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.expireAt = this.createdAt + (24 * 60 * 60 * 1000L); // 24小时后过期
    }

    /**
     * 添加问题到会话
     */
    public void addQuestion(String question) {
        if (this.questions == null) {
            this.questions = new ArrayList<>();
        }
        this.questions.add(question);
    }

    /**
     * 添加解析后的问题到会话
     */
    public void addParsedQuestion(String parsedQuestion) {
        if (this.parsedQuestions == null) {
            this.parsedQuestions = new ArrayList<>();
        }
        this.parsedQuestions.add(parsedQuestion);
    }

    /**
     * 添加文件到会话
     */
    public void addFile(String fileName, String filePath, long fileSize, String fileType) {
        if (this.files == null) {
            this.files = new ArrayList<>();
        }
        SessionFile sessionFile = new SessionFile(fileName, filePath, fileSize, fileType, System.currentTimeMillis());
        this.files.add(sessionFile);
    }

    /**
     * 设置Tombstone信息
     */
    public void setTombstone(AArch64Tombstone tombstone) {
        this.tombstone = tombstone;
        if (tombstone != null) {
            this.success = true;
        }
    }
}
