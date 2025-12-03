package com.stability.martrix.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件预处理
 */

@RestController
@RequestMapping("/test")
public class TestController {

    private final ChatClient chatClient;

    public TestController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello world";
    }


}
