package com.stability.martrix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置属性
 */
@Component
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    /**
     * 会话文件存储根路径
     */
    private String basePath = "/tmp/sessions";

    /**
     * 会话文件夹前缀
     */
    private String sessionFolderPrefix = "session_";

    /**
     * 清理过期会话文件的时间阈值（小时）
     */
    private int cleanupExpiredHours = 48;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getSessionFolderPrefix() {
        return sessionFolderPrefix;
    }

    public void setSessionFolderPrefix(String sessionFolderPrefix) {
        this.sessionFolderPrefix = sessionFolderPrefix;
    }

    public int getCleanupExpiredHours() {
        return cleanupExpiredHours;
    }

    public void setCleanupExpiredHours(int cleanupExpiredHours) {
        this.cleanupExpiredHours = cleanupExpiredHours;
    }

    /**
     * 获取会话文件夹完整路径
     */
    public String getSessionPath(String sessionId) {
        return basePath + "/" + sessionFolderPrefix + sessionId;
    }
}
