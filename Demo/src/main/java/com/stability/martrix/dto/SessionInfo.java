package com.stability.martrix.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
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

    public SessionInfo(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.expireAt = this.createdAt + (24 * 60 * 60 * 1000L); // 24小时后过期
    }
}
