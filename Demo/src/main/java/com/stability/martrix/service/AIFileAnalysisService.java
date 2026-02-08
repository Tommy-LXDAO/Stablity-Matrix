package com.stability.martrix.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stability.martrix.constants.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stability.martrix.dto.AIAnalysisResponse;
import com.stability.martrix.dto.CrashInfo;
import com.stability.martrix.dto.FileParseResult;
import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.dto.SessionContext;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.util.FileTypeDetector;
import com.stability.martrix.util.ZipFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI文件分析服务
 * 负责处理用户上传的文件和问题，提取崩溃信息
 */
@Service
public class AIFileAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AIFileAnalysisService.class);

    private final FileService fileService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionFileStorageService sessionFileStorageService;
    private final ArchiveExtractionService archiveExtractionService;
    private final SessionService sessionService;
    private final PatternMatchService patternMatchService;

    public AIFileAnalysisService(FileService fileService,
                                  ChatClient.Builder chatClientBuilder,
                                  SessionFileStorageService sessionFileStorageService,
                                  ArchiveExtractionService archiveExtractionService,
                                  SessionService sessionService,
                                  PatternMatchService patternMatchService) {
        this.fileService = fileService;
        this.chatClient = chatClientBuilder.build();
        this.sessionFileStorageService = sessionFileStorageService;
        this.archiveExtractionService = archiveExtractionService;
        this.sessionService = sessionService;
        this.patternMatchService = patternMatchService;
    }

    /**
     * 分析用户请求
     * 功能：解析请求内容，更新会话上下文，返回分析结果
     * 注意：使用 logger 输出处理日志，不存储到响应或会话上下文中
     *
     * @param question 用户提问
     * @param sessionId 会话ID
     * @param files 上传的文件列表
     * @return 分析结果
     */
    public AIAnalysisResponse analyzeRequest(String question, String sessionId, MultipartFile[] files) {
        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setSessionId(sessionId);

        try {
            // ========================================
            // 第一步：获取会话上下文
            // ========================================
            SessionContext sessionContext = sessionService.getSession(sessionId);
            if (sessionContext == null) {
                logger.warn("[sessionId={}] 会话不存在", sessionId);
                return AIAnalysisResponse.fail(
                        ErrorCode.SESSION_NOT_FOUND,
                        "会话不存在，请先创建会话"
                );
            }

            // ========================================
            // 第二步：更新会话上下文（原始问题信息）
            // ========================================
            if (question != null && !question.trim().isEmpty()) {
                sessionContext.addQuestion(question);
            }

            // ========================================
            // 第三步：处理文件（工具解析）- 优先使用工具解析
            // ========================================
            FileParseResult fileParseResult;
            boolean hasTombstone = false;
            AArch64Tombstone tombstone = null;
            PatternMatchResult patternMatchResult = null;

            if (files == null || files.length == 0) {
                tombstone = sessionContext.getTombstone();
                if (tombstone != null) {
                    hasTombstone = true;
                    patternMatchResult = patternMatchService.analyzePattern(tombstone);
                }
                fileParseResult = new FileParseResult();
                fileParseResult.setTombstone(tombstone);
                fileParseResult.setSuccess(false);
            } else {
                fileParseResult = processFiles(sessionId, files, sessionContext);
                hasTombstone = fileParseResult.hasTombstone();
                tombstone = fileParseResult.getTombstone();

                // 更新会话上下文（Tombstone信息）
                if (tombstone != null) {
                    sessionContext.setTombstone(tombstone);
                    logger.info("[sessionId={}] 解析完成: 找到Tombstone信息", sessionId);

                    // ========================================
                    // 第四步：模式匹配（仅在Tombstone解析成功后执行）
                    // ========================================
                    logger.info("[sessionId={}] 开始模式匹配分析...", sessionId);
                    patternMatchResult = patternMatchService.analyzePattern(tombstone);
                    if (patternMatchResult != null) {
                        logger.info("[sessionId={}] 模式匹配完成: 置信度={}, 结果={}",
                                sessionId, patternMatchResult.getConfidence(), patternMatchResult.getResult());
                    } else {
                        logger.info("[sessionId={}] 模式匹配: 未匹配到特定模式", sessionId);
                    }
                } else {
                    logger.info("[sessionId={}] 解析完成: 未找到崩溃信息", sessionId);
                }
            }

            // ========================================
            // 第五步：AI模型调用（解析用户提问）
            // ========================================
            String parsedQuestion = null;
            CrashInfo crashInfo = null;

            // parseQuestion始终需要调用，用于清理用户问题
            if (question != null && !question.trim().isEmpty()) {
                logger.info("[sessionId={}] 使用AI模型解析用户提问...", sessionId);
                parsedQuestion = parseQuestion(question);
                response.setParsedQuestion(parsedQuestion);
                if (parsedQuestion != null && !parsedQuestion.trim().isEmpty()) {
                    sessionContext.addParsedQuestion(parsedQuestion);
                }
                logger.info("[sessionId={}] 用户提问解析完成: {}", sessionId, parsedQuestion);
            }

            // extractCrashInfo仅在工具解析失败时需要调用
            if (!hasTombstone && question != null && !question.trim().isEmpty()) {
                logger.info("[sessionId={}] 工具解析未找到Tombstone，使用AI模型提取崩溃信息...", sessionId);
                crashInfo = extractCrashInfo(question);
                response.setCrashInfo(crashInfo);

                logger.info("[sessionId={}] 崩溃信息提取完成: hasCrashInfo={}", sessionId, crashInfo.isHasCrashInfo());
                if (crashInfo.getCrashType() != null) {
                    logger.info("[sessionId={}] 崩溃类型: {}", sessionId, crashInfo.getCrashType());
                }
            } else if (hasTombstone) {
                // 工具解析成功，设置null
                response.setCrashInfo(null);
            }

            // 判断是否解析成功
            boolean success = (fileParseResult != null && fileParseResult.isSuccess()) ||
                              hasTombstone || parsedQuestion != null;
            sessionContext.setSuccess(success);

            // ========================================
            // 第六步：AI分析（调用大模型分析崩溃原因）
            // ========================================
            String aiAnalysis = null;
            if (hasTombstone || (crashInfo != null && crashInfo.isHasCrashInfo())) {
                logger.info("[sessionId={}] 开始AI分析...", sessionId);
                aiAnalysis = analyzeCrashWithAI(sessionId, question, parsedQuestion,
                        crashInfo, tombstone, patternMatchResult);
                logger.info("[sessionId={}] AI分析完成", sessionId);
            }

            // 设置响应结果
            if (fileParseResult != null) {
                response.setTombstone(fileParseResult.getTombstone());
            }
            response.setAiAnalysis(aiAnalysis);
            response.setSuccess(success);

            // ========================================
            // 第七步：更新会话上下文到Redis
            // ========================================
            sessionService.updateSessionContext(sessionId, sessionContext);

        } catch (Exception e) {
            logger.error("[sessionId={}] 分析请求失败: error={}", sessionId, e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage("分析请求失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 处理文件列表
     * 先存储文件到会话文件夹，然后解析
     *
     * @param sessionId 会话ID
     * @param files 上传的文件列表
     * @param sessionContext 会话上下文
     * @return 文件解析结果
     */
    private FileParseResult processFiles(String sessionId, MultipartFile[] files, SessionContext sessionContext) {
        // 第一步：存储所有文件到会话文件夹
        List<String> storedFilePaths = sessionFileStorageService.storeFiles(sessionId, files);
        logger.info("[sessionId={}] 文件已存储到会话文件夹，共 {} 个文件", sessionId, storedFilePaths.size());

        // 将文件信息记录到会话上下文
        for (String filePath : storedFilePaths) {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();
            String fileType = detectFileTypeByName(fileName);
            long fileSize = getFileSize(path);
            sessionContext.addFile(fileName, filePath, fileSize, fileType);
        }

        // 第二步：检测并解压归档文件
        List<String> archiveFiles = new ArrayList<>();
        List<String> regularFiles = new ArrayList<>();

        for (String filePath : storedFilePaths) {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();
            ArchiveExtractionService.ArchiveType archiveType = detectArchiveType(fileName);
            if (archiveType != ArchiveExtractionService.ArchiveType.UNKNOWN) {
                archiveFiles.add(filePath);
            } else {
                regularFiles.add(filePath);
            }
        }

        List<String> allFilePaths = new ArrayList<>();
        if (!archiveFiles.isEmpty()) {
            String sessionPath = sessionFileStorageService.getFileStorageProperties().getSessionPath(sessionId);
            logger.info("[sessionId={}] 检测到 {} 个归档文件，开始解压...", sessionId, archiveFiles.size());
            List<String> extractedFiles = archiveExtractionService.extractArchives(archiveFiles, sessionPath);
            logger.info("[sessionId={}] 解压归档完成，共 {} 个文件", sessionId, extractedFiles.size());
            // 将解压后的文件信息也记录到会话上下文
            for (String filePath : extractedFiles) {
                Path path = Paths.get(filePath);
                String fileName = path.getFileName().toString();
                String fileType = detectFileTypeByName(fileName);
                long fileSize = getFileSize(path);
                sessionContext.addFile(fileName, filePath, fileSize, fileType);
            }
            // 合并存储的文件路径和解压的文件路径
            allFilePaths.addAll(regularFiles);
            allFilePaths.addAll(extractedFiles);
        } else {
            // 没有归档文件，直接使用存储的文件路径
            allFilePaths.addAll(regularFiles);
        }

        return processStoredFiles(sessionId, allFilePaths, sessionContext);
    }

    /**
     * 通过文件名检测文件类型
     */
    private String detectFileTypeByName(String fileName) {
        if (fileName == null) {
            return "UNKNOWN";
        }

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "TXT";
        } else if (lower.endsWith(".zip")) {
            return "ZIP";
        } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return "TAR_GZ";
        } else if (lower.endsWith(".tar")) {
            return "TAR";
        }
        return "UNKNOWN";
    }

    /**
     * 处理已存储的文件
     *
     * @param sessionId 会话ID
     * @param filePaths 文件路径列表
     * @param sessionContext 会话上下文
     * @return 文件解析结果
     */
    private FileParseResult processStoredFiles(String sessionId, List<String> filePaths, SessionContext sessionContext) {
        FileParseResult result = new FileParseResult();
        AArch64Tombstone tombstone = null;

        for (String filePath : filePaths) {
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    logger.warn("[sessionId={}] 文件不存在，跳过: {}", sessionId, path);
                    continue;
                }

                String fileName = path.getFileName().toString();
                logger.info("[sessionId={}] 处理文件: {}", sessionId, fileName);

                // 检测文件类型
                FileTypeDetector.FileType fileType;
                try {
                    fileType = detectFileTypeByPath(path);
                } catch (IOException e) {
                    logger.warn("[sessionId={}] 检测文件类型失败: file={}, error={}", sessionId, fileName, e.getMessage());
                    continue;
                }
                logger.debug("[sessionId={}] 检测到文件类型: {}", sessionId, fileType);

                switch (fileType) {
                    case TXT:
                        logger.info("[sessionId={}] 尝试将TXT文件解析为Tombstone...", sessionId);
                        AArch64Tombstone parsedTombstone = parseTextFileAsPath(path);
                        if (parsedTombstone != null && isValidTombstone(parsedTombstone)) {
                            tombstone = parsedTombstone;
                            logger.info("[sessionId={}] 成功解析Tombstone文件: {}", sessionId, fileName);
                        } else {
                            logger.debug("[sessionId={}] 文件不是有效的Tombstone格式: {}", sessionId, fileName);
                        }
                        break;

                    case ZIP:
                        // 这种情况是文件名不是zip但内容是zip的情况
                        logger.info("[sessionId={}] 检测到ZIP文件内容，开始解压并分析...", sessionId);
                        FileParseResult zipResult = handleZipContent(sessionId, path);
                        if (zipResult.getTombstone() != null && tombstone == null) {
                            tombstone = zipResult.getTombstone();
                        }
                        break;
                }
            } catch (Exception e) {
                logger.error("[sessionId={}] 处理文件失败: path={}, error={}", sessionId, filePath, e.getMessage(), e);
            }
        }

        result.setTombstone(tombstone);
        result.setSuccess(true);
        return result;
    }

    /**
     * 处理ZIP文件内容（通过魔数检测到的）
     *
     * @param sessionId 会话ID
     * @param zipPath ZIP文件路径
     * @return 文件解析结果
     */
    private FileParseResult handleZipContent(String sessionId, Path zipPath) {
        FileParseResult result = new FileParseResult();
        AArch64Tombstone tombstone = null;

        try {
            // 查找可能的Tombstone文件
            List<ZipFileParser.ZipEntryInfo> potentialTombstones =
                findPotentialTombstonesInZip(zipPath);
            logger.info("[sessionId={}] 在ZIP中找到 {} 个可能的Tombstone文件", sessionId, potentialTombstones.size());

            for (ZipFileParser.ZipEntryInfo entry : potentialTombstones) {
                logger.info("[sessionId={}] 尝试解析文件: {}", sessionId, entry.getName());
                AArch64Tombstone parsedTombstone = parseZipEntryAsTombstone(entry);
                if (parsedTombstone != null && isValidTombstone(parsedTombstone)) {
                    tombstone = parsedTombstone;
                    logger.info("[sessionId={}] 成功解析Tombstone: {}", sessionId, entry.getName());
                    break;
                } else {
                    logger.debug("[sessionId={}] 文件不是有效的Tombstone: {}", sessionId, entry.getName());
                }
            }

        } catch (Exception e) {
            logger.error("[sessionId={}] 分析ZIP文件内容失败: path={}, error={}", sessionId, zipPath, e.getMessage(), e);
        }

        result.setTombstone(tombstone);
        result.setSuccess(true);
        return result;
    }

    /**
     * 通过路径检测文件类型
     */
    private FileTypeDetector.FileType detectFileTypeByPath(Path path) throws IOException {
        byte[] header = new byte[8];
        try (var inputStream = Files.newInputStream(path)) {
            int read = inputStream.read(header);
            if (read >= 2 && isZipMagic(header)) {
                return FileTypeDetector.FileType.ZIP;
            }
        }

        return FileTypeDetector.FileType.TXT;
    }

    /**
     * 检查是否为ZIP魔数
     */
    private boolean isZipMagic(byte[] header) {
        if (header.length < 2) {
            return false;
        }
        return (header[0] & 0xFF) == 'P' && (header[1] & 0xFF) == 'K';
    }

    /**
     * 检测归档类型
     */
    private ArchiveExtractionService.ArchiveType detectArchiveType(String fileName) {
        return ArchiveExtractionService.detectArchiveType(fileName);
    }

    /**
     * 解析用户提问
     * 使用 AI 提取纯净的问题内容
     */
    private String parseQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return null;
        }

        try {
            String prompt = """
                你是一个问题清理助手。请分析用户的问题，并提取出纯净的核心问题。
                规则：
                1. 去除冗余信息、客套话、重复内容
                2. 保留核心技术问题和关键信息
                3. 如果用户提到崩溃、错误等，保留相关关键词
                4. 返回简洁清晰的问题描述

                用户问题：%s

                请只返回清理后的问题，不要有其他说明。
                """.formatted(question);
            // TODO: （AI请忽略这里）需要评估chatClient对应模型的耗时，这里耗时不要太多
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            logger.warn("AI解析问题失败，使用原始问题: " + e.getMessage());
        }

        // 降级处理：简单去除空白字符
        return question.trim();
    }

    /**
     * 从用户提问中提取崩溃信息
     * 使用 AI 智能提取
     */
    private CrashInfo extractCrashInfo(String question) {
        CrashInfo crashInfo = new CrashInfo();
        crashInfo.setHasCrashInfo(false);

        if (question == null || question.trim().isEmpty()) {
            return crashInfo;
        }

        try {
            String prompt = """
                你是一个技术分析助手。请分析用户的问题，提取崩溃相关的信息。

                请返回JSON格式，包含以下字段：
                {
                    "hasCrashInfo": true/false,
                    "crashType": "崩溃类型（如：SIGSEGV、SIGABRT、ANR、NullPointerException等）",
                    "description": "崩溃简要描述",
                    "errorMessage": "错误消息（如果有）",
                    "relatedLibrary": "涉及的库或模块（如果有）",
                    "timestamp": "时间信息（如果有）",
                    "additionalInfo": "其他相关信息"
                }

                如果没有崩溃信息，所有字段返回null。

                用户问题：%s

                请只返回JSON格式，不要有其他说明。
                """.formatted(question);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null) {
                // 解析JSON响应
                var jsonNode = objectMapper.readTree(response);

                if (jsonNode.has("hasCrashInfo")) {
                    crashInfo.setHasCrashInfo(jsonNode.get("hasCrashInfo").asBoolean());
                }
                if (jsonNode.has("crashType") && !jsonNode.get("crashType").isNull()) {
                    crashInfo.setCrashType(jsonNode.get("crashType").asText());
                }
                if (jsonNode.has("description") && !jsonNode.get("description").isNull()) {
                    crashInfo.setDescription(jsonNode.get("description").asText());
                }
                if (jsonNode.has("errorMessage") && !jsonNode.get("errorMessage").isNull()) {
                    crashInfo.setErrorMessage(jsonNode.get("errorMessage").asText());
                }
                if (jsonNode.has("relatedLibrary") && !jsonNode.get("relatedLibrary").isNull()) {
                    crashInfo.setRelatedLibrary(jsonNode.get("relatedLibrary").asText());
                }
                if (jsonNode.has("timestamp") && !jsonNode.get("timestamp").isNull()) {
                    crashInfo.setTimestamp(jsonNode.get("timestamp").asText());
                }
                if (jsonNode.has("additionalInfo") && !jsonNode.get("additionalInfo").isNull()) {
                    crashInfo.setAdditionalInfo(jsonNode.get("additionalInfo").asText());
                }

                return crashInfo;
            }
        } catch (JsonProcessingException e) {
            logger.warn("解析AI响应JSON失败: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("AI提取崩溃信息失败，使用关键词匹配: " + e.getMessage());
        }

        // 降级处理：使用关键词匹配
        String lower = question.toLowerCase();
        boolean hasCrash = lower.contains("crash") ||
               lower.contains("崩溃") ||
               lower.contains("tombstone") ||
               lower.contains("signal") ||
               lower.contains("sigsegv") ||
               lower.contains("sigabrt") ||
               lower.contains("anr") ||
               lower.contains("异常") ||
               lower.contains("错误");

        crashInfo.setHasCrashInfo(hasCrash);

        // 尝试提取崩溃类型
        if (lower.contains("sigsegv")) {
            crashInfo.setCrashType("SIGSEGV");
        } else if (lower.contains("sigabrt")) {
            crashInfo.setCrashType("SIGABRT");
        } else if (lower.contains("sigbus")) {
            crashInfo.setCrashType("SIGBUS");
        } else if (lower.contains("sigill")) {
            crashInfo.setCrashType("SIGILL");
        } else if (lower.contains("sigfpe")) {
            crashInfo.setCrashType("SIGFPE");
        } else if (lower.contains("anr")) {
            crashInfo.setCrashType("ANR");
        } else if (lower.contains("nullpointer") || lower.contains("null pointer")) {
            crashInfo.setCrashType("NullPointerException");
        }

        return crashInfo;
    }

    /**
     * 将文本文件解析为Tombstone
     */
    private AArch64Tombstone parseTextFileAsTombstone(MultipartFile file) {
        try {
            List<String> lines = new BufferedReader(new InputStreamReader(file.getInputStream()))
                    .lines()
                    .collect(Collectors.toList());

            TroubleEntity entity = fileService.parseFile(lines);
            if (entity instanceof AArch64Tombstone) {
                return (AArch64Tombstone) entity;
            }
        } catch (Exception e) {
            logger.error("解析文本文件为Tombstone失败", e);
        }
        return null;
    }

    /**
     * 将文本文件解析为Tombstone（通过路径）
     */
    private AArch64Tombstone parseTextFileAsPath(Path filePath) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            TroubleEntity entity = fileService.parseFile(lines);
            if (entity instanceof AArch64Tombstone) {
                return (AArch64Tombstone) entity;
            }
        } catch (Exception e) {
            logger.error("解析文本文件为Tombstone失败: " + filePath, e);
        }
        return null;
    }

    /**
     * 验证Tombstone是否有效
     */
    private boolean isValidTombstone(AArch64Tombstone tombstone) {
        if (tombstone == null) {
            return false;
        }
        // 至少需要有PID或Signal信息才算有效
        return tombstone.getPid() != null || tombstone.getSignalInfo() != null;
    }

    /**
     * 查找ZIP中可能的Tombstone文件
     */
    private List<ZipFileParser.ZipEntryInfo> findPotentialTombstonesInZip(Path zipPath) {
        try {
            return ZipFileParser.findPotentialTombstoneFiles(createMultipartFileFromPath(zipPath));
        } catch (Exception e) {
            logger.error("读取ZIP文件失败: {}", zipPath, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将Path转换为MultipartFile（用于使用现有的ZipFileParser）
     */
    private MultipartFile createMultipartFileFromPath(Path filePath) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return filePath.getFileName().toString();
            }

            @Override
            public String getOriginalFilename() {
                return filePath.getFileName().toString();
            }

            @Override
            public String getContentType() {
                try {
                    String probe = Files.probeContentType(filePath);
                    return probe != null ? probe : "application/octet-stream";
                } catch (IOException e) {
                    return "application/octet-stream";
                }
            }

            @Override
            public boolean isEmpty() {
                try {
                    return Files.size(filePath) == 0;
                } catch (IOException e) {
                    return true;
                }
            }

            @Override
            public long getSize() {
                try {
                    return Files.size(filePath);
                } catch (IOException e) {
                    return 0;
                }
            }

            @Override
            public byte[] getBytes() {
                try {
                    return Files.readAllBytes(filePath);
                } catch (IOException e) {
                    return new byte[0];
                }
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return Files.newInputStream(filePath);
            }

            @Override
            public void transferTo(java.io.File dest) {
                try {
                    Files.copy(filePath, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to transfer file", e);
                }
            }
        };
    }

    /**
     * 获取文件大小
     */
    private long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 将ZIP条目解析为Tombstone
     */
    private AArch64Tombstone parseZipEntryAsTombstone(ZipFileParser.ZipEntryInfo entry) {
        if (entry == null || entry.getContent() == null) {
            return null;
        }

        try {
            List<String> lines = new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(entry.getContent())))
                    .lines()
                    .collect(Collectors.toList());

            TroubleEntity entity = fileService.parseFile(lines);
            if (entity instanceof AArch64Tombstone) {
                return (AArch64Tombstone) entity;
            }
        } catch (Exception e) {
            logger.error("解析ZIP条目为Tombstone失败: " + entry.getName(), e);
        }
        return null;
    }

    /**
     * 使用AI分析崩溃原因
     *
     * @param sessionId 会话ID
     * @param originalQuestion 原始问题
     * @param parsedQuestion 解析后的问题
     * @param crashInfo 崩溃信息
     * @param tombstone Tombstone数据
     * @param patternMatchResult 模式匹配结果
     * @return AI分析结果
     */
    private String analyzeCrashWithAI(String sessionId, String originalQuestion, String parsedQuestion,
                                       CrashInfo crashInfo, AArch64Tombstone tombstone,
                                       PatternMatchResult patternMatchResult) {
        try {
            AnalysisData analysisData = new AnalysisData();

            if (patternMatchResult != null) {
                analysisData.patternMatch = new PatternMatchData(
                    patternMatchResult.getConfidence(),
                    patternMatchResult.getResult(),
                    patternMatchResult.getAiPrompt()
                );
            }

            analysisData.userQuestion = parsedQuestion != null && !parsedQuestion.isEmpty()
                ? parsedQuestion : originalQuestion;

            if (crashInfo != null && crashInfo.isHasCrashInfo()) {
                analysisData.crashInfo = new CrashInfoData(
                    crashInfo.getCrashType(),
                    crashInfo.getDescription(),
                    crashInfo.getErrorMessage(),
                    crashInfo.getRelatedLibrary()
                );
            }

            if (tombstone != null) {
                TombstoneData tombstoneData = new TombstoneData();
                tombstoneData.pid = tombstone.getPid();
                tombstoneData.tid = tombstone.getFirstTid();
                tombstoneData.processName = tombstone.getProcessName();

                if (tombstone.getSignalInfo() != null) {
                    tombstoneData.signal = new SignalData(
                        tombstone.getSignalInfo().getSigNumber(),
                        tombstone.getSignalInfo().getSigInformation()
                    );
                }

                if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
                    tombstoneData.stackFrames = tombstone.getStackDumpInfo().getStackFrames().stream()
                        .limit(10)
                        .map(frame -> new StackFrameData(
                            frame.getIndex(),
                            frame.getSymbol(),
                            frame.getOffsetFromSymbolStart(),
                            frame.getMapsInfo()
                        ))
                        .collect(Collectors.toList());
                }

                analysisData.tombstone = tombstoneData;
            }

            String jsonData = objectMapper.writeValueAsString(analysisData);

            String prompt = String.format("""
                你是一个Android系统崩溃分析专家。请分析以下JSON格式的崩溃数据，并提供专业的原因分析和解决方案。

                崩溃数据：
                %s

                请按照以下JSON格式返回分析结果：
                {
                  "rootCause": "崩溃的直接原因",
                  "triggers": ["可能的触发场景1", "可能的触发场景2"],
                  "solutions": ["解决方案1", "解决方案2"],
                  "prevention": ["预防措施1", "预防措施2"]
                }

                请用专业但易懂的语言回答，返回纯JSON格式，不要包含其他说明文字。
                """, jsonData);

            String response = chatClient.prompt()
                    .system("你是Android崩溃分析专家，请始终返回纯JSON格式的分析结果，不要包含任何其他文字说明。")
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            logger.warn("[sessionId={}] AI分析失败: {}", sessionId, e.getMessage());
        }

        return "AI分析暂时不可用，请稍后重试。";
    }

    static class AnalysisData {
        public PatternMatchData patternMatch;
        public String userQuestion;
        public CrashInfoData crashInfo;
        public TombstoneData tombstone;
    }

    static class PatternMatchData {
        public double confidence;
        public String result;
        public String detail;

        public PatternMatchData(double confidence, String result, String detail) {
            this.confidence = confidence;
            this.result = result;
            this.detail = detail;
        }
    }

    static class CrashInfoData {
        public String crashType;
        public String description;
        public String errorMessage;
        public String relatedLibrary;

        public CrashInfoData(String crashType, String description, String errorMessage, String relatedLibrary) {
            this.crashType = crashType;
            this.description = description;
            this.errorMessage = errorMessage;
            this.relatedLibrary = relatedLibrary;
        }
    }

    static class TombstoneData {
        public Integer pid;
        public Integer tid;
        public String processName;
        public SignalData signal;
        public List<StackFrameData> stackFrames;
    }

    static class SignalData {
        public int number;
        public String name;

        public SignalData(int number, String name) {
            this.number = number;
            this.name = name;
        }
    }

    static class StackFrameData {
        public int index;
        public String symbol;
        public Long offset;
        public String library;

        public StackFrameData(int index, String symbol, Long offset, String library) {
            this.index = index;
            this.symbol = symbol;
            this.offset = offset;
            this.library = library;
        }
    }
}
