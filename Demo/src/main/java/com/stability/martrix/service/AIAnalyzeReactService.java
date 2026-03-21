package com.stability.martrix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stability.martrix.constants.ErrorCode;
import com.stability.martrix.dto.AIAnalysisResponse;
import com.stability.martrix.dto.AIReactResponse;
import com.stability.martrix.dto.CodeLocation;
import com.stability.martrix.dto.CrashAnalysisResult;
import com.stability.martrix.dto.CrashInfo;
import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.dto.ProgrammingAdvice;
import com.stability.martrix.dto.RootCauseInsight;
import com.stability.martrix.dto.SessionContext;
import com.stability.martrix.dto.SessionToolResult;
import com.stability.martrix.entity.AArch64Tombstone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 首轮分析版ReAct服务
 * 使用与 /ai/analyze 相同的请求规格，但将工具边界重写为：
 * 文件解析 -> 模式匹配 -> 源码定位 -> 根因指向 -> 编程指导。
 */
@Service
public class AIAnalyzeReactService {

    private static final Logger logger = LoggerFactory.getLogger(AIAnalyzeReactService.class);
    private static final int MAX_STEPS = 6;
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final long LOCK_EXPIRE_SECONDS = 60L;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final DistributedLockService distributedLockService;
    private final SessionAnalysisToolService sessionAnalysisToolService;
    private final AIFileAnalysisService aiFileAnalysisService;

    public AIAnalyzeReactService(ChatClient.Builder chatClientBuilder,
                                 ObjectMapper objectMapper,
                                 SessionService sessionService,
                                 DistributedLockService distributedLockService,
                                 SessionAnalysisToolService sessionAnalysisToolService,
                                 AIFileAnalysisService aiFileAnalysisService) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.distributedLockService = distributedLockService;
        this.sessionAnalysisToolService = sessionAnalysisToolService;
        this.aiFileAnalysisService = aiFileAnalysisService;
    }

    public AIAnalysisResponse analyzeRequest(String question, String sessionId, MultipartFile[] files) {
        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setSessionId(sessionId);

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return AIAnalysisResponse.fail(ErrorCode.SESSION_ID_EMPTY, "sessionId不能为空");
        }

        // 同一session的分析请求必须串行执行，避免多个上下文同时覆盖Redis里的会话状态。
        String lockKey = "ai:analyze:react:" + sessionId;
        String lockValue = distributedLockService.tryLock(lockKey, LOCK_EXPIRE_SECONDS);
        if (lockValue == null) {
            return AIAnalysisResponse.fail(ErrorCode.SESSION_LOCKED, "当前会话正在被其他请求处理，请稍后重试");
        }

        try {
            SessionContext sessionContext = sessionService.getSession(sessionId);
            if (sessionContext == null) {
                return AIAnalysisResponse.fail(ErrorCode.SESSION_NOT_FOUND, "会话不存在，请先创建会话");
            }

            // 先把当前请求本身写入session，让后续ReAct决策看到的是最新上下文。
            if (question != null && !question.trim().isEmpty()) {
                sessionContext.addQuestion(question);
                sessionContext.addChatMessage("user", question);
                persistContext(sessionContext);
            }

            if (files != null && files.length > 0) {
                sessionAnalysisToolService.attachUploadedFiles(sessionId, files, sessionContext);
                persistContext(sessionContext);
            }

            String parsedQuestion = null;
            if (question != null && !question.trim().isEmpty()) {
                parsedQuestion = aiFileAnalysisService.parseQuestion(question);
                response.setParsedQuestion(parsedQuestion);
                if (parsedQuestion != null && !parsedQuestion.trim().isEmpty()) {
                    sessionContext.addParsedQuestion(parsedQuestion);
                }
                persistContext(sessionContext);
            }

            // ReAct只负责编排工具顺序，最终响应由结构化工具产物统一组装。
            List<AIReactResponse.ReactStep> steps = new ArrayList<>();
            List<String> scratchpad = new ArrayList<>();
            String finalThought = "达到ReAct步数上限，开始组装最终分析结果";

            for (int stepIndex = 1; stepIndex <= MAX_STEPS; stepIndex++) {
                ReactDecision decision = decideNextAction(sessionContext, parsedQuestion, scratchpad);
                AIReactResponse.ReactStep step = new AIReactResponse.ReactStep();
                step.setStep(stepIndex);
                step.setThought(decision.thought);
                step.setAction(decision.action);
                step.setActionInput("");

                if ("final_answer".equals(decision.action)) {
                    step.setObservation("已具备生成最终分析所需上下文");
                    steps.add(step);
                    finalThought = safeText(decision.thought, finalThought);
                    break;
                }

                SessionToolResult<?> toolResult = executeTool(decision.action, sessionContext);
                String observation = safeText(toolResult.getObservation(), "工具执行完成。");
                step.setObservation(observation);
                steps.add(step);

                scratchpad.add("Thought: " + safeText(decision.thought, ""));
                scratchpad.add("Action: " + decision.action);
                scratchpad.add("Observation: " + observation);

                // 每执行完一步工具，都立即把中间态写回Redis，保证流程可恢复且可追溯。
                sessionContext.addChatMessage("tool", decision.action + ": " + observation);
                persistContext(sessionContext);
            }

            // 如果模型提前收束但没有得到最终可交付产物，则自动补齐最小工具链。
//            finalThought = ensureFinalArtifacts(sessionContext, steps, finalThought);

            if (sessionContext.getTombstone() == null && question != null && !question.trim().isEmpty()) {
                CrashInfo crashInfo = aiFileAnalysisService.extractCrashInfo(question);
                sessionContext.setCrashInfo(crashInfo);
                persistContext(sessionContext);
            }

            fillResponseFromSession(response, sessionContext);
            response.setReactSteps(steps);
            response.setReactFinalThought(finalThought);

            boolean success = sessionContext.getTombstone() != null
                || (sessionContext.getCrashInfo() != null && sessionContext.getCrashInfo().isHasCrashInfo())
                || sessionContext.getProgrammingAdvice() != null
                || parsedQuestion != null;
            sessionContext.setSuccess(success);
            response.setSuccess(success);

            String aiAnalysis = buildFinalAiAnalysis(response, sessionContext);
            response.setAiAnalysis(aiAnalysis);
            if (response.getCrashAnalysisResult() == null && sessionContext.getRootCauseInsight() != null) {
                response.setCrashAnalysisResult(toCrashAnalysisResult(sessionContext.getRootCauseInsight()));
            }

            if (aiAnalysis != null && !aiAnalysis.isBlank()) {
                sessionContext.addChatMessage("assistant", aiAnalysis);
            }

            persistContext(sessionContext);
            return response;
        } catch (IllegalStateException e) {
            logger.error("[sessionId={}] ReAct分析上下文持久化失败: {}", sessionId, e.getMessage(), e);
            return AIAnalysisResponse.fail(ErrorCode.SESSION_UPDATE_FAILED, e.getMessage());
        } catch (Exception e) {
            logger.error("[sessionId={}] ReAct分析失败: {}", sessionId, e.getMessage(), e);
            return AIAnalysisResponse.fail(ErrorCode.AI_ANALYSIS_FAILED, "ReAct分析失败: " + e.getMessage());
        } finally {
            distributedLockService.releaseLock(lockKey, lockValue);
        }
    }

    private void fillResponseFromSession(AIAnalysisResponse response, SessionContext sessionContext) {
        AArch64Tombstone tombstone = sessionContext.getTombstone();
        PatternMatchResult patternMatchResult = sessionContext.getPatternMatchResult();
        CodeLocation topCodeLocation = sessionContext.getTopCodeLocation();
        RootCauseInsight rootCauseInsight = sessionContext.getRootCauseInsight();
        ProgrammingAdvice programmingAdvice = sessionContext.getProgrammingAdvice();
        CrashInfo crashInfo = sessionContext.getCrashInfo();

        response.setTombstone(tombstone);
        response.setPatternMatchResult(patternMatchResult);
        response.setTopCodeLocation(topCodeLocation);
        response.setRootCauseInsight(rootCauseInsight);
        response.setProgrammingAdvice(programmingAdvice);
        response.setCrashInfo(tombstone != null ? null : crashInfo);
        response.setProcessLogs(copyLogs(sessionContext.getProcessLogs()));
    }

    private String buildFinalAiAnalysis(AIAnalysisResponse response, SessionContext sessionContext) {
        if (sessionContext.getProgrammingAdvice() != null) {
            return serialize(sessionContext.getProgrammingAdvice());
        }

        if (sessionContext.getRootCauseInsight() != null) {
            CrashAnalysisResult crashAnalysisResult = toCrashAnalysisResult(sessionContext.getRootCauseInsight());
            response.setCrashAnalysisResult(crashAnalysisResult);
            return serialize(crashAnalysisResult);
        }

        if (sessionContext.getCrashInfo() != null && sessionContext.getCrashInfo().isHasCrashInfo()) {
            CrashAnalysisResult fallback = buildFallbackCrashAnalysis(sessionContext.getCrashInfo());
            response.setCrashAnalysisResult(fallback);
            return serialize(fallback);
        }

        return "当前会话中还没有足够的崩溃或代码上下文，建议补充日志文件后继续分析。";
    }

    private String ensureFinalArtifacts(SessionContext sessionContext,
                                        List<AIReactResponse.ReactStep> steps,
                                        String finalThought) {
        int nextStep = steps.size() + 1;

        if (sessionContext.getTombstone() != null) {
            if (sessionContext.getPatternMatchResult() == null) {
                nextStep = appendAutomaticStep(
                    sessionContext,
                    steps,
                    nextStep,
                    "自动补齐高可信模式匹配",
                    SessionAnalysisToolService.TOOL_MATCH_PATTERN,
                    sessionAnalysisToolService.matchTombstonePattern(sessionContext)
                );
            }

            if (sessionContext.getPatternMatchResult() == null && sessionContext.getTopCodeLocation() == null) {
                nextStep = appendAutomaticStep(
                    sessionContext,
                    steps,
                    nextStep,
                    "自动补齐源码定位",
                    SessionAnalysisToolService.TOOL_LOCATE_SOURCE,
                    sessionAnalysisToolService.locateSourceCode(sessionContext)
                );
            }

            if (sessionContext.getRootCauseInsight() == null
                && (sessionContext.getPatternMatchResult() != null || sessionContext.getTopCodeLocation() != null)) {
                nextStep = appendAutomaticStep(
                    sessionContext,
                    steps,
                    nextStep,
                    "自动补齐根因指向",
                    SessionAnalysisToolService.TOOL_ROOT_CAUSE,
                    sessionAnalysisToolService.inferRootCause(sessionContext)
                );
            }
        }

        if (sessionAnalysisToolService.isProgrammingQuestion(sessionContext) && sessionContext.getProgrammingAdvice() == null) {
            appendAutomaticStep(
                sessionContext,
                steps,
                nextStep,
                "自动补齐编程指导",
                SessionAnalysisToolService.TOOL_PROGRAMMING_GUIDE,
                sessionAnalysisToolService.provideProgrammingGuidance(sessionContext)
            );
            return "ReAct结束后自动补齐了编程指导结果";
        }

        if (sessionContext.getRootCauseInsight() != null) {
            return "ReAct结束后已具备根因指向结果";
        }
        return finalThought;
    }

    private int appendAutomaticStep(SessionContext sessionContext,
                                    List<AIReactResponse.ReactStep> steps,
                                    int stepNumber,
                                    String thought,
                                    String action,
                                    SessionToolResult<?> toolResult) {
        AIReactResponse.ReactStep step = new AIReactResponse.ReactStep();
        step.setStep(stepNumber);
        step.setThought(thought);
        step.setAction(action);
        step.setActionInput("");
        step.setObservation(safeText(toolResult.getObservation(), "工具执行完成。"));
        steps.add(step);
        sessionContext.addChatMessage("tool", action + ": " + safeText(toolResult.getObservation(), "工具执行完成。"));
        persistContext(sessionContext);
        return stepNumber + 1;
    }

    private void persistContext(SessionContext sessionContext) {
        boolean updated = sessionService.updateSessionContext(sessionContext.getSessionId(), sessionContext);
        if (!updated) {
            throw new IllegalStateException("会话上下文写入Redis失败");
        }
    }

    private CrashAnalysisResult buildFallbackCrashAnalysis(CrashInfo crashInfo) {
        CrashAnalysisResult result = new CrashAnalysisResult();
        result.setRootCause("用户问题中提到了 " + safeText(crashInfo.getCrashType(), "异常") + "，但当前缺少可精确定位的tombstone上下文。");
        result.setTriggers(List.of(safeText(crashInfo.getDescription(), "请结合触发路径和现场日志继续确认触发条件")));
        result.setSolutions(List.of(
            "补充完整 tombstone 或崩溃日志后再做精确分析",
            "优先检查 " + safeText(crashInfo.getRelatedLibrary(), "相关模块") + " 的参数与指针合法性"
        ));
        result.setPrevention(List.of(
            "为关键调用路径增加边界校验和日志",
            "补充稳定复现用例以验证修复"
        ));
        return result;
    }

    private CrashAnalysisResult toCrashAnalysisResult(RootCauseInsight insight) {
        CrashAnalysisResult result = new CrashAnalysisResult();
        String rootCause = safeText(insight.getRootCause(), "暂未生成根因结论");
        if (insight.getSuspectedLibrary() != null && !insight.getSuspectedLibrary().isBlank()) {
            rootCause = rootCause + "；优先排查: " + insight.getSuspectedLibrary();
        }
        result.setRootCause(rootCause);
        result.setTriggers(copyLogs(insight.getTriggers()));
        result.setSolutions(copyLogs(insight.getSolutions()));
        result.setPrevention(copyLogs(insight.getPrevention()));
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.warn("序列化响应失败: {}", e.getMessage());
            return value == null ? "" : value.toString();
        }
    }

    private ReactDecision decideNextAction(SessionContext sessionContext,
                                           String parsedQuestion,
                                           List<String> scratchpad) {
        String raw = null;
        try {
            // 提示词带上完整session摘要、最近消息和已有观察，让模型只做“下一步工具选择”。
            String prompt = """
                你是一个用于首轮崩溃分析的ReAct代理。
                你要在以下工具中选择下一步动作，直到具备交付最终答案的条件。

                可用动作：
                1. %s：当session里有文件但还没有tombstone时，先做文件解析
                2. %s：当已有tombstone但还没有高可信模式匹配结果时，做模式匹配
                3. %s：仅当没有高可信模式匹配结果时，才做源码定位
                4. %s：当已有tombstone，并且已有模式匹配或源码定位结果时，生成根因和排查so指向
                5. %s：仅当用户是在问“代码怎么写/怎么改/怎么修”时，生成编程指导
                6. final_answer：当前上下文已足够，可以结束

                规则：
                - 每一轮只能选择一个动作
                - 如果没有文件且没有tombstone，不要调用文件解析之外的崩溃工具
                - 如果已有高可信模式匹配结果，不要再做源码定位
                - 如果用户不是在问编程实现，不要调用编程指导工具
                - 如果要给出崩溃根因，优先调用根因指向工具后再结束
                - 只返回合法JSON，不要输出Markdown
                - JSON格式固定为：
                  {
                    "thought": "简短思考",
                    "action": "动作名"
                  }

                当前问题：
                %s

                session摘要：
                %s

                最近对话：
                %s

                已有观察：
                %s
                """.formatted(
                SessionAnalysisToolService.TOOL_PARSE_FILES,
                SessionAnalysisToolService.TOOL_MATCH_PATTERN,
                SessionAnalysisToolService.TOOL_LOCATE_SOURCE,
                SessionAnalysisToolService.TOOL_ROOT_CAUSE,
                SessionAnalysisToolService.TOOL_PROGRAMMING_GUIDE,
                safeText(parsedQuestion, "无"),
                sessionAnalysisToolService.buildPlannerSummary(sessionContext),
                buildHistory(sessionContext),
                scratchpad.isEmpty() ? "暂无" : String.join("\n", scratchpad)
            );

            raw = chatClient.prompt()
                .system("你是一个严谨的ReAct代理，请只返回合法JSON。")
                .user(prompt)
                .call()
                .content();

            String cleaned = cleanupModelResponse(raw);
            String jsonCandidate = extractJsonObject(cleaned);
            String jsonText = (jsonCandidate != null && !jsonCandidate.isBlank()) ? jsonCandidate : cleaned;
            JsonNode node = objectMapper.readTree(jsonText);

            ReactDecision decision = new ReactDecision();
            decision.thought = readText(node, "thought");
            decision.action = readText(node, "action");

            if (decision.action == null || decision.action.isBlank()) {
                decision.action = "final_answer";
            }
            if (!isSupportedAction(decision.action)) {
                decision.action = "final_answer";
            }
            return decision;
        } catch (Exception e) {
            logger.warn("ReAct决策失败，直接进入最终分析: {}", e.getMessage());
            ReactDecision fallback = new ReactDecision();
            fallback.thought = "模型未稳定输出动作，直接转入最终分析";
            fallback.action = "final_answer";
            return fallback;
        }
    }

    private boolean isSupportedAction(String action) {
        return SessionAnalysisToolService.TOOL_PARSE_FILES.equals(action)
            || SessionAnalysisToolService.TOOL_MATCH_PATTERN.equals(action)
            || SessionAnalysisToolService.TOOL_LOCATE_SOURCE.equals(action)
            || SessionAnalysisToolService.TOOL_ROOT_CAUSE.equals(action)
            || SessionAnalysisToolService.TOOL_PROGRAMMING_GUIDE.equals(action)
            || "final_answer".equals(action);
    }

    private SessionToolResult<?> executeTool(String action, SessionContext sessionContext) {
        return switch (action) {
            case SessionAnalysisToolService.TOOL_PARSE_FILES -> sessionAnalysisToolService.parseFilesToTombstone(sessionContext);
            case SessionAnalysisToolService.TOOL_MATCH_PATTERN -> sessionAnalysisToolService.matchTombstonePattern(sessionContext);
            case SessionAnalysisToolService.TOOL_LOCATE_SOURCE -> sessionAnalysisToolService.locateSourceCode(sessionContext);
            case SessionAnalysisToolService.TOOL_ROOT_CAUSE -> sessionAnalysisToolService.inferRootCause(sessionContext);
            case SessionAnalysisToolService.TOOL_PROGRAMMING_GUIDE -> sessionAnalysisToolService.provideProgrammingGuidance(sessionContext);
            default -> SessionToolResult.fail(action, "未知工具: " + action, null);
        };
    }

    private String buildHistory(SessionContext sessionContext) {
        if (sessionContext.getChatMessages() == null || sessionContext.getChatMessages().isEmpty()) {
            return "暂无历史消息";
        }

        int start = Math.max(0, sessionContext.getChatMessages().size() - MAX_HISTORY_MESSAGES);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < sessionContext.getChatMessages().size(); i++) {
            SessionContext.ChatMessage message = sessionContext.getChatMessages().get(i);
            sb.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        return sb.toString().trim();
    }

    private List<String> copyLogs(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
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

    private String safeText(String text, String defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        return text;
    }

    private static class ReactDecision {
        private String thought;
        private String action;
    }
}
