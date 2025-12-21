package com.stability.martrix.controller;

import com.stability.martrix.annotation.AArch64;
import com.stability.martrix.annotation.AArch64Demo;
import com.stability.martrix.annotation.AndroidAArch64Demo;
import com.stability.martrix.service.FileService;
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

    private final FileService aarch64DemoFileService;

    public TestController(@AndroidAArch64Demo FileService aarch64DemoFileService) {
        this.aarch64DemoFileService = aarch64DemoFileService;
    }

    @GetMapping("/hello")
    public String hello() {

        return "hello";
    }


}
