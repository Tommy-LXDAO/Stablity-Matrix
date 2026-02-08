package com.stability.martrix.dto;

import lombok.Data;

/**
 * 崩溃信息DTO
 * 用于存储从用户提问中提取的崩溃相关信息
 */
@Data
public class CrashInfo {
    /**
     * 是否包含崩溃信息
     */
    private boolean hasCrashInfo;

    /**
     * 崩溃类型（如：SIGSEGV、SIGABRT、ANR等）
     */
    private String crashType;

    /**
     * 崩溃描述
     */
    private String description;

    /**
     * 相关的错误消息或堆栈信息
     */
    private String errorMessage;

    /**
     * 涉及的库或模块名称
     */
    private String relatedLibrary;

    /**
     * 发生崩溃的时间信息
     */
    private String timestamp;

    /**
     * 其他相关信息
     */
    private String additionalInfo;
}
