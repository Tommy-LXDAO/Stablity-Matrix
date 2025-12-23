package com.stability.martrix.controller;

import com.stability.martrix.annotation.AArch64;
import com.stability.martrix.annotation.AArch64Demo;
import com.stability.martrix.annotation.AndroidAArch64Demo;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.service.AITroubleAnalysisService;
import com.stability.martrix.service.FileService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件预处理
 */

@RestController
@RequestMapping("/test")
public class TestController {

    private final FileService aarch64DemoFileService;
    private final AITroubleAnalysisService aiTroubleAnalysisService;

    public TestController(@AndroidAArch64Demo FileService aarch64DemoFileService,
                          AITroubleAnalysisService aiTroubleAnalysisService) {
        this.aarch64DemoFileService = aarch64DemoFileService;
        this.aiTroubleAnalysisService = aiTroubleAnalysisService;
    }

    @GetMapping("/hello")
    public String hello() {
        return aiTroubleAnalysisService.simpleQuery("你是谁?你的名字叫什么?");
    }

    // 入参为文件，取出文件内容，并进行解析，调用parseFile，然后返回结果
    @PostMapping("/query")
    public TroubleEntity simpleQuery(@RequestParam("file") MultipartFile file) throws Exception {
        // 将MultipartFile转换为List<String>
        List<String> lines = new BufferedReader(new InputStreamReader(file.getInputStream()))
                .lines()
                .collect(Collectors.toList());
        
        // 调用parseFile方法解析文件内容
        return aarch64DemoFileService.parseFile(lines);
    }
    
    // 分析tombstone文件的故障原因
    @PostMapping("/analyze")
    public String analyzeTrouble(@RequestParam("file") MultipartFile file) throws Exception {
        // 将MultipartFile转换为List<String>
        List<String> lines = new BufferedReader(new InputStreamReader(file.getInputStream()))
                .lines()
                .collect(Collectors.toList());
        
        // 调用parseFile方法解析文件内容
        TroubleEntity entity = aarch64DemoFileService.parseFile(lines);
        
        // 确保解析结果是AArch64Tombstone类型
        if (!(entity instanceof AArch64Tombstone)) {
            return "错误：无法解析为AArch64Tombstone格式";
        }
        
        AArch64Tombstone tombstone = (AArch64Tombstone) entity;
        
        // 使用AI分析故障原因
        return aiTroubleAnalysisService.analyzeTrouble(tombstone);
    }
}