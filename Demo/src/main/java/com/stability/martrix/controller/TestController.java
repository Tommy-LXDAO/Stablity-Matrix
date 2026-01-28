package com.stability.martrix.controller;

import com.stability.martrix.annotation.AArch64;
import com.stability.martrix.annotation.AArch64Demo;
import com.stability.martrix.annotation.AndroidAArch64Demo;
import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.service.AITroubleAnalysisService;
import com.stability.martrix.service.FileService;
import com.stability.martrix.service.PatternMatchService;
import com.stability.martrix.util.TombstoneFormatter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 文件预处理
 */

@RestController
@RequestMapping("/test")
public class TestController {

    private static final Logger logger = Logger.getLogger(TestController.class.getName());
    private final FileService aarch64DemoFileService;
    private final AITroubleAnalysisService aiTroubleAnalysisService;
    private final PatternMatchService patternMatchService;
    private final ChatClient chatClient;

    public TestController(@AndroidAArch64Demo FileService aarch64DemoFileService,
                          AITroubleAnalysisService aiTroubleAnalysisService,
                          PatternMatchService patternMatchService,
                          ChatClient.Builder chatClientBuilder) {
        this.aarch64DemoFileService = aarch64DemoFileService;
        this.aiTroubleAnalysisService = aiTroubleAnalysisService;
        this.patternMatchService = patternMatchService;
        this.chatClient = chatClientBuilder.build();
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

        // Step 1: 使用既有的问题维护表来查找已知问题
        String patternMatchResult = null;
        if (tombstone.getSignalInfo() != null) {
            var result = patternMatchService.analyzePattern(tombstone);
            if (result != null) {
                patternMatchResult = String.format(
                    "[模式匹配结果] 置信度: %.2f, 分析: %s, 直接结论: %s\n\n",
                    result.getConfidence(),
                    result.getResult(),
                    result.isDirectConclusion() ? "是" : "否"
                );
            }
        }

        // Step 2: 使用AI分析故障原因
        String aiAnalysis = aiTroubleAnalysisService.analyzeTrouble(tombstone);

        // Step 3: 组合结果
        if (patternMatchResult != null) {
            return patternMatchResult + "---\n\n[AI详细分析]\n" + aiAnalysis;
        }

        return aiAnalysis;
    }

    @PostMapping("/demo/analyzeTombstone")
    public String analyzeTombstone(@RequestParam("file") MultipartFile file) {
        try {
            TroubleEntity troubleEntity = simpleQuery(file);

            // Ensure it's AArch64Tombstone type
            if (!(troubleEntity instanceof AArch64Tombstone)) {
                return "错误：无法解析为AArch64Tombstone格式";
            }

            AArch64Tombstone tombstone = (AArch64Tombstone) troubleEntity;

            // Preserve tombstone info for logging/analysis
            logger.info("=== Tombstone Info Reserved ===");
            logger.info("PID: " + tombstone.getPid());
            logger.info("Process Name: " + tombstone.getProcessName());
            if (tombstone.getSignalInfo() != null) {
                logger.info("Signal: " + tombstone.getSignalInfo().getSigNumber() +
                           " (" + tombstone.getSignalInfo().getSigInformation() + ")");
            }

            // Use PatternMatchService to analyze the abort pattern
            PatternMatchResult result = patternMatchService.analyzePattern(tombstone);

            if (result == null) {
                return "未检测到SIGABRT信号或无法匹配特定模式";
            }

            // Build context message for AI
            String contextMessage = String.format(
                "模式匹配检测结果:\n" +
                "- 置信度: %.2f\n" +
                "- 分析: %s\n" +
                "- 是否直接给出结论: %s\n\n" +
                "请基于以上模式匹配结果,对以下tombstone数据提供更深入的分析和可能的解决方案建议:",
                result.getConfidence(),
                result.getResult(),
                result.isDirectConclusion() ? "是" : "否"
            );

            // Build simplified tombstone object for AI analysis (basic signal info + top 10 stack traces)
            AArch64Tombstone simplifiedTombstone = TombstoneFormatter.createSimplifiedTombstone(tombstone);

            // Convert simplified tombstone to JSON string for AI
            String tombstoneInfo = com.alibaba.fastjson.JSON.toJSONString(simplifiedTombstone);

            // Call AI for enhanced analysis with retry logic for HTTP 429 errors
            String aiEnhancedAnalysis = callAIWithRetry(contextMessage, tombstoneInfo);

            return String.format(
                "=== 模式匹配结果 ===\n" +
                "置信度: %.2f\n" +
                "分析: %s\n" +
                "=== AI增强分析 ===\n%s",
                result.getConfidence(),
                result.getResult(),
                aiEnhancedAnalysis
            );
        } catch (Exception e) {
            logger.log(Level.WARNING, "解析文件失败: " + e.getMessage());
            return "解析文件失败: " + e.getMessage();
        }
    }

    /**
     * Call AI with retry logic for HTTP 429 (rate limit) errors
     * Maximum 3 retry attempts
     *
     * @param contextMessage the pattern matching context
     * @param tombstoneInfo the tombstone information
     * @return AI analysis result
     */
    private String callAIWithRetry(String contextMessage, String tombstoneInfo) {
        final int MAX_RETRIES = 3;
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                logger.info("AI调用尝试 #" + (attempt + 1));

                String response = chatClient.prompt()
                        .system("你是一个crash堆栈的AI助手，请分析当前堆栈信息，结合工具分析结果，并给出答复")
                        .system("这是通过工具分析出来的结果:"+contextMessage)
                        .user(tombstoneInfo)
                        .call()
                        .content();

                logger.info("AI调用成功，尝试次数: " + (attempt + 1));
                return response;

            } catch (Exception e) {
                attempt++;
                String errorMsg = e.getMessage();

                // Check if it's an HTTP 429 error (rate limit)
                if (errorMsg != null && errorMsg.contains("429")) {
                    logger.warning(String.format(
                        "遇到HTTP 429错误(速率限制)，第%d次重试中...",
                        attempt));

                    if (attempt < MAX_RETRIES) {
                        // Immediately retry (no wait as requested)
                        continue;
                    } else {
                        logger.severe("达到最大重试次数(" + MAX_RETRIES + ")，放弃重试");
                        return "错误: AI服务繁忙(HTTP 429)，请稍后重试。已重试" + MAX_RETRIES + "次。错误信息"+errorMsg;
                    }
                } else {
                    // Not a 429 error, don't retry
                    logger.severe("AI调用失败(非429错误): " + errorMsg);
                    return "错误: AI调用失败 - " + errorMsg;
                }
            }
        }

        return "错误: 未知错误";
    }
}
