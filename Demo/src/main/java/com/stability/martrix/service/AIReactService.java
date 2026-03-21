package com.stability.martrix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stability.martrix.constants.ErrorCode;
import com.stability.martrix.dto.AIReactResponse;
import com.stability.martrix.dto.CodeLocation;
import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.dto.SessionContext;
import com.stability.martrix.entity.AArch64Tombstone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于ReAct模式的多轮对话服务
 */
@Service
public class AIReactService {

    private static final Logger logger = LoggerFactory.getLogger(AIReactService.class);
    private static final int MAX_STEPS = 4;
    private static final int MAX_HISTORY_MESSAGES = 8;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final PatternMatchService patternMatchService;
    private final BinaryCodeResolver binaryCodeResolver;

    public AIReactService(ChatClient.Builder chatClientBuilder,
                          SessionService sessionService,
                          PatternMatchService patternMatchService,
                          BinaryCodeResolver binaryCodeResolver,
                          ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.sessionService = sessionService;
        this.patternMatchService = patternMatchService;
        this.binaryCodeResolver = binaryCodeResolver;
        this.objectMapper = objectMapper;
    }

    public AIReactResponse chat(String sessionId, String question) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return AIReactResponse.fail(ErrorCode.SESSION_ID_EMPTY, "sessionId不能为空");
        }
        if (question == null || question.trim().isEmpty()) {
            return AIReactResponse.fail(ErrorCode.AI_ANALYSIS_FAILED, "question不能为空");
        }

        SessionContext sessionContext = sessionService.getSession(sessionId);
        if (sessionContext == null) {
            return AIReactResponse.fail(ErrorCode.SESSION_NOT_FOUND, "会话不存在，请先创建会话");
        }

        AIReactResponse response = new AIReactResponse();
        response.setSessionId(sessionId);
        response.setQuestion(question);

        sessionContext.addQuestion(question);
        sessionContext.addChatMessage("user", question);

        List<AIReactResponse.ReactStep> steps = new ArrayList<>();
        List<String> scratchpad = new ArrayList<>();

        try {
            // ReAct主循环：每一轮只允许模型“选一个动作”或“直接给最终答案”。
            for (int stepIndex = 1; stepIndex <= MAX_STEPS; stepIndex++) {
                ReactDecision decision = decideNextAction(sessionContext, question, scratchpad);
                if (decision == null) {
                    break;
                }

                AIReactResponse.ReactStep step = new AIReactResponse.ReactStep();
                step.setStep(stepIndex);
                step.setThought(decision.thought);
                step.setAction(decision.action);
                step.setActionInput(decision.actionInput);

                if ("final_answer".equals(decision.action)) {
                    String answer = safeText(decision.answer, "当前无法生成有效回答，请稍后重试。");
                    step.setObservation("已生成最终回答");
                    steps.add(step);
                    response.setAnswer(answer);
                    response.setFinalThought(decision.thought);
                    break;
                }

                String observation = executeTool(decision.action, decision.actionInput, sessionContext);
                step.setObservation(observation);
                steps.add(step);
                // 将本轮的 thought / action / observation 回灌到上下文，驱动下一轮继续推理。
                scratchpad.add("Thought: " + safeText(decision.thought, ""));
                scratchpad.add("Action: " + decision.action);
                scratchpad.add("Action Input: " + safeText(decision.actionInput, ""));
                scratchpad.add("Observation: " + observation);
            }

            if (response.getAnswer() == null || response.getAnswer().isBlank()) {
                response.setAnswer(buildFallbackAnswer(sessionContext, question, scratchpad));
            }

            response.setSteps(steps);
            sessionContext.addChatMessage("assistant", response.getAnswer());
            sessionService.updateSessionContext(sessionId, sessionContext);
            return response;
        } catch (Exception e) {
            logger.error("[sessionId={}] ReAct对话失败: {}", sessionId, e.getMessage(), e);
            return AIReactResponse.fail(ErrorCode.AI_ANALYSIS_FAILED, "ReAct对话失败: " + e.getMessage());
        }
    }

    private ReactDecision decideNextAction(SessionContext sessionContext, String question, List<String> scratchpad) {
        String raw = null;
        try {
            // 把 session 摘要、近期对话和已有观察压进同一个提示词，让模型做单步决策。
            String prompt = """
                你是一个采用ReAct模式的崩溃分析助手。
                你必须在每一轮只做一件事：要么选择一个工具，要么直接给出最终答案。

                可用工具：
                1. get_session_summary: 查看当前session中的文件、历史问题和是否已有崩溃上下文
                2. get_crash_summary: 查看当前崩溃摘要，包括进程、信号、前几帧栈
                3. analyze_pattern: 执行模式匹配，获取规则侧结论
                4. resolve_top_frame: 解析栈顶代码位置和代码片段

                约束：
                - 如果已有足够信息，可以直接输出 final_answer
                - 如果当前session没有tombstone，不要强行调用 analyze_pattern 或 resolve_top_frame
                - 必须返回JSON，且字段固定为：
                  {
                    "thought": "你当前的简短思考",
                    "action": "工具名或final_answer",
                    "actionInput": "给工具的输入，没有就传空字符串",
                    "answer": "只有action是final_answer时填写，否则传空字符串"
                  }

                当前用户问题：
                %s

                会话概览：
                %s

                最近对话：
                %s

                已有观察：
                %s
                """.formatted(
                question,
                buildSessionSummary(sessionContext),
                buildHistory(sessionContext),
                scratchpad.isEmpty() ? "暂无" : String.join("\n", scratchpad)
            );

            raw = chatClient.prompt()
                .system("你是一个严谨的ReAct代理，请只返回合法JSON，不要输出Markdown。")
                .user(prompt)
                .call()
                .content();

            // 兼容模型夹带思维标签、Markdown代码块或自然语言前后缀的情况，尽量提纯出JSON。
            String cleaned = cleanupModelResponse(raw);
            String jsonCandidate = extractJsonObject(cleaned);
            String jsonText = (jsonCandidate != null && !jsonCandidate.isBlank()) ? jsonCandidate : cleaned;
            JsonNode node = objectMapper.readTree(jsonText);

            ReactDecision decision = new ReactDecision();
            decision.thought = readText(node, "thought");
            decision.action = readText(node, "action");
            decision.actionInput = readText(node, "actionInput");
            decision.answer = readText(node, "answer");

            if (decision.action == null || decision.action.isBlank()) {
                decision.action = "final_answer";
            }
            if (!isSupportedAction(decision.action)) {
                decision.action = "final_answer";
                decision.answer = "当前工具链无法支持该动作，我基于现有上下文给出回答：" +
                    buildFallbackAnswer(sessionContext, question, scratchpad);
            }
            return decision;
        } catch (Exception e) {
            logger.warn("ReAct决策失败，降级为直接回答: {}", e.getMessage());
            ReactDecision fallback = new ReactDecision();
            fallback.thought = "模型未能稳定输出动作，直接返回自然语言回答";
            fallback.action = "final_answer";
            fallback.answer = buildNaturalLanguageFallback(sessionContext, question, scratchpad, raw);
            fallback.actionInput = "";
            return fallback;
        }
    }

    private boolean isSupportedAction(String action) {
        return "get_session_summary".equals(action)
            || "get_crash_summary".equals(action)
            || "analyze_pattern".equals(action)
            || "resolve_top_frame".equals(action)
            || "final_answer".equals(action);
    }

    private String executeTool(String action, String actionInput, SessionContext sessionContext) {
        // 这里把已有 session 能力包装成 ReAct 可消费的“观察结果”，不引入额外副作用。
        return switch (action) {
            case "get_session_summary" -> buildSessionSummary(sessionContext);
            case "get_crash_summary" -> buildCrashSummary(sessionContext.getTombstone());
            case "analyze_pattern" -> buildPatternSummary(sessionContext.getTombstone());
            case "resolve_top_frame" -> buildCodeLocationSummary(sessionContext.getTombstone());
            default -> "未知工具: " + action;
        };
    }

    private String buildSessionSummary(SessionContext sessionContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("sessionId=").append(sessionContext.getSessionId()).append('\n');
        sb.append("questions=").append(sessionContext.getQuestions() == null ? 0 : sessionContext.getQuestions().size()).append('\n');
        sb.append("files=").append(sessionContext.getFiles() == null ? 0 : sessionContext.getFiles().size()).append('\n');
        sb.append("hasTombstone=").append(sessionContext.getTombstone() != null).append('\n');

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

        return sb.toString().trim();
    }

    private String buildCrashSummary(AArch64Tombstone tombstone) {
        if (tombstone == null) {
            return "当前session没有可用的tombstone上下文。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("process=").append(tombstone.getProcessName()).append('\n');
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
                    .append(frame.getMapsInfo())
                    .append('\n'));
        }

        return sb.toString().trim();
    }

    private String buildPatternSummary(AArch64Tombstone tombstone) {
        if (tombstone == null) {
            return "当前session没有tombstone，无法执行模式匹配。";
        }

        PatternMatchResult result = patternMatchService.analyzePattern(tombstone);
        if (result == null) {
            return "模式匹配未命中特定规则。";
        }

        return """
            confidence=%s
            result=%s
            detail=%s
            """.formatted(result.getConfidence(), safeText(result.getResult(), ""), safeText(result.getAiPrompt(), "")).trim();
    }

    private String buildCodeLocationSummary(AArch64Tombstone tombstone) {
        if (tombstone == null) {
            return "当前session没有tombstone，无法解析栈顶代码位置。";
        }

        CodeLocation codeLocation = binaryCodeResolver.resolveTopStackFrame(tombstone);
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

    private String buildHistory(SessionContext sessionContext) {
        if (sessionContext.getChatMessages() == null || sessionContext.getChatMessages().isEmpty()) {
            return "暂无历史消息";
        }

        // 只保留最近几轮消息，避免历史内容过长稀释当前问题和工具观察。
        int start = Math.max(0, sessionContext.getChatMessages().size() - MAX_HISTORY_MESSAGES);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < sessionContext.getChatMessages().size(); i++) {
            SessionContext.ChatMessage message = sessionContext.getChatMessages().get(i);
            sb.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        return sb.toString().trim();
    }

    private String buildFallbackAnswer(SessionContext sessionContext, String question, List<String> scratchpad) {
        String crashSummary = buildCrashSummary(sessionContext.getTombstone());
        if (sessionContext.getTombstone() == null) {
            return "当前会话里还没有崩溃文件上下文。我可以继续一般性回答，但如果你想要准确定位 musl/崩溃问题，需要先上传日志文件，再基于同一个 sessionId 追问。你的问题是：" + question;
        }

        // 当模型没有稳定收束到 final_answer 时，至少把已确认的上下文事实返回给调用方。
        String observations = scratchpad.isEmpty() ? crashSummary : String.join("\n", scratchpad);
        return "基于当前 session 的崩溃上下文，我的判断是：\n" + observations;
    }

    private String buildNaturalLanguageFallback(SessionContext sessionContext,
                                                String question,
                                                List<String> scratchpad,
                                                String rawModelResponse) {
        String cleaned = cleanupModelResponse(rawModelResponse);
        if (cleaned != null && !cleaned.isBlank()) {
            String jsonCandidate = extractJsonObject(cleaned);
            if (jsonCandidate == null || jsonCandidate.isBlank() || !jsonCandidate.equals(cleaned.trim())) {
                return cleaned;
            }
        }
        return buildFallbackAnswer(sessionContext, question, scratchpad);
    }

    private String cleanupModelResponse(String raw) {
        if (raw == null) {
            return "{}";
        }
        // 不同模型可能输出 <think> 或 ```json 包裹内容，这里统一清洗成可解析文本。
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

    private String safeText(String text, String defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        return text;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return "";
        }
        return field.asText();
    }

    private static class ReactDecision {
        private String thought;
        private String action;
        private String actionInput;
        private String answer;
    }
}
