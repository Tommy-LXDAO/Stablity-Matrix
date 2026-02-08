package com.stability.martrix.controller;

import com.stability.martrix.dto.SessionResponse;
import com.stability.martrix.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理控制器
 * 负责会话的创建和管理
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 创建会话接口
     * 使用雪花算法生成会话ID，并存储到Redis缓存中，缓存时间24小时
     *
     * @return 会话响应，包含会话ID和过期时间
     */
    @PostMapping("/create")
    public SessionResponse createSession() {
        return sessionService.createSession();
    }
}
