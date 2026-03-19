package com.stability.martrix.dto;

/**
 * ReAct对话请求
 */
public class AIReactRequest {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户问题
     */
    private String question;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
