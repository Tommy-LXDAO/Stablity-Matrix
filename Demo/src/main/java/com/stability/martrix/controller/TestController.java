package com.stability.martrix.controller;

import com.stability.martrix.annotation.AArch64;
import com.stability.martrix.annotation.AArch64Demo;
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

    private final FileService aarch64FileService;
    private final FileService aarch64DemoFileService;

    public TestController(@AArch64 FileService aarch64FileService, @AArch64Demo FileService aarch64DemoFileService) {
        this.aarch64FileService = aarch64FileService;
        this.aarch64DemoFileService = aarch64DemoFileService;
    }

    @GetMapping("/hello")
    public String hello() {
        aarch64DemoFileService.parseFile("test");
        aarch64FileService.parseFile("test");
        return "hello";
    }


}
