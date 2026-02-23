package com.stability.martrix.dto;

import com.stability.martrix.entity.AArch64Tombstone;

import java.util.List;

/**
 * AI分析响应DTO
 */
public class AIAnalysisResponse extends BaseResponse {
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 解析后的用户提问
     */
    private String parsedQuestion;

    /**
     * 用户提问中的崩溃信息
     */
    private CrashInfo crashInfo;

    /**
     * 解析到的tombstone信息（如果有）
     */
    private AArch64Tombstone tombstone;

    /**
     * 处理过程中的日志信息
     */
    private List<String> processLogs;

    /**
     * AI分析结果（原始JSON字符串）
     */
    private String aiAnalysis;

    /**
     * AI崩溃分析结果（解析后的对象）
     */
    private CrashAnalysisResult crashAnalysisResult;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getParsedQuestion() {
        return parsedQuestion;
    }

    public void setParsedQuestion(String parsedQuestion) {
        this.parsedQuestion = parsedQuestion;
    }

    /**
     * 用户提问中是否包含崩溃信息
     */
    public boolean isQuestionHasCrashInfo() {
        return crashInfo != null && crashInfo.isHasCrashInfo();
    }

    public CrashInfo getCrashInfo() {
        return crashInfo;
    }

    public void setCrashInfo(CrashInfo crashInfo) {
        this.crashInfo = crashInfo;
    }

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

    public String getAiAnalysis() {
        return aiAnalysis;
    }

    public void setAiAnalysis(String aiAnalysis) {
        this.aiAnalysis = aiAnalysis;
    }

    public CrashAnalysisResult getCrashAnalysisResult() {
        return crashAnalysisResult;
    }

    public void setCrashAnalysisResult(CrashAnalysisResult crashAnalysisResult) {
        this.crashAnalysisResult = crashAnalysisResult;
    }

    /**
     * 创建失败响应的静态方法
     *
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static AIAnalysisResponse fail(String errorMessage) {
        return fail("ANALYSIS_FAILED", errorMessage);
    }

    /**
     * 创建失败响应的静态方法
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static AIAnalysisResponse fail(String errorCode, String errorMessage) {
        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }

    /**
     * 创建成功响应的静态方法
     *
     * @param sessionId 会话ID
     * @return 成功响应
     */
    public static AIAnalysisResponse success(String sessionId) {
        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setSuccess(true);
        response.setSessionId(sessionId);
        return response;
    }
}
