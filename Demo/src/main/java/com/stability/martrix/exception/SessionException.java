package com.stability.martrix.exception;

/**
 * 会话相关异常
 */
public class SessionException extends RuntimeException {

    private final String sessionId;
    private final SessionErrorType errorType;

    public enum SessionErrorType {
        CREATE_FAILED,
        UPDATE_FAILED,
        DELETE_FAILED,
        NOT_FOUND,
        ALREADY_EXISTS,
        REDIS_ERROR,
        CONNECTION_ERROR,
        SERIALIZATION_ERROR,
        UNKNOWN
    }

    public SessionException(String sessionId, SessionErrorType errorType, String message) {
        super(message);
        this.sessionId = sessionId;
        this.errorType = errorType;
    }

    public SessionException(String sessionId, SessionErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.sessionId = sessionId;
        this.errorType = errorType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionErrorType getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return String.format("SessionException{sessionId='%s', errorType=%s, message='%s'}",
                sessionId, errorType, getMessage());
    }
}
