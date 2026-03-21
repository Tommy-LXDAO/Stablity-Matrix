package com.stability.martrix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stability.martrix.dto.*;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.service.parser.FileParserFactory;
import com.stability.martrix.util.FileTypeDetector;
import com.stability.martrix.util.ZipFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于SessionContext执行的首轮分析工具集合
 */
@Service
public class SessionAnalysisToolService {

    public static final String TOOL_PARSE_FILES = "parse_files_to_tombstone";
    public static final String TOOL_MATCH_PATTERN = "match_tombstone_pattern";
    public static final String TOOL_LOCATE_SOURCE = "locate_source_code";
    public static final String TOOL_ROOT_CAUSE = "infer_root_cause";
    public static final String TOOL_PROGRAMMING_GUIDE = "provide_programming_guidance";

    private static final Logger logger = LoggerFactory.getLogger(SessionAnalysisToolService.class);
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8d;

    private final FileParserFactory fileParserFactory;
    private final SessionFileStorageService sessionFileStorageService;
    private final ArchiveExtractionService archiveExtractionService;
    private final PatternMatchService patternMatchService;
    private final BinaryCodeResolver binaryCodeResolver;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SessionAnalysisToolService(FileParserFactory fileParserFactory,
                                      SessionFileStorageService sessionFileStorageService,
                                      ArchiveExtractionService archiveExtractionService,
                                      PatternMatchService patternMatchService,
                                      BinaryCodeResolver binaryCodeResolver,
                                      ChatClient.Builder chatClientBuilder,
                                      ObjectMapper objectMapper) {
        this.fileParserFactory = fileParserFactory;
        this.sessionFileStorageService = sessionFileStorageService;
        this.archiveExtractionService = archiveExtractionService;
        this.patternMatchService = patternMatchService;
        this.binaryCodeResolver = binaryCodeResolver;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 先把请求里的文件落到session目录，并登记到上下文中。
     */
    public void attachUploadedFiles(String sessionId, MultipartFile[] files, SessionContext sessionContext) {
        if (sessionContext == null || files == null || files.length == 0) {
            return;
        }

        // 新文件会使旧的分析中间态失效，因此先清理派生结果。
        sessionContext.resetAnalysisArtifacts();
        List<String> storedFilePaths = sessionFileStorageService.storeFiles(sessionId, files);
        for (String filePath : storedFilePaths) {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();
            String fileType = detectFileTypeByName(fileName);
            long fileSize = getFileSize(path);
            sessionContext.addFileIfAbsent(fileName, filePath, fileSize, fileType);
        }
    }

    /**
     * 工具1：只负责把session中的文件解析成tombstone。
     */
    public SessionToolResult<FileParseResult> parseFilesToTombstone(SessionContext sessionContext) {
        FileParseResult emptyResult = new FileParseResult();

        if (!hasValidSession(sessionContext)) {
            return SessionToolResult.fail(TOOL_PARSE_FILES, "当前session无效，无法执行文件解析。", emptyResult);
        }

        try {
            List<String> storedFilePaths = getUniqueSessionFilePaths(sessionContext);
            if (storedFilePaths.isEmpty()) {
                emptyResult.setSuccess(false);
                emptyResult.setProcessLogs(List.of("当前session没有可解析的文件。"));
                sessionContext.setProcessLogs(new ArrayList<>(emptyResult.getProcessLogs()));
                sessionContext.setTombstone(null);
                sessionContext.setPatternMatchResult(null);
                sessionContext.setTopCodeLocation(null);
                sessionContext.setRootCauseInsight(null);
                return SessionToolResult.success(TOOL_PARSE_FILES, "当前session没有可解析的文件。", emptyResult);
            }

            List<String> allFilePaths = expandArchiveFiles(sessionContext, storedFilePaths);
            FileParseResult result = processStoredFiles(sessionContext.getSessionId(), allFilePaths);

            sessionContext.setProcessLogs(copyLogs(result.getProcessLogs()));
            sessionContext.setTombstone(result.getTombstone());
            sessionContext.setPatternMatchResult(null);
            sessionContext.setTopCodeLocation(null);
            sessionContext.setRootCauseInsight(null);

            if (result.isSuccess()) {
                sessionContext.setSuccess(true);
            }

            return SessionToolResult.success(TOOL_PARSE_FILES, buildFileParseObservation(result, sessionContext), result);
        } catch (Exception e) {
            logger.error("[sessionId={}] 解析session文件失败: {}", sessionContext.getSessionId(), e.getMessage(), e);
            emptyResult.setSuccess(false);
            emptyResult.setProcessLogs(List.of("文件解析失败: " + e.getMessage()));
            sessionContext.setProcessLogs(copyLogs(emptyResult.getProcessLogs()));
            sessionContext.setTombstone(null);
            return SessionToolResult.fail(TOOL_PARSE_FILES, "文件解析失败: " + e.getMessage(), emptyResult);
        }
    }

    /**
     * 工具2：只负责做模式匹配，高可信度才保留。
     */
    public SessionToolResult<PatternMatchResult> matchTombstonePattern(SessionContext sessionContext) {
        if (!hasValidSession(sessionContext) || sessionContext.getTombstone() == null) {
            return SessionToolResult.fail(TOOL_MATCH_PATTERN, "当前session没有tombstone，无法执行模式匹配。", null);
        }

        PatternMatchResult result = patternMatchService.analyzePattern(sessionContext.getTombstone());
        if (result == null) {
            sessionContext.setPatternMatchResult(null);
            sessionContext.setRootCauseInsight(null);
            return SessionToolResult.success(TOOL_MATCH_PATTERN, "模式匹配未命中任何规则。", null);
        }

        if (result.getConfidence() < HIGH_CONFIDENCE_THRESHOLD) {
            sessionContext.setPatternMatchResult(null);
            sessionContext.setRootCauseInsight(null);
            return SessionToolResult.success(
                    TOOL_MATCH_PATTERN,
                    "模式匹配已执行，但置信度不足以作为高可信结论: confidence=" + result.getConfidence(),
                    null
            );
        }

        sessionContext.setPatternMatchResult(result);
        sessionContext.setRootCauseInsight(null);
        return SessionToolResult.success(TOOL_MATCH_PATTERN, buildPatternObservation(result), result);
    }

    /**
     * 工具3：只有在没有高可信模式匹配时才做源码定位。
     */
    public SessionToolResult<CodeLocation> locateSourceCode(SessionContext sessionContext) {
        if (!hasValidSession(sessionContext) || sessionContext.getTombstone() == null) {
            return SessionToolResult.fail(TOOL_LOCATE_SOURCE, "当前session没有tombstone，无法执行源码定位。", null);
        }

        if (sessionContext.getPatternMatchResult() != null) {
            return SessionToolResult.fail(TOOL_LOCATE_SOURCE, "已有高可信模式匹配结果，无需继续做源码定位。", null);
        }

        try {
            CodeLocation codeLocation = binaryCodeResolver.resolveTopStackFrame(sessionContext.getTombstone());
            if (codeLocation == null) {
                return SessionToolResult.fail(TOOL_LOCATE_SOURCE, "未解析到可用的源码位置。", null);
            }
            sessionContext.setTopCodeLocation(codeLocation);
            sessionContext.setRootCauseInsight(null);
            return SessionToolResult.success(TOOL_LOCATE_SOURCE, buildCodeLocationObservation(codeLocation), codeLocation);
        } catch (Exception e) {
            logger.error("[sessionId={}] 解析栈顶代码位置失败: {}", sessionContext.getSessionId(), e.getMessage(), e);
            sessionContext.setTopCodeLocation(null);
            return SessionToolResult.fail(TOOL_LOCATE_SOURCE, "源码定位失败: " + e.getMessage(), null);
        }
    }

    /**
     * 工具4：基于tombstone和模式/源码定位结果给出根因与排查so指向。
     */
    public SessionToolResult<RootCauseInsight> inferRootCause(SessionContext sessionContext) {
        if (!hasValidSession(sessionContext) || sessionContext.getTombstone() == null) {
            return SessionToolResult.fail(TOOL_ROOT_CAUSE, "当前session没有tombstone，无法推断根因。", null);
        }

        if (sessionContext.getPatternMatchResult() == null && sessionContext.getTopCodeLocation() == null) {
            return SessionToolResult.fail(TOOL_ROOT_CAUSE, "缺少模式匹配或源码定位结果，暂时无法推断根因。", null);
        }

        try {
            String prompt = """
                    你是一个Native崩溃根因定位助手。
                    请根据以下session上下文，输出“根因”和“优先排查的so/模块”。
                    
                    输出JSON格式：
                    {
                      "rootCause": "一句话描述根因",
                      "suspectedLibrary": "优先排查的so/模块，未知时返回空字符串",
                      "evidenceType": "pattern 或 source 或 hybrid",
                      "reasoning": "简短说明为何得出该结论",
                      "triggers": ["可能触发场景1", "可能触发场景2"],
                      "solutions": ["排查建议1", "排查建议2"],
                      "prevention": ["预防建议1", "预防建议2"]
                    }
                    
                    要求：
                    - 优先利用高可信模式匹配结论
                    - 如果有源码定位结果，请结合 sourceFile / functionName / codeSnippet 给出排查方向
                    - suspectedLibrary 尽量具体到 so 名称或模块名
                    - 只返回合法JSON
                    
                    用户问题：
                    %s
                    
                    tombstone摘要：
                    %s
                    
                    模式匹配：
                    %s
                    
                    源码定位：
                    %s
                    """.formatted(
                    safeText(getLatestQuestion(sessionContext), "无"),
                    buildTombstoneSummary(sessionContext.getTombstone()),
                    buildPatternObservation(sessionContext.getPatternMatchResult()),
                    buildCodeLocationObservation(sessionContext.getTopCodeLocation())
            );

            String raw = chatClient.prompt()
                    .system("你是一个严谨的崩溃根因定位助手，请只返回合法JSON。")
                    .user(prompt)
                    .call()
                    .content();

            RootCauseInsight insight = parseRootCauseInsight(raw);
            if (insight == null) {
                insight = buildRootCauseFallback(sessionContext);
            }

            sessionContext.setRootCauseInsight(insight);
            return SessionToolResult.success(TOOL_ROOT_CAUSE, buildRootCauseObservation(insight), insight);
        } catch (Exception e) {
            logger.error("[sessionId={}] 根因定位失败: {}", sessionContext.getSessionId(), e.getMessage(), e);
            RootCauseInsight fallback = buildRootCauseFallback(sessionContext);
            sessionContext.setRootCauseInsight(fallback);
            return SessionToolResult.fail(TOOL_ROOT_CAUSE, buildRootCauseObservation(fallback), fallback);
        }
    }

    /**
     * 工具5：当用户在问“代码应该怎么写/怎么改”时，提供编程指导。
     */
    public SessionToolResult<ProgrammingAdvice> provideProgrammingGuidance(SessionContext sessionContext) {
        if (!isProgrammingQuestion(sessionContext)) {
            return SessionToolResult.fail(TOOL_PROGRAMMING_GUIDE, "当前问题不是编程指导类问题，无需执行该工具。", null);
        }

        try {
            String prompt = """
                    你是一个Native崩溃修复与编码指导助手。
                    请结合用户问题和当前session上下文，给出针对性的代码修改建议。
                    
                    输出JSON格式：
                    {
                      "topic": "本次指导的主题",
                      "guidance": "总体建议",
                      "steps": ["步骤1", "步骤2"],
                      "exampleSnippet": "如有必要，给出一段简短示例代码；没有则返回空字符串"
                    }
                    
                    要求：
                    - 如果已有根因结论，请围绕该根因给出修复建议
                    - 如果有源码定位和代码片段，请尽量结合具体函数或文件说明
                    - 如果上下文不足，也要明确指出缺什么信息
                    - 只返回合法JSON
                    - 如果用户只是在问某个接口如何调用，某个接口的使用例子，那么只需要回答用户问题即可
                    
                    用户问题：
                    %s
                    
                    根因指向：
                    %s
                    
                    源码定位：
                    %s
                    """.formatted(
                    safeText(getLatestQuestion(sessionContext), "无"),
                    buildRootCauseObservation(sessionContext.getRootCauseInsight()),
                    buildCodeLocationObservation(sessionContext.getTopCodeLocation())
            );

            String raw = chatClient.prompt()
                    .system("你是一个严谨的编程指导助手，请只返回合法JSON。")
                    .user(prompt)
                    .call()
                    .content();

            ProgrammingAdvice advice = parseProgrammingAdvice(raw);
            if (advice == null) {
                advice = buildProgrammingAdviceFallback(sessionContext);
            }

            sessionContext.setProgrammingAdvice(advice);
            return SessionToolResult.success(TOOL_PROGRAMMING_GUIDE, buildProgrammingObservation(advice), advice);
        } catch (Exception e) {
            logger.error("[sessionId={}] 编程指导生成失败: {}", sessionContext.getSessionId(), e.getMessage(), e);
            ProgrammingAdvice fallback = buildProgrammingAdviceFallback(sessionContext);
            sessionContext.setProgrammingAdvice(fallback);
            return SessionToolResult.fail(TOOL_PROGRAMMING_GUIDE, buildProgrammingObservation(fallback), fallback);
        }
    }

    public boolean isProgrammingQuestion(SessionContext sessionContext) {
        String question = getLatestQuestion(sessionContext);
        if (question == null || question.isBlank()) {
            return false;
        }

        String lower = question.toLowerCase();
        return lower.contains("怎么写")
                || lower.contains("如何写")
                || lower.contains("怎么改")
                || lower.contains("如何改")
                || lower.contains("实现")
                || lower.contains("代码")
                || lower.contains("示例")
                || lower.contains("fix")
                || lower.contains("patch")
                || lower.contains("example");
    }

    public String buildPlannerSummary(SessionContext sessionContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("sessionId=").append(sessionContext.getSessionId()).append('\n');
        sb.append("questions=").append(sessionContext.getQuestions() == null ? 0 : sessionContext.getQuestions().size()).append('\n');
        sb.append("files=").append(sessionContext.getFiles() == null ? 0 : sessionContext.getFiles().size()).append('\n');
        sb.append("hasTombstone=").append(sessionContext.getTombstone() != null).append('\n');
        sb.append("hasHighConfidencePattern=").append(sessionContext.getPatternMatchResult() != null).append('\n');
        sb.append("hasSourceLocation=").append(sessionContext.getTopCodeLocation() != null).append('\n');
        sb.append("hasRootCauseInsight=").append(sessionContext.getRootCauseInsight() != null).append('\n');
        sb.append("isProgrammingQuestion=").append(isProgrammingQuestion(sessionContext)).append('\n');

        if (sessionContext.getFiles() != null && !sessionContext.getFiles().isEmpty()) {
            sb.append("uploadedFiles=");
            int limit = Math.min(5, sessionContext.getFiles().size());
            for (int i = 0; i < limit; i++) {
                SessionContext.SessionFile file = sessionContext.getFiles().get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(file.getFileName()).append('(').append(file.getFileType()).append(')');
            }
            sb.append('\n');
        }

        if (sessionContext.getParsedQuestions() != null && !sessionContext.getParsedQuestions().isEmpty()) {
            sb.append("recentParsedQuestion=")
                    .append(sessionContext.getParsedQuestions().get(sessionContext.getParsedQuestions().size() - 1))
                    .append('\n');
        }

        if (sessionContext.getPatternMatchResult() != null) {
            sb.append("patternResult=").append(sessionContext.getPatternMatchResult().getResult()).append('\n');
        }

        if (sessionContext.getTopCodeLocation() != null) {
            sb.append("topSource=").append(safeText(sessionContext.getTopCodeLocation().getSourceFile(), ""))
                    .append(':').append(sessionContext.getTopCodeLocation().getLineNumber()).append('\n');
        }

        return sb.toString().trim();
    }

    private boolean hasValidSession(SessionContext sessionContext) {
        return sessionContext != null
                && sessionContext.getSessionId() != null
                && !sessionContext.getSessionId().isBlank();
    }

    private List<String> expandArchiveFiles(SessionContext sessionContext, List<String> storedFilePaths) {
        List<String> archiveFiles = new ArrayList<>();
        List<String> regularFiles = new ArrayList<>();

        for (String filePath : storedFilePaths) {
            ArchiveExtractionService.ArchiveType archiveType = detectArchiveType(filePath);
            if (archiveType != ArchiveExtractionService.ArchiveType.UNKNOWN) {
                archiveFiles.add(filePath);
            } else {
                regularFiles.add(filePath);
            }
        }

        List<String> allFilePaths = new ArrayList<>(regularFiles);
        if (!archiveFiles.isEmpty()) {
            String sessionPath = sessionFileStorageService.getFileStorageProperties()
                    .getSessionPath(sessionContext.getSessionId());
            List<String> extractedFiles = archiveExtractionService.extractArchives(archiveFiles, sessionPath);
            for (String filePath : extractedFiles) {
                Path path = Paths.get(filePath);
                String fileName = path.getFileName().toString();
                String fileType = detectFileTypeByName(fileName);
                long fileSize = getFileSize(path);
                sessionContext.addFileIfAbsent(fileName, filePath, fileSize, fileType);
            }
            allFilePaths.addAll(extractedFiles);
        }
        return allFilePaths;
    }

    private List<String> getUniqueSessionFilePaths(SessionContext sessionContext) {
        if (sessionContext.getFiles() == null || sessionContext.getFiles().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> uniquePaths = new LinkedHashSet<>();
        for (SessionContext.SessionFile file : sessionContext.getFiles()) {
            if (file == null || file.getFilePath() == null || file.getFilePath().isBlank()) {
                continue;
            }
            uniquePaths.add(file.getFilePath());
        }
        return new ArrayList<>(uniquePaths);
    }

    private String getLatestQuestion(SessionContext sessionContext) {
        if (sessionContext == null) {
            return null;
        }
        if (sessionContext.getParsedQuestions() != null && !sessionContext.getParsedQuestions().isEmpty()) {
            return sessionContext.getParsedQuestions().get(sessionContext.getParsedQuestions().size() - 1);
        }
        if (sessionContext.getQuestions() != null && !sessionContext.getQuestions().isEmpty()) {
            return sessionContext.getQuestions().get(sessionContext.getQuestions().size() - 1);
        }
        return null;
    }

    private String buildFileParseObservation(FileParseResult result, SessionContext sessionContext) {
        if (result == null) {
            return "文件解析未返回结果。";
        }

        StringBuilder observation = new StringBuilder();
        observation.append("processedFiles=")
                .append(sessionContext.getFiles() == null ? 0 : sessionContext.getFiles().size())
                .append('\n');
        observation.append("hasTombstone=").append(result.hasTombstone()).append('\n');

        if (result.getProcessLogs() != null && !result.getProcessLogs().isEmpty()) {
            observation.append("processLogs=\n");
            result.getProcessLogs().stream()
                    .filter(Objects::nonNull)
                    .limit(6)
                    .forEach(log -> observation.append("- ").append(log).append('\n'));
        }

        return observation.toString().trim();
    }

    private String buildPatternObservation(PatternMatchResult result) {
        if (result == null) {
            return "当前没有可用的高可信模式匹配结果。";
        }

        return """
                confidence=%s
                result=%s
                directConclusion=%s
                detail=%s
                """.formatted(
                result.getConfidence(),
                safeText(result.getResult(), ""),
                result.isDirectConclusion(),
                safeText(result.getAiPrompt(), "")
        ).trim();
    }

    private String buildCodeLocationObservation(CodeLocation codeLocation) {
        if (codeLocation == null) {
            return "未解析到可用的源码位置。";
        }

        return """
                sourceFile=%s
                lineNumber=%s
                functionName=%s
                codeSnippet=%s
                """.formatted(
                safeText(codeLocation.getSourceFile(), ""),
                codeLocation.getLineNumber(),
                safeText(codeLocation.getFunctionName(), ""),
                safeText(codeLocation.getCodeSnippet(), "")
        ).trim();
    }

    private String buildRootCauseObservation(RootCauseInsight insight) {
        if (insight == null) {
            return "暂未生成根因指向结果。";
        }

        return """
                rootCause=%s
                suspectedLibrary=%s
                evidenceType=%s
                reasoning=%s
                """.formatted(
                safeText(insight.getRootCause(), ""),
                safeText(insight.getSuspectedLibrary(), ""),
                safeText(insight.getEvidenceType(), ""),
                safeText(insight.getReasoning(), "")
        ).trim();
    }

    private String buildProgrammingObservation(ProgrammingAdvice advice) {
        if (advice == null) {
            return "暂未生成编程指导。";
        }

        return """
                topic=%s
                guidance=%s
                hasExampleSnippet=%s
                """.formatted(
                safeText(advice.getTopic(), ""),
                safeText(advice.getGuidance(), ""),
                advice.getExampleSnippet() != null && !advice.getExampleSnippet().isBlank()
        ).trim();
    }

    private String buildTombstoneSummary(AArch64Tombstone tombstone) {
        if (tombstone == null) {
            return "暂无tombstone。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("process=").append(safeText(tombstone.getProcessName(), "")).append('\n');
        sb.append("pid=").append(tombstone.getPid()).append(", tid=").append(tombstone.getFirstTid()).append('\n');

        if (tombstone.getSignalInfo() != null) {
            sb.append("signal=").append(tombstone.getSignalInfo().getSigInformation())
                    .append('(').append(tombstone.getSignalInfo().getSigNumber()).append(')').append('\n');
            sb.append("trouble=").append(tombstone.getSignalInfo().getTroubleInformation()).append('\n');
        }

        if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
            sb.append("topFrames:\n");
            tombstone.getStackDumpInfo().getStackFrames().stream()
                    .limit(5)
                    .forEach(frame -> sb.append('#')
                            .append(frame.getIndex())
                            .append(' ')
                            .append(safeText(frame.getSymbol(), "<no-symbol>"))
                            .append(" @ ")
                            .append(safeText(frame.getMapsInfo(), ""))
                            .append('\n'));
        }

        return sb.toString().trim();
    }

    private RootCauseInsight parseRootCauseInsight(String raw) {
        try {
            String cleaned = cleanupModelResponse(raw);
            String jsonCandidate = extractJsonObject(cleaned);
            String jsonText = (jsonCandidate != null && !jsonCandidate.isBlank()) ? jsonCandidate : cleaned;
            JsonNode node = objectMapper.readTree(jsonText);

            RootCauseInsight insight = new RootCauseInsight();
            insight.setRootCause(readText(node, "rootCause"));
            insight.setSuspectedLibrary(readText(node, "suspectedLibrary"));
            insight.setEvidenceType(readText(node, "evidenceType"));
            insight.setReasoning(readText(node, "reasoning"));
            insight.setTriggers(readStringList(node, "triggers"));
            insight.setSolutions(readStringList(node, "solutions"));
            insight.setPrevention(readStringList(node, "prevention"));
            return insight;
        } catch (Exception e) {
            logger.warn("解析根因指向结果失败: {}", e.getMessage());
            return null;
        }
    }

    private ProgrammingAdvice parseProgrammingAdvice(String raw) {
        try {
            String cleaned = cleanupModelResponse(raw);
            String jsonCandidate = extractJsonObject(cleaned);
            String jsonText = (jsonCandidate != null && !jsonCandidate.isBlank()) ? jsonCandidate : cleaned;
            JsonNode node = objectMapper.readTree(jsonText);

            ProgrammingAdvice advice = new ProgrammingAdvice();
            advice.setTopic(readText(node, "topic"));
            advice.setGuidance(readText(node, "guidance"));
            advice.setSteps(readStringList(node, "steps"));
            advice.setExampleSnippet(readText(node, "exampleSnippet"));
            return advice;
        } catch (Exception e) {
            logger.warn("解析编程指导结果失败: {}", e.getMessage());
            return null;
        }
    }

    private RootCauseInsight buildRootCauseFallback(SessionContext sessionContext) {
        RootCauseInsight insight = new RootCauseInsight();
        PatternMatchResult pattern = sessionContext.getPatternMatchResult();
        CodeLocation codeLocation = sessionContext.getTopCodeLocation();

        if (pattern != null) {
            insight.setRootCause(safeText(pattern.getResult(), "检测到高可信模式匹配异常。"));
            insight.setEvidenceType("pattern");
            insight.setReasoning(safeText(pattern.getAiPrompt(), "模式匹配给出了较高置信度结论。"));
        } else if (codeLocation != null) {
            insight.setRootCause("崩溃可能出现在 " + safeText(codeLocation.getFunctionName(), "目标函数") + " 附近。");
            insight.setEvidenceType("source");
            insight.setReasoning("源码定位命中了 " + safeText(codeLocation.getSourceFile(), "未知文件")
                    + ":" + codeLocation.getLineNumber());
        } else {
            insight.setRootCause("当前上下文不足，暂时无法准确给出根因。");
            insight.setEvidenceType("unknown");
            insight.setReasoning("缺少高可信模式匹配和源码定位结果。");
        }

        insight.setSuspectedLibrary(extractSuspectedLibrary(sessionContext));
        insight.setTriggers(List.of("结合最近一次崩溃栈和对应模块继续排查"));
        insight.setSolutions(List.of("优先检查 " + safeText(insight.getSuspectedLibrary(), "目标模块") + " 的内存访问与参数合法性"));
        insight.setPrevention(List.of("为关键指针和边界条件增加保护与日志"));
        return insight;
    }

    private ProgrammingAdvice buildProgrammingAdviceFallback(SessionContext sessionContext) {
        ProgrammingAdvice advice = new ProgrammingAdvice();
        advice.setTopic("崩溃修复编程建议");

        RootCauseInsight insight = sessionContext.getRootCauseInsight();
        CodeLocation codeLocation = sessionContext.getTopCodeLocation();

        if (insight != null) {
            advice.setGuidance("先围绕根因结论修改代码，再补充保护性校验和日志。");
            advice.setSteps(List.of(
                    "优先检查 " + safeText(insight.getSuspectedLibrary(), "相关模块") + " 中与崩溃路径对应的参数和指针",
                    "在关键调用前增加空指针、越界和生命周期校验",
                    "为修复点补充可复现日志和单元/集成验证"
            ));
        } else {
            advice.setGuidance("当前上下文不足，建议先补齐源码定位或模式匹配结果后再改代码。");
            advice.setSteps(List.of(
                    "先确认崩溃发生的函数和模块",
                    "再针对该函数补充边界条件校验",
                    "最后通过日志和测试验证修复是否生效"
            ));
        }

        if (codeLocation != null && codeLocation.getCodeSnippet() != null && !codeLocation.getCodeSnippet().isBlank()) {
            advice.setExampleSnippet(codeLocation.getCodeSnippet());
        } else {
            advice.setExampleSnippet("");
        }
        return advice;
    }

    private String extractSuspectedLibrary(SessionContext sessionContext) {
        if (sessionContext.getTopCodeLocation() != null && sessionContext.getTopCodeLocation().getSourceFile() != null) {
            return sessionContext.getTopCodeLocation().getSourceFile();
        }

        AArch64Tombstone tombstone = sessionContext.getTombstone();
        if (tombstone != null && tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null
                && !tombstone.getStackDumpInfo().getStackFrames().isEmpty()) {
            return safeText(tombstone.getStackDumpInfo().getStackFrames().getFirst().getMapsInfo(), "");
        }
        return "";
    }

    private FileParseResult processStoredFiles(String sessionId, List<String> filePaths) {
        FileParseResult result = new FileParseResult();
        List<String> processLogs = new ArrayList<>();
        AArch64Tombstone tombstone = null;
        boolean fileReadSucceeded = false;

        for (String filePath : filePaths) {
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    logger.warn("[sessionId={}] 文件不存在，跳过: {}", sessionId, path);
                    processLogs.add("文件读取失败: %s，不存在或不是普通文件".formatted(path.getFileName()));
                    continue;
                }

                String fileName = path.getFileName().toString();
                logger.info("[sessionId={}] 处理文件: {}", sessionId, fileName);

                FileTypeDetector.FileType fileType;
                try {
                    fileType = detectFileTypeByPath(path);
                } catch (IOException e) {
                    logger.warn("[sessionId={}] 检测文件类型失败: file={}, error={}", sessionId, fileName, e.getMessage());
                    processLogs.add("文件读取失败: %s，检测文件类型失败: %s".formatted(fileName, e.getMessage()));
                    continue;
                }

                switch (fileType) {
                    case TXT:
                        List<String> lines;
                        try {
                            lines = fileParserFactory.readFileLines(path);
                            fileReadSucceeded = true;
                        } catch (IOException e) {
                            logger.warn("[sessionId={}] 读取文本文件失败: file={}, error={}",
                                    sessionId, fileName, e.getMessage());
                            processLogs.add("文件读取失败: %s，%s".formatted(fileName, e.getMessage()));
                            continue;
                        }

                        TroubleEntity entity = fileParserFactory.parseLines(lines);
                        if (entity instanceof AArch64Tombstone parsedTombstone && isValidTombstone(parsedTombstone)) {
                            tombstone = parsedTombstone;
                            processLogs.add("文件解析成功: %s".formatted(fileName));
                        } else {
                            processLogs.add("文件解析失败: %s，未识别为有效崩溃日志".formatted(fileName));
                        }
                        break;

                    case ZIP:
                        FileParseResult zipResult = handleZipContent(sessionId, path);
                        mergeProcessLogs(processLogs, zipResult.getProcessLogs());
                        fileReadSucceeded = fileReadSucceeded || zipResult.isSuccess();
                        if (zipResult.getTombstone() != null && tombstone == null) {
                            tombstone = zipResult.getTombstone();
                        }
                        break;

                    default:
                        processLogs.add("跳过不支持的文件类型: %s (%s)".formatted(fileName, fileType));
                        break;
                }
            } catch (Exception e) {
                logger.error("[sessionId={}] 处理文件失败: path={}, error={}", sessionId, filePath, e.getMessage(), e);
                processLogs.add("文件处理失败: %s，%s".formatted(filePath, e.getMessage()));
            }
        }

        result.setTombstone(tombstone);
        result.setProcessLogs(processLogs);
        result.setSuccess(tombstone != null || fileReadSucceeded);
        return result;
    }

    private FileParseResult handleZipContent(String sessionId, Path zipPath) {
        FileParseResult result = new FileParseResult();
        List<String> processLogs = new ArrayList<>();
        AArch64Tombstone tombstone = null;
        boolean zipReadSucceeded = false;

        try {
            List<ZipFileParser.ZipEntryInfo> potentialTombstones = findPotentialTombstonesInZip(zipPath);
            zipReadSucceeded = true;
            if (potentialTombstones.isEmpty()) {
                processLogs.add("ZIP读取成功: %s，未找到候选崩溃文件".formatted(zipPath.getFileName()));
            }

            for (ZipFileParser.ZipEntryInfo entry : potentialTombstones) {
                AArch64Tombstone parsedTombstone = parseZipEntryAsTombstone(entry);
                if (parsedTombstone != null && isValidTombstone(parsedTombstone)) {
                    tombstone = parsedTombstone;
                    processLogs.add("ZIP条目解析成功: %s".formatted(entry.getName()));
                    break;
                } else {
                    processLogs.add("ZIP条目解析失败: %s，未识别为有效崩溃日志".formatted(entry.getName()));
                }
            }
        } catch (IOException e) {
            processLogs.add("ZIP读取失败: %s，%s".formatted(zipPath.getFileName(), e.getMessage()));
            logger.error("[sessionId={}] 读取ZIP文件失败: path={}, error={}", sessionId, zipPath, e.getMessage(), e);
        } catch (Exception e) {
            processLogs.add("ZIP处理失败: %s，%s".formatted(zipPath.getFileName(), e.getMessage()));
            logger.error("[sessionId={}] 分析ZIP文件内容失败: path={}, error={}", sessionId, zipPath, e.getMessage(), e);
        }

        result.setTombstone(tombstone);
        result.setProcessLogs(processLogs);
        result.setSuccess(tombstone != null || zipReadSucceeded);
        return result;
    }

    private FileTypeDetector.FileType detectFileTypeByPath(Path path) throws IOException {
        return FileTypeDetector.detectFileType(path);
    }

    private void mergeProcessLogs(List<String> target, List<String> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }

    private ArchiveExtractionService.ArchiveType detectArchiveType(String fileName) {
        return ArchiveExtractionService.detectArchiveType(fileName);
    }

    private boolean isValidTombstone(AArch64Tombstone tombstone) {
        if (tombstone == null) {
            return false;
        }
        return tombstone.getPid() != null || tombstone.getSignalInfo() != null;
    }

    private List<ZipFileParser.ZipEntryInfo> findPotentialTombstonesInZip(Path zipPath) throws IOException {
        return ZipFileParser.findPotentialTombstoneFiles(createMultipartFileFromPath(zipPath));
    }

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

    private long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            return 0;
        }
    }

    private AArch64Tombstone parseZipEntryAsTombstone(ZipFileParser.ZipEntryInfo entry) {
        if (entry == null || entry.getContent() == null) {
            return null;
        }

        try {
            List<String> lines = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(entry.getContent())))
                    .lines()
                    .collect(Collectors.toList());

            TroubleEntity entity = fileParserFactory.parseLines(lines);
            if (entity instanceof AArch64Tombstone tombstone) {
                return tombstone;
            }
        } catch (Exception e) {
            logger.error("解析ZIP条目为Tombstone失败: {}", entry.getName(), e);
        }
        return null;
    }

    private List<String> copyLogs(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

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

    private String cleanupModelResponse(String raw) {
        if (raw == null) {
            return "{}";
        }
        return raw.trim()
                .replaceAll("(?si)<think>.*?</think>", "")
                .replaceAll("(?si)<thinking>.*?</thinking>", "")
                .replaceAll("(?si)<thought>.*?</thought>", "")
                .replaceAll("(?si)<reasoning>.*?</reasoning>", "")
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1).trim();
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return "";
        }
        return field.asText();
    }

    private List<String> readStringList(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isArray()) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        field.forEach(item -> {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private String safeText(String text, String defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        return text;
    }
}
