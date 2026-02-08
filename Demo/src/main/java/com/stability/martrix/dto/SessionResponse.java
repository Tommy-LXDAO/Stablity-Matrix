package com.stability.martrix.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse extends BaseResponse {
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 创建时间
     */
    private Long createdAt;

    /**
     * 过期时间（小时）
     */
    private Integer expireHours;

    /**
     * 过期时间戳
     */
    private Long expireAt;

    /**
     * 创建成功响应的构造函数
     */
    public SessionResponse(String sessionId, Long createdAt, Long expireAt) {
        super(true);
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.expireHours = 24;
        this.expireAt = expireAt;
    }

    /**
     * 创建失败响应的静态方法
     *
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static SessionResponse fail(String errorMessage) {
        return fail("SESSION_CREATE_FAILED", errorMessage);
    }

    /**
     * 创建失败响应的静态方法
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static SessionResponse fail(String errorCode, String errorMessage) {
        SessionResponse response = new SessionResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
