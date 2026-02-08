package com.stability.martrix.controller;

import com.stability.martrix.constants.ErrorCode;
import com.stability.martrix.dto.AIAnalysisResponse;
import com.stability.martrix.service.AIFileAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI分析控制器
 * 负责处理用户提问和文件，提取崩溃信息和ELF文件信息
 */
@RestController
@RequestMapping("/ai")
public class AnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);

    private final AIFileAnalysisService aiFileAnalysisService;

    public AnalysisController(AIFileAnalysisService aiFileAnalysisService) {
        this.aiFileAnalysisService = aiFileAnalysisService;
    }

    /**
     * AI分析接口
     * 处理用户提问和文件，提取崩溃信息和ELF文件信息
     *
     * @param question 用户提问
     * @param sessionId 会话ID（必填）
     * @param files 上传的文件（支持多个）
     * @return 分析结果
     */
    @PostMapping("/analyze")
    public AIAnalysisResponse analyze(@RequestParam(value = "question", required = false) String question,
                                      @RequestParam(value = "sessionId", required = true) String sessionId,
                                      @RequestParam(value = "files", required = false) MultipartFile[] files) {
        logger.info("收到AI分析请求，sessionId={}, question={}, files={}",
                sessionId, question, files != null ? files.length : 0);

        if (ObjectUtils.isEmpty(sessionId)) {
            logger.warn("sessionId为空，请求失败");
            return AIAnalysisResponse.fail(
                    ErrorCode.SESSION_ID_EMPTY,
                    "sessionId不能为空"
            );
        }

        return aiFileAnalysisService.analyzeRequest(question, sessionId, files);
    }
}
