package com.stability.martrix.dto;

import com.stability.martrix.entity.AArch64Tombstone;

import java.util.List;

/**
 * 文件解析结果
 */
public class FileParseResult {
    /**
     * 解析到的Tombstone信息
     */
    private AArch64Tombstone tombstone;

    /**
     * 处理日志
     */
    private List<String> processLogs;

    /**
     * 是否解析成功
     */
    private boolean success;

    public AArch64Tombstone getTombstone() {
        return tombstone;
    }

    public void setTombstone(AArch64Tombstone tombstone) {
        this.tombstone = tombstone;
    }

    public List<String> getProcessLogs() {
        return processLogs;
    }

    public void setProcessLogs(List<String> processLogs) {
        this.processLogs = processLogs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean hasTombstone() {
        return tombstone != null;
    }
}
